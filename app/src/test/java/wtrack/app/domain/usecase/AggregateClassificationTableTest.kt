package wtrack.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferOrigin
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.model.TransferStatus
import wtrack.integration.Direction

class AggregateClassificationTableTest {

    private val categories = listOf(
        Category(1L, "Uncategorized", "UNCATEGORIZED", null, null, isSystem = true, isArchived = false, createdAt = 1L),
        Category(2L, "Fees", "FEES", Direction.OUTFLOW, null, isSystem = true, isArchived = false, createdAt = 1L),
        Category(3L, "Cash withdrawal", "CASH_WITHDRAWAL", Direction.OUTFLOW, null, isSystem = true, isArchived = false, createdAt = 1L),
        Category(4L, "Food & Dining", null, Direction.OUTFLOW, null, isSystem = false, isArchived = false, createdAt = 1L),
        Category(5L, "Shopping", null, Direction.OUTFLOW, null, isSystem = false, isArchived = false, createdAt = 1L),
    )

    private val calculator = AggregateCalculator()

    @Test
    fun evaluatesAllSixteenRowsOfPrdSection46ClassificationTable() {
        val baseTime = 1_700_000_000_000L
        val txs = listOf(
            // Row 1: OUTFLOW + PURCHASE -> Expense in Food (50.000)
            tx(id = 1L, accountId = 1L, amount = 5_000_000L, dir = Direction.OUTFLOW, type = DomainTransactionType.PURCHASE, catId = 4L, at = baseTime),
            // Row 2: OUTFLOW + FEE -> Expense in Fees (6.500)
            tx(id = 2L, accountId = 1L, amount = 650_000L, dir = Direction.OUTFLOW, type = DomainTransactionType.FEE, catId = null, at = baseTime + 1),
            // Row 3: OUTFLOW + TRANSFER (unpaired) -> Expense in Uncategorized (100.000)
            tx(id = 3L, accountId = 1L, amount = 10_000_000L, dir = Direction.OUTFLOW, type = DomainTransactionType.TRANSFER, catId = null, at = baseTime + 2),
            // Row 4: INFLOW + TRANSFER (unpaired) -> Income (250.000)
            tx(id = 4L, accountId = 1L, amount = 25_000_000L, dir = Direction.INFLOW, type = DomainTransactionType.TRANSFER, catId = null, at = baseTime + 3),
            // Row 5: Confirmed TRANSFER pair: 502.500 out of acc 1 -> 500.000 into acc 2 -> legs excluded, 2.500 derived Fee on acc 1
            tx(id = 5L, accountId = 1L, amount = 50_250_000L, dir = Direction.OUTFLOW, type = DomainTransactionType.TRANSFER, catId = null, at = baseTime + 4),
            tx(id = 6L, accountId = 2L, amount = 50_000_000L, dir = Direction.INFLOW, type = DomainTransactionType.TRANSFER, catId = null, at = baseTime + 5),
            // Row 6: Confirmed CASH pair (ATM withdrawal out of acc 1 -> into cash acc 3, zero fee) -> legs excluded
            tx(id = 7L, accountId = 1L, amount = 20_000_000L, dir = Direction.OUTFLOW, type = DomainTransactionType.CASH, catId = 3L, at = baseTime + 6),
            tx(id = 8L, accountId = 3L, amount = 20_000_000L, dir = Direction.INFLOW, type = DomainTransactionType.CASH, catId = null, at = baseTime + 7),
            // Row 7: Unpaired OUTFLOW + CASH -> Expense in Cash withdrawal (30.000)
            tx(id = 9L, accountId = 1L, amount = 3_000_000L, dir = Direction.OUTFLOW, type = DomainTransactionType.CASH, catId = null, at = baseTime + 8),
            // Row 8: Unpaired INFLOW + CASH -> Income (40.000)
            tx(id = 10L, accountId = 3L, amount = 4_000_000L, dir = Direction.INFLOW, type = DomainTransactionType.CASH, catId = null, at = baseTime + 9),
            // Row 9: INFLOW + REFUND linked to Row 1 (Food 50.000 -> reduces Food spend by 15.000; NOT income)
            tx(id = 11L, accountId = 1L, amount = 1_500_000L, dir = Direction.INFLOW, type = DomainTransactionType.REFUND, catId = null, relatedTxId = 1L, at = baseTime + 10),
            // Row 10: Unlinked INFLOW + REFUND in Shopping -> reduces Shopping spend by 10.000; NOT income
            tx(id = 12L, accountId = 1L, amount = 1_000_000L, dir = Direction.INFLOW, type = DomainTransactionType.REFUND, catId = 5L, at = baseTime + 11),
            // Row 11: OUTFLOW + REFUND -> Excluded in MVP
            tx(id = 13L, accountId = 1L, amount = 999_000L, dir = Direction.OUTFLOW, type = DomainTransactionType.REFUND, catId = 5L, at = baseTime + 12),
            // Row 12: INFLOW + OTHER -> Income (20.000)
            tx(id = 14L, accountId = 1L, amount = 2_000_000L, dir = Direction.INFLOW, type = DomainTransactionType.OTHER, catId = null, at = baseTime + 13),
            // Row 13: OUTFLOW + OTHER -> Expense in Shopping (10.000)
            tx(id = 15L, accountId = 1L, amount = 1_000_000L, dir = Direction.OUTFLOW, type = DomainTransactionType.OTHER, catId = 5L, at = baseTime + 14),
            // Row 14: ADJUSTMENT (INFLOW +12.000, OUTFLOW -2.000) -> net +10.000 unreconciled diff only
            tx(id = 16L, accountId = 1L, amount = 1_200_000L, dir = Direction.INFLOW, type = DomainTransactionType.ADJUSTMENT, catId = null, at = baseTime + 15),
            tx(id = 17L, accountId = 2L, amount = 200_000L, dir = Direction.OUTFLOW, type = DomainTransactionType.ADJUSTMENT, catId = null, at = baseTime + 16),
            // Row 15: VOIDED transaction -> excluded completely
            tx(id = 18L, accountId = 1L, amount = 77_777_00L, dir = Direction.OUTFLOW, type = DomainTransactionType.PURCHASE, catId = 4L, status = TransactionStatus.VOIDED, at = baseTime + 17),
            // Row 16: Foreign currency USD -> excluded & counted in excludedNonBaseCurrencyCount
            tx(id = 19L, accountId = 1L, amount = 5_000L, currency = "USD", dir = Direction.OUTFLOW, type = DomainTransactionType.PURCHASE, catId = 4L, at = baseTime + 18),
        )

        val pairs = listOf(
            TransferPair(100L, outgoingTransactionId = 5L, incomingTransactionId = 6L, TransferOrigin.MANUAL, null, TransferStatus.CONFIRMED, feeMinor = 250_000L, createdAt = baseTime),
            TransferPair(101L, outgoingTransactionId = 7L, incomingTransactionId = 8L, TransferOrigin.MANUAL, null, TransferStatus.CONFIRMED, feeMinor = 0L, createdAt = baseTime),
        )

        val summary = calculator.summarizePeriod(txs, pairs, categories, baseCurrency = "IDR")

        // Income = Row4(250k) + Row8(40k) + Row12(20k) = 310.000 IDR = 31_000_000 minor
        assertThat(summary.totalIncomeMinor).isEqualTo(31_000_000L)

        // Expense = Row1(50k) + Row2(6.5k) + Row3(100k) + Row5_fee(2.5k) + Row7(30k)
        //           - Row9(15k) - Row10(10k) + Row13(10k) = 174.000 IDR = 17_400_000 minor
        assertThat(summary.totalExpenseMinor).isEqualTo(17_400_000L)
        assertThat(summary.netCashFlowMinor).isEqualTo(13_600_000L)
        assertThat(summary.unreconciledAdjustmentNetMinor).isEqualTo(1_000_000L) // +12k - 2k
        assertThat(summary.excludedNonBaseCurrencyCount).isEqualTo(1)

        // Category total invariant: sum of category breakdowns equals totalExpenseMinor
        assertThat(summary.categoryBreakdowns.sumOf { it.netSpendMinor }).isEqualTo(summary.totalExpenseMinor)

        val byName = summary.categoryBreakdowns.associateBy { it.categoryName }
        assertThat(byName["Food & Dining"]?.netSpendMinor).isEqualTo(3_500_000L) // 50k purchase - 15k linked refund
        assertThat(byName["Fees"]?.netSpendMinor).isEqualTo(900_000L) // 6.5k fee tx + 2.5k derived transfer fee
        assertThat(byName["Cash withdrawal"]?.netSpendMinor).isEqualTo(3_000_000L) // 30k unpaired ATM
        assertThat(byName["Uncategorized"]?.netSpendMinor).isEqualTo(10_000_000L) // 100k unpaired transfer
        assertThat(byName["Shopping"]?.netSpendMinor).isEqualTo(0L) // 10k OTHER - 10k unlinked refund

        assertThat(summary.derivedFees).hasSize(1)
        assertThat(summary.derivedFees.single().amountMinor).isEqualTo(250_000L)
        assertThat(summary.derivedFees.single().accountId).isEqualTo(1L) // outgoing leg account

        // Per-account filter check: Account 2 was only the INCOMING leg of Transfer 100 -> derived fee MUST NOT appear under Account 2!
        val acc2Summary = calculator.summarizePeriod(txs, pairs, categories, baseCurrency = "IDR", accountIdFilter = 2L)
        assertThat(acc2Summary.derivedFees).isEmpty()
        assertThat(acc2Summary.totalExpenseMinor).isEqualTo(0L)
        assertThat(acc2Summary.unreconciledAdjustmentNetMinor).isEqualTo(-200_000L)
    }

