package wtrack.app.domain.usecase

import kotlin.math.abs
import wtrack.app.domain.exception.NotFoundException
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.time.Clock
import wtrack.integration.Direction

/**
 * Pure ledger balance computation (`ACC-04`, `ACC-08`, `TX-08`).
 */
object AccountBalanceCalculator {
    fun calculateEstimatedBalance(account: Account, transactions: List<Transaction>): Long {
        val anchorTime = account.initialBalanceAt ?: Long.MIN_VALUE
        val base = account.initialBalanceMinor ?: 0L
        val delta = transactions
            .asSequence()
            .filter {
                it.accountId == account.id &&
                    it.status == TransactionStatus.CONFIRMED &&
                    it.occurredAt >= anchorTime
            }
            .sumOf { tx ->
                if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor
            }
        return base + delta
    }

    fun calculateEstimatedBalanceUpTo(
        account: Account,
        transactions: List<Transaction>,
        inclusiveOccurredAt: Long,
        inclusiveMaxTxId: Long = Long.MAX_VALUE,
    ): Long {
        val anchorTime = account.initialBalanceAt ?: Long.MIN_VALUE
        val base = account.initialBalanceMinor ?: 0L
        val delta = transactions
            .asSequence()
            .filter {
                it.accountId == account.id &&
                    it.status == TransactionStatus.CONFIRMED &&
                    it.occurredAt >= anchorTime &&
                    (it.occurredAt < inclusiveOccurredAt ||
                        (it.occurredAt == inclusiveOccurredAt && it.id <= inclusiveMaxTxId))
            }
            .sumOf { tx ->
                if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor
            }
        return base + delta
    }
}

data class BalanceCorrectionResult(
    val adjustmentTransactionId: Long?,
    val deltaMinor: Long,
)

/**
 * Implements `ACC-08` balance correction by recording the signed difference between actual
 * entered balance and current ledger estimate as a non-cash-flow `ADJUSTMENT` transaction.
 */
class CorrectBalanceUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        accountId: Long,
        actualBalanceMinor: Long,
        note: String? = null,
    ): BalanceCorrectionResult {
        val account = accountRepository.account(accountId)
            ?: throw NotFoundException("Account $accountId not found")
        val allTransactions = transactionRepository.getAllOnce()
        val estimated = AccountBalanceCalculator.calculateEstimatedBalance(account, allTransactions)
        val delta = actualBalanceMinor - estimated
        val now = clock.nowEpochMillis()

        if (delta == 0L) {
            accountRepository.updateLastReconciledAt(accountId, now)
            return BalanceCorrectionResult(adjustmentTransactionId = null, deltaMinor = 0L)
        }

        val direction = if (delta > 0L) Direction.INFLOW else Direction.OUTFLOW
        val txId = transactionRepository.insertAdjustment(
            accountId = accountId,
            amountMinor = abs(delta),
            currency = account.currency,
            direction = direction,
            occurredAt = now,
            description = note?.trim()?.takeIf { it.isNotEmpty() } ?: "Balance reconciliation adjustment",
        )
        return BalanceCorrectionResult(adjustmentTransactionId = txId, deltaMinor = delta)
    }
}

data class BalanceCheckpointEvaluation(
    val checkpointTransactionId: Long,
    val checkpointOccurredAt: Long,
    val reportedBalanceMinor: Long,
    val expectedBalanceMinor: Long,
    val driftMinor: Long,
    val usedOpeningBalanceAnchor: Boolean,
)

/**
 * Evaluates provider-reported balance checkpoints (`ACC-07` in PRD v1.4).
 * Works whether `initial_balance_minor` is configured or not:
 * - With an opening balance (`initial_balance_at`), checks cumulative estimate at the latest checkpoint.
 * - Without an opening balance, compares consecutive reported-balance checkpoints (`prevBalance + intervalDelta`)
 *   so missing capture notifications still surface exact drift.
 */
class BalanceCheckpointEvaluator {
    fun evaluateLatest(
        account: Account,
        transactions: List<Transaction>,
    ): BalanceCheckpointEvaluation? {
        val accountTxs = transactions
            .filter { it.accountId == account.id && it.status == TransactionStatus.CONFIRMED }
            .sortedWith(compareBy<Transaction> { it.occurredAt }.thenBy { it.id })

        val checkpoints = accountTxs.filter { it.reportedBalanceMinor != null }
        val latest = checkpoints.lastOrNull() ?: return null
        val latestReported = latest.reportedBalanceMinor ?: return null

        // PRD ACC-07: When two consecutive balance reports cannot be put in order with certainty
        // (same post/occurred timestamp, or delivered out of order), no comparison is made.
        if (checkpoints.size >= 2) {
            val prev = checkpoints[checkpoints.size - 2]
            if (prev.occurredAt == latest.occurredAt || latest.createdAt < prev.createdAt) {
                return null
            }
        }

        val hasOpeningAnchor = account.initialBalanceMinor != null && account.initialBalanceAt != null
        if (hasOpeningAnchor && latest.occurredAt >= (account.initialBalanceAt ?: Long.MIN_VALUE)) {
            val expected = AccountBalanceCalculator.calculateEstimatedBalanceUpTo(
                account = account,
                transactions = accountTxs,
                inclusiveOccurredAt = latest.occurredAt,
                inclusiveMaxTxId = latest.id,
            )
            return BalanceCheckpointEvaluation(
                checkpointTransactionId = latest.id,
                checkpointOccurredAt = latest.occurredAt,
                reportedBalanceMinor = latestReported,
                expectedBalanceMinor = expected,
                driftMinor = latestReported - expected,
                usedOpeningBalanceAnchor = true,
            )
        }

        // Fallback without initial balance: require >= 2 reported checkpoints to measure interval drift.
        if (checkpoints.size < 2) return null
        val prev = checkpoints[checkpoints.size - 2]
        val prevReported = prev.reportedBalanceMinor ?: return null
        val prevIndex = accountTxs.indexOfFirst { it.id == prev.id }
        val latestIndex = accountTxs.indexOfFirst { it.id == latest.id }
        if (prevIndex < 0 || latestIndex <= prevIndex) return null

        val betweenDelta = accountTxs
            .subList(prevIndex + 1, latestIndex + 1)
            .sumOf { tx -> if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor }
        val expected = prevReported + betweenDelta

        return BalanceCheckpointEvaluation(
            checkpointTransactionId = latest.id,
            checkpointOccurredAt = latest.occurredAt,
            reportedBalanceMinor = latestReported,
            expectedBalanceMinor = expected,
            driftMinor = latestReported - expected,
            usedOpeningBalanceAnchor = false,
        )
    }
}
