package wtrack.app.data.crypto

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.After
import org.junit.Before
import org.junit.Test
import wtrack.app.domain.model.LockedReason

class DatabaseKeyManagerTest {
    private lateinit var tempDir: File
    private lateinit var dbFile: File
    private lateinit var keyFile: File
    private lateinit var keyStore: WrappedKeyStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("wtrack_key_test").toFile()
        dbFile = File(tempDir, "wtrack.db")
        keyFile = File(tempDir, "keystore/db_key.bin")
        keyStore = WrappedKeyStore(keyFile)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun freshInstallGeneratesWrapsAndUnwraps32ByteDek() {
        val cipher = InMemoryAesGcmCipher()
        val manager = DatabaseKeyManager(dbFile, keyStore, cipher)

        assertThat(manager.classifyStartupState()).isEqualTo(StartupDiskState.FRESH_INSTALL)

        val first = manager.obtainDatabaseKey()
        assertThat(first).isInstanceOf(DekAccessResult.Unlocked::class.java)
        val firstUnlocked = first as DekAccessResult.Unlocked
        assertThat(firstUnlocked.dek).hasLength(32)
        assertThat(keyStore.exists()).isTrue()

        // Simulate Room having created and populated the encrypted DB file.
        dbFile.writeBytes(byteArrayOf(0x11, 0x22, 0x33, 0x44))
        assertThat(manager.classifyStartupState()).isEqualTo(StartupDiskState.READY)

        val reopened = manager.obtainDatabaseKey() as DekAccessResult.Unlocked
        assertThat(reopened.dek).isEqualTo(firstUnlocked.dek)
    }

    @Test
    fun missingWrappedKeyWithExistingDbLocksWithoutDeletingDatabase() {
        val cipher = InMemoryAesGcmCipher()
        val originalDbBytes = "ENCRYPTED_SQLCIPHER_PAGES_SENTINEL".toByteArray()
        dbFile.writeBytes(originalDbBytes)

        val manager = DatabaseKeyManager(dbFile, keyStore, cipher)
        val result = manager.obtainDatabaseKey()

        assertThat(result).isInstanceOf(DekAccessResult.Locked::class.java)
        val locked = result as DekAccessResult.Locked
        assertThat(locked.reason).isEqualTo(LockedReason.KEY_MISSING)
        // Critical invariant: existing database file must never be overwritten or deleted.
        assertThat(dbFile.exists()).isTrue()
        assertThat(dbFile.readBytes()).isEqualTo(originalDbBytes)
    }

    @Test
    fun invalidatedKeystoreKeyWithExistingDbLocksWithoutDeletingDatabase() {
        val cipher = InMemoryAesGcmCipher()
        val manager = DatabaseKeyManager(dbFile, keyStore, cipher)
        manager.obtainDatabaseKey()
        val originalDbBytes = "ENCRYPTED_DATA_MUST_SURVIVE".toByteArray()
        dbFile.writeBytes(originalDbBytes)

        // Simulate OS Keystore wiping or invalidating the KEK alias.
        cipher.deleteKey(DatabaseKeyManager.DEFAULT_KEK_ALIAS)

        val result = manager.obtainDatabaseKey()
        assertThat(result).isInstanceOf(DekAccessResult.Locked::class.java)
        val locked = result as DekAccessResult.Locked
        assertThat(locked.reason).isEqualTo(LockedReason.KEY_INVALIDATED_OR_CORRUPT)
        assertThat(dbFile.readBytes()).isEqualTo(originalDbBytes)
    }

    @Test
    fun corruptWrappedFileWithExistingDbLocksSafely() {
        val cipher = InMemoryAesGcmCipher()
        dbFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        keyFile.parentFile?.mkdirs()
        keyFile.writeBytes(byteArrayOf(9, 9, 9, 9, 9))

        val manager = DatabaseKeyManager(dbFile, keyStore, cipher)
        val result = manager.obtainDatabaseKey()

        assertThat(result).isInstanceOf(DekAccessResult.Locked::class.java)
        assertThat((result as DekAccessResult.Locked).reason).isEqualTo(LockedReason.KEY_INVALIDATED_OR_CORRUPT)
        assertThat(dbFile.exists()).isTrue()
    }

    @Test
    fun orphanWrappedKeyWithoutDbRecoversCleanly() {
        val cipher = InMemoryAesGcmCipher()
        val manager = DatabaseKeyManager(dbFile, keyStore, cipher)
        val initial = (manager.obtainDatabaseKey() as DekAccessResult.Unlocked).dek.copyOf()

        // Database file does not exist yet (interrupted initial setup).
        assertThat(dbFile.exists()).isFalse()
        assertThat(manager.classifyStartupState()).isEqualTo(StartupDiskState.CORRUPT_INITIAL_STATE)

        val recovered = (manager.obtainDatabaseKey() as DekAccessResult.Unlocked).dek
        assertThat(recovered).hasLength(32)
        assertThat(recovered).isNotEqualTo(initial)
    }

    @Test
    fun rotateWrappingKeyRewrapsSameDekUnderNewAlias() {
        val cipher = InMemoryAesGcmCipher()
        val manager = DatabaseKeyManager(dbFile, keyStore, cipher)
        val originalDek = (manager.obtainDatabaseKey() as DekAccessResult.Unlocked).dek.copyOf()
        dbFile.writeBytes(byteArrayOf(7, 8, 9))

        val rotated = manager.rotateWrappingKey("wtrack_db_kek_v2")
        assertThat(rotated).isTrue()
        assertThat(keyStore.read()?.alias).isEqualTo("wtrack_db_kek_v2")

        val unwrappedAfterRotation = (manager.obtainDatabaseKey() as DekAccessResult.Unlocked).dek
        assertThat(unwrappedAfterRotation).isEqualTo(originalDek)
    }

    private class InMemoryAesGcmCipher : KeyWrappingCipher {
        private val keys = mutableMapOf<String, SecretKey>()

        override fun wrap(alias: String, plaintext: ByteArray): WrappedPayload {
            val key = keys.getOrPut(alias) {
                KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return WrappedPayload(
                formatVersion = 1,
                alias = alias,
                iv = cipher.iv,
                ciphertext = cipher.doFinal(plaintext),
            )
        }

        override fun unwrap(payload: WrappedPayload): ByteArray {
            val key = keys[payload.alias] ?: throw GeneralSecurityException("Key deleted")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.iv))
            return cipher.doFinal(payload.ciphertext)
        }

        override fun deleteKey(alias: String) {
            keys.remove(alias)
        }
    }
}
