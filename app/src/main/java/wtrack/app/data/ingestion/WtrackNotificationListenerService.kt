package wtrack.app.data.ingestion

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import wtrack.app.core.coroutines.DispatcherProvider
import wtrack.app.core.logging.WtrackLogger

/**
 * Provider-agnostic Android NotificationListenerService (ING-01..ING-09).
 *
 * Strictly filters by user allowlist synchronously on the Binder callback thread and hands off
 * accepted envelopes to [IngestionPipeline] on an IO coroutine without hardcoding provider rules.
 */
@AndroidEntryPoint
class WtrackNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var allowlistCache: AllowlistCache
    @Inject lateinit var ingestionPipeline: IngestionPipeline
    @Inject lateinit var recoveryCoordinator: EventRecoveryCoordinator
    @Inject lateinit var heartbeatMonitor: HeartbeatMonitor
    @Inject lateinit var captureStatusTracker: CaptureStatusTracker
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var logger: WtrackLogger

    private val serviceJob = SupervisorJob()
    private val serviceScope by lazy { CoroutineScope(serviceJob + dispatcherProvider.io) }
    private var heartbeatJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        allowlistCache.startObserving()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        val pkg = statusBarNotification.packageName ?: return
        // O(1) in-memory allowlist check: disallowed packages never allocate or hit disk (ING-04).
        if (!allowlistCache.isAllowlisted(pkg)) return

        val envelope = NotificationEnvelopeExtractor.extract(statusBarNotification)
        serviceScope.launch {
            ingestionPipeline.ingest(envelope, enforceAllowlist = true)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch {
            allowlistCache.refreshSnapshotNow()
            captureStatusTracker.onListenerConnected()
            heartbeatMonitor.checkAndRecordStaleGap()
            runCatchUpScan()
            recoveryCoordinator.recoverUnfinished()
        }
        startHeartbeatLoop()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        heartbeatJob?.cancel()
        heartbeatJob = null
        serviceScope.launch {
            captureStatusTracker.onListenerDisconnected()
        }
        runCatching {
            requestRebind(ComponentName(this, WtrackNotificationListenerService::class.java))
        }.onFailure {
            logger.logEvent("LISTENER", "REBIND_REQUEST_FAILED")
        }
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun runCatchUpScan() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val allowlisted = active.filter { allowlistCache.isAllowlisted(it.packageName) }
        if (allowlisted.isNotEmpty()) {
            logger.logEvent("LISTENER", "CATCH_UP_SCAN", mapOf("count" to allowlisted.size.toString()))
        }
        allowlisted.forEach { sbn ->
            val envelope = NotificationEnvelopeExtractor.extract(sbn)
            ingestionPipeline.ingest(envelope, enforceAllowlist = true)
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(HeartbeatMonitor.HEARTBEAT_INTERVAL_MILLIS)
                heartbeatMonitor.recordAlive()
            }
        }
    }
}
