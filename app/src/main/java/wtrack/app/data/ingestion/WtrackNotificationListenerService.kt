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
import wtrack.app.domain.repository.StatusAlertNotifier

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
    @Inject lateinit var statusAlertNotifier: StatusAlertNotifier
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
        // Always drop W-Track's own notifications before allowlist evaluation, then O(1) allowlist check (ING-04).
        if (!shouldAcceptPackage(pkg, packageName, allowlistCache::isAllowlisted)) return

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
            runCatching { statusAlertNotifier.cancelCaptureDisconnected() }
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
            runCatching { statusAlertNotifier.notifyCaptureDisconnected() }
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
        val allowlisted = active.filter { shouldAcceptPackage(it.packageName, packageName, allowlistCache::isAllowlisted) }
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

    internal companion object {
        internal fun shouldAcceptPackage(
            postedPackage: String?,
            selfPackage: String,
            isAllowlisted: (String) -> Boolean,
        ): Boolean = !postedPackage.isNullOrBlank() &&
            postedPackage != selfPackage &&
            isAllowlisted(postedPackage)
    }
}
