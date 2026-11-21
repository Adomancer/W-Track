package wtrack.app.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wtrack.app.data.backup.BackupCryptoConfig
import wtrack.app.data.backup.BackupCryptoContainer
import wtrack.app.data.backup.BackupService
import wtrack.app.data.backup.CsvExporter
import wtrack.app.data.backup.M3aSampleExporter
import wtrack.app.data.backup.WtrackBackupAndDiagnosticsPort
import wtrack.app.data.ingestion.IngestionPipeline
import wtrack.app.data.ingestion.IngestionTestHarness
import wtrack.app.data.ingestion.StubTransactionParser
import wtrack.app.data.ingestion.sampleEnvelope
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.repository.AccountRepositoryImpl
import wtrack.app.data.repository.CategoryRepositoryImpl
import wtrack.app.data.repository.DatabaseHealthRepositoryImpl
import wtrack.app.data.repository.TransactionRepositoryImpl
import wtrack.app.data.repository.TransferRepositoryImpl
import wtrack.app.domain.exception.TransferPairEditBlockedException
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.CategorySource
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.parser.DefaultParserRegistry
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.CreateManualTransactionRequest
import wtrack.app.domain.repository.UpdateTransactionRequest
import wtrack.app.domain.usecase.LinkTransferUseCase
import wtrack.integration.Direction
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.TransactionParser
import wtrack.integration.TransactionType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RetainedEventReparseTest {

    private lateinit var harness: IngestionTestHarness
    private val fastArgonConfig = BackupCryptoConfig(
        kdfId = BackupCryptoContainer.KDF_ARGON2ID,
        memoryKiB = 4_096,
        iterations = 1,
        parallelism = 1,
    )

    @Before
    fun setUp() {
        harness = IngestionTestHarness()
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    private fun buildPort(vararg parsers: TransactionParser): WtrackBackupAndDiagnosticsPort {
        val registry = DefaultParserRegistry(parsers.toSet())
        val pipeline = IngestionPipeline(
            holder = harness.holder,
            allowlistCache = harness.allowlistCache,
            parserRegistry = registry,
            settingsStore = harness.settingsStore,
            heartbeatMonitor = harness.heartbeatMonitor,
            clock = harness.clock,
            logger = harness.logger,
        )
        return WtrackBackupAndDiagnosticsPort(
            holder = harness.holder,
            backupService = BackupService(harness.holder, harness.settingsStore, harness.allowlistCache, harness.captureStatusTracker, harness.clock, harness.logger, fastArgonConfig),
            csvExporter = CsvExporter(),
            m3aSampleExporter = M3aSampleExporter(harness.clock),
            accountRepository = AccountRepositoryImpl(harness.holder, harness.clock),
            transactionRepository = TransactionRepositoryImpl(harness.holder, harness.settingsStore, harness.clock),
            categoryRepository = CategoryRepositoryImpl(harness.holder, harness.clock),
            databaseHealthRepository = DatabaseHealthRepositoryImpl(harness.holder, harness.settingsStore),
            logger = harness.logger,
            allowlistCache = harness.allowlistCache,
            parserRegistry = registry,
            ingestionPipeline = pipeline,
            clock = harness.clock,
            captureStatusTracker = harness.captureStatusTracker,
        )
    }

    @Test
    fun proposesAndAppliesReparseUpdatesWhilePreservingOriginalSnapshotAndUserCategory() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val srcId = "pkg.reparse.bank"
        val accountRepo = AccountRepositoryImpl(harness.holder, harness.clock)
        val txRepo = TransactionRepositoryImpl(harness.holder, harness.settingsStore, harness.clock)
        val catRepo = CategoryRepositoryImpl(harness.holder, harness.clock)

        db.transactionSourceDao().upsert(TransactionSourceEntity(srcId, srcId, "Reparse Bank", true, 1, null, now, now))
        val accId = accountRepo.create(CreateAccountRequest("Main", "Bank", AccountType.BANK, "IDR"))
        db.accountSourceDao().insert(AccountSourceEntity(accountId = accId, sourceId = srcId, accountHint = null, createdAt = now))
        val customCatId = catRepo.create("Coffee", Direction.OUTFLOW)
        harness.allowlistCache.refreshSnapshotNow()

        val v1Parser = StubTransactionParser(srcId, version = 1) {
            ParseResult.Success(ParsedTransaction(50_000_00L, "IDR", Direction.OUTFLOW, TransactionType.OTHER, now, "Kopi", "Paid Rp50.000", null, "REF-1", null))
        }
        val eventId = harness.pipeline(v1Parser).ingest(sampleEnvelope(sourceId = srcId, text = "Paid Rp50.000 at Kopi", notificationKey = "k-reparse-1", notificationId = 1))!!
        val tx = db.transactionDao().findByEventId(eventId)!!
        txRepo.setCategory(tx.id, customCatId, byUser = true)
        val originalJsonBefore = tx.originalJson

        // Upgrade to v2 parser that classifies as PURCHASE and cleaner description
        val v2Parser = StubTransactionParser(srcId, version = 2) {
            ParseResult.Success(ParsedTransaction(55_000_00L, "IDR", Direction.OUTFLOW, TransactionType.PURCHASE, now, "Kopi", "Coffee purchase", null, "REF-1", null))
        }
        val portV2 = buildPort(v2Parser)
        val proposals = portV2.findReparseProposals()
        assertThat(proposals).hasSize(1)
        val prop = proposals.single()
        assertThat(prop.oldParserVersion).isEqualTo(1)
        assertThat(prop.newParserVersion).isEqualTo(2)
        assertThat(prop.proposedAmountMinor).isEqualTo(55_000_00L)
        assertThat(prop.proposedType).isEqualTo(DomainTransactionType.PURCHASE)

        assertThat(portV2.applyReparseProposal(prop)).isTrue()

        val updated = db.transactionDao().getById(tx.id)!!
        assertThat(updated.amountMinor).isEqualTo(55_000_00L)
        assertThat(updated.transactionType).isEqualTo(DomainTransactionType.PURCHASE.name)
        assertThat(updated.description).isEqualTo("Coffee purchase")
        assertThat(updated.parserVersion).isEqualTo(2)
        assertThat(updated.originalJson).isEqualTo(originalJsonBefore)
        assertThat(updated.categoryId).isEqualTo(customCatId)
        assertThat(updated.categorySource).isEqualTo(CategorySource.USER.name)
        assertThat(portV2.findReparseProposals()).isEmpty()
    }

    @Test
    fun neverProposesOrOverwritesUserEditedTransactionAndProtectsTransferPairs() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val srcId = "pkg.guard.bank"
        val accountRepo = AccountRepositoryImpl(harness.holder, harness.clock)
        val txRepo = TransactionRepositoryImpl(harness.holder, harness.settingsStore, harness.clock)
        val transferRepo = TransferRepositoryImpl(harness.holder, harness.clock)

        db.transactionSourceDao().upsert(TransactionSourceEntity(srcId, srcId, "Guard Bank", true, 1, null, now, now))
        val acc1 = accountRepo.create(CreateAccountRequest("Bank1", "Bank", AccountType.BANK, "IDR"))
        val acc2 = accountRepo.create(CreateAccountRequest("Wallet2", "Wallet", AccountType.EWALLET, "IDR"))
        db.accountSourceDao().insert(AccountSourceEntity(accountId = acc1, sourceId = srcId, accountHint = null, createdAt = now))
        harness.allowlistCache.refreshSnapshotNow()

        val v1Parser = StubTransactionParser(srcId, version = 1) { env ->
            if (env.notificationId == 1) {
                ParseResult.Success(ParsedTransaction(50_000_00L, "IDR", Direction.OUTFLOW, TransactionType.TRANSFER, now, "Merchant", "Initial 1", null, "R1", null))
            } else {
                ParseResult.Success(ParsedTransaction(100_000_00L, "IDR", Direction.OUTFLOW, TransactionType.TRANSFER, now, "Topup", "Initial 2", null, "R2", null))
            }
        }
        val pipelineV1 = harness.pipeline(v1Parser)
        val e1 = pipelineV1.ingest(sampleEnvelope(sourceId = srcId, notificationKey = "k-1", notificationId = 1))!!
        val e2 = pipelineV1.ingest(sampleEnvelope(sourceId = srcId, notificationKey = "k-2", notificationId = 2))!!
        val tx1 = db.transactionDao().findByEventId(e1)!!
        val tx2 = db.transactionDao().findByEventId(e2)!!

        // User edits tx1's amount manually -> PAR-11 MUST skip it!
        txRepo.update(
            UpdateTransactionRequest(
                id = tx1.id,
                accountId = acc1,
                amountMinor = 52_000_00L,
                currency = "IDR",
                direction = Direction.OUTFLOW,
                transactionType = DomainTransactionType.TRANSFER,
                occurredAt = now,
                description = "Initial 1",
            ),
        )

        // Link tx2 to an incoming 100.000 transaction on acc2
        val inId = txRepo.insertManual(
            CreateManualTransactionRequest(acc2, 100_000_00L, "IDR", Direction.INFLOW, DomainTransactionType.TRANSFER, now, description = "In"),
        )
        LinkTransferUseCase(txRepo, transferRepo).invoke(tx2.id, inId)

        // v2 parser proposes lowering outgoing leg tx2 to 80.000 (< incoming 100.000 -> invalid transfer pair!)
        val v2Parser = StubTransactionParser(srcId, version = 2) { env ->
            if (env.notificationId == 1) {
                ParseResult.Success(ParsedTransaction(99_000_00L, "IDR", Direction.OUTFLOW, TransactionType.PURCHASE, now, "Merchant", "Updated 1", null, "R1", null))
            } else {
                ParseResult.Success(ParsedTransaction(80_000_00L, "IDR", Direction.OUTFLOW, TransactionType.TRANSFER, now, "Topup", "Updated 2", null, "R2", null))
            }
        }
        val portV2 = buildPort(v2Parser)
        val proposals = portV2.findReparseProposals()
        // Only tx2 should be proposed; user-edited tx1 is skipped!
        assertThat(proposals.map { it.transactionId }).containsExactly(tx2.id)

        val err = runCatching { portV2.applyReparseProposal(proposals.single()) }.exceptionOrNull()
        assertThat(err).isInstanceOf(TransferPairEditBlockedException::class.java)
        assertThat(db.transactionDao().getById(tx2.id)?.amountMinor).isEqualTo(100_000_00L)
    }
}
