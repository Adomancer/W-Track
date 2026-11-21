package wtrack.app.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import wtrack.app.domain.exception.BackupRestoreException

data class BackupCryptoConfig(
    val kdfId: Int = BackupCryptoContainer.KDF_ARGON2ID,
    val memoryKiB: Int = BackupCryptoContainer.DEFAULT_ARGON2_MEMORY_KIB,
    val iterations: Int = BackupCryptoContainer.DEFAULT_ARGON2_ITERATIONS,
    val parallelism: Int = BackupCryptoContainer.DEFAULT_ARGON2_PARALLELISM,
)

/**
 * Authenticated encrypted backup container (ADR-006 / DATA-09 / DATA-10).
 *
 * Binary layout:
 * - Magic: "WTBK" (4B)
 * - FormatVersion: uint16 BE (1)
 * - KdfId: uint8 (1 = Argon2id, 2 = PBKDF2-HMAC-SHA256)
 * - KdfMemoryKiB: uint32 BE
 * - KdfIterations: uint32 BE
 * - KdfParallelism: uint8
 * - SaltLength: uint8 (32) + Salt bytes
 * - CipherId: uint8 (1 = AES-256-GCM)
 * - IvLength: uint8 (12) + IV bytes
 * - CompressionId: uint8 (1 = GZIP)
 * - ReservedFlags: uint16 BE (0)
 * - Ciphertext: AES-256-GCM(GZIP(UTF8_JSON), AAD = FULL_HEADER_BYTES) + 128-bit GCM tag
 */
object BackupCryptoContainer {
    private val MAGIC = byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    const val SUPPORTED_FORMAT_VERSION = 1
    const val KDF_ARGON2ID = 1
    const val KDF_PBKDF2_SHA256 = 2
    const val CIPHER_AES_256_GCM = 1
    const val COMPRESSION_GZIP = 1

    const val DEFAULT_ARGON2_MEMORY_KIB = 65_536
    const val DEFAULT_ARGON2_ITERATIONS = 3
    const val DEFAULT_ARGON2_PARALLELISM = 1
    const val DEFAULT_PBKDF2_ITERATIONS = 600_000

    private const val SALT_BYTES = 32
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private const val MIN_PASSPHRASE_LENGTH = 8
    private const val MAX_ALLOWED_ARGON2_MEMORY_KIB = 262_144
    private const val MAX_ALLOWED_ARGON2_ITERATIONS = 100
    private const val MAX_ALLOWED_ARGON2_PARALLELISM = 16
    private const val MAX_ALLOWED_PBKDF2_ITERATIONS = 5_000_000
    private const val MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024

    fun encrypt(
        jsonPayload: String,
        passphrase: String,
        config: BackupCryptoConfig = BackupCryptoConfig(),
        secureRandom: SecureRandom = SecureRandom(),
    ): ByteArray {
        if (passphrase.length < MIN_PASSPHRASE_LENGTH) {
            throw BackupRestoreException("Backup passphrase must be at least $MIN_PASSPHRASE_LENGTH characters.")
        }
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)

        val memKiB = if (config.kdfId == KDF_PBKDF2_SHA256) 0 else config.memoryKiB
        val headerOut = ByteArrayOutputStream()
        DataOutputStream(headerOut).use { dos ->
            dos.write(MAGIC)
            dos.writeShort(SUPPORTED_FORMAT_VERSION)
            dos.writeByte(config.kdfId)
            dos.writeInt(memKiB)
            dos.writeInt(config.iterations)
            dos.writeByte(if (config.kdfId == KDF_PBKDF2_SHA256) 0 else config.parallelism)
            dos.writeByte(salt.size)
            dos.write(salt)
            dos.writeByte(CIPHER_AES_256_GCM)
            dos.writeByte(iv.size)
            dos.write(iv)
            dos.writeByte(COMPRESSION_GZIP)
            dos.writeShort(0)
        }
        val headerBytes = headerOut.toByteArray()

