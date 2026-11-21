package wtrack.app.domain.usecase

import kotlin.math.abs
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.DerivedFeeRow
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.model.TransferStatus
import wtrack.integration.Direction

data class CategorySpendBreakdown(
    val categoryId: Long?,
    val categoryName: String,
    val netSpendMinor: Long,
    val transactionCount: Int,
)

data class PeriodAggregateSummary(
    val baseCurrency: String,
    val totalIncomeMinor: Long,
    val totalExpenseMinor: Long,
    val netCashFlowMinor: Long,
    val unreconciledAdjustmentNetMinor: Long,
    val excludedNonBaseCurrencyCount: Int,
    val categoryBreakdowns: List<CategorySpendBreakdown>,
    val derivedFees: List<DerivedFeeRow>,
    val previousMonthExpenseMinor: Long? = null,
    val expenseDeltaVsPreviousMonthMinor: Long? = null,
    val expenseDeltaPercent: Double? = null,
)

/**
 * Single authoritative aggregate calculator implementing the 16-row Classification Table in
 * PRD section 4.6, read-only transfer fee derivation (TRF-04), and single base currency exclusion.
 * Shared across Dashboard, Insights, and per-account summaries so totals never drift.
 */
class AggregateCalculator {

    fun summarizePeriod(
        transactions: List<Transaction>,
        transferPairs: List<TransferPair>,
        categories: List<Category>,
        baseCurrency: String,
        periodStartEpochMillis: Long = Long.MIN_VALUE,
        periodEndEpochMillis: Long = Long.MAX_VALUE,
        previousMonthRange: Pair<Long, Long>? = null,
        accountIdFilter: Long? = null,
    ): PeriodAggregateSummary {
        val current = computeSingleWindow(
            allTransactions = transactions,
            transferPairs = transferPairs,
            categories = categories,
            baseCurrency = baseCurrency,
            startMillis = periodStartEpochMillis,
            endMillis = periodEndEpochMillis,
            accountIdFilter = accountIdFilter,
        )

        if (previousMonthRange == null) {
            return current
        }

        val previous = computeSingleWindow(
            allTransactions = transactions,
            transferPairs = transferPairs,
            categories = categories,
            baseCurrency = baseCurrency,
            startMillis = previousMonthRange.first,
            endMillis = previousMonthRange.second,
            accountIdFilter = accountIdFilter,
        )
        val delta = current.totalExpenseMinor - previous.totalExpenseMinor
        val pct = if (previous.totalExpenseMinor > 0L) {
            (delta.toDouble() / previous.totalExpenseMinor.toDouble()) * 100.0
        } else {
            null
        }
        return current.copy(
            previousMonthExpenseMinor = previous.totalExpenseMinor,
            expenseDeltaVsPreviousMonthMinor = delta,
            expenseDeltaPercent = pct,
        )
    }

