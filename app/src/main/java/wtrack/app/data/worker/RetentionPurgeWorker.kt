package wtrack.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import wtrack.app.core.logging.WtrackLogger
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.ingestion.HeartbeatMonitor
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.repository.SourceAndEventRepository
import wtrack.app.domain.time.Clock

class RetentionPurgeRunner(
    private val holder: WtrackDatabaseHolder,
    private val sourceAndEventRepository: SourceAndEventRepository,
    private val heartbeatMonitor: HeartbeatMonitor,
    private val logger: WtrackLogger,
    private val clock: Clock,
) {
    suspend fun run(): Int {
        logger.pruneOlderThan()
        if (holder.healthState.value !is DatabaseHealthState.Ready) {
            return 0
        }
        val purged = sourceAndEventRepository.purgeExpiredPayloads(clock.nowEpochMillis())
        if (purged > 0) {
            runCatching {
                holder.requireDatabase().openHelper.writableDatabase
                    .query("PRAGMA incremental_vacuum")
                    .use { it.moveToFirst() }
            }
            logger.logEvent("RETENTION", "PAYLOADS_PURGED", mapOf("count" to purged.toString()))
        }
        heartbeatMonitor.checkAndRecordStaleGap()
        return purged
    }
}

/**
 * Periodic background maintenance worker enforcing notification payload retention limits
 * (`SEC-002` / `DATA-07`), 7-day log pruning, and heartbeat stale checks (`ING-14`).
 */
@HiltWorker
class RetentionPurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val holder: WtrackDatabaseHolder,
    private val sourceAndEventRepository: SourceAndEventRepository,
    private val heartbeatMonitor: HeartbeatMonitor,
    private val logger: WtrackLogger,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        RetentionPurgeRunner(
            holder = holder,
            sourceAndEventRepository = sourceAndEventRepository,
            heartbeatMonitor = heartbeatMonitor,
            logger = logger,
            clock = clock,
        ).run()
        Result.success()
    }.getOrElse {
        Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "wtrack_daily_retention_purge"

        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionPurgeWorker>(24L, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
