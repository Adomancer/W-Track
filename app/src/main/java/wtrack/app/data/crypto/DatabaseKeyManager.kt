package wtrack.app.data.crypto

import java.io.File
import java.security.SecureRandom
import java.util.Arrays
import wtrack.app.domain.model.LockedReason

enum class StartupDiskState {
    FRESH_INSTALL,
    READY,
    CORRUPT_INITIAL_STATE,
    LOCKED_KEY_MISSING_OR_INVALID,
}

sealed interface DekAccessResult {
    data class Unlocked(val dek: ByteArray, val startupDiskState: StartupDiskState) : DekAccessResult
    data class Locked(val reason: LockedReason, val detail: String) : DekAccessResult
}

/**
 * Manages DEK generation, Android Keystore KEK wrapping, key rotation, and non-destructive
 * key-failure classification (ADR-005 / PRD section 9.2).
 *
 * Invariant: whenever an encrypted database file exists on disk, missing or invalid keys MUST
 * surface [DekAccessResult.Locked] and NEVER overwrite or delete the database file.
 */
class DatabaseKeyManager(
    private val dbFile: File,
    private val wrappedKeyStore: WrappedKeyStore,
    private val wrappingCipher: KeyWrappingCipher,
    private val keyAlias: String = DEFAULT_KEK_ALIAS,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun classifyStartupState(): StartupDiskState {
        val hasDb = dbFile.exists() && dbFile.length() > 0L
        val hasWrappedKey = wrappedKeyStore.exists()
        return when {
            !hasDb && !hasWrappedKey -> StartupDiskState.FRESH_INSTALL
            !hasDb && hasWrappedKey -> StartupDiskState.CORRUPT_INITIAL_STATE
            hasDb && !hasWrappedKey -> StartupDiskState.LOCKED_KEY_MISSING_OR_INVALID
            else -> StartupDiskState.READY
        }
    }

    fun obtainDatabaseKey(): DekAccessResult {
        val state = classifyStartupState()
        return when (state) {
            StartupDiskState.FRESH_INSTALL -> {
                val dek = generateRandomDek()
                try {
                    wrapAndPersist(dek, keyAlias)
                    DekAccessResult.Unlocked(dek, StartupDiskState.FRESH_INSTALL)
                } catch (t: Throwable) {
                    Arrays.fill(dek, 0.toByte())
                    DekAccessResult.Locked(
                        reason = LockedReason.KEY_INVALIDATED_OR_CORRUPT,
                        detail = t.message ?: t::class.java.simpleName,
                    )
                }
            }
            StartupDiskState.CORRUPT_INITIAL_STATE -> {
                // Safe to clear orphan key ONLY because no database file exists on disk.
                wrappedKeyStore.delete()
                val dek = generateRandomDek()
                try {
                    wrapAndPersist(dek, keyAlias)
                    DekAccessResult.Unlocked(dek, StartupDiskState.CORRUPT_INITIAL_STATE)
                } catch (t: Throwable) {
                    Arrays.fill(dek, 0.toByte())
                    DekAccessResult.Locked(
                        reason = LockedReason.KEY_INVALIDATED_OR_CORRUPT,
                        detail = t.message ?: t::class.java.simpleName,
                    )
                }
            }
            StartupDiskState.LOCKED_KEY_MISSING_OR_INVALID -> {
                DekAccessResult.Locked(
                    reason = LockedReason.KEY_MISSING,
                    detail = "Encrypted database exists on disk, but wrapped database key is missing.",
                )
            }
            StartupDiskState.READY -> {
                val payload = wrappedKeyStore.read()
                    ?: return DekAccessResult.Locked(
                        reason = LockedReason.KEY_INVALIDATED_OR_CORRUPT,
                        detail = "Wrapped database key file could not be parsed.",
                    )
                try {
                    val dek = wrappingCipher.unwrap(payload)
                    if (dek.size != DEK_BYTES) {
                        Arrays.fill(dek, 0.toByte())
                        return DekAccessResult.Locked(
                            reason = LockedReason.KEY_INVALIDATED_OR_CORRUPT,
                            detail = "Unwrapped database key had invalid length.",
                        )
                    }
                    DekAccessResult.Unlocked(dek, StartupDiskState.READY)
                } catch (t: Throwable) {
                    DekAccessResult.Locked(
                        reason = LockedReason.KEY_INVALIDATED_OR_CORRUPT,
                        detail = t.message ?: t::class.java.simpleName,
                    )
                }
            }
        }
    }

    /**
     * Re-wraps the existing DEK under a rotated Keystore alias atomically without touching SQLCipher data.
     */
    fun rotateWrappingKey(newAlias: String): Boolean {
        val existing = wrappedKeyStore.read() ?: return false
        val dek = try {
            wrappingCipher.unwrap(existing)
        } catch (_: Throwable) {
            return false
        }
        return try {
            wrapAndPersist(dek, newAlias)
            if (existing.alias != newAlias) {
                runCatching { wrappingCipher.deleteKey(existing.alias) }
            }
            true
        } finally {
            Arrays.fill(dek, 0.toByte())
        }
    }

    /**
     * Explicit destructive reset (DATA-06). Only called after double-confirmed user action.
     */
    fun wipeAllKeyMaterial() {
        val currentAlias = wrappedKeyStore.read()?.alias ?: keyAlias
        wrappedKeyStore.delete()
        runCatching { wrappingCipher.deleteKey(currentAlias) }
        runCatching { wrappingCipher.deleteKey(keyAlias) }
    }

    private fun wrapAndPersist(dek: ByteArray, alias: String) {
        val wrapped = wrappingCipher.wrap(alias, dek)
        wrappedKeyStore.write(wrapped)
    }

    private fun generateRandomDek(): ByteArray = ByteArray(DEK_BYTES).also(secureRandom::nextBytes)

    companion object {
        const val DEFAULT_KEK_ALIAS = "wtrack_db_kek_v1"
        const val DEK_BYTES = 32
    }
}
