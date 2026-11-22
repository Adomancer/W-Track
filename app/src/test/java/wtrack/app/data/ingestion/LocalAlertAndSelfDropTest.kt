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
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.parser.DefaultParserRegistry
import wtrack.app.domain.repository.StatusAlertNotifier
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalAlertAndSelfDropTest {

    private lateinit var harness: IngestionTestHarness

    @Before
    fun setUp() {
        harness = IngestionTestHarness()
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    @Test
    fun selfNotificationsAreAlwaysDroppedEvenIfAllowlisted() {
        assertThat(
            WtrackNotificationListenerService.shouldAcceptPackage(
                postedPackage = "wtrack.app",
                selfPackage = "wtrack.app",
                isAllowlisted = { true },
            ),
        ).isFalse()

        assertThat(
            WtrackNotificationListenerService.shouldAcceptPackage(
                postedPackage = null,
                selfPackage = "wtrack.app",
                isAllowlisted = { true },
            ),
        ).isFalse()

        assertThat(
            WtrackNotificationListenerService.shouldAcceptPackage(
                postedPackage = "com.jago.digitalBanking",
                selfPackage = "wtrack.app",
                isAllowlisted = { it == "com.jago.digitalBanking" },
            ),
        ).isTrue()
    }

    @Test
    fun ingestionPipelineNotifiesStatusAlertNotifierWhenEventRoutesToNeedsReview() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val pkg = "pkg.test.wallet"

        db.transactionSourceDao().upsert(
            TransactionSourceEntity(pkg, pkg, "Test Wallet", true, 1, null, now, now),
        )
        val accountId = db.accountDao().insert(
            AccountEntity(
                name = "Test Wallet",
                provider = "Test Wallet",
                accountType = AccountType.EWALLET.name,
                currency = "IDR",
                initialBalanceMinor = null,
                initialBalanceAt = null,
                lastReconciledAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.accountSourceDao().insert(
            AccountSourceEntity(accountId = accountId, sourceId = pkg, accountHint = null, createdAt = now),
        )
        harness.allowlistCache.refreshSnapshotNow()

        val recordedAlerts = mutableListOf<Pair<String, Int>>()
        val fakeNotifier = object : StatusAlertNotifier {
            override suspend fun notifyNeedsReview(sourceId: String, pendingCount: Int) {
                recordedAlerts += sourceId to pendingCount
            }
            override suspend fun notifyCaptureDisconnected() = Unit
            override suspend fun cancelCaptureDisconnected() = Unit
            override suspend fun notifyBackupReminderDue() = Unit
        }
        val reviewParser = object : TransactionParser {
            override val sourceId: String = pkg
            override val version: Int = 1
            override fun supports(event: NotificationEvent): Boolean = event.sourceId == pkg || event.packageName == pkg
            override suspend fun parse(event: NotificationEvent): ParseResult =
                ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, null)
        }
        val pipeline = IngestionPipeline(
            holder = harness.holder,
            allowlistCache = harness.allowlistCache,
            parserRegistry = DefaultParserRegistry(setOf(reviewParser)),
            settingsStore = harness.settingsStore,
            heartbeatMonitor = harness.heartbeatMonitor,
            clock = harness.clock,
            logger = harness.logger,
            statusAlertNotifier = fakeNotifier,
        )

        pipeline.ingest(
            NotificationEvent(
                sourceId = pkg,
                packageName = pkg,
                title = "Payment Update",
                text = "Unrecognized message",
                bigText = null,
                subText = null,
                channelId = "tx",
                isGroupSummary = false,
                isContentRedacted = false,
                postedAtEpochMillis = now,
                notificationKey = "0|$pkg|1|null|1000",
                notificationId = 1,
                notificationTag = null,
                sourceEventReference = null,
            ),
            enforceAllowlist = true,
        )

        assertThat(recordedAlerts).containsExactly(pkg to 1)
    }
}