    @Test
    fun computesMonthOverMonthExpenseComparisonAccurately() {
        val prevStart = 1_000_000L
        val currStart = 2_000_000L
        val currEnd = 3_000_000L

        val txs = listOf(
            tx(id = 1L, accountId = 1L, amount = 10_000_000L, dir = Direction.OUTFLOW, type = DomainTransactionType.PURCHASE, catId = 4L, at = 1_500_000L),
            tx(id = 2L, accountId = 1L, amount = 15_000_000L, dir = Direction.OUTFLOW, type = DomainTransactionType.PURCHASE, catId = 4L, at = 2_500_000L),
        )

        val summary = calculator.summarizePeriod(
            transactions = txs,
            transferPairs = emptyList(),
            categories = categories,
            baseCurrency = "IDR",
            periodStartEpochMillis = currStart,
            periodEndEpochMillis = currEnd,
            previousMonthRange = prevStart to currStart,
        )

        assertThat(summary.totalExpenseMinor).isEqualTo(15_000_000L)
        assertThat(summary.previousMonthExpenseMinor).isEqualTo(10_000_000L)
        assertThat(summary.expenseDeltaVsPreviousMonthMinor).isEqualTo(5_000_000L)
        assertThat(summary.expenseDeltaPercent).isWithin(0.01).of(50.0)
    }