        val compressedPlaintext = gzipCompress(jsonPayload.toByteArray(StandardCharsets.UTF_8))
        val derivedKey = deriveKey(
            passphrase = passphrase,
            kdfId = config.kdfId,
            memoryKiB = memKiB,
            iterations = config.iterations,
            parallelism = config.parallelism,
            salt = salt,
        )
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(derivedKey, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(headerBytes)
            val ciphertext = cipher.doFinal(compressedPlaintext)
            ByteArray(headerBytes.size + ciphertext.size).also { out ->
                System.arraycopy(headerBytes, 0, out, 0, headerBytes.size)
                System.arraycopy(ciphertext, 0, out, headerBytes.size, ciphertext.size)
            }
        } catch (t: Throwable) {
            throw BackupRestoreException("Failed to encrypt backup payload.", t)
        } finally {
            Arrays.fill(derivedKey, 0.toByte())
            Arrays.fill(compressedPlaintext, 0.toByte())
        }
    }

    fun decrypt(
        containerBytes: ByteArray,
        passphrase: String,
    ): String {
        if (containerBytes.size < 22) {
            throw BackupRestoreException("Backup file is too short or corrupt.")
        }
        if (passphrase.isEmpty()) {
            throw BackupRestoreException("Passphrase cannot be empty.")
        }

        val bais = ByteArrayInputStream(containerBytes)
        val dis = DataInputStream(bais)

        val magic = ByteArray(4)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC)) {
            throw BackupRestoreException("Selected file is not a valid W-Track backup.")
        }

        val formatVersion = dis.readUnsignedShort()
        if (formatVersion <= 0 || formatVersion > SUPPORTED_FORMAT_VERSION) {
            throw BackupRestoreException(
                "Backup format version $formatVersion is newer than supported version $SUPPORTED_FORMAT_VERSION. Please update W-Track.",
            )
        }

        val kdfId = dis.readUnsignedByte()
        val memoryKiB = dis.readInt()
        val iterations = dis.readInt()
        val parallelism = dis.readUnsignedByte()
        val saltLen = dis.readUnsignedByte()
        if (saltLen < 16 || saltLen > 64) {
            throw BackupRestoreException("Invalid backup salt length: $saltLen")
        }
        when (kdfId) {
            KDF_ARGON2ID -> {
                if (memoryKiB <= 0 || memoryKiB > MAX_ALLOWED_ARGON2_MEMORY_KIB) {
                    throw BackupRestoreException("Invalid Argon2 memory parameter.")
                }
                if (iterations <= 0 || iterations > MAX_ALLOWED_ARGON2_ITERATIONS) {
                    throw BackupRestoreException("Invalid Argon2 iteration count.")
                }
                if (parallelism <= 0 || parallelism > MAX_ALLOWED_ARGON2_PARALLELISM) {
                    throw BackupRestoreException("Invalid Argon2 parallelism parameter.")
                }
            }
            KDF_PBKDF2_SHA256 -> {
                if (iterations <= 0 || iterations > MAX_ALLOWED_PBKDF2_ITERATIONS) {
                    throw BackupRestoreException("Invalid PBKDF2 iteration count.")
                }
            }
            else -> throw BackupRestoreException("Unsupported backup KDF ID: $kdfId")
        }
        val salt = ByteArray(saltLen)
        dis.readFully(salt)

        val cipherId = dis.readUnsignedByte()
        if (cipherId != CIPHER_AES_256_GCM) {
            throw BackupRestoreException("Unsupported backup cipher ID: $cipherId")
        }

        val ivLen = dis.readUnsignedByte()
        if (ivLen < 12 || ivLen > 16) {
            throw BackupRestoreException("Invalid GCM IV length: $ivLen")
        }
        val iv = ByteArray(ivLen)
        dis.readFully(iv)

        val compressionId = dis.readUnsignedByte()
        if (compressionId != COMPRESSION_GZIP) {
            throw BackupRestoreException("Unsupported backup compression ID: $compressionId")
        }

        dis.readUnsignedShort() // reserved flags (covered via full header AAD)
        val headerLength = containerBytes.size - bais.available()
        val headerBytes = containerBytes.copyOfRange(0, headerLength)
        val ciphertext = containerBytes.copyOfRange(headerLength, containerBytes.size)
        if (ciphertext.size <= 16) {
            throw BackupRestoreException("Backup ciphertext is truncated or corrupt.")
        }

        val derivedKey = deriveKey(
            passphrase = passphrase,
            kdfId = kdfId,
            memoryKiB = memoryKiB,
            iterations = iterations,
            parallelism = parallelism,
            salt = salt,
        )
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(derivedKey, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(headerBytes)
            val decompressedInput = cipher.doFinal(ciphertext)
            try {
                val rawJsonBytes = gzipDecompress(decompressedInput)
                String(rawJsonBytes, StandardCharsets.UTF_8)
            } finally {
                Arrays.fill(decompressedInput, 0.toByte())
            }
        } catch (t: Throwable) {
            throw BackupRestoreException("Incorrect passphrase or corrupted/tampered backup file.", t)
        } finally {
            Arrays.fill(derivedKey, 0.toByte())
        }
    }

    private fun deriveKey(
        passphrase: String,
        kdfId: Int,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
        salt: ByteArray,
    ): ByteArray {
        val output = ByteArray(KEY_BYTES)
        when (kdfId) {
            KDF_ARGON2ID -> {
                val passBytes = passphrase.toByteArray(StandardCharsets.UTF_8)
                try {
                    val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                        .withMemoryAsKB(memoryKiB)
                        .withIterations(iterations)
                        .withParallelism(parallelism.coerceAtLeast(1))
                        .withSalt(salt)
                        .build()
                    val generator = Argon2BytesGenerator()
                    generator.init(params)
                    generator.generateBytes(passBytes, output)
                } finally {
                    Arrays.fill(passBytes, 0.toByte())
                }
            }

            KDF_PBKDF2_SHA256 -> {
                val chars = passphrase.toCharArray()
                try {
                    val spec = PBEKeySpec(chars, salt, iterations, KEY_BYTES * 8)
                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    val encoded = factory.generateSecret(spec).encoded
                    System.arraycopy(encoded, 0, output, 0, KEY_BYTES)
                    Arrays.fill(encoded, 0.toByte())
                    spec.clearPassword()
                } finally {
                    Arrays.fill(chars, '\u0000')
                }
            }

            else -> throw BackupRestoreException("Unsupported backup KDF ID: $kdfId")
        }
        return output
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }

    private fun gzipDecompress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(data)).use { gis ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = gis.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_DECOMPRESSED_BYTES) {
                    throw BackupRestoreException("Backup payload exceeds maximum allowed size.")
                }
                baos.write(buffer, 0, read)
            }
        }
        return baos.toByteArray()
    }
}
