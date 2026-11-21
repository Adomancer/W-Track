package wtrack.app.data.ingestion

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import wtrack.app.core.logging.WtrackLogger
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.local.entity.CaptureGapEntity
import wtrack.app.data.local.preferences.AppSettingsDataStore
import wtrack.app.domain.model.CaptureGapReason
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.time.Clock

/**
 * Tracks observable OS Notification Access & listener connection signals (ING-07, ING-11, ACC-06).
 */
class CaptureStatusTracker(
    private val context: Context,
    private val holder: WtrackDatabaseHolder,
    private val clock: Clock,
    private val logger: WtrackLogger,
    private val permissionGrantedCheck: () -> Boolean = {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    },
) {
    private val _isListenerConnected = MutableStateFlow(false)
    val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

    @Volatile
    private var lastObservedPermissionGranted: Boolean? = null

    fun isNotificationAccessGranted(): Boolean = permissionGrantedCheck()

    fun currentAppStatus(): CaptureStatus = when {
        !isNotificationAccessGranted() -> CaptureStatus.ATTENTION
        !_isListenerConnected.value -> CaptureStatus.DISCONNECTED
        else -> CaptureStatus.ACTIVE
    }

    suspend fun onPermissionStateChecked() {
        if (holder.healthState.value !is DatabaseHealthState.Ready) return
        val granted = isNotificationAccessGranted()
        val prev = lastObservedPermissionGranted
        lastObservedPermissionGranted = granted
        val db = holder.requireDatabase()
        val now = clock.nowEpochMillis()
        if (!granted) {
            val latestOpen = db.captureGapDao().getLatestOpen()
            val hasAllowlistedSources = db.transactionSourceDao().getAllOnce().any { it.isAllowlisted }
            val shouldRecordRevoked = (prev == true || (prev == null && hasAllowlistedSources)) &&
                latestOpen?.reason != CaptureGapReason.ACCESS_REVOKED.name
            if (shouldRecordRevoked) {
                db.captureGapDao().insert(
                    CaptureGapEntity(
                        startedAt = now,
                        endedAt = null,
                        reason = CaptureGapReason.ACCESS_REVOKED.name,
                    ),
                )
                logger.logEvent("CAPTURE", "ACCESS_REVOKED")
            }
        } else {
            val latestOpen = db.captureGapDao().getLatestOpen()
            if (prev == false || latestOpen?.reason == CaptureGapReason.ACCESS_REVOKED.name) {
                db.captureGapDao().closeOpenByReason(CaptureGapReason.ACCESS_REVOKED.name, now)
                logger.logEvent("CAPTURE", "ACCESS_RESTORED")
            }
        }
    }

    suspend fun onListenerConnected() {
        _isListenerConnected.value = true
        lastObservedPermissionGranted = isNotificationAccessGranted()
        if (holder.healthState.value !is DatabaseHealthState.Ready) return
        val db = holder.requireDatabase()
        val now = clock.nowEpochMillis()
        db.captureGapDao().closeOpenByReason(CaptureGapReason.LISTENER_DISCONNECTED.name, now)
        db.captureGapDao().closeOpenByReason(CaptureGapReason.ACCESS_REVOKED.name, now)
        logger.logEvent("CAPTURE", "LISTENER_CONNECTED")
    }

    suspend fun onListenerDisconnected() {
        _isListenerConnected.value = false
        if (holder.healthState.value !is DatabaseHealthState.Ready) return
        val db = holder.requireDatabase()
        val now = clock.nowEpochMillis()
        val reason = if (!isNotificationAccessGranted()) {
            CaptureGapReason.ACCESS_REVOKED
        } else {
            CaptureGapReason.LISTENER_DISCONNECTED
        }
        val latest = db.captureGapDao().getLatestOpen()
        if (latest == null || latest.reason != reason.name) {
            db.captureGapDao().insert(
                CaptureGapEntity(
                    startedAt = now,
                    endedAt = null,
                    reason = reason.name,
                ),
            )
        }
        logger.logEvent("CAPTURE", "LISTENER_DISCONNECTED", mapOf("gapReason" to reason.name))
    }
}

/**
 * Best-effort heartbeat estimating unobserved outages such as OS process termination without
 * `onListenerDisconnected()` callbacks (ING-14). Cannot distinguish killed process from powered-off device.
 */
class HeartbeatMonitor(
    private val holder: WtrackDatabaseHolder,
    private val settingsStore: AppSettingsDataStore,
    private val clock: Clock,
    private val logger: WtrackLogger,
) {
    suspend fun recordAlive() {
        settingsStore.updateLastAliveAt(clock.nowEpochMillis())
    }

    suspend fun checkAndRecordStaleGap(): Boolean {
        if (holder.healthState.value !is DatabaseHealthState.Ready) return false
        val settings = settingsStore.current()
        val now = clock.nowEpochMillis()
        val lastAlive = settings.lastAliveAt
        val thresholdMillis = settings.heartbeatStaleHours * MILLIS_PER_HOUR

        val recorded = if (lastAlive > 0L && (now - lastAlive) > thresholdMillis) {
            holder.requireDatabase().captureGapDao().insert(
                CaptureGapEntity(
                    startedAt = lastAlive,
                    endedAt = now,
                    reason = CaptureGapReason.HEARTBEAT_STALE.name,
                ),
            )
            logger.logEvent("CAPTURE", "HEARTBEAT_STALE_GAP")
            true
        } else {
            false
        }
        settingsStore.updateLastAliveAt(now)
        return recorded
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MILLIS = 15 * 60_000L
        private const val MILLIS_PER_HOUR = 3_600_000L
    }
}
