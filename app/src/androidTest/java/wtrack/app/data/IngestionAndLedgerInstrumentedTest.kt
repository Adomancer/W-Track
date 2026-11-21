package wtrack.app.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wtrack.app.data.backup.BackupCryptoConfig
import wtrack.app.data.backup.BackupService
import wtrack.app.data.backup.CsvExporter
import wtrack.app.data.backup.M3aSampleExporter
import wtrack.app.data.backup.WtrackBackupAndDiagnosticsPort
import wtrack.app.data.ingestion.EventRecoveryCoordinator
import wtrack.app.data.ingestion.IngestionPipeline
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.repository.AccountRepositoryImpl
import wtrack.app.data.repository.CategoryRepositoryImpl
import wtrack.app.data.repository.DatabaseHealthRepositoryImpl
import wtrack.app.data.repository.SourceAndEventRepositoryImpl
import wtrack.app.data.repository.TagRepositoryImpl
import wtrack.app.data.repository.TransactionRepositoryImpl
import wtrack.app.data.repository.TransferRepositoryImpl
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.CaptureGapReason
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.parser.DefaultParserRegistry
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.UpdateTransactionRequest
import wtrack.app.domain.usecase.AccountBalanceCalculator
import wtrack.app.domain.usecase.CorrectBalanceUseCase
import wtrack.app.domain.usecase.LinkTransferUseCase
import wtrack.app.domain.usecase.VoidRestoreTransactionUseCase
import wtrack.integration.Direction
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionParser
import wtrack.integration.TransactionType
import wtrack.integration.providers.ProviderParsers
import wtrack.integration.providers.bca.BcaTransactionParser
import wtrack.integration.providers.dana.DanaTransactionParser
import wtrack.integration.providers.jago.JagoTransactionParser

/**
 * Hardware-backed SQLCipher integration suite verifying real Jago/DANA/myBCA parsers,
 * 3-tier dedup, Android 16 redaction & burst handling (`ING-06`), recovery (`ING-12`),
 * unsupported sources (`ING-13`), heartbeat gaps (`ING-14`), and accounting invariants (`TEST-005`).
 */
@RunWith(AndroidJUnit4::class)
class IngestionAndLedgerInstrumentedTest {
    private lateinit var harness: InstrumentedTestHarness

