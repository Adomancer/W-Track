package wtrack.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountSource
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.CreateManualTransactionRequest
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.UpdateAccountRequest
import wtrack.app.domain.repository.UpdateTransactionRequest
import wtrack.app.domain.time.Clock
import wtrack.integration.Direction

class BalanceCheckpointAndCorrectionTest {

    @Test
    fun balanceCalculationHonorsInitialAnchorVoidingAndAdjustments() {
        val account = Account(
            id = 1L, name = "Bank", provider = "Bank", accountType = AccountType.BANK, currency = "IDR",
            initialBalanceMinor = 100_000_00L, initialBalanceAt = 1_000L, lastReconciledAt = 1_000L, isActive = true,
        )
        val txs = listOf(
            // Before initialBalanceAt -> must NOT affect balance
            tx(1L, amount = 50_000_00L, dir = Direction.OUTFLOW, at = 500L),
            // After anchor -> -25.000
            tx(2L, amount = 25_000_00L, dir = Direction.OUTFLOW, at = 1_500L),
            // Voided -> excluded
            tx(3L, amount = 99_000_00L, dir = Direction.OUTFLOW, at = 1_600L, status = TransactionStatus.VOIDED),
            // Adjustment -> +5.000
            tx(4L, amount = 5_000_00L, dir = Direction.INFLOW, type = DomainTransactionType.ADJUSTMENT, at = 1_700L),
        )

        val estimated = AccountBalanceCalculator.calculateEstimatedBalance(account, txs)
        assertThat(estimated).isEqualTo(80_000_00L) // 100k - 25k + 5k
    }

    @Test
    fun correctBalanceInsertsSignedAdjustmentOrUpdatesReconciledTimestampWhenMatched() = runTest {
        val account = Account(
            id = 1L, name = "Main", provider = "Bank", accountType = AccountType.BANK, currency = "IDR",
            initialBalanceMinor = 50_000_00L, initialBalanceAt = 1_000L, lastReconciledAt = 1_000L, isActive = true,
        )
        val accRepo = FakeAccRepo(account)
        val txRepo = FakeTxRepo(
            tx(1L, amount = 10_000_00L, dir = Direction.OUTFLOW, at = 2_000L),
        )
        val clock = object : Clock { override fun nowEpochMillis(): Long = 5_000L }
        val useCase = CorrectBalanceUseCase(accRepo, txRepo, clock)

        // Estimated = 40.000; if actual = 40.000 -> no adjustment, updated reconciledAt
        val zeroResult = useCase(1L, actualBalanceMinor = 40_000_00L)
        assertThat(zeroResult.adjustmentTransactionId).isNull()
        assertThat(zeroResult.deltaMinor).isEqualTo(0L)
        assertThat(accRepo.acc.lastReconciledAt).isEqualTo(5_000L)

        // Actual = 32.500 while estimated = 40.000 -> OUTFLOW adjustment 7.500
        val negResult = useCase(1L, actualBalanceMinor = 32_500_00L)
        assertThat(negResult.deltaMinor).isEqualTo(-7_500_00L)
        val created = txRepo.items.last()
        assertThat(created.transactionType).isEqualTo(DomainTransactionType.ADJUSTMENT)
        assertThat(created.direction).isEqualTo(Direction.OUTFLOW)
        assertThat(created.amountMinor).isEqualTo(7_500_00L)
        assertThat(AccountBalanceCalculator.calculateEstimatedBalance(accRepo.acc, txRepo.items)).isEqualTo(32_500_00L)
    }

    @Test
    fun checkpointEvaluatorDetectsDriftWithAndWithoutOpeningBalance() {
        val evaluator = BalanceCheckpointEvaluator()

        // Without initial balance: checkpoint 1 = 100.000, tx -20.000, missed -15.000 -> checkpoint 2 reports 65.000
        val noOpeningAccount = Account(
            id = 1L, name = "NoOpening", provider = "Bank", accountType = AccountType.BANK, currency = "IDR",
            initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, isActive = true,
        )
        val noOpeningTxs = listOf(
            tx(1L, amount = 10_000_00L, dir = Direction.INFLOW, at = 1000L, reported = 100_000_00L),
            tx(2L, amount = 20_000_00L, dir = Direction.OUTFLOW, at = 2000L, reported = 65_000_00L),
        )
        val driftNoOpening = evaluator.evaluateLatest(noOpeningAccount, noOpeningTxs)!!
        assertThat(driftNoOpening.usedOpeningBalanceAnchor).isFalse()
        assertThat(driftNoOpening.expectedBalanceMinor).isEqualTo(80_000_00L)
        assertThat(driftNoOpening.reportedBalanceMinor).isEqualTo(65_000_00L)
        assertThat(driftNoOpening.driftMinor).isEqualTo(-15_000_00L)

        // With initial balance = 200.000, tx -50.000, reported = 150.000 -> zero drift
        val openingAccount = noOpeningAccount.copy(initialBalanceMinor = 200_000_00L, initialBalanceAt = 500L)
        val openingTxs = listOf(tx(1L, amount = 50_000_00L, dir = Direction.OUTFLOW, at = 1000L, reported = 150_000_00L))
        val driftWithOpening = evaluator.evaluateLatest(openingAccount, openingTxs)!!
        assertThat(driftWithOpening.usedOpeningBalanceAnchor).isTrue()
        assertThat(driftWithOpening.driftMinor).isEqualTo(0L)
    }

