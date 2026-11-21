package wtrack.app.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.BudgetEntity
import wtrack.app.data.local.entity.CaptureGapEntity
import wtrack.app.data.local.entity.CategoryEntity
import wtrack.app.data.local.entity.MerchantEntity
import wtrack.app.data.local.entity.TagEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionRuleEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.entity.TransactionTagEntity
import wtrack.app.data.local.entity.TransferMatchEntity
import wtrack.app.data.local.mapper.LocalJson
import wtrack.app.data.local.mapper.toDomain
import wtrack.app.data.local.mapper.toDomainRecord
import wtrack.app.data.local.mapper.toNeedsReviewItem
import wtrack.app.data.local.preferences.AppSettingsDataStore
import wtrack.app.domain.exception.NotFoundException
import wtrack.app.domain.exception.ValidationException
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.Budget
import wtrack.app.domain.model.CaptureGap
import wtrack.app.domain.model.CaptureGapReason
import wtrack.app.domain.model.CaptureHealthSummary
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.CategorySource
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.Merchant
import wtrack.app.domain.model.NeedsReviewItem
import wtrack.app.domain.model.NotificationEventRecord
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.model.OriginalTransactionSnapshot
import wtrack.app.domain.model.Tag
import wtrack.app.domain.model.ThemeMode
import wtrack.app.domain.model.TransactionRule
import wtrack.app.domain.model.TransactionSource
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferOrigin
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.model.TransferStatus
import wtrack.app.domain.repository.BudgetRepository
import wtrack.app.domain.repository.CaptureStatusRepository
import wtrack.app.domain.repository.CategoryRepository
import wtrack.app.domain.repository.DatabaseHealthRepository
import wtrack.app.domain.repository.LinkTransferRequest
import wtrack.app.domain.repository.MerchantRepository
import wtrack.app.domain.repository.NeedsReviewRepository
import wtrack.app.domain.repository.RuleRepository
import wtrack.app.domain.repository.SaveReviewItemRequest
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.repository.SourceAndEventRepository
import wtrack.app.domain.repository.TagRepository
import wtrack.app.domain.repository.TransferRepository
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.IdentityHashGenerator
import wtrack.app.domain.usecase.MerchantKeyNormalizer
import wtrack.integration.Direction

