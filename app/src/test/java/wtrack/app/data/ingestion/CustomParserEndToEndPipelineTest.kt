package wtrack.app.data.ingestion

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
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
import wtrack.app.data.repository.AccountRepositoryImpl
import wtrack.app.data.repository.CustomParserRuleRepositoryImpl
import wtrack.app.data.repository.NeedsReviewRepositoryImpl
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.repository.SaveReviewItemRequest
import wtrack.app.domain.repository.UpsertCustomParserRuleRequest
import wtrack.integration.Direction
import wtrack.integration.NotTransactionReason
import wtrack.integration.providers.ProviderParsers
import wtrack.integration.providers.dana.DanaTransactionParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomParserEndToEndPipelineTest {

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
    fun newWalletUsesFallbackReviewThenAutoParsesOncePatternIsTaught() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val seaBankPkg = "id.co.seabank.id"

        db.transactionSourceDao().upsert(
            TransactionSourceEntity(seaBankPkg, seaBankPkg, "SeaBank", true, null, null, now, now),
        )
        val seaBankAccId = db.accountDao().insert(
            AccountEntity(
                name = "SeaBank Main",
                provider = "SeaBank",
                accountType = AccountType.BANK.name,
                currency = "IDR",
                initialBalanceMinor = 0L,
                initialBalanceAt = now - 10_000L,
                lastReconciledAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.accountSourceDao().insert(
            AccountSourceEntity(accountId = seaBankAccId, sourceId = seaBankPkg, accountHint = null, createdAt = now),
        )
        harness.allowlistCache.refreshSnapshotNow()
        harness.captureStatusTracker.onListenerConnected()

        val ruleCache = CustomParserRuleCache(harness.holder, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
        val registry = SmartParserRegistry(
            builtInParsers = ProviderParsers.registered(),
            customRulesProvider = { sourceId -> ruleCache.rulesForSource(sourceId) },
            customSourceIdsProvider = { ruleCache.enabledSourceIds() },
            enableGenericFallback = true,
        )
        val pipeline = IngestionPipeline(
            holder = harness.holder,
            allowlistCache = harness.allowlistCache,
            parserRegistry = registry,
            settingsStore = harness.settingsStore,
            heartbeatMonitor = harness.heartbeatMonitor,
            clock = harness.clock,
            logger = harness.logger,
        )
        val customRuleRepo = CustomParserRuleRepositoryImpl(
            holder = harness.holder,
            ruleCache = ruleCache,
            clock = harness.clock,
            onRetryEvent = { eventId -> pipeline.processEvent(eventId) },
        )
        val accountRepo = AccountRepositoryImpl(
            holder = harness.holder,
            clock = harness.clock,
            listenerConnectedFlow = harness.captureStatusTracker.isListenerConnected,
            parserRegistry = registry,
            permissionGrantedProvider = { harness.permissionGranted },
        )
        val reviewRepo = NeedsReviewRepositoryImpl(
            holder = harness.holder,
            settingsStore = harness.settingsStore,
            clock = harness.clock,
            onRetryRequested = { eventId -> pipeline.processEvent(eventId) },
        )

        // Before any custom pattern is taught: status is UNSUPPORTED (fallback-only)
        assertThat(accountRepo.accounts.first().single { it.id == seaBankAccId }.captureStatus)
            .isEqualTo(CaptureStatus.UNSUPPORTED)

        // 1. Two unseen monetary notifications arrive -> both land in NEEDS_REVIEW pre-filled!
        val evt1 = pipeline.ingest(
            sampleEnvelope(
                sourceId = seaBankPkg,
                title = "SeaBank",
                text = "Pembayaran Rp25.000 ke Kopi Kenangan berhasil",
                notificationId = 1,
                notificationKey = "sea-1",
            ),
        )!!
        val evt2 = pipeline.ingest(
            sampleEnvelope(
                sourceId = seaBankPkg,
                title = "SeaBank",
                text = "Pembayaran Rp40.000 ke Alfamart berhasil",
                notificationId = 2,
                notificationKey = "sea-2",
            ),
        )!!
        // OTP and non-monetary marketing noise are still ignored immediately with purged payloads
        val otpEvt = pipeline.ingest(
            sampleEnvelope(
                sourceId = seaBankPkg,
                title = "OTP",
                text = "Kode OTP Anda 123456. Jangan bagikan.",
                notificationId = 3,
                notificationKey = "sea-otp",
            ),
        )!!
        val infoEvt = pipeline.ingest(
            sampleEnvelope(
                sourceId = seaBankPkg,
                title = "SeaBank",
                text = "Nikmati fitur terbaru di aplikasi kami.",
                notificationId = 4,
                notificationKey = "sea-info",
            ),
        )!!

        assertThat(db.notificationEventDao().getById(otpEvt)?.failureReason)
            .isEqualTo(NotTransactionReason.SECURITY_NOTICE.name)
        assertThat(db.notificationEventDao().getById(otpEvt)?.payloadJson).isNull()
        assertThat(db.notificationEventDao().getById(infoEvt)?.processingStatus)
            .isEqualTo(NotificationEventStatus.IGNORED.name)
        assertThat(db.notificationEventDao().getById(infoEvt)?.payloadJson).isNull()

        val reviewItems = reviewRepo.items.first()
        assertThat(reviewItems).hasSize(2)
        val firstReview = reviewItems.first { it.id == evt1 }
        assertThat(firstReview.draftAmountMinor).isEqualTo(2_500_000L)
        assertThat(firstReview.draftDirection).isEqualTo(Direction.OUTFLOW)
        assertThat(firstReview.draftMerchantRaw).isEqualTo("Kopi Kenangan")

        // 2. User confirms evt1 & remembers generated pattern
        val generated = customRuleRepo.generateTemplateFromSample(
            sampleBody = firstReview.payloadBody!!,
            merchantOrDescHint = firstReview.draftMerchantRaw,
            defaultCurrency = "IDR",
        )!!
        reviewRepo.saveResolved(
            itemId = evt1,
            resolved = SaveReviewItemRequest(
                accountId = seaBankAccId,
                amountMinor = 2_500_000L,
                currency = "IDR",
                direction = Direction.OUTFLOW,
                transactionType = DomainTransactionType.PURCHASE,
                occurredAt = firstReview.postedAt,
                description = "Kopi Kenangan",
            ),
        )
        customRuleRepo.upsertRule(
            UpsertCustomParserRuleRequest(
                sourceId = seaBankPkg,
                name = "SeaBank Purchase",
                templatePattern = generated.templatePattern,
                direction = Direction.OUTFLOW,
                transactionType = DomainTransactionType.PURCHASE,
                currency = generated.currency,
                numberLocale = generated.numberLocale,
            ),
        )
        val autoParsedPending = customRuleRepo.retryPendingReviewForSource(seaBankPkg)
        assertThat(autoParsedPending).isEqualTo(1) // evt2 automatically confirmed!
        assertThat(reviewRepo.items.first()).isEmpty()
        assertThat(accountRepo.accounts.first().single { it.id == seaBankAccId }.captureStatus)
            .isEqualTo(CaptureStatus.ACTIVE)

        // 3. New SeaBank notification auto-parses directly into confirmed ledger
        val evt3 = pipeline.ingest(
            sampleEnvelope(
                sourceId = seaBankPkg,
                title = "SeaBank",
                text = "Pembayaran Rp15.000 ke Indomaret berhasil",
                notificationId = 5,
                notificationKey = "sea-3",
            ),
        )!!
        val tx3 = db.transactionDao().findByEventId(evt3)!!
        assertThat(tx3.amountMinor).isEqualTo(1_500_000L)
        assertThat(tx3.direction).isEqualTo(Direction.OUTFLOW.name)
        assertThat(db.transactionDao().getAllOnce()).hasSize(3)
    }

    @Test
    fun customRuleCoexistsWithBuiltInDanaParserForUnseenOutgoingFormat() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val danaPkg = DanaTransactionParser.SOURCE_ID

        db.transactionSourceDao().upsert(
            TransactionSourceEntity(danaPkg, danaPkg, "DANA", true, 1, null, now, now),
        )
        val danaAccId = db.accountDao().insert(
            AccountEntity(
                name = "DANA",
                provider = "DANA",
                accountType = AccountType.EWALLET.name,
                currency = "IDR",
                initialBalanceMinor = 0L,
                initialBalanceAt = now - 10_000L,
                lastReconciledAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.accountSourceDao().insert(
            AccountSourceEntity(accountId = danaAccId, sourceId = danaPkg, accountHint = null, createdAt = now),
        )
        harness.allowlistCache.refreshSnapshotNow()

        val ruleCache = CustomParserRuleCache(harness.holder, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
        val registry = SmartParserRegistry(
            builtInParsers = ProviderParsers.registered(),
            customRulesProvider = { sourceId -> ruleCache.rulesForSource(sourceId) },
            customSourceIdsProvider = { ruleCache.enabledSourceIds() },
            enableGenericFallback = true,
        )
        val pipeline = IngestionPipeline(
            holder = harness.holder,
            allowlistCache = harness.allowlistCache,
            parserRegistry = registry,
            settingsStore = harness.settingsStore,
            heartbeatMonitor = harness.heartbeatMonitor,
            clock = harness.clock,
            logger = harness.logger,
        )
        val customRuleRepo = CustomParserRuleRepositoryImpl(
            holder = harness.holder,
            ruleCache = ruleCache,
            clock = harness.clock,
            onRetryEvent = { eventId -> pipeline.processEvent(eventId) },
        )
        customRuleRepo.upsertRule(
            UpsertCustomParserRuleRequest(
                sourceId = danaPkg,
                name = "DANA QRIS Out",
                templatePattern = "Pembayaran QRIS {AMOUNT} di {MERCHANT} berhasil",
                direction = Direction.OUTFLOW,
                transactionType = DomainTransactionType.PURCHASE,
                currency = "IDR",
                numberLocale = CustomNumberLocale.INDONESIAN,
            ),
        )

        val builtInEvt = pipeline.ingest(
            sampleEnvelope(
                sourceId = danaPkg,
                title = "DANA",
                text = "Rp50.000 telah diterima dari ANDI 💰",
                notificationId = 1,
                notificationKey = "dana-in",
            ),
        )!!
        val customEvt = pipeline.ingest(
            sampleEnvelope(
                sourceId = danaPkg,
                title = "DANA",
                text = "Pembayaran QRIS Rp18.000 di Warteg Jaya berhasil",
                notificationId = 2,
                notificationKey = "dana-out",
            ),
        )!!

        val inTx = db.transactionDao().findByEventId(builtInEvt)!!
        val outTx = db.transactionDao().findByEventId(customEvt)!!
        assertThat(inTx.amountMinor).isEqualTo(5_000_000L)
        assertThat(inTx.direction).isEqualTo(Direction.INFLOW.name)
        assertThat(outTx.amountMinor).isEqualTo(1_800_000L)
        assertThat(outTx.direction).isEqualTo(Direction.OUTFLOW.name)
    }
}