    private fun computeSingleWindow(
        allTransactions: List<Transaction>,
        transferPairs: List<TransferPair>,
        categories: List<Category>,
        baseCurrency: String,
        startMillis: Long,
        endMillis: Long,
        accountIdFilter: Long?,
    ): PeriodAggregateSummary {
        val normalizedBase = baseCurrency.trim().uppercase()
        val txById = allTransactions.associateBy { it.id }
        val categoryById = categories.associateBy { it.id }
        val uncategorizedId = categories.firstOrNull { it.systemKey == "UNCATEGORIZED" }?.id
        val feesCategoryId = categories.firstOrNull { it.systemKey == "FEES" }?.id
        val cashWithdrawalCategoryId = categories.firstOrNull { it.systemKey == "CASH_WITHDRAWAL" }?.id

        // Pairs are valid only while CONFIRMED and neither leg is VOIDED (TX-08, TRF-01).
        val activeConfirmedPairs = transferPairs.filter { pair ->
            if (pair.status != TransferStatus.CONFIRMED) return@filter false
            val outLeg = txById[pair.outgoingTransactionId]
            val inLeg = txById[pair.incomingTransactionId]
            outLeg != null && inLeg != null &&
                outLeg.status == TransactionStatus.CONFIRMED &&
                inLeg.status == TransactionStatus.CONFIRMED
        }
        val pairedTxIds = activeConfirmedPairs.flatMapTo(HashSet()) {
            listOf(it.outgoingTransactionId, it.incomingTransactionId)
        }

        var incomeMinor = 0L
        var expenseMinor = 0L
        var adjustmentNetMinor = 0L
        var excludedForeignCount = 0

        val categorySpendAccum = linkedMapOf<Long?, Long>()
        val categoryTxCountAccum = linkedMapOf<Long?, Int>()

        fun addExpense(categoryId: Long?, amount: Long) {
            val resolvedCat = resolveBucketCategoryId(categoryId, uncategorizedId)
            expenseMinor += amount
            categorySpendAccum[resolvedCat] = (categorySpendAccum[resolvedCat] ?: 0L) + amount
            categoryTxCountAccum[resolvedCat] = (categoryTxCountAccum[resolvedCat] ?: 0) + 1
        }

        fun reduceExpense(categoryId: Long?, refundAmount: Long) {
            val resolvedCat = resolveBucketCategoryId(categoryId, uncategorizedId)
            expenseMinor -= refundAmount
            categorySpendAccum[resolvedCat] = (categorySpendAccum[resolvedCat] ?: 0L) - refundAmount
            categoryTxCountAccum[resolvedCat] = (categoryTxCountAccum[resolvedCat] ?: 0) + 1
        }

        for (tx in allTransactions) {
            if (accountIdFilter != null && tx.accountId != accountIdFilter) continue
            if (tx.occurredAt < startMillis || tx.occurredAt >= endMillis) continue

            // Row 15: Voided transactions excluded from totals, balances, and pairs.
            if (tx.status == TransactionStatus.VOIDED) continue

            // Row 16: Foreign currency stored & flagged, excluded from base-currency totals.
            if (!tx.currency.equals(normalizedBase, ignoreCase = true)) {
                excludedForeignCount++
                continue
            }

            // Row 14: Adjustments affect balance & unreconciled difference only.
            if (tx.transactionType == DomainTransactionType.ADJUSTMENT) {
                adjustmentNetMinor += if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor
                continue
            }

            // Rows 5 & 6 & ACC-10: Any confirmed internal transfer / cash pair leg is excluded from income & expense.
            if (tx.id in pairedTxIds) {
                continue
            }

            when (tx.transactionType) {
                // Row 1 & INFLOW fallback
                DomainTransactionType.PURCHASE -> {
                    if (tx.direction == Direction.OUTFLOW) {
                        addExpense(tx.categoryId, tx.amountMinor)
                    } else {
                        incomeMinor += tx.amountMinor
                    }
                }

                // Row 2
                DomainTransactionType.FEE -> {
                    if (tx.direction == Direction.OUTFLOW) {
                        addExpense(tx.categoryId ?: feesCategoryId, tx.amountMinor)
                    } else {
                        incomeMinor += tx.amountMinor
                    }
                }

                // Rows 3 & 4 (unpaired transfer)
                DomainTransactionType.TRANSFER -> {
                    if (tx.direction == Direction.OUTFLOW) {
                        addExpense(tx.categoryId, tx.amountMinor)
                    } else {
                        incomeMinor += tx.amountMinor
                    }
                }

                // Rows 7 & 8 (unpaired cash)
                DomainTransactionType.CASH -> {
                    if (tx.direction == Direction.OUTFLOW) {
                        addExpense(tx.categoryId ?: cashWithdrawalCategoryId, tx.amountMinor)
                    } else {
                        incomeMinor += tx.amountMinor
                    }
                }

                // Rows 9, 10, 11 (refund/reversal)
                DomainTransactionType.REFUND -> {
                    if (tx.direction == Direction.INFLOW) {
                        val linkedOriginal = tx.relatedTransactionId
                            ?.let { txById[it] }
                            ?.takeIf { it.status == TransactionStatus.CONFIRMED }
                        val targetCategory = linkedOriginal?.categoryId ?: tx.categoryId
                        reduceExpense(targetCategory, tx.amountMinor)
                    }
                    // OUTFLOW + REFUND (Row 11) never enters confirmed totals in MVP.
                }

                // Rows 12 & 13
                DomainTransactionType.OTHER -> {
                    if (tx.direction == Direction.INFLOW) {
                        incomeMinor += tx.amountMinor
                    } else {
                        addExpense(tx.categoryId, tx.amountMinor)
                    }
                }

                DomainTransactionType.ADJUSTMENT -> Unit
            }
        }

        // TRF-04: Read-only derived transfer fee attributed in Fees category on outgoing leg's account and date.
        val derivedFees = mutableListOf<DerivedFeeRow>()
        for (pair in activeConfirmedPairs) {
            if (pair.feeMinor <= 0L) continue
            val outLeg = txById[pair.outgoingTransactionId] ?: continue
            if (accountIdFilter != null && outLeg.accountId != accountIdFilter) continue
            if (outLeg.occurredAt < startMillis || outLeg.occurredAt >= endMillis) continue
            if (!outLeg.currency.equals(normalizedBase, ignoreCase = true)) continue

            derivedFees += DerivedFeeRow(
                transferPairId = pair.id,
                outgoingTransactionId = outLeg.id,
                accountId = outLeg.accountId,
                occurredAt = outLeg.occurredAt,
                amountMinor = pair.feeMinor,
                currency = outLeg.currency,
            )
            addExpense(feesCategoryId, pair.feeMinor)
        }

        val breakdowns = categorySpendAccum.entries
            .map { (catId, netMinor) ->
                val catName = when {
                    catId == null || catId == uncategorizedId -> "Uncategorized"
                    else -> categoryById[catId]?.name ?: "Uncategorized"
                }
                CategorySpendBreakdown(
                    categoryId = catId,
                    categoryName = catName,
                    netSpendMinor = netMinor,
                    transactionCount = categoryTxCountAccum[catId] ?: 0,
                )
            }
            .sortedByDescending { abs(it.netSpendMinor) }

        return PeriodAggregateSummary(
            baseCurrency = normalizedBase,
            totalIncomeMinor = incomeMinor,
            totalExpenseMinor = expenseMinor,
            netCashFlowMinor = incomeMinor - expenseMinor,
            unreconciledAdjustmentNetMinor = adjustmentNetMinor,
            excludedNonBaseCurrencyCount = excludedForeignCount,
            categoryBreakdowns = breakdowns,
            derivedFees = derivedFees.sortedByDescending { it.occurredAt },
        )
    }

    private fun resolveBucketCategoryId(categoryId: Long?, uncategorizedId: Long?): Long? =
        categoryId ?: uncategorizedId
}
