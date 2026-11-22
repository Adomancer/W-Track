package wtrack.app.domain.usecase

import wtrack.app.domain.model.AppSettings

/**
 * Pure-Kotlin state machine governing W-Track biometric / device-credential lock transitions.
 */
class AppLockStateMachine {

    data class Snapshot(
        val isLocked: Boolean = false,
        val lastBackgroundedAtMillis: Long? = null,
        val hasInitializedSettings: Boolean = false,
    )

    private var snapshot = Snapshot()

    val isLocked: Boolean
        get() = snapshot.isLocked

    fun onSettingsObserved(settings: AppSettings): Boolean {
        val current = snapshot
        if (!current.hasInitializedSettings) {
            snapshot = current.copy(
                isLocked = settings.appLockEnabled,
                lastBackgroundedAtMillis = null,
                hasInitializedSettings = true,
            )
            return snapshot.isLocked
        }

        if (!settings.appLockEnabled && (current.isLocked || current.lastBackgroundedAtMillis != null)) {
            snapshot = current.copy(
                isLocked = false,
                lastBackgroundedAtMillis = null,
            )
        }
        return snapshot.isLocked
    }

    fun onAppBackgrounded(nowMillis: Long, settings: AppSettings): Boolean {
        if (!settings.appLockEnabled) {
            snapshot = snapshot.copy(isLocked = false, lastBackgroundedAtMillis = null)
            return false
        }
        val shouldLockImmediately = settings.appLockTimeoutSeconds <= 0
        snapshot = snapshot.copy(
            isLocked = snapshot.isLocked || shouldLockImmediately,
            lastBackgroundedAtMillis = snapshot.lastBackgroundedAtMillis ?: nowMillis,
        )
        return snapshot.isLocked
    }

    fun onAppForegrounded(nowMillis: Long, settings: AppSettings): Boolean {
        if (!settings.appLockEnabled) {
            snapshot = snapshot.copy(isLocked = false, lastBackgroundedAtMillis = null)
            return false
        }
        val backgroundedAt = snapshot.lastBackgroundedAtMillis
        if (snapshot.isLocked) {
            snapshot = snapshot.copy(lastBackgroundedAtMillis = null)
            return true
        }
        val shouldLock = if (backgroundedAt != null) {
            val elapsedMillis = (nowMillis - backgroundedAt).coerceAtLeast(0L)
            val timeoutMillis = settings.appLockTimeoutSeconds.coerceAtLeast(0).toLong() * 1_000L
            elapsedMillis >= timeoutMillis
        } else {
            false
        }
        snapshot = snapshot.copy(
            isLocked = shouldLock,
            lastBackgroundedAtMillis = null,
        )
        return snapshot.isLocked
    }

    fun onUnlocked(): Boolean {
        snapshot = snapshot.copy(
            isLocked = false,
            lastBackgroundedAtMillis = null,
        )
        return false
    }

    fun reset() {
        snapshot = Snapshot()
    }
}
