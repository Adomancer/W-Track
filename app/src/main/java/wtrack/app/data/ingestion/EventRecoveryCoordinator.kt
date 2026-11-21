package wtrack.app.data.ingestion

import wtrack.app.core.logging.WtrackLogger
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.time.Clock

/**
 * Recovers accepted events left in `RECEIVED`, stale `PROCESSING`, or `FAILED` states on app
 * start or listener reconnect so no accepted event remains silently stuck (ING-12).
 */
class EventRecoveryCoordinator(
    private val holder: WtrackDatabaseHolder,
    private val pipeline: IngestionPipeline,
    private val clock: Clock,
    private val logger: WtrackLogger,
) {
    suspend fun recoverUnfinished(processingGraceMillis: Long = DEFAULT_GRACE_MILLIS): Int {
        if (holder.healthState.value !is DatabaseHealthState.Ready) return 0
        val db = holder.requireDatabase()
        val staleBefore = clock.nowEpochMillis() - processingGraceMillis
        val pending = db.notificationEventDao().getUnfinishedForRecovery(staleBefore)
        if (pending.isEmpty()) return 0

        logger.logEvent("RECOVERY", "SWEEP_STARTED", mapOf("count" to pending.size.toString()))
        pending.forEach { event ->
            pipeline.processEvent(event.id)
        }
        return pending.size
    }

    companion object {
        const val DEFAULT_GRACE_MILLIS = 60_000L
    }
}
