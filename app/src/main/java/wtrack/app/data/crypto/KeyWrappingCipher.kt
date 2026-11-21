package wtrack.app.data.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class WrappedPayload(
    val formatVersion: Int,
    val alias: String,
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

/**
 * Wraps and unwraps the 256-bit SQLCipher Database Encryption Key (DEK) using an
 * Android Keystore Key Encryption Key (KEK). Abstracted for deterministic JVM unit testing.
 */
interface KeyWrappingCipher {
    fun wrap(alias: String, plaintext: ByteArray): WrappedPayload
    fun unwrap(payload: WrappedPayload): ByteArray
    fun deleteKey(alias: String)
}

class AndroidKeyStoreWrappingCipher : KeyWrappingCipher {
    override fun wrap(alias: String, plaintext: ByteArray): WrappedPayload {
        val secretKey = getOrCreateKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext = cipher.doFinal(plaintext)
        return WrappedPayload(
            formatVersion = FORMAT_VERSION,
            alias = alias,
            iv = cipher.iv,
            ciphertext = ciphertext,
        )
    }

    override fun unwrap(payload: WrappedPayload): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(payload.alias, null) as? KeyStore.SecretKeyEntry
            ?: throw GeneralSecurityException("Keystore alias ${payload.alias} is missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, entry.secretKey, GCMParameterSpec(GCM_TAG_BITS, payload.iv))
        return cipher.doFinal(payload.ciphertext)
    }

    override fun deleteKey(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateKey(alias, strongBoxBacked = true)
            } catch (_: StrongBoxUnavailableException) {
                // Fall back gracefully to standard TEE-backed Keystore.
            } catch (_: ProviderException) {
                // Some OEM StrongBox implementations throw ProviderException when unavailable.
            }
        }
        return generateKey(alias, strongBoxBacked = false)
    }

    private fun generateKey(alias: String, strongBoxBacked: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            // AFU locked-screen capture requires the KEK to remain usable when the screen is locked
            // after initial post-boot unlock (ADR-005).
            .setUserAuthenticationRequired(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(false)
            if (strongBoxBacked) {
                builder.setIsStrongBoxBacked(true)
            }
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val FORMAT_VERSION = 1
    }
}