@OptIn(ExperimentalCoroutinesApi::class)
class NeedsReviewRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val settingsStore: AppSettingsDataStore,
    private val clock: Clock,
    private val onRetryRequested: (suspend (Long) -> Unit)? = null,
) : NeedsReviewRepository {

    override val items: Flow<List<NeedsReviewItem>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) {
            flowOf(emptyList())
        } else {
            db.notificationEventDao().observeNeedsReview().map { rows ->
                rows.map { row ->
                    val draft = LocalJson.decodePartialDraft(row.reviewDraftJson)
                    val links = db.accountSourceDao().getForSource(row.sourceId)
                    val matchedAccountId = draft?.accountHint?.let { hint ->
                        links.firstOrNull { it.accountHint == hint }?.accountId
                    } ?: links.singleOrNull()?.accountId
                    row.toNeedsReviewItem(resolvedAccountId = matchedAccountId)
                }
            }
        }
    }

    override val pendingCount: Flow<Int> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) {
            flowOf(0)
        } else {
            db.notificationEventDao().observeNeedsReviewCount()
        }
    }

    override suspend fun item(id: Long): NeedsReviewItem? {
        val db = holder.requireDatabase()
        val row = db.notificationEventDao().getById(id) ?: return null
        if (row.processingStatus != NotificationEventStatus.NEEDS_REVIEW.name) return null
        val draft = LocalJson.decodePartialDraft(row.reviewDraftJson)
        val links = db.accountSourceDao().getForSource(row.sourceId)
        val matchedAccountId = draft?.accountHint?.let { hint ->
            links.firstOrNull { it.accountHint == hint }?.accountId
        } ?: links.singleOrNull()?.accountId
        return row.toNeedsReviewItem(resolvedAccountId = matchedAccountId)
    }

    override suspend fun saveResolved(itemId: Long, resolved: SaveReviewItemRequest): Long {
        if (resolved.amountMinor <= 0L) throw ValidationException("Amount must be positive")
        val normalizedCurrency = resolved.currency.trim().uppercase()
        if (normalizedCurrency.length != 3 || !normalizedCurrency.all { it in 'A'..'Z' }) {
            throw ValidationException("Currency must be a 3-letter ISO code")
        }
        if (resolved.transactionType == DomainTransactionType.ADJUSTMENT) {
            throw ValidationException("Needs Review resolution cannot create ADJUSTMENT transactions")
        }
        if (resolved.transactionType == DomainTransactionType.REFUND && resolved.direction == Direction.OUTFLOW) {
            throw ValidationException("Refunds must have INFLOW direction in the MVP (PRD section 4.6)")
        }
        val db = holder.requireDatabase()
        val now = clock.nowEpochMillis()
        val settings = settingsStore.current()
        val verificationPurgeAfter = if (settings.verificationWindowDays == 0) {
            null
        } else {
            now + settings.verificationWindowDays * MILLIS_PER_DAY
        }

        val txId = db.withTransaction {
            val event = db.notificationEventDao().getById(itemId)
                ?: throw NotFoundException("Needs Review event $itemId not found")
            if (event.processingStatus != NotificationEventStatus.NEEDS_REVIEW.name) {
                throw ValidationException("Event $itemId is not in NEEDS_REVIEW state")
            }
            val draft = LocalJson.decodePartialDraft(event.reviewDraftJson)
            val sourceParserVersion = db.transactionSourceDao().getById(event.sourceId)?.parserVersion
            val normalizedMerchantKey = MerchantKeyNormalizer.normalize(draft?.merchantRaw)
            val merchantId = if (normalizedMerchantKey.isNotEmpty()) {
                val existingMerchant = db.merchantDao().findByKey(normalizedMerchantKey)
                existingMerchant?.id ?: db.merchantDao().insert(
                    MerchantEntity(
                        displayName = draft?.merchantRaw?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedMerchantKey,
                        key = normalizedMerchantKey,
                        createdAt = now,
                    ),
                )
            } else {
                null
            }
            val cleanedDesc = resolved.description?.trim()?.takeIf { it.isNotEmpty() }
            val fingerprint = IdentityHashGenerator.transactionFingerprint(
                accountId = resolved.accountId,
                amountMinor = resolved.amountMinor,
                currency = normalizedCurrency,
                direction = resolved.direction,
                occurredAtEpochMillis = resolved.occurredAt,
                merchantKeyOrDesc = normalizedMerchantKey.ifEmpty { cleanedDesc?.lowercase().orEmpty() },
                providerReference = draft?.providerReference,
                notificationKeyOrId = event.notificationKey ?: "${event.notificationId}:${event.notificationTag.orEmpty()}",
            )
            val snapshot = OriginalTransactionSnapshot(
                amountMinor = resolved.amountMinor,
                currency = normalizedCurrency,
                direction = resolved.direction,
                transactionType = resolved.transactionType,
                occurredAt = resolved.occurredAt,
                accountId = resolved.accountId,
                merchantRaw = draft?.merchantRaw,
                description = cleanedDesc,
            )
            val createdId = db.transactionDao().insert(
                TransactionEntity(
                    accountId = resolved.accountId,
                    sourceId = event.sourceId,
                    eventId = event.id,
                    amountMinor = resolved.amountMinor,
                    currency = snapshot.currency,
                    direction = resolved.direction.name,
                    transactionType = resolved.transactionType.name,
                    merchantId = merchantId,
                    description = snapshot.description,
                    note = null,
                    occurredAt = resolved.occurredAt,
                    categoryId = resolved.categoryId,
                    categorySource = resolved.categoryId?.let { CategorySource.USER.name },
                    appliedRuleId = null,
                    status = TransactionStatus.CONFIRMED.name,
                    fingerprint = fingerprint,
                    parserVersion = sourceParserVersion,
                    originalJson = LocalJson.encodeOriginalSnapshot(snapshot),
                    providerReference = draft?.providerReference,
                    relatedTransactionId = null,
                    reportedBalanceMinor = draft?.reportedBalanceMinor,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            db.notificationEventDao().updateOutcome(
                id = event.id,
                newStatus = NotificationEventStatus.PROCESSED.name,
                failureReason = null,
                reviewDraftJson = event.reviewDraftJson,
                payloadJson = if (verificationPurgeAfter == null) null else event.payloadJson,
                purgeAfter = verificationPurgeAfter,
                processedAt = now,
                updatedAt = now,
            )

            if (resolved.rememberAccountHint && !draft?.accountHint.isNullOrBlank()) {
                val existingHints = db.accountSourceDao().getForSource(event.sourceId)
                if (existingHints.none { it.accountHint == draft?.accountHint }) {
                    db.accountSourceDao().insert(
                        AccountSourceEntity(
                            accountId = resolved.accountId,
                            sourceId = event.sourceId,
                            accountHint = draft?.accountHint,
                            createdAt = now,
                        ),
                    )
                }
            }
            createdId
        }
        settingsStore.incrementTxCountSinceBackup()
        return txId
    }

    override suspend fun ignore(itemId: Long) {
        val db = holder.requireDatabase()
        val now = clock.nowEpochMillis()
        db.notificationEventDao().updateOutcome(
            id = itemId,
            newStatus = NotificationEventStatus.IGNORED.name,
            failureReason = "USER_IGNORED",
            reviewDraftJson = null,
            payloadJson = null,
            purgeAfter = null,
            processedAt = now,
            updatedAt = now,
        )
    }

    override suspend fun retry(itemId: Long) {
        val db = holder.requireDatabase()
        val event = db.notificationEventDao().getById(itemId) ?: return
        val now = clock.nowEpochMillis()
        db.notificationEventDao().update(
            event.copy(
                processingStatus = NotificationEventStatus.RECEIVED.name,
                failureReason = null,
                attemptCount = 0,
                updatedAt = now,
            ),
        )
        onRetryRequested?.invoke(itemId)
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransferRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val clock: Clock,
) : TransferRepository {
    override val pairs: Flow<List<TransferPair>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) flowOf(emptyList()) else db.transferMatchDao().observeAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllOnce(): List<TransferPair> =
        holder.requireDatabase().transferMatchDao().getAllOnce().map { it.toDomain() }

    override suspend fun findConfirmedForTransaction(transactionId: Long): TransferPair? =
        holder.requireDatabase().transferMatchDao().findConfirmedForTransaction(transactionId)?.toDomain()

    override suspend fun link(request: LinkTransferRequest, feeMinor: Long): Long {
        if (feeMinor < 0L) throw ValidationException("Transfer fee cannot be negative")
        return holder.requireDatabase().transferMatchDao().insert(
            TransferMatchEntity(
                outgoingTransactionId = request.outgoingTransactionId,
                incomingTransactionId = request.incomingTransactionId,
                origin = TransferOrigin.MANUAL.name,
                confidence = null,
                status = TransferStatus.CONFIRMED.name,
                feeMinor = feeMinor,
                createdAt = clock.nowEpochMillis(),
            ),
        )
    }

    override suspend fun updateFee(pairId: Long, feeMinor: Long) {
        holder.requireDatabase().transferMatchDao().updateFee(pairId, feeMinor)
    }

    override suspend fun unlink(pairId: Long) {
        holder.requireDatabase().transferMatchDao().deleteById(pairId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val clock: Clock,
) : CategoryRepository {
    override val categories: Flow<List<Category>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) flowOf(emptyList()) else db.categoryDao().observeAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllOnce(): List<Category> =
        holder.requireDatabase().categoryDao().getAllOnce().map { it.toDomain() }

    override suspend fun findBySystemKey(systemKey: String): Category? =
        holder.requireDatabase().categoryDao().findBySystemKey(systemKey)?.toDomain()

    override suspend fun create(name: String, directionScope: Direction?, parentId: Long?): Long {
        if (name.isBlank()) throw ValidationException("Category name is required")
        return holder.requireDatabase().categoryDao().insert(
            CategoryEntity(
                name = name.trim(),
                systemKey = null,
                directionScope = directionScope?.name,
                parentId = parentId,
                isSystem = false,
                isArchived = false,
                createdAt = clock.nowEpochMillis(),
            ),
        )
    }

    override suspend fun rename(id: Long, newName: String) {
        if (newName.isBlank()) throw ValidationException("Category name is required")
        holder.requireDatabase().categoryDao().renameNonSystem(id, newName.trim())
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        holder.requireDatabase().categoryDao().setArchivedNonSystem(id, archived)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MerchantRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val clock: Clock,
) : MerchantRepository {
    override val merchants: Flow<List<Merchant>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) flowOf(emptyList()) else db.merchantDao().observeAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun findByKey(key: String): Merchant? =
        holder.requireDatabase().merchantDao().findByKey(key)?.toDomain()

    override suspend fun getOrCreate(displayName: String, normalizedKey: String): Long {
        val dao = holder.requireDatabase().merchantDao()
        dao.findByKey(normalizedKey)?.let { return it.id }
        return dao.insert(
            MerchantEntity(
                displayName = displayName.trim().ifEmpty { normalizedKey },
                key = normalizedKey,
                createdAt = clock.nowEpochMillis(),
            ),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RuleRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val clock: Clock,
) : RuleRepository {
    override val rules: Flow<List<TransactionRule>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) flowOf(emptyList()) else db.transactionRuleDao().observeAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getEnabledOnce(): List<TransactionRule> =
        holder.requireDatabase().transactionRuleDao().getEnabledOnce().map { it.toDomain() }

    override suspend fun createRule(
        name: String,
        merchantKey: String?,
        descriptionFragment: String?,
        accountId: Long?,
        direction: Direction?,
        categoryId: Long,
    ): Long {
        if (merchantKey.isNullOrBlank() && descriptionFragment.isNullOrBlank()) {
            throw ValidationException("Rule requires a merchant key or description fragment")
        }
        val now = clock.nowEpochMillis()
        return holder.requireDatabase().transactionRuleDao().insert(
            TransactionRuleEntity(
                name = name.trim().ifEmpty { merchantKey ?: descriptionFragment ?: "Rule" },
                accountId = accountId,
                categoryId = categoryId,
                conditionJson = LocalJson.encodeRuleCondition(merchantKey, descriptionFragment, accountId, direction),
                actionJson = LocalJson.encodeRuleAction(categoryId),
                enabled = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        holder.requireDatabase().transactionRuleDao().setEnabled(id, enabled, clock.nowEpochMillis())
    }

    override suspend fun delete(id: Long) {
        holder.requireDatabase().transactionRuleDao().deleteById(id)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TagRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val clock: Clock,
) : TagRepository {
    override val tags: Flow<List<Tag>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) flowOf(emptyList()) else db.tagDao().observeAll().map { list -> list.map { it.toDomain() } }
    }

    override fun observeAllTransactionTags(): Flow<Map<Long, List<Tag>>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) {
            flowOf(emptyMap())
        } else {
            kotlinx.coroutines.flow.combine(
                db.tagDao().observeAll(),
                db.tagDao().observeAllTransactionTags(),
            ) { tagEntities, links ->
                val tagMap = tagEntities.associate { it.id to it.toDomain() }
                links.groupBy(
                    keySelector = { it.transactionId },
                    valueTransform = { link -> tagMap[link.tagId] },
                ).mapValues { (_, list) ->
                    list.filterNotNull().sortedBy { it.name.lowercase() }
                }
            }
        }
    }

    override suspend fun getTagsForTransaction(transactionId: Long): List<Tag> =
        holder.requireDatabase().tagDao().getTagsForTransaction(transactionId).map { it.toDomain() }

    override suspend fun addTagToTransaction(transactionId: Long, tagName: String) {
        val cleaned = tagName.trim()
        if (cleaned.isEmpty()) return
        val db = holder.requireDatabase()
        db.withTransaction {
            val existing = db.tagDao().findByNameIgnoreCase(cleaned)
            val tagId = existing?.id ?: db.tagDao().insertTag(TagEntity(name = cleaned, createdAt = clock.nowEpochMillis()))
            db.tagDao().insertTransactionTag(TransactionTagEntity(transactionId = transactionId, tagId = tagId))
        }
    }

    override suspend fun removeTagFromTransaction(transactionId: Long, tagId: Long) {
        holder.requireDatabase().tagDao().deleteTransactionTag(transactionId, tagId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val clock: Clock,
) : BudgetRepository {
    override val budgets: Flow<List<Budget>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) flowOf(emptyList()) else db.budgetDao().observeAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun upsertBudget(categoryId: Long, accountId: Long?, limitMinor: Long, period: String): Long {
        if (limitMinor <= 0L) throw ValidationException("Budget limit must be positive")
        val now = clock.nowEpochMillis()
        return holder.requireDatabase().budgetDao().insert(
            BudgetEntity(
                categoryId = categoryId,
                accountId = accountId,
                limitMinor = limitMinor,
                period = period,
                isActive = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun deleteBudget(id: Long) {
        holder.requireDatabase().budgetDao().deleteById(id)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SourceAndEventRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val clock: Clock,
) : SourceAndEventRepository {
    override val sources: Flow<List<TransactionSource>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) flowOf(emptyList()) else db.transactionSourceDao().observeAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getSource(id: String): TransactionSource? =
        holder.requireDatabase().transactionSourceDao().getById(id)?.toDomain()

    override suspend fun upsertSource(
        packageName: String,
        displayName: String,
        isAllowlisted: Boolean,
        parserVersion: Int?,
    ) {
        val dao = holder.requireDatabase().transactionSourceDao()
        val now = clock.nowEpochMillis()
        val existing = dao.getById(packageName)
        dao.upsert(
            TransactionSourceEntity(
                id = packageName,
                packageName = packageName,
                displayName = displayName.ifBlank { packageName },
                isAllowlisted = isAllowlisted,
                parserVersion = parserVersion,
                sampleCollectionUntil = existing?.sampleCollectionUntil,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun setAllowlisted(packageName: String, allowlisted: Boolean) {
        val dao = holder.requireDatabase().transactionSourceDao()
        val now = clock.nowEpochMillis()
        val existing = dao.getById(packageName)
        if (existing == null) {
            dao.upsert(
                TransactionSourceEntity(
                    id = packageName,
                    packageName = packageName,
                    displayName = packageName,
                    isAllowlisted = allowlisted,
                    parserVersion = null,
                    sampleCollectionUntil = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            dao.setAllowlisted(packageName, allowlisted, now)
        }
    }

    override suspend fun setSampleCollectionUntil(sourceId: String, untilEpochMillis: Long?) {
        holder.requireDatabase().transactionSourceDao().setSampleCollectionUntil(
            sourceId = sourceId,
            untilEpochMillis = untilEpochMillis,
            updatedAt = clock.nowEpochMillis(),
        )
    }

    override suspend fun countNoParserLast30Days(sourceId: String, sinceEpochMillis: Long): Int =
        holder.requireDatabase().notificationEventDao().countNoParserSince(sourceId, sinceEpochMillis)

    override suspend fun getAllEventsOnce(): List<NotificationEventRecord> =
        holder.requireDatabase().notificationEventDao().getAllOnce().map { it.toDomainRecord() }

    override suspend fun purgeExpiredPayloads(nowEpochMillis: Long): Int =
        holder.requireDatabase().notificationEventDao().purgeExpiredPayloads(nowEpochMillis)
}

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureStatusRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val settingsStore: AppSettingsDataStore,
    private val permissionGrantedProvider: () -> Boolean,
    private val listenerConnectedFlow: StateFlow<Boolean>,
    private val onRefreshStatus: suspend () -> Unit = {},
) : CaptureStatusRepository {
    private val refreshSignal = MutableStateFlow(0)

    override val captureGaps: Flow<List<CaptureGap>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) flowOf(emptyList()) else db.captureGapDao().observeAll().map { list -> list.map { it.toDomain() } }
    }

    override val healthSummary: Flow<CaptureHealthSummary> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) {
            flowOf(
                CaptureHealthSummary(
                    appStatus = CaptureStatus.ATTENTION,
                    isPermissionGranted = permissionGrantedProvider(),
                    isListenerConnected = listenerConnectedFlow.value,
                    openGap = null,
                    lastAliveAt = 0L,
                ),
            )
        } else {
            val sourcesFlow = db.transactionSourceDao().observeAll()
            val gapsFlow = db.captureGapDao().observeAll().map { list -> list.map { it.toDomain() } }
            kotlinx.coroutines.flow.combine(
                gapsFlow,
                settingsStore.settings,
                listenerConnectedFlow,
                sourcesFlow,
                refreshSignal,
            ) { gaps, settings, connected, sources, _ ->
                val granted = permissionGrantedProvider()
                val hasAllowlisted = sources.any { it.isAllowlisted }
                val status = when {
                    !granted || !hasAllowlisted -> CaptureStatus.ATTENTION
                    !connected -> CaptureStatus.DISCONNECTED
                    else -> CaptureStatus.ACTIVE
                }
                CaptureHealthSummary(
                    appStatus = status,
                    isPermissionGranted = granted,
                    isListenerConnected = connected,
                    openGap = gaps.firstOrNull { it.endedAt == null },
                    lastAliveAt = settings.lastAliveAt,
                )
            }
        }
    }

    override suspend fun refreshStatus() {
        onRefreshStatus()
        refreshSignal.value = refreshSignal.value + 1
    }

    override suspend fun recordGap(startedAt: Long, endedAt: Long?, reason: CaptureGapReason) {
        holder.requireDatabase().captureGapDao().insert(
            CaptureGapEntity(
                startedAt = startedAt,
                endedAt = endedAt,
                reason = reason.name,
            ),
        )
    }

    override suspend fun closeOpenGaps(reason: CaptureGapReason, endedAt: Long) {
        holder.requireDatabase().captureGapDao().closeOpenByReason(reason.name, endedAt)
    }
}

class SettingsRepositoryImpl(
    private val settingsStore: AppSettingsDataStore,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = settingsStore.settings
    override suspend fun current(): AppSettings = settingsStore.current()
    override suspend fun setThemeMode(mode: ThemeMode) = settingsStore.setThemeMode(mode)
    override suspend fun setBaseCurrency(currency: String) = settingsStore.setBaseCurrency(currency)
    override suspend fun setVerificationWindowDays(days: Int) = settingsStore.setVerificationWindowDays(days)
    override suspend fun setHeartbeatStaleHours(hours: Int) = settingsStore.setHeartbeatStaleHours(hours)
    override suspend fun updateLastAliveAt(epochMillis: Long) = settingsStore.updateLastAliveAt(epochMillis)
    override suspend fun recordBackupCompleted(atEpochMillis: Long) = settingsStore.recordBackupCompleted(atEpochMillis)
    override suspend fun dismissBackupReminder(atEpochMillis: Long) = settingsStore.dismissBackupReminder(atEpochMillis)
    override suspend fun setBackupReminderThresholds(days: Int, txCount: Int) =
        settingsStore.setBackupReminderThresholds(days, txCount)

    override suspend fun setOnboardingCompleted(completed: Boolean) = settingsStore.setOnboardingCompleted(completed)
}

class DatabaseHealthRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val settingsStore: AppSettingsDataStore,
) : DatabaseHealthRepository {
    override val state: Flow<DatabaseHealthState> = holder.healthState

    override suspend fun wipeAllDataAndReset() {
        holder.wipeAllDataAndReinitialize()
        settingsStore.clearAll()
    }
}
