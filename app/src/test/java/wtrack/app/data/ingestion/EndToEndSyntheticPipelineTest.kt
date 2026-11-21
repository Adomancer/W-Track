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
import wtrack.app.data.repository.CategoryRepositoryImpl
import wtrack.app.data.repository.NeedsReviewRepositoryImpl
import wtrack.app.data.repository.RuleRepositoryImpl
import wtrack.app.data.repository.TransactionRepositoryImpl
import wtrack.app.domain.model.CategorySource
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.repository.SaveReviewItemRequest
import wtrack.app.domain.usecase.AggregateCalculator
import wtrack.app.domain.usecase.BalanceCheckpointEvaluator
import wtrack.app.domain.usecase.CorrectBalanceUseCase
import wtrack.app.domain.usecase.CreateRuleWithBackfillUseCase
import wtrack.app.domain.usecase.LinkTransferUseCase
import wtrack.integration.Direction
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.TransactionParser
import wtrack.integration.TransactionType
import wtrack.integration.providers.common.LocaleNumberParser
import wtrack.integration.providers.common.ParseAmountResult
import wtrack.integration.providers.common.ProviderDateTimeParser
import wtrack.integration.providers.common.ProviderNumberLocale
import wtrack.integration.providers.common.SensitiveContentGuard

/**
 * End-to-end M3 integration test joining `:integration:providers` common parsing utilities,
 * `:app` ingestion pipeline, transfer linking, rules with backfill, balance checkpoint + correction,
 * and Section 4.6 aggregates inside Robolectric SQLite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EndToEndSyntheticPipelineTest {

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
    fun syntheticFinancialFlowProducesConsistentBalancesAndAggregates() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val bankPkg = "synthetic.bank"
        val walletPkg = "synthetic.wallet"
        db.transactionSourceDao().upsert(TransactionSourceEntity(bankPkg, bankPkg, "Synthetic Bank", true, 1, null, now, now))
        db.transactionSourceDao().upsert(TransactionSourceEntity(walletPkg, walletPkg, "Synthetic Wallet", true, 1, null, now, now))

        val bankId = db.accountDao().insert(
            AccountEntity(name = "Bank Main", provider = "Synthetic", accountType = "BANK", currency = "IDR", initialBalanceMinor = 1_000_000_00L, initialBalanceAt = now - 10_000L, lastReconciledAt = now - 10_000L, createdAt = now, updatedAt = now),
        )
        val walletId = db.accountDao().insert(
            AccountEntity(name = "E-Wallet", provider = "Synthetic", accountType = "EWALLET", currency = "IDR", initialBalanceMinor = 100_000_00L, initialBalanceAt = now - 10_000L, lastReconciledAt = now - 10_000L, createdAt = now, updatedAt = now),
        )
        db.accountSourceDao().insert(AccountSourceEntity(accountId = bankId, sourceId = bankPkg, accountHint = null, createdAt = now))
        db.accountSourceDao().insert(AccountSourceEntity(accountId = walletId, sourceId = walletPkg, accountHint = null, createdAt = now))
        harness.allowlistCache.refreshSnapshotNow()

        val syntheticBankParser = object : TransactionParser {
            override val sourceId = bankPkg
            override val version = 1
            override fun supports(event: NotificationEvent) = event.packageName == bankPkg
            override suspend fun parse(event: NotificationEvent): ParseResult {
                SensitiveContentGuard.inspectPreconditions(event)?.let { return it }
                val parts = event.text.split("|")
                val amt = LocaleNumberParser.parse(parts[1], "IDR", ProviderNumberLocale.INDONESIAN) as ParseAmountResult.Parsed
                val ts = parts.getOrNull(4)?.let { ProviderDateTimeParser.parseToUtcEpochMillis(it) }
                return ParseResult.Success(
                    ParsedTransaction(
                        amountMinor = amt.amountMinor,
                        currency = "IDR",
                        direction = Direction.valueOf(parts[0]),
                        type = TransactionType.valueOf(parts[2]),
                        occurredAtEpochMillis = ts,
                        merchantRaw = parts[3],
                        description = parts[3],
                        accountHint = null,
                        providerReference = parts.getOrNull(5),
                        reportedBalanceMinor = parts.getOrNull(6)?.toLongOrNull(),
                    ),
                )
            }
        }
        val syntheticWalletParser = object : TransactionParser {
            override val sourceId = walletPkg
            override val version = 1
            override fun supports(event: NotificationEvent) = event.packageName == walletPkg
            override suspend fun parse(event: NotificationEvent): ParseResult {
                SensitiveContentGuard.inspectPreconditions(event)?.let { return it }
                val parts = event.text.split("|")
                val amt = LocaleNumberParser.parse(parts[1], "IDR", ProviderNumberLocale.INDONESIAN) as ParseAmountResult.Parsed
                return ParseResult.Success(
                    ParsedTransaction(amt.amountMinor, "IDR", Direction.valueOf(parts[0]), TransactionType.valueOf(parts[2]), event.postedAtEpochMillis, parts[3], parts[3], null, null, null),
                )
            }
        }

        val pipeline = harness.pipeline(syntheticBankParser, syntheticWalletParser)

        // 1. Purchase on Bank: Rp 50.000 at Kopi Kenangan, timestamp parsed from WIB
        val buyEventId = pipeline.ingest(sampleEnvelope(sourceId = bankPkg, text = "OUTFLOW|Rp 50.000|PURCHASE|Kopi Kenangan|20/11/2026 14:30 WIB|ORD-1", notificationId = 1, notificationKey = "b1"))!!
        // 2. Top-up Bank -> Wallet: Rp 202.500 out (Bank), Rp 200.000 in (Wallet)
        val outTopupEventId = pipeline.ingest(sampleEnvelope(sourceId = bankPkg, text = "OUTFLOW|Rp 202.500|TRANSFER|Topup Wallet|20/11/2026 14:35 WIB|TRF-1", notificationId = 2, notificationKey = "b2"))!!
        val inTopupEventId = pipeline.ingest(sampleEnvelope(sourceId = walletPkg, text = "INFLOW|Rp 200.000|TRANSFER|Topup from Bank", notificationId = 3, notificationKey = "w1", postedAtEpochMillis = now + 2_000L))!!
        // 3. OTP Security notice from Bank -> ignored & purged
        val otpEventId = pipeline.ingest(sampleEnvelope(sourceId = bankPkg, text = "Kode OTP Anda 771122 jangan berikan", notificationId = 4, notificationKey = "b3"))!!
        // 4. Redacted wallet notification -> NeedsReview -> resolved manually by user as Food Rp 15.000
        val redactedId = pipeline.ingest(sampleEnvelope(sourceId = walletPkg, text = "", isContentRedacted = true, notificationId = 5, notificationKey = "w2"))!!

        val otpEvent = db.notificationEventDao().getById(otpEventId)!!
        assertThat(otpEvent.processingStatus).isEqualTo(NotificationEventStatus.IGNORED.name)
        assertThat(otpEvent.failureReason).isEqualTo("SECURITY_NOTICE")

        // Link the transfer pair (derives Rp 2.500 fee without creating a 3rd tx)
        val txRepo = TransactionRepositoryImpl(harness.holder, harness.settingsStore, harness.clock)
        val transferRepo = wtrack.app.data.repository.TransferRepositoryImpl(harness.holder, harness.clock)
        val linkUseCase = LinkTransferUseCase(txRepo, transferRepo)
        val outTopupTx = db.transactionDao().findByEventId(outTopupEventId)!!
        val inTopupTx = db.transactionDao().findByEventId(inTopupEventId)!!
        linkUseCase(outTopupTx.id, inTopupTx.id)

        // Resolve NeedsReview -> Food (categoryId = 4L) Rp 15.000 on Wallet
        val reviewRepo = NeedsReviewRepositoryImpl(harness.holder, harness.settingsStore, harness.clock)
        reviewRepo.saveResolved(
            itemId = redactedId,
            resolved = SaveReviewItemRequest(walletId, 1_500_000L, "IDR", Direction.OUTFLOW, DomainTransactionType.PURCHASE, now + 3_000L, "Snack", categoryId = 4L),
        )

        // Create category rule for Kopi Kenangan -> Food (id 4) & backfill existing purchase
        val buyTx = db.transactionDao().findByEventId(buyEventId)!!
        val ruleRepo = RuleRepositoryImpl(harness.holder, harness.clock)
        val ruleUseCase = CreateRuleWithBackfillUseCase(ruleRepo, txRepo)
        val backfillResult = ruleUseCase(
            name = "Kopi -> Food",
            merchantId = buyTx.merchantId,
            merchantKey = "Kopi Kenangan",
            descriptionFragment = null,
            accountId = null,
            direction = Direction.OUTFLOW,
            categoryId = 4L,
            applyBackfill = true,
        )
        assertThat(backfillResult.backfilledCount).isEqualTo(1)
        assertThat(db.transactionDao().getById(buyTx.id)?.categorySource).isEqualTo(CategorySource.RULE.name)

        // Verify Account balances:
        // Bank = 1.000.000 - 50.000 - 202.500 = 747.500 IDR (74_750_000 minor)
        // Wallet = 100.000 + 200.000 - 15.000 = 285.000 IDR (28_500_000 minor)
        val accountRepo = AccountRepositoryImpl(harness.holder, harness.clock, harness.captureStatusTracker.isListenerConnected) { harness.permissionGranted }
        val accounts = accountRepo.accounts.first().associateBy { it.id }
        assertThat(accounts[bankId]?.estimatedBalanceMinor).isEqualTo(74_750_000L)
        assertThat(accounts[walletId]?.estimatedBalanceMinor).isEqualTo(28_500_000L)

        // Apply ACC-08 Balance Correction on Bank (suppose actual balance is 745.000 -> -2.500 adjustment)
        val correctBalance = CorrectBalanceUseCase(accountRepo, txRepo, harness.clock)
        correctBalance(bankId, actualBalanceMinor = 74_500_000L)

        // Verify Section 4.6 Aggregates:
        // Income = 0 (transfer pair excluded!)
        // Expense = 50.000 (Food) + 15.000 (Food) + 2.500 (Derived Transfer Fee) = 67.500 IDR (6_750_000 minor)
        // Unreconciled diff = -2.500 IDR (-250_000 minor), excluded from expense & income!
        val allTxs = txRepo.transactions.first()
        val allPairs = transferRepo.pairs.first()
        val allCats = CategoryRepositoryImpl(harness.holder, harness.clock).categories.first()
        val summary = AggregateCalculator().summarizePeriod(allTxs, allPairs, allCats, baseCurrency = "IDR")

        assertThat(summary.totalIncomeMinor).isEqualTo(0L)
        assertThat(summary.totalExpenseMinor).isEqualTo(6_750_000L)
        assertThat(summary.netCashFlowMinor).isEqualTo(-6_750_000L)
        assertThat(summary.unreconciledAdjustmentNetMinor).isEqualTo(-250_000L)
        assertThat(summary.categoryBreakdowns.associateBy { it.categoryName }["Food & Dining"]?.netSpendMinor)
            .isEqualTo(6_500_000L)
        assertThat(summary.categoryBreakdowns.associateBy { it.categoryName }["Fees"]?.netSpendMinor)
            .isEqualTo(250_000L)
        assertThat(allTxs.all { it.status == TransactionStatus.CONFIRMED }).isTrue()
        assertThat(BalanceCheckpointEvaluator().evaluateLatest(accounts[bankId]!!, allTxs)).isNull()
    }
}
