package wtrack.app.data.crypto

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Atomically persists the Keystore-wrapped DEK in app-private storage (`filesDir/keystore/db_key.bin`)
 * using fsync + atomic rename, replacing deprecated `EncryptedSharedPreferences`.
 */
class WrappedKeyStore(private val keyFile: File) {
    fun exists(): Boolean = keyFile.exists() && keyFile.length() > 0L

    fun read(): WrappedPayload? {
        if (!exists()) return null
        val bytes = try {
            Files.readAllBytes(keyFile.toPath())
        } catch (_: IOException) {
            return null
        }
        return try {
            DataInputStream(bytes.inputStream()).use { input ->
                val magic = ByteArray(4)
                input.readFully(magic)
                if (!magic.contentEquals(MAGIC)) return null
                val version = input.readUnsignedByte()
                if (version != SUPPORTED_VERSION) return null
                val aliasLen = input.readUnsignedShort()
                val aliasBytes = ByteArray(aliasLen)
                input.readFully(aliasBytes)
                val ivLen = input.readUnsignedByte()
                if (ivLen <= 0 || ivLen > 32) return null
                val iv = ByteArray(ivLen)
                input.readFully(iv)
                val cipherLen = input.readInt()
                if (cipherLen <= 0 || cipherLen > 4096) return null
                val ciphertext = ByteArray(cipherLen)
                input.readFully(ciphertext)
                WrappedPayload(
                    formatVersion = version,
                    alias = String(aliasBytes, StandardCharsets.UTF_8),
                    iv = iv,
                    ciphertext = ciphertext,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun write(payload: WrappedPayload) {
        keyFile.parentFile?.mkdirs()
        val outBytes = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { out ->
                out.write(MAGIC)
                out.writeByte(payload.formatVersion)
                val aliasBytes = payload.alias.toByteArray(StandardCharsets.UTF_8)
                out.writeShort(aliasBytes.size)
                out.write(aliasBytes)
                out.writeByte(payload.iv.size)
                out.write(payload.iv)
                out.writeInt(payload.ciphertext.size)
                out.write(payload.ciphertext)
            }
            baos.toByteArray()
        }

        val tmpFile = File(keyFile.parentFile, "${keyFile.name}.tmp")
        try {
            FileOutputStream(tmpFile).use { fos ->
                fos.write(outBytes)
                fos.flush()
                fos.fd.sync()
            }
            try {
                Files.move(
                    tmpFile.toPath(),
                    keyFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tmpFile.toPath(),
                    keyFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (t: Throwable) {
            tmpFile.delete()
            throw t
        }
    }

    fun delete() {
        Files.deleteIfExists(keyFile.toPath())
        Files.deleteIfExists(File(keyFile.parentFile, "${keyFile.name}.tmp").toPath())
    }

    private companion object {
        val MAGIC = byteArrayOf('W'.code.toByte(), 'K'.code.toByte(), 'E'.code.toByte(), 'Y'.code.toByte())
        const val SUPPORTED_VERSION = 1
    }
}