    @Before
    fun setUp() {
        harness = InstrumentedTestHarness()
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    @Test
    fun runsProductionParsersDedupAndroid16EdgeCasesAndLedgerInvariantsUnderSqlCipher() = runBlocking {
        withTimeout(60_000L) {
        Log.i("WTrackIngest", "step 1: seed accounts & sources")
        val db = harness.db()
        val now = harness.clock.now
        val accountRepo = AccountRepositoryImpl(harness.holder, harness.clock, harness.captureStatusTracker.isListenerConnected, DefaultParserRegistry(ProviderParsers.registered())) { true }
        val txRepo = TransactionRepositoryImpl(harness.holder, harness.settingsStore, harness.clock)
        val transferRepo = TransferRepositoryImpl(harness.holder, harness.clock)
        val tagRepo = TagRepositoryImpl(harness.holder, harness.clock)

        val jagoAccId = accountRepo.create(CreateAccountRequest("Jago Main", "Bank Jago", AccountType.BANK, "IDR", 10_000_000L, now - 10_000L))
        val danaAccId = accountRepo.create(CreateAccountRequest("DANA Wallet", "DANA", AccountType.EWALLET, "IDR", 0L, now - 10_000L))
        val bcaAccId = accountRepo.create(CreateAccountRequest("myBCA", "BCA", AccountType.BANK, "IDR", 50_000_000L, now - 10_000L))

        listOf(
            JagoTransactionParser.SOURCE_ID to "Bank Jago",
            DanaTransactionParser.SOURCE_ID to "DANA",
            BcaTransactionParser.MYBCA_SOURCE_ID to "myBCA",
        ).forEach { (pkg, name) ->
            db.transactionSourceDao().upsert(TransactionSourceEntity(pkg, pkg, name, true, null, null, now, now))
        }
        db.accountSourceDao().insert(AccountSourceEntity(accountId = jagoAccId, sourceId = JagoTransactionParser.SOURCE_ID, accountHint = null, createdAt = now))
        db.accountSourceDao().insert(AccountSourceEntity(accountId = danaAccId, sourceId = DanaTransactionParser.SOURCE_ID, accountHint = null, createdAt = now))
        db.accountSourceDao().insert(AccountSourceEntity(accountId = bcaAccId, sourceId = BcaTransactionParser.MYBCA_SOURCE_ID, accountHint = null, createdAt = now))
        harness.allowlistCache.refreshSnapshotNow()

        val parsers = ProviderParsers.registered().toTypedArray()
        val pipeline = harness.pipeline(*parsers)
        Log.i("WTrackIngest", "step 2: ingest production templates & burst")

        // 1. Real production templates + Android 16 group summaries + DANA quest filter + sub-pocket ignore
        val jagoOutEnv = NotificationEvent(JagoTransactionParser.SOURCE_ID, JagoTransactionParser.SOURCE_ID, "Jago", "You've transferred Rp50.000 to BUDI. Need help? Contact Tanya Jago at 1500 746.", null, null, "foreground", false, false, now + 100L, "0|jago|1", 1, null, null)
        pipeline.ingest(jagoOutEnv)
        // Duplicate replay of exact same event -> must produce 0 extra rows
        assertThat(pipeline.ingest(jagoOutEnv)).isNull()
        // Android 16 group summary -> ignored cleanly
        pipeline.ingest(NotificationEvent(JagoTransactionParser.SOURCE_ID, JagoTransactionParser.SOURCE_ID, null, "", null, null, "foreground", true, false, now + 200L, "0|jago|0", 0, "0|jago|g:Aggregate_AlertingSection", null))
        // Sub-pocket <-> sub-pocket -> ignored cleanly
        pipeline.ingest(NotificationEvent(JagoTransactionParser.SOURCE_ID, JagoTransactionParser.SOURCE_ID, "Jago", "You've moved Rp1.000 from Bills Pocket to your Saving Pocket.", null, null, "foreground", false, false, now + 300L, "0|jago|2", 2, null, null))
        // DANA quest promo + real inflow
        pipeline.ingest(NotificationEvent(DanaTransactionParser.SOURCE_ID, DanaTransactionParser.SOURCE_ID, "DANA", "Yay, kamu berhasil selesaikan quest!", null, null, "fcm", false, false, now + 400L, "0|dana|1", 10, null, null))
        pipeline.ingest(NotificationEvent(DanaTransactionParser.SOURCE_ID, DanaTransactionParser.SOURCE_ID, "DANA", "Rp47.500 telah diterima dari BUDI 💰", null, null, "fcm", false, false, now + 500L, "0|dana|2", 11, null, null))
        // myBCA inflow
        pipeline.ingest(NotificationEvent(BcaTransactionParser.MYBCA_SOURCE_ID, BcaTransactionParser.MYBCA_SOURCE_ID, "Financial Diary", "You received IDR 50,000.00 from AL** at Account Transfer category.", null, null, "General Notification", false, false, now + 600L, "0|bca|1", 20, null, null))

        // 2. Android 16 redacted content (ING-06) -> routes to NEEDS_REVIEW without crash
        val redactedId = pipeline.ingest(NotificationEvent(JagoTransactionParser.SOURCE_ID, JagoTransactionParser.SOURCE_ID, "Jago", "", null, null, "foreground", false, true, now + 700L, "0|jago|red", 30, null, null))!!
        assertThat(db.notificationEventDao().getById(redactedId)?.processingStatus).isEqualTo(NotificationEventStatus.NEEDS_REVIEW.name)

        // 3. Anti-collapse & rapid burst (20 distinct events same amount within seconds)
        for (i in 1..20) {
            val suffix = ('A'.code + i - 1).toChar()
            pipeline.ingest(NotificationEvent(BcaTransactionParser.MYBCA_SOURCE_ID, BcaTransactionParser.MYBCA_SOURCE_ID, "Financial Diary", "You spent IDR 10,000.00 at Bistro $suffix.", null, null, "General Notification", false, false, now + 1_000L + i, "0|bca|burst|$i", 100 + i, null, null))
        }

        val confirmedTxs = txRepo.getAllOnce()
        // 1 (Jago out) + 1 (DANA in) + 1 (myBCA in) + 20 (myBCA burst) = 23 confirmed txs
        assertThat(confirmedTxs).hasSize(23)
        Log.i("WTrackIngest", "step 3: verify ledger invariants")

        // 4. Ledger invariants: TRF-04 transfer pair + derived fee, TX-06 tags, TX-08 void/restore, ACC-08 balance correction, TX-03 originalJson immutability
        val jagoOutTx = confirmedTxs.first { it.accountId == jagoAccId }
        val danaInTx = confirmedTxs.first { it.accountId == danaAccId }
        LinkTransferUseCase(txRepo, transferRepo).invoke(jagoOutTx.id, danaInTx.id)
        assertThat(transferRepo.getAllOnce().single().feeMinor).isEqualTo(250_000L) // Rp2.500 derived fee

        tagRepo.addTagToTransaction(jagoOutTx.id, "wallet-topup")
        assertThat(tagRepo.getTagsForTransaction(jagoOutTx.id).map { it.name }).containsExactly("wallet-topup")

        val bcaOriginalTx = confirmedTxs.first { it.accountId == bcaAccId && it.direction == Direction.INFLOW }
        val origSnapshotBefore = db.transactionDao().getById(bcaOriginalTx.id)?.originalJson
        txRepo.update(UpdateTransactionRequest(bcaOriginalTx.id, bcaAccId, 55_000_00L, "IDR", Direction.INFLOW, DomainTransactionType.TRANSFER, bcaOriginalTx.occurredAt, description = "Edited"))
        assertThat(db.transactionDao().getById(bcaOriginalTx.id)?.originalJson).isEqualTo(origSnapshotBefore)

        val voidRestore = VoidRestoreTransactionUseCase(txRepo)
        voidRestore.void(bcaOriginalTx.id)
        assertThat(txRepo.transaction(bcaOriginalTx.id)?.status).isEqualTo(TransactionStatus.VOIDED)
        voidRestore.restore(bcaOriginalTx.id)
        assertThat(txRepo.transaction(bcaOriginalTx.id)?.status).isEqualTo(TransactionStatus.CONFIRMED)

        CorrectBalanceUseCase(accountRepo, txRepo, harness.clock).invoke(jagoAccId, 4_000_000L, "Reconcile")
        Log.i("WTrackIngest", "step 4: verify estimated balance & accounts flow")
        val jagoRaw = accountRepo.account(jagoAccId)!!
        assertThat(AccountBalanceCalculator.calculateEstimatedBalance(jagoRaw, txRepo.getAllOnce())).isEqualTo(4_000_000L)
        val jagoAfter = withContext(Dispatchers.IO) {
            withTimeout(10_000L) {
                accountRepo.accounts.first().first { it.id == jagoAccId }
            }
        }
        assertThat(jagoAfter.estimatedBalanceMinor).isEqualTo(4_000_000L)

        // 5. Unsupported source (ING-13) + Recovery/Escalation (ING-12) + Heartbeat stale (ING-14)
        Log.i("WTrackIngest", "step 5: unsupported source, recovery sweep, heartbeat")
        val unsupportedPkg = "id.co.bri.brimo"
        db.transactionSourceDao().upsert(TransactionSourceEntity(unsupportedPkg, unsupportedPkg, "BRImo", true, null, null, now, now))
        harness.allowlistCache.refreshSnapshotNow()
        val unsupportedId = pipeline.ingest(NotificationEvent(unsupportedPkg, unsupportedPkg, "BRImo", "Transfer Rp100.000", null, null, "ch", false, false, now + 5_000L, "k-unsup", 500, null, null))!!
        val unsupportedEvt = db.notificationEventDao().getById(unsupportedId)!!
        assertThat(unsupportedEvt.processingStatus).isEqualTo(NotificationEventStatus.IGNORED.name)
        assertThat(unsupportedEvt.failureReason).isEqualTo("NO_PARSER")
        assertThat(unsupportedEvt.payloadJson).isNull()
        assertThat(SourceAndEventRepositoryImpl(harness.holder, harness.clock).countNoParserLast30Days(unsupportedPkg, now - 1_000L)).isEqualTo(1)

        // Recovery sweep (ING-12)
        val stubSrc = "pkg.hw.recover"
        db.transactionSourceDao().upsert(TransactionSourceEntity(stubSrc, stubSrc, "Recover", true, 1, null, now, now))
        val recAcc = accountRepo.create(CreateAccountRequest("Rec", "Rec", AccountType.BANK, "IDR"))
        db.accountSourceDao().insert(AccountSourceEntity(accountId = recAcc, sourceId = stubSrc, accountHint = null, createdAt = now))
        val stubParser = object : TransactionParser {
            override val sourceId: String = stubSrc
            override val version: Int = 1
            override fun supports(event: NotificationEvent) = event.sourceId == stubSrc
            override suspend fun parse(event: NotificationEvent) = ParseResult.Success(
                ParsedTransaction(1_000_00L, "IDR", Direction.OUTFLOW, DomainTransactionType.PURCHASE.let { TransactionType.PURCHASE }, now, "RecShop", "Recovered", null, "REC-${event.notificationId}", null),
            )
        }
        val recRegistry = DefaultParserRegistry(setOf(stubParser))
        val recPipeline = IngestionPipeline(harness.holder, harness.allowlistCache, recRegistry, harness.settingsStore, harness.heartbeatMonitor, harness.clock, harness.logger)
        val coordinator = EventRecoveryCoordinator(harness.holder, recPipeline, harness.clock, harness.logger)

        val payloadJson = "{\"text\":\"Paid\"}"
        val stuckReceived = db.notificationEventDao().insertIgnore(NotificationEventEntity(sourceId = stubSrc, packageName = stubSrc, stableEventKey = "rec-1", notificationKey = "rk1", notificationId = 1, notificationTag = null, postedAt = now, payloadJson = payloadJson, purgeAfter = null, processingStatus = NotificationEventStatus.RECEIVED.name, attemptCount = 0, failureReason = null, reviewDraftJson = null, receivedAt = now, processedAt = null, updatedAt = now))
        val stuckProcessing = db.notificationEventDao().insertIgnore(NotificationEventEntity(sourceId = stubSrc, packageName = stubSrc, stableEventKey = "rec-proc", notificationKey = "rk3", notificationId = 3, notificationTag = null, postedAt = now, payloadJson = payloadJson, purgeAfter = null, processingStatus = NotificationEventStatus.PROCESSING.name, attemptCount = 1, failureReason = null, reviewDraftJson = null, receivedAt = now - 120_000L, processedAt = null, updatedAt = now - 120_000L))
        val maxFailed = db.notificationEventDao().insertIgnore(NotificationEventEntity(sourceId = stubSrc, packageName = stubSrc, stableEventKey = "rec-2", notificationKey = "rk2", notificationId = 2, notificationTag = null, postedAt = now, payloadJson = payloadJson, purgeAfter = null, processingStatus = NotificationEventStatus.FAILED.name, attemptCount = 3, failureReason = "IOException", reviewDraftJson = null, receivedAt = now, processedAt = null, updatedAt = now))
        assertThat(coordinator.recoverUnfinished()).isEqualTo(3)
        assertThat(db.notificationEventDao().getById(stuckReceived)?.processingStatus).isEqualTo(NotificationEventStatus.PROCESSED.name)
        assertThat(db.notificationEventDao().getById(stuckProcessing)?.processingStatus).isEqualTo(NotificationEventStatus.PROCESSED.name)
        val escalated = db.notificationEventDao().getById(maxFailed)!!
        assertThat(escalated.processingStatus).isEqualTo(NotificationEventStatus.NEEDS_REVIEW.name)
        assertThat(escalated.failureReason).isEqualTo(ReviewReason.PIPELINE_ERROR.name)

        // Heartbeat stale gap (ING-14)
        harness.heartbeatMonitor.recordAlive()
        harness.clock.advanceBy(4L * 3_600_000L) // > 3h default
        assertThat(harness.heartbeatMonitor.checkAndRecordStaleGap()).isTrue()
        assertThat(db.captureGapDao().getAllOnce().any { it.reason == CaptureGapReason.HEARTBEAT_STALE.name }).isTrue()

        // Capture self-check via WtrackBackupAndDiagnosticsPort
        Log.i("WTrackIngest", "step 6: self-check & re-parse proposals")
        val port = WtrackBackupAndDiagnosticsPort(
            holder = harness.holder,
            backupService = BackupService(harness.holder, harness.settingsStore, harness.allowlistCache, harness.captureStatusTracker, harness.clock, harness.logger, BackupCryptoConfig(memoryKiB = 4096, iterations = 1)),
            csvExporter = CsvExporter(),
            m3aSampleExporter = M3aSampleExporter(harness.clock),
            accountRepository = accountRepo,
            transactionRepository = txRepo,
            categoryRepository = CategoryRepositoryImpl(harness.holder, harness.clock),
            databaseHealthRepository = DatabaseHealthRepositoryImpl(harness.holder, harness.settingsStore),
            logger = harness.logger,
            allowlistCache = harness.allowlistCache,
            parserRegistry = DefaultParserRegistry(ProviderParsers.registered()),
            ingestionPipeline = pipeline,
            clock = harness.clock,
            captureStatusTracker = harness.captureStatusTracker,
        )
        val selfCheck = port.runCaptureSelfCheck()
        assertThat(selfCheck.databaseReady).isTrue()
        assertThat(selfCheck.registeredParserCount).isEqualTo(3)
        assertThat(selfCheck.dryRunParserPassed).isTrue()

        // PAR-11 user-edit guard under SQLCipher: edited myBCA tx (bcaOriginalTx) must never be proposed
        val v2BcaParser = object : TransactionParser {
            override val sourceId: String = BcaTransactionParser.MYBCA_SOURCE_ID
            override val version: Int = 2
            override fun supports(event: NotificationEvent) = event.sourceId == BcaTransactionParser.MYBCA_SOURCE_ID
            override suspend fun parse(event: NotificationEvent) = ParseResult.Success(
                ParsedTransaction(99_000_00L, "IDR", Direction.OUTFLOW, TransactionType.PURCHASE, now, "Changed", "Changed", null, null, null),
            )
        }
        val portV2 = WtrackBackupAndDiagnosticsPort(
            holder = harness.holder,
            backupService = BackupService(harness.holder, harness.settingsStore, harness.allowlistCache, harness.captureStatusTracker, harness.clock, harness.logger, BackupCryptoConfig(memoryKiB = 4096, iterations = 1)),
            csvExporter = CsvExporter(),
            m3aSampleExporter = M3aSampleExporter(harness.clock),
            accountRepository = accountRepo,
            transactionRepository = txRepo,
            categoryRepository = CategoryRepositoryImpl(harness.holder, harness.clock),
            databaseHealthRepository = DatabaseHealthRepositoryImpl(harness.holder, harness.settingsStore),
            logger = harness.logger,
            allowlistCache = harness.allowlistCache,
            parserRegistry = DefaultParserRegistry(setOf(v2BcaParser)),
            ingestionPipeline = pipeline,
            clock = harness.clock,
            captureStatusTracker = harness.captureStatusTracker,
        )
        val proposals = portV2.findReparseProposals()
        assertThat(proposals.none { it.transactionId == bcaOriginalTx.id }).isTrue()
        assertThat(proposals.any { it.sourceId == BcaTransactionParser.MYBCA_SOURCE_ID }).isTrue()
        }
    }
}
