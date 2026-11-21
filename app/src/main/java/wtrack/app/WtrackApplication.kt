package wtrack.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import wtrack.app.core.coroutines.DispatcherProvider
import wtrack.app.core.logging.WtrackLogger
import wtrack.app.data.di.ApplicationScope
import wtrack.app.data.ingestion.AllowlistCache
import wtrack.app.data.ingestion.CaptureStatusTracker
import wtrack.app.data.ingestion.EventRecoveryCoordinator
import wtrack.app.data.ingestion.HeartbeatMonitor
import wtrack.app.data.worker.RetentionPurgeRunner
import wtrack.app.data.worker.RetentionPurgeWorker

@HiltAndroidApp
class WtrackApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var allowlistCache: AllowlistCache
    @Inject lateinit var captureStatusTracker: CaptureStatusTracker
    @Inject lateinit var heartbeatMonitor: HeartbeatMonitor
    @Inject lateinit var recoveryCoordinator: EventRecoveryCoordinator
    @Inject lateinit var retentionPurgeRunner: RetentionPurgeRunner
    @Inject lateinit var logger: WtrackLogger
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        allowlistCache.startObserving()
        RetentionPurgeWorker.scheduleDaily(this)
        applicationScope.launch(dispatcherProvider.io) {
            retentionPurgeRunner.run()
            captureStatusTracker.onPermissionStateChecked()
            recoveryCoordinator.recoverUnfinished()
        }
    }
}
