package wtrack.app.domain.usecase

import wtrack.app.domain.exception.InvalidTransferPairException
import wtrack.app.domain.exception.NotFoundException
import wtrack.app.domain.exception.ValidationException
import wtrack.app.domain.model.CategorySource
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.CreateManualTransactionRequest
import wtrack.app.domain.repository.LinkTransferRequest
import wtrack.app.domain.repository.NeedsReviewRepository
import wtrack.app.domain.repository.RuleRepository
import wtrack.app.domain.repository.SaveReviewItemRequest
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.TransferRepository
import wtrack.app.domain.repository.UpdateTransactionRequest
import wtrack.integration.Direction

/**
 * Validates and creates an internal transfer pair (`TRF-01`, `TRF-02`, `TRF-04`).
 */
class LinkTransferUseCase(
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository,
) {
    suspend operator fun invoke(outgoingTransactionId: Long, incomingTransactionId: Long): Long {
        if (outgoingTransactionId == incomingTransactionId) {
            throw InvalidTransferPairException("Cannot link a transaction to itself.")
        }
        val outgoing = transactionRepository.transaction(outgoingTransactionId)
            ?: throw NotFoundException("Outgoing transaction $outgoingTransactionId not found.")
        val incoming = transactionRepository.transaction(incomingTransactionId)
            ?: throw NotFoundException("Incoming transaction $incomingTransactionId not found.")

        if (outgoing.status != TransactionStatus.CONFIRMED || incoming.status != TransactionStatus.CONFIRMED) {
            throw InvalidTransferPairException("Both transactions must be CONFIRMED to link as a transfer.")
        }
        if (outgoing.transactionType == wtrack.app.domain.model.DomainTransactionType.ADJUSTMENT ||
            incoming.transactionType == wtrack.app.domain.model.DomainTransactionType.ADJUSTMENT ||
            outgoing.transactionType == wtrack.app.domain.model.DomainTransactionType.REFUND ||
            incoming.transactionType == wtrack.app.domain.model.DomainTransactionType.REFUND
        ) {
            throw InvalidTransferPairException("Adjustments and refunds cannot be linked as internal transfer pairs.")
        }
        if (outgoing.accountId == incoming.accountId) {
            throw InvalidTransferPairException("Transfer pair legs must belong to two different accounts.")
        }
        if (outgoing.direction != Direction.OUTFLOW || incoming.direction != Direction.INFLOW) {
            throw InvalidTransferPairException("Transfer requires one OUTFLOW leg and one INFLOW leg.")
        }
        if (!outgoing.currency.equals(incoming.currency, ignoreCase = true)) {
            throw InvalidTransferPairException("Transfer pair legs must share the same currency (TRF-04).")
        }
        if (outgoing.amountMinor < incoming.amountMinor) {
            throw InvalidTransferPairException("Outgoing amount must be at least the incoming amount (TRF-04).")
        }
        if (transferRepository.findConfirmedForTransaction(outgoing.id) != null ||
            transferRepository.findConfirmedForTransaction(incoming.id) != null
        ) {
            throw InvalidTransferPairException("One of the transactions already belongs to a confirmed transfer pair.")
        }

        val derivedFeeMinor = outgoing.amountMinor - incoming.amountMinor
        return transferRepository.link(
            request = LinkTransferRequest(
                outgoingTransactionId = outgoing.id,
                incomingTransactionId = incoming.id,
            ),
            feeMinor = derivedFeeMinor,
        )
    }
}

class UnlinkTransferUseCase(
    private val transferRepository: TransferRepository,
) {
    suspend operator fun invoke(pairId: Long) {
        transferRepository.unlink(pairId)
    }
}

data class RuleCreationResult(
    val ruleId: Long,
    val backfilledCount: Int,
)

/**
 * Creates a categorization rule and optionally backfills matching uncategorized transactions for
 * the merchant without ever overwriting `CategorySource.USER` (`CAT-02`, `CAT-04`).
 */
