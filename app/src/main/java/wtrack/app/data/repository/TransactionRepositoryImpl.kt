package wtrack.app.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.local.WtrackDatabase
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.mapper.LocalJson
import wtrack.app.data.local.mapper.formatPayloadPreview
import wtrack.app.data.local.mapper.toDomain
import wtrack.app.data.local.preferences.AppSettingsDataStore
import wtrack.app.domain.exception.NotFoundException
import wtrack.app.domain.exception.TransferPairEditBlockedException
import wtrack.app.domain.exception.ValidationException
import wtrack.app.domain.model.CategorySource
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.repository.CreateManualTransactionRequest
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.UpdateTransactionRequest
import wtrack.app.domain.time.Clock
import wtrack.integration.Direction

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val settingsStore: AppSettingsDataStore,
    private val clock: Clock,
) : TransactionRepository {

    override val transactions: Flow<List<Transaction>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) {
            flowOf(emptyList())
        } else {
            combine(
                db.transactionDao().observeAll(),
                db.merchantDao().observeAll(),
                db.notificationEventDao().observeAll(),
            ) { txRows, merchantRows, eventRows ->
                val merchantsById = merchantRows.associateBy { it.id }
                val eventsById = eventRows.associateBy { it.id }
                txRows.map { row ->
                    val merchantName = row.merchantId?.let { merchantsById[it]?.displayName }
                    val payloadPreview = row.eventId
                        ?.let { eventsById[it]?.payloadJson }
                        ?.let { LocalJson.decodePayload(it)?.let(::formatPayloadPreview) }
                    row.toDomain(
                        merchantName = merchantName,
                        retainedPayloadPreview = payloadPreview,
                    )
                }
            }
        }
    }

    override suspend fun transaction(id: Long): Transaction? {
        val db = holder.requireDatabase()
        val row = db.transactionDao().getById(id) ?: return null
        val merchantName = row.merchantId?.let { db.merchantDao().getById(it)?.displayName }
        val payloadPreview = row.eventId
            ?.let { db.notificationEventDao().getById(it)?.payloadJson }
            ?.let { LocalJson.decodePayload(it)?.let(::formatPayloadPreview) }
        return row.toDomain(merchantName = merchantName, retainedPayloadPreview = payloadPreview)
    }

    override suspend fun getAllOnce(): List<Transaction> {
        val db = holder.requireDatabase()
        val merchantsById = db.merchantDao().getAllOnce().associateBy { it.id }
        return db.transactionDao().getAllOnce().map { row ->
            row.toDomain(merchantName = row.merchantId?.let { merchantsById[it]?.displayName })
        }
    }

    override suspend fun insertManual(request: CreateManualTransactionRequest): Long {
        if (request.amountMinor <= 0L) throw ValidationException("Amount must be positive")
        val normalizedCurrency = request.currency.trim().uppercase()
        if (normalizedCurrency.length != 3 || !normalizedCurrency.all { it in 'A'..'Z' }) {
            throw ValidationException("Currency must be a 3-letter ISO code")
        }
        if (request.transactionType == DomainTransactionType.ADJUSTMENT) {
            throw ValidationException("Manual entry cannot create ADJUSTMENT directly")
        }
        if (request.transactionType == DomainTransactionType.REFUND && request.direction == Direction.OUTFLOW) {
            throw ValidationException("Refunds must have INFLOW direction in the MVP (PRD section 4.6)")
        }
        val now = clock.nowEpochMillis()
        val id = holder.requireDatabase().transactionDao().insert(
            TransactionEntity(
                accountId = request.accountId,
                sourceId = null,
                eventId = null,
                amountMinor = request.amountMinor,
                currency = normalizedCurrency,
                direction = request.direction.name,
                transactionType = request.transactionType.name,
                merchantId = null,
                description = request.description?.trim()?.takeIf { it.isNotEmpty() },
                note = request.note?.trim()?.takeIf { it.isNotEmpty() },
                occurredAt = request.occurredAt,
                categoryId = request.categoryId,
                categorySource = request.categoryId?.let { CategorySource.USER.name },
                appliedRuleId = null,
                status = TransactionStatus.CONFIRMED.name,
                fingerprint = null,
                parserVersion = null,
                originalJson = null,
                providerReference = null,
                relatedTransactionId = null,
                reportedBalanceMinor = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        settingsStore.incrementTxCountSinceBackup()
        return id
    }

    override suspend fun insertAdjustment(
        accountId: Long,
        amountMinor: Long,
        currency: String,
        direction: Direction,
        occurredAt: Long,
        description: String?,
    ): Long {
        if (amountMinor <= 0L) throw ValidationException("Adjustment amount must be positive")
        val now = clock.nowEpochMillis()
        val db = holder.requireDatabase()
        return db.withTransaction {
            val id = db.transactionDao().insert(
                TransactionEntity(
                    accountId = accountId,
                    sourceId = null,
                    eventId = null,
                    amountMinor = amountMinor,
                    currency = currency.trim().uppercase(),
                    direction = direction.name,
                    transactionType = DomainTransactionType.ADJUSTMENT.name,
                    merchantId = null,
                    description = description?.trim() ?: "Balance adjustment",
                    note = null,
                    occurredAt = occurredAt,
                    categoryId = null,
                    categorySource = null,
                    appliedRuleId = null,
                    status = TransactionStatus.CONFIRMED.name,
                    fingerprint = null,
                    parserVersion = null,
                    originalJson = null,
                    providerReference = null,
                    relatedTransactionId = null,
                    reportedBalanceMinor = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            db.accountDao().updateLastReconciledAt(accountId, occurredAt, now)
            id
        }
    }

    override suspend fun update(request: UpdateTransactionRequest) {
        if (request.amountMinor <= 0L) throw ValidationException("Amount must be positive")
        val normalizedCurrency = request.currency.trim().uppercase()
        if (normalizedCurrency.length != 3 || !normalizedCurrency.all { it in 'A'..'Z' }) {
            throw ValidationException("Currency must be a 3-letter ISO code")
        }
        if (request.transactionType == DomainTransactionType.REFUND && request.direction == Direction.OUTFLOW) {
            throw ValidationException("Refunds must have INFLOW direction in the MVP (PRD section 4.6)")
        }

        val db = holder.requireDatabase()
        db.withTransaction {
            val txDao = db.transactionDao()
            val existing = txDao.getById(request.id) ?: throw NotFoundException("Transaction ${request.id} not found")
            if (request.transactionType == DomainTransactionType.ADJUSTMENT &&
                existing.transactionType != DomainTransactionType.ADJUSTMENT.name
            ) {
                throw ValidationException("Only balance reconciliation can create ADJUSTMENT transactions (ACC-08)")
            }

            // PRD section 4.5 & TRF-04: Re-evaluate confirmed transfer pair on edit; block only if
            // the edited leg would invalidate the pair, otherwise recompute fee_minor atomically.
            val confirmedPair = db.transferMatchDao().findConfirmedForTransaction(request.id)
            var recomputedFeeMinor: Long? = null
            if (confirmedPair != null) {
                val isOutgoingLeg = confirmedPair.outgoingTransactionId == request.id
                val counterpartId = if (isOutgoingLeg) {
                    confirmedPair.incomingTransactionId
                } else {
                    confirmedPair.outgoingTransactionId
                }
                val counterpart = txDao.getById(counterpartId)
                    ?: throw TransferPairEditBlockedException("Counterpart transfer leg missing; unlink pair first.")

                val expectedDir = if (isOutgoingLeg) Direction.OUTFLOW else Direction.INFLOW
                val newOutAmount = if (isOutgoingLeg) request.amountMinor else counterpart.amountMinor
                val newInAmount = if (isOutgoingLeg) counterpart.amountMinor else request.amountMinor

                val invalidPair = request.direction != expectedDir ||
                    request.accountId == counterpart.accountId ||
                    !normalizedCurrency.equals(counterpart.currency, ignoreCase = true) ||
                    newOutAmount < newInAmount ||
                    request.transactionType == DomainTransactionType.ADJUSTMENT ||
                    request.transactionType == DomainTransactionType.REFUND

                if (invalidPair) {
                    throw TransferPairEditBlockedException(
                        "This edit would make the linked transfer pair invalid. Unlink the transfer pair first (TRF-04).",
                    )
                }
                recomputedFeeMinor = newOutAmount - newInAmount
            }

            val resolvedCategoryId = if (request.categorySetByUser) request.categoryId else existing.categoryId
            val resolvedCategorySource = if (request.categorySetByUser) {
                request.categoryId?.let { CategorySource.USER.name }
            } else {
                existing.categorySource
            }
            val resolvedRuleId = if (request.categorySetByUser) null else existing.appliedRuleId

            // Immutable originalJson is never overwritten on edits (TX-03).
            txDao.update(
                existing.copy(
                    accountId = request.accountId,
                    amountMinor = request.amountMinor,
                    currency = normalizedCurrency,
                    direction = request.direction.name,
                    transactionType = request.transactionType.name,
                    occurredAt = request.occurredAt,
                    description = request.description?.trim(),
                    note = request.note?.trim(),
                    categoryId = resolvedCategoryId,
                    categorySource = resolvedCategorySource,
                    appliedRuleId = resolvedRuleId,
                    updatedAt = clock.nowEpochMillis(),
                ),
            )
            if (confirmedPair != null && recomputedFeeMinor != null && recomputedFeeMinor != confirmedPair.feeMinor) {
                db.transferMatchDao().updateFee(confirmedPair.id, recomputedFeeMinor)
            }
        }
    }

    override suspend fun setCategory(transactionId: Long, categoryId: Long?, byUser: Boolean, ruleId: Long?) {
        val db = holder.requireDatabase()
        val source = when {
            categoryId == null -> null
            byUser -> CategorySource.USER.name
            else -> CategorySource.RULE.name
        }
        db.transactionDao().updateCategory(
            id = transactionId,
            categoryId = categoryId,
            categorySource = source,
            appliedRuleId = if (byUser) null else ruleId,
            updatedAt = clock.nowEpochMillis(),
        )
    }

    override suspend fun voidTransaction(id: Long) {
        val db = holder.requireDatabase()
        val now = clock.nowEpochMillis()
        db.withTransaction {
            val confirmedPair = db.transferMatchDao().findConfirmedForTransaction(id)
            if (confirmedPair != null) {
                db.transferMatchDao().deleteById(confirmedPair.id)
            }
            db.transactionDao().updateStatus(id, TransactionStatus.VOIDED.name, now)
        }
    }

    override suspend fun restoreTransaction(id: Long) {
        holder.requireDatabase().transactionDao().updateStatus(
            id = id,
            status = TransactionStatus.CONFIRMED.name,
            updatedAt = clock.nowEpochMillis(),
        )
    }

    override suspend fun deleteManual(id: Long) {
        val db = holder.requireDatabase()
        val existing = db.transactionDao().getById(id) ?: return
        if (existing.eventId != null) {
            throw ValidationException("Captured transactions cannot be deleted; void instead (TX-08)")
        }
        db.withTransaction {
            val confirmedPair = db.transferMatchDao().findConfirmedForTransaction(id)
            if (confirmedPair != null) {
                db.transferMatchDao().deleteById(confirmedPair.id)
            }
            db.transactionDao().deleteManualById(id)
        }
    }

    override suspend fun linkRefund(refundTransactionId: Long, originalTransactionId: Long?) {
        holder.requireDatabase().transactionDao().updateRelatedTransaction(
            id = refundTransactionId,
            relatedId = originalTransactionId,
            updatedAt = clock.nowEpochMillis(),
        )
    }

    override suspend fun findByProviderReference(sourceId: String, providerReference: String): Transaction? =
        holder.requireDatabase().transactionDao().findByProviderReference(sourceId, providerReference)?.toDomain()

    override suspend fun countUncategorizedForMerchant(merchantId: Long): Int {
        val db = holder.requireDatabase()
        val uncatId = db.categoryDao().findBySystemKey(WtrackDatabase.SYSTEM_KEY_UNCATEGORIZED)?.id ?: 1L
        return db.transactionDao().countUncategorizedForMerchant(merchantId, uncatId)
    }

    override suspend fun applyCategoryToUncategorizedMerchant(
        merchantId: Long,
        categoryId: Long,
        ruleId: Long,
    ): Int {
        val db = holder.requireDatabase()
        val uncatId = db.categoryDao().findBySystemKey(WtrackDatabase.SYSTEM_KEY_UNCATEGORIZED)?.id ?: 1L
        return db.transactionDao().applyCategoryToUncategorizedMerchant(
            merchantId = merchantId,
            uncategorizedId = uncatId,
            categoryId = categoryId,
            ruleId = ruleId,
            updatedAt = clock.nowEpochMillis(),
        )
    }
}
