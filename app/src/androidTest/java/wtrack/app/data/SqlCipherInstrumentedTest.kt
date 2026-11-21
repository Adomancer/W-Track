package wtrack.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wtrack.app.data.crypto.AndroidKeyStoreWrappingCipher
import wtrack.app.data.crypto.DatabaseKeyManager
import wtrack.app.data.crypto.WrappedKeyStore
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.LockedReason
import kotlinx.coroutines.test.runTest

/**
 * Instrumented verification for real SQLCipher native loading (`4.20.0`), ciphertext header
 * validation, reboot/reopen persistence, and key-loss non-destructive locked state (TEST-004).
 *
 * Run on physical `iQOO Z10R` (`arm64-v8a`, Android 16) during the physical-device test phase.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherInstrumentedTest {
    private lateinit var context: Context
    private val testDbName = "wtrack_instrumented_test.db"
    private lateinit var keyFile: File
    private val testAlias = "wtrack_test_kek_v1"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keyFile = File(context.filesDir, "keystore/test_db_key.bin")
        cleanupFiles()
    }

    @After
    fun tearDown() {
        cleanupFiles()
    }

    @Test
    fun encryptedDatabaseLacksPlaintextSqliteHeaderAndReopensCleanly() = runTest {
        val keyManager = DatabaseKeyManager(
            dbFile = context.getDatabasePath(testDbName),
            wrappedKeyStore = WrappedKeyStore(keyFile),
            wrappingCipher = AndroidKeyStoreWrappingCipher(),
            keyAlias = testAlias,
        )
        val holder1 = WtrackDatabaseHolder(context, keyManager, databaseName = testDbName)
        assertThat(holder1.healthState.value).isEqualTo(DatabaseHealthState.Ready)

        val now = System.currentTimeMillis()
        val id = holder1.requireDatabase().accountDao().insert(
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
        holder1.requireDatabase().close()

        val dbFile = context.getDatabasePath(testDbName)
        assertThat(dbFile.exists()).isTrue()
        val header = ByteArray(16)
        dbFile.inputStream().use { it.read(header) }
        val headerString = String(header, StandardCharsets.US_ASCII)
        assertThat(headerString).doesNotContain("SQLite format 3")

        // Simulate app restart / post-reboot reopen.
        val holder2 = WtrackDatabaseHolder(context, keyManager, databaseName = testDbName)
        assertThat(holder2.healthState.value).isEqualTo(DatabaseHealthState.Ready)
        val loaded = holder2.requireDatabase().accountDao().getById(id)
        assertThat(loaded?.name).isEqualTo("Encrypted Account")
        holder2.requireDatabase().close()

        // Simulate key loss: delete wrapped key file and reopen -> must enter Locked state.
        keyFile.delete()
        val holderLocked = WtrackDatabaseHolder(context, keyManager, databaseName = testDbName)
        assertThat(holderLocked.healthState.value).isInstanceOf(DatabaseHealthState.Locked::class.java)
        val locked = holderLocked.healthState.value as DatabaseHealthState.Locked
        assertThat(locked.reason).isEqualTo(LockedReason.KEY_MISSING)
        assertThat(dbFile.exists()).isTrue()
    }

    private fun cleanupFiles() {
        val dbFile = context.getDatabasePath(testDbName)
        listOf(
            dbFile,
            File("${dbFile.path}-wal"),
            File("${dbFile.path}-shm"),
            keyFile,
        ).forEach { if (it.exists()) it.delete() }
        runCatching { AndroidKeyStoreWrappingCipher().deleteKey(testAlias) }
    }
}