class CreateRuleWithBackfillUseCase(
    private val ruleRepository: RuleRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        name: String,
        merchantId: Long?,
        merchantKey: String?,
        descriptionFragment: String?,
        accountId: Long?,
        direction: Direction?,
        categoryId: Long,
        applyBackfill: Boolean,
    ): RuleCreationResult {
        val ruleId = ruleRepository.createRule(
            name = name,
            merchantKey = merchantKey?.let { MerchantKeyNormalizer.normalize(it).ifEmpty { it.trim().lowercase() } },
            descriptionFragment = descriptionFragment?.trim()?.takeIf { it.isNotEmpty() },
            accountId = accountId,
            direction = direction,
            categoryId = categoryId,
        )
        val backfilled = if (applyBackfill && merchantId != null) {
            transactionRepository.applyCategoryToUncategorizedMerchant(
                merchantId = merchantId,
                categoryId = categoryId,
                ruleId = ruleId,
            )
        } else {
            0
        }
        return RuleCreationResult(ruleId = ruleId, backfilledCount = backfilled)
    }

    suspend fun countEligibleBackfill(merchantId: Long): Int =
        transactionRepository.countUncategorizedForMerchant(merchantId)
}

class CreateManualTransactionUseCase(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(request: CreateManualTransactionRequest): Long =
        transactionRepository.insertManual(request)
}

/**
 * Updates a transaction while re-evaluating any linked transfer pair fee (`TRF-04`).
 * Changing structural fields (`accountId`, `amountMinor`, `currency`, `direction`) on a confirmed
 * transfer leg is blocked cleanly by `TransactionRepositoryImpl` via `TransferPairEditBlockedException`.
 */
class UpdateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository,
) {
    suspend operator fun invoke(request: UpdateTransactionRequest) {
        transactionRepository.update(request)
        val pair = transferRepository.findConfirmedForTransaction(request.id) ?: return
        val outgoing = transactionRepository.transaction(pair.outgoingTransactionId) ?: return
        val incoming = transactionRepository.transaction(pair.incomingTransactionId) ?: return
        val newFee = (outgoing.amountMinor - incoming.amountMinor).coerceAtLeast(0L)
        if (newFee != pair.feeMinor) {
            transferRepository.updateFee(pair.id, newFee)
        }
    }
}

class VoidRestoreTransactionUseCase(
    private val transactionRepository: TransactionRepository,
) {
    suspend fun void(transactionId: Long) = transactionRepository.voidTransaction(transactionId)
    suspend fun restore(transactionId: Long) = transactionRepository.restoreTransaction(transactionId)
}

class DeleteManualTransactionUseCase(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(transactionId: Long) = transactionRepository.deleteManual(transactionId)
}

class LinkRefundUseCase(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(refundTransactionId: Long, originalTransactionId: Long?) {
        val refund = transactionRepository.transaction(refundTransactionId)
            ?: throw NotFoundException("Refund transaction $refundTransactionId not found.")
        if (originalTransactionId != null) {
            if (refundTransactionId == originalTransactionId) {
                throw ValidationException("Refund cannot link to itself.")
            }
            val original: Transaction = transactionRepository.transaction(originalTransactionId)
                ?: throw NotFoundException("Original transaction $originalTransactionId not found.")
            if (original.direction != Direction.OUTFLOW || original.status != TransactionStatus.CONFIRMED) {
                throw ValidationException("Refund can only link to a confirmed outgoing transaction.")
            }
            if (!original.currency.equals(refund.currency, ignoreCase = true)) {
                throw ValidationException("Linked original transaction must share currency with refund.")
            }
        }
        transactionRepository.linkRefund(refundTransactionId, originalTransactionId)
    }
}

class ResolveNeedsReviewUseCase(
    private val needsReviewRepository: NeedsReviewRepository,
    private val accountRepository: AccountRepository,
) {
    suspend fun save(itemId: Long, resolved: SaveReviewItemRequest): Long {
        if (accountRepository.account(resolved.accountId) == null) {
            throw ValidationException("Selected target account does not exist.")
        }
        return needsReviewRepository.saveResolved(itemId, resolved)
    }

    suspend fun ignore(itemId: Long) = needsReviewRepository.ignore(itemId)
    suspend fun retry(itemId: Long) = needsReviewRepository.retry(itemId)
}

fun Transaction.isUserCategorized(): Boolean = categorySource == CategorySource.USER
