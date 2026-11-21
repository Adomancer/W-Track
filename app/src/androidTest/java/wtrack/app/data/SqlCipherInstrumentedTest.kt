package wtrack.app.data

import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.LockedReason

/**
 * Instrumented verification for real SQLCipher native loading (`4.20.0`), ciphertext header
 * validation, StrongBox/TEE Keystore audit, reboot/reopen persistence, non-destructive
 * key-loss locked states, and explicit wipe reinitialization (`TEST-004`, `ADR-005`).
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherInstrumentedTest {
    private lateinit var harness: InstrumentedTestHarness

    @Before
    fun setUp() {
        harness = InstrumentedTestHarness()
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    @Test
    fun encryptedDatabaseLacksPlaintextHeaderAuditsKeystoreAndPreservesBytesOnKeyFailure() = runBlocking {
        withTimeout(60_000L) {
        assertThat(harness.holder.healthState.value).isEqualTo(DatabaseHealthState.Ready)

        val now = harness.clock.now
        val id = harness.db().accountDao().insert(
            AccountEntity(
                name = "Encrypted Account",
                provider = "Test",
                accountType = "BANK",
                currency = "IDR",
                initialBalanceMinor = 5_000_000L,
                initialBalanceAt = now,
                lastReconciledAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        harness.db().close()

        // 1. Audit Keystore hardware security level (StrongBox vs TEE) and AFU policy.
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secretKey = (ks.getEntry(harness.keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
        val factory = SecretKeyFactory.getInstance(secretKey.algorithm, "AndroidKeyStore")
        val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo
        assertThat(keyInfo.isUserAuthenticationRequired).isFalse()
        val levelLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (keyInfo.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
                KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> "UNKNOWN_SECURE"
                else -> "UNKNOWN(${keyInfo.securityLevel})"
            }
        } else {
            "API_PRE_31"
        }
        Log.i(
            "WTrackCryptoAudit",
            "alias=${harness.keyAlias} securityLevel=$levelLabel userAuth=${keyInfo.isUserAuthenticationRequired}",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertThat(keyInfo.securityLevel).isNotEqualTo(KeyProperties.SECURITY_LEVEL_SOFTWARE)
        }

        // 2. Verify SQLCipher ciphertext header does not start with plaintext SQLite header.
        val dbFile = harness.context.getDatabasePath(harness.dbName)
        assertThat(dbFile.exists()).isTrue()
        val rawDbBytesSnapshot = dbFile.readBytes()
        val headerString = String(rawDbBytesSnapshot.copyOfRange(0, 16), StandardCharsets.US_ASCII)
        assertThat(headerString).doesNotContain("SQLite format 3")

        // 3. Simulate app restart / post-unlock reopen.
        val holderReopened = WtrackDatabaseHolder(
            context = harness.context,
            keyManager = harness.keyManager,
            databaseName = harness.dbName,
        )
        assertThat(holderReopened.healthState.value).isEqualTo(DatabaseHealthState.Ready)
        val loaded = holderReopened.requireDatabase().accountDao().getById(id)
        assertThat(loaded?.name).isEqualTo("Encrypted Account")
        holderReopened.requireDatabase().close()

        val validWrappedBytes = harness.keyFile.readBytes()

        // 4a. Key failure: corrupted wrapped key -> KEY_INVALIDATED_OR_CORRUPT, raw DB untouched.
        harness.keyFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val holderCorruptKey = WtrackDatabaseHolder(harness.context, harness.keyManager, databaseName = harness.dbName)
        val corruptState = holderCorruptKey.healthState.value as DatabaseHealthState.Locked
        assertThat(corruptState.reason).isEqualTo(LockedReason.KEY_INVALIDATED_OR_CORRUPT)
        assertThat(dbFile.readBytes()).isEqualTo(rawDbBytesSnapshot)

        // 4b. Key failure: missing wrapped key -> KEY_MISSING, raw DB untouched.
        harness.keyFile.delete()
        val holderMissingKey = WtrackDatabaseHolder(harness.context, harness.keyManager, databaseName = harness.dbName)
        val missingState = holderMissingKey.healthState.value as DatabaseHealthState.Locked
        assertThat(missingState.reason).isEqualTo(LockedReason.KEY_MISSING)
        assertThat(dbFile.readBytes()).isEqualTo(rawDbBytesSnapshot)

        // 4c. Key failure: wrapped key present, Keystore alias deleted -> KEY_INVALIDATED_OR_CORRUPT, DB untouched.
        harness.keyFile.writeBytes(validWrappedBytes)
        harness.wrappingCipher.deleteKey(harness.keyAlias)
        val holderMissingAlias = WtrackDatabaseHolder(harness.context, harness.keyManager, databaseName = harness.dbName)
        val missingAliasState = holderMissingAlias.healthState.value as DatabaseHealthState.Locked
        assertThat(missingAliasState.reason).isEqualTo(LockedReason.KEY_INVALIDATED_OR_CORRUPT)
        assertThat(dbFile.readBytes()).isEqualTo(rawDbBytesSnapshot)

        // 5. Explicit wipe recovery transitions cleanly to Ready with seeded system categories.
        holderMissingAlias.wipeAllDataAndReinitialize()
        assertThat(holderMissingAlias.healthState.value).isEqualTo(DatabaseHealthState.Ready)
        assertThat(holderMissingAlias.requireDatabase().accountDao().getAllOnce()).isEmpty()
        assertThat(holderMissingAlias.requireDatabase().categoryDao().getAllOnce()).isNotEmpty()
        holderMissingAlias.requireDatabase().close()
        }
    }
}
