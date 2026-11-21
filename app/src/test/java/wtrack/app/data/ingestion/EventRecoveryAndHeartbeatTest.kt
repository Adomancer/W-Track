package wtrack.app.data.ingestion

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.domain.model.CaptureGapReason
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.integration.Direction
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventRecoveryAndHeartbeatTest {

    private lateinit var harness: IngestionTestHarness
    private val sourceId = "pkg.recovery.bank"

    @Before
    fun setUp() = runTest {
        harness = IngestionTestHarness()
        val db = harness.db()
        val now = harness.clock.now
        db.transactionSourceDao().upsert(TransactionSourceEntity(sourceId, sourceId, "Recovery Bank", true, 1, null, now, now))
        val accountId = db.accountDao().insert(
            AccountEntity(name = "Main", provider = "Recovery Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        db.accountSourceDao().insert(AccountSourceEntity(accountId = accountId, sourceId = sourceId, accountHint = null, createdAt = now))
        harness.allowlistCache.refreshSnapshotNow()
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    @Test
    fun recoversReceivedFailedAndStaleProcessingAndEscalatesAfterMaxAttempts() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val payload = "{\"text\":\"Paid Rp 55.000\",\"isGroupSummary\":false,\"isContentRedacted\":false}"

        val receivedId = db.notificationEventDao().insertIgnore(
            NotificationEventEntity(
                sourceId = sourceId,
                packageName = sourceId,
                stableEventKey = "rec-1",
                notificationKey = "k-rec",
                notificationId = 1,
                notificationTag = null,
                postedAt = now - 5_000L,
                payloadJson = payload,
                purgeAfter = null,
                processingStatus = NotificationEventStatus.RECEIVED.name,
                attemptCount = 0,
                failureReason = null,
                reviewDraftJson = null,
                receivedAt = now - 5_000L,
                processedAt = null,
                updatedAt = now - 5_000L,
            ),
        )
        val staleProcessingId = db.notificationEventDao().insertIgnore(
            NotificationEventEntity(
                sourceId = sourceId,
                packageName = sourceId,
                stableEventKey = "stale-1",
                notificationKey = "k-stale",
                notificationId = 2,
                notificationTag = null,
                postedAt = now - 90_000L,
                payloadJson = payload,
                purgeAfter = null,
                processingStatus = NotificationEventStatus.PROCESSING.name,
                attemptCount = 1,
                failureReason = null,
                reviewDraftJson = null,
                receivedAt = now - 90_000L,
                processedAt = null,
                updatedAt = now - 90_000L,
            ),
        )
        val freshProcessingId = db.notificationEventDao().insertIgnore(
            NotificationEventEntity(
                sourceId = sourceId,
                packageName = sourceId,
                stableEventKey = "fresh-1",
                notificationKey = "k-fresh",
                notificationId = 3,
                notificationTag = null,
                postedAt = now - 10_000L,
                payloadJson = payload,
                purgeAfter = null,
                processingStatus = NotificationEventStatus.PROCESSING.name,
                attemptCount = 1,
                failureReason = null,
                reviewDraftJson = null,
                receivedAt = now - 10_000L,
                processedAt = null,
                updatedAt = now - 10_000L,
            ),
        )
        val exhaustedFailedId = db.notificationEventDao().insertIgnore(
            NotificationEventEntity(
                sourceId = sourceId,
                packageName = sourceId,
                stableEventKey = "fail-3",
                notificationKey = "k-fail",
                notificationId = 4,
                notificationTag = null,
                postedAt = now - 20_000L,
                payloadJson = payload,
                purgeAfter = null,
                processingStatus = NotificationEventStatus.FAILED.name,
                attemptCount = IngestionPipeline.MAX_ATTEMPTS,
                failureReason = "IllegalStateException",
                reviewDraftJson = null,
                receivedAt = now - 20_000L,
                processedAt = null,
                updatedAt = now - 20_000L,
            ),
        )

        var counter = 0
        val parser = StubTransactionParser(sourceId) { env ->
            counter++
            ParseResult.Success(
                ParsedTransaction(
                    amountMinor = 55_000_00L + counter * 100L,
                    currency = "IDR",
                    direction = Direction.OUTFLOW,
                    type = TransactionType.PURCHASE,
                    occurredAtEpochMillis = env.postedAtEpochMillis,
                    merchantRaw = "Mart $counter",
                    description = null,
                    accountHint = null,
                    providerReference = "REF-$counter",
                    reportedBalanceMinor = null,
                ),
            )
        }
        val pipeline = harness.pipeline(parser)
        val coordinator = EventRecoveryCoordinator(harness.holder, pipeline, harness.clock, harness.logger)

        val recoveredCount = coordinator.recoverUnfinished()
        assertThat(recoveredCount).isEqualTo(3)
        assertThat(db.notificationEventDao().getById(receivedId)?.processingStatus).isEqualTo(NotificationEventStatus.PROCESSED.name)
        assertThat(db.notificationEventDao().getById(staleProcessingId)?.processingStatus).isEqualTo(NotificationEventStatus.PROCESSED.name)
        assertThat(db.notificationEventDao().getById(freshProcessingId)?.processingStatus).isEqualTo(NotificationEventStatus.PROCESSING.name)

        val escalated = db.notificationEventDao().getById(exhaustedFailedId)!!
        assertThat(escalated.processingStatus).isEqualTo(NotificationEventStatus.NEEDS_REVIEW.name)
        assertThat(escalated.failureReason).isEqualTo(ReviewReason.PIPELINE_ERROR.name)
    }

    @Test
    fun heartbeatStaleGapHonorsThresholdBoundary() = runTest {
        harness.settingsStore.setHeartbeatStaleHours(3)
        harness.heartbeatMonitor.recordAlive()
        val baseAlive = harness.clock.now

        // Exactly 3h elapsed -> NOT stale (> threshold required)
        harness.clock.advanceBy(3L * 3_600_000L)
        assertThat(harness.heartbeatMonitor.checkAndRecordStaleGap()).isFalse()
        assertThat(harness.db().captureGapDao().getAllOnce()).isEmpty()

        // Advance > 3h from updated lastAliveAt -> HEARTBEAT_STALE recorded with [lastAlive, now]
        val prevAlive = harness.clock.now
        harness.clock.advanceBy(3L * 3_600_000L + 1_000L)
        assertThat(harness.heartbeatMonitor.checkAndRecordStaleGap()).isTrue()

        val gaps = harness.db().captureGapDao().getAllOnce()
        assertThat(gaps).hasSize(1)
        assertThat(gaps.single().reason).isEqualTo(CaptureGapReason.HEARTBEAT_STALE.name)
        assertThat(gaps.single().startedAt).isEqualTo(prevAlive)
        assertThat(gaps.single().endedAt).isEqualTo(harness.clock.now)
        assertThat(baseAlive).isLessThan(prevAlive)
    }

    @Test
    fun captureStatusTrackerRecordsAndClosesPermissionAndListenerGaps() = runTest {
        val tracker = harness.captureStatusTracker
        val gapDao = harness.db().captureGapDao()

        tracker.onPermissionStateChecked()
        assertThat(tracker.currentAppStatus()).isEqualTo(CaptureStatus.DISCONNECTED)

        // Revoke permission -> ACCESS_REVOKED open gap
        harness.permissionGranted = false
        tracker.onPermissionStateChecked()
        assertThat(tracker.currentAppStatus()).isEqualTo(CaptureStatus.ATTENTION)
        assertThat(gapDao.getLatestOpen()?.reason).isEqualTo(CaptureGapReason.ACCESS_REVOKED.name)

        // Restore permission & connect listener -> open gaps closed, status ACTIVE
        harness.permissionGranted = true
        tracker.onListenerConnected()
        assertThat(tracker.currentAppStatus()).isEqualTo(CaptureStatus.ACTIVE)
        assertThat(gapDao.getLatestOpen()).isNull()

        // Disconnect listener while permission granted -> LISTENER_DISCONNECTED open gap
        harness.clock.advanceBy(5_000L)
        tracker.onListenerDisconnected()
        assertThat(tracker.currentAppStatus()).isEqualTo(CaptureStatus.DISCONNECTED)
        assertThat(gapDao.getLatestOpen()?.reason).isEqualTo(CaptureGapReason.LISTENER_DISCONNECTED.name)
    }

    @Test
    fun loggerDropsSensitiveKeysAndPrunesExpiredEntries() {
        val t0 = 1_700_000_000_000L
        harness.clock.now = t0
        harness.logger.logEvent(
            category = "INGEST",
            action = "TEST_EVENT",
            metadata = mapOf(
                "eventId" to "42",
                "sourceId" to "pkg.bank",
                "reason" to "OK",
                // Forbidden sensitive keys must NEVER be persisted
                "text" to "SECRET_TEXT_OTP_9911",
                "title" to "SECRET_TITLE",
                "amount" to "999999",
                "accountHint" to "1234",
                "merchant" to "SecretMerchant",
                "otp" to "654321",
                "pin" to "112233",
                "payload" to "RAW_PAYLOAD",
            ),
        )

        harness.clock.advanceBy(10L * 86_400_000L)
        harness.logger.logEvent("INGEST", "RECENT_EVENT", mapOf("eventId" to "43"))

        val beforePrune = harness.logFile.readText()
        assertThat(beforePrune).contains("eventId=42")
        assertThat(beforePrune).contains("eventId=43")
        assertThat(beforePrune).doesNotContain("SECRET")
        assertThat(beforePrune).doesNotContain("999999")
        assertThat(beforePrune).doesNotContain("1234")
        assertThat(beforePrune).doesNotContain("654321")
        assertThat(beforePrune).doesNotContain("112233")
        assertThat(beforePrune).doesNotContain("RAW_PAYLOAD")

        harness.logger.pruneOlderThan(7)
        val afterPrune = harness.logger.readRecentLines()
        assertThat(afterPrune).hasSize(1)
        assertThat(afterPrune.single()).contains("eventId=43")
    }
}