    @Test
    fun checkpointEvaluatorSkipsSameTimestampAndOutOfOrderReportsPerAcc07() {
        val evaluator = BalanceCheckpointEvaluator()
        val account = Account(
            id = 1L, name = "Bank", provider = "Bank", accountType = AccountType.BANK, currency = "IDR",
            initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, isActive = true,
        )

        // Same occurredAt timestamp -> ordering uncertain -> no comparison made
        val sameTimeTxs = listOf(
            tx(1L, amount = 10_000_00L, dir = Direction.OUTFLOW, at = 5_000L, reported = 90_000_00L),
            tx(2L, amount = 10_000_00L, dir = Direction.OUTFLOW, at = 5_000L, reported = 70_000_00L),
        )
        assertThat(evaluator.evaluateLatest(account, sameTimeTxs)).isNull()

        // Delivered out of order (newer occurredAt arrived before older occurredAt: createdAt reversed)
        val outOfOrderTxs = listOf(
            tx(1L, amount = 10_000_00L, dir = Direction.OUTFLOW, at = 1_000L, reported = 90_000_00L).copy(createdAt = 9_000L),
            tx(2L, amount = 10_000_00L, dir = Direction.OUTFLOW, at = 2_000L, reported = 70_000_00L).copy(createdAt = 8_000L),
        )
        assertThat(evaluator.evaluateLatest(account, outOfOrderTxs)).isNull()
    }

    private fun tx(
        id: Long,
        amount: Long,
        dir: Direction,
        type: DomainTransactionType = DomainTransactionType.PURCHASE,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        at: Long,
        reported: Long? = null,
    ) = Transaction(
        id = id, accountId = 1L, sourceId = null, eventId = null, amountMinor = amount,
        currency = "IDR", direction = dir, transactionType = type, merchantId = null,
        description = null, occurredAt = at, categoryId = null, categorySource = null,
        appliedRuleId = null, status = status, fingerprint = null, parserVersion = null,
        originalSnapshot = null, providerReference = null, relatedTransactionId = null,
        reportedBalanceMinor = reported, createdAt = at, updatedAt = at,
    )

    private class FakeAccRepo(var acc: Account) : AccountRepository {
        override val accounts: Flow<List<Account>> = MutableStateFlow(listOf(acc))
        override suspend fun account(id: Long): Account? = if (id == acc.id) acc else null
        override suspend fun getAllOnce(): List<Account> = listOf(acc)
        override suspend fun create(request: CreateAccountRequest): Long = 0L
        override suspend fun update(request: UpdateAccountRequest) {}
        override suspend fun archive(id: Long) {}
        override suspend fun restore(id: Long) {}
        override suspend fun updateLastReconciledAt(accountId: Long, atEpochMillis: Long) { acc = acc.copy(lastReconciledAt = atEpochMillis) }
        override val accountSources: Flow<List<AccountSource>> = MutableStateFlow(emptyList())
        override suspend fun getAccountSourcesForSource(sourceId: String): List<AccountSource> = emptyList()
        override suspend fun linkAccountSource(accountId: Long, sourceId: String, accountHint: String?): Long = 0L
        override suspend fun unlinkAccountSource(id: Long) {}
    }

    private class FakeTxRepo(vararg initial: Transaction) : TransactionRepository {
        val items = initial.toMutableList()
        private var nextId = 100L
        override val transactions: Flow<List<Transaction>> = MutableStateFlow(items)
        override suspend fun transaction(id: Long): Transaction? = items.firstOrNull { it.id == id }
        override suspend fun getAllOnce(): List<Transaction> = items.toList()
        override suspend fun insertManual(request: CreateManualTransactionRequest): Long = 0L
        override suspend fun insertAdjustment(accountId: Long, amountMinor: Long, currency: String, direction: Direction, occurredAt: Long, description: String?): Long {
            val id = nextId++
            items += Transaction(
                id = id,
                accountId = accountId,
                sourceId = null,
                eventId = null,
                amountMinor = amountMinor,
                currency = currency,
                direction = direction,
                transactionType = DomainTransactionType.ADJUSTMENT,
                merchantId = null,
                description = description,
                occurredAt = occurredAt,
                categoryId = null,
                categorySource = null,
                appliedRuleId = null,
                status = TransactionStatus.CONFIRMED,
                fingerprint = null,
                parserVersion = null,
                originalSnapshot = null,
                providerReference = null,
                relatedTransactionId = null,
                reportedBalanceMinor = null,
                createdAt = occurredAt,
                updatedAt = occurredAt,
            )
            return id
        }
        override suspend fun update(request: UpdateTransactionRequest) {}
        override suspend fun setCategory(transactionId: Long, categoryId: Long?, byUser: Boolean, ruleId: Long?) {}
        override suspend fun voidTransaction(id: Long) {}
        override suspend fun restoreTransaction(id: Long) {}
        override suspend fun deleteManual(id: Long) {}
        override suspend fun linkRefund(refundTransactionId: Long, originalTransactionId: Long?) {}
        override suspend fun findByProviderReference(sourceId: String, providerReference: String): Transaction? = null
        override suspend fun countUncategorizedForMerchant(merchantId: Long): Int = 0
        override suspend fun applyCategoryToUncategorizedMerchant(merchantId: Long, categoryId: Long, ruleId: Long): Int = 0
    }
}