    @Test
    fun excludesConfirmedTransferPairsRegardlessOfLegTypeAndCountsDerivedFeeOnce() {
        val t = 1_700_000_000_000L
        // E.g. bank payment to credit card captured as OTHER/PURCHASE and manually paired as internal transfer
        val txs = listOf(
            tx(id = 1L, accountId = 1L, amount = 102_500_00L, dir = Direction.OUTFLOW, type = DomainTransactionType.OTHER, catId = 5L, at = t),
            tx(id = 2L, accountId = 2L, amount = 100_000_00L, dir = Direction.INFLOW, type = DomainTransactionType.OTHER, catId = null, at = t + 1000L),
        )
        val pairs = listOf(
            TransferPair(1L, outgoingTransactionId = 1L, incomingTransactionId = 2L, TransferOrigin.MANUAL, null, TransferStatus.CONFIRMED, feeMinor = 250_000L, createdAt = t),
        )
        val summary = calculator.summarizePeriod(txs, pairs, categories, baseCurrency = "IDR")
        assertThat(summary.totalIncomeMinor).isEqualTo(0L)
        assertThat(summary.totalExpenseMinor).isEqualTo(250_000L)
        assertThat(summary.derivedFees).hasSize(1)
    }

    private fun tx(
        id: Long,
        accountId: Long,
        amount: Long,
        currency: String = "IDR",
        dir: Direction,
        type: DomainTransactionType,
        catId: Long?,
        relatedTxId: Long? = null,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
        at: Long,
    ): Transaction = Transaction(
        id = id,
        accountId = accountId,
        sourceId = null,
        eventId = null,
        amountMinor = amount,
        currency = currency,
        direction = dir,
        transactionType = type,
        merchantId = null,
        description = null,
        occurredAt = at,
        categoryId = catId,
        categorySource = null,
        appliedRuleId = null,
        status = status,
        fingerprint = null,
        parserVersion = null,
        originalSnapshot = null,
        providerReference = null,
        relatedTransactionId = relatedTxId,
        reportedBalanceMinor = null,
        createdAt = at,
        updatedAt = at,
    )
}
