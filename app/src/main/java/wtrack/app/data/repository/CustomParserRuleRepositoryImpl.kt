package wtrack.app.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.ingestion.CustomParserRuleCache
import wtrack.app.data.ingestion.toDomainLocale
import wtrack.app.data.ingestion.toIntegrationType
import wtrack.app.data.ingestion.toProviderLocale
import wtrack.app.data.local.entity.CustomParserRuleEntity
import wtrack.app.data.local.mapper.toDomain
import wtrack.app.domain.exception.ValidationException
import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.CustomParserRule
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.model.toDomain
import wtrack.app.domain.repository.CustomParserRuleRepository
import wtrack.app.domain.repository.CustomParserTestOutcome
import wtrack.app.domain.repository.GeneratedParserTemplate
import wtrack.app.domain.repository.UpsertCustomParserRuleRequest
import wtrack.app.domain.time.Clock
import wtrack.integration.Direction
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.providers.custom.CustomParserRuleSpec
import wtrack.integration.providers.custom.NotificationTemplateEngine

@OptIn(ExperimentalCoroutinesApi::class)
class CustomParserRuleRepositoryImpl(
    private val holder: WtrackDatabaseHolder,
    private val ruleCache: CustomParserRuleCache,
    private val clock: Clock,
    private val onRetryEvent: (suspend (Long) -> Unit)? = null,
) : CustomParserRuleRepository {

    override val rules: Flow<List<CustomParserRule>> = holder.databaseFlow.flatMapLatest { db ->
        if (db == null) {
            flowOf(emptyList())
        } else {
            db.customParserRuleDao().observeAll().map { rows -> rows.map { it.toDomain() } }
        }
    }

    override suspend fun getEnabledOnce(): List<CustomParserRule> =
        holder.requireDatabase().customParserRuleDao().getEnabledOnce().map { it.toDomain() }

    override suspend fun upsertRule(request: UpsertCustomParserRuleRequest): Long {
        val cleanedSourceId = request.sourceId.trim()
        if (cleanedSourceId.isEmpty()) throw ValidationException("Source package ID is required.")

        val validationError = NotificationTemplateEngine.validateTemplate(request.templatePattern)
        if (validationError != null) throw ValidationException(validationError)

        val normalizedCurrency = request.currency.trim().uppercase()
        if (normalizedCurrency.length != 3 || !normalizedCurrency.all { it in 'A'..'Z' }) {
            throw ValidationException("Currency must be a 3-letter ISO code.")
        }
        if (request.transactionType == DomainTransactionType.ADJUSTMENT) {
            throw ValidationException("Custom parser rules cannot produce ADJUSTMENT transactions.")
        }
        if (request.transactionType == DomainTransactionType.REFUND && request.direction == Direction.OUTFLOW) {
            throw ValidationException("Refund rules must have INFLOW direction (PRD section 4.6).")
        }

        val db = holder.requireDatabase()
        val dao = db.customParserRuleDao()
        val now = clock.nowEpochMillis()
        val cleanedName = request.name.trim().ifEmpty {
            "${cleanedSourceId.substringAfterLast('.')} ${request.direction.name}"
        }
        val cleanedTitle = request.titleContains?.trim()?.takeIf { it.isNotEmpty() }
        val cleanedPattern = request.templatePattern.trim()
        val cleanedHint = request.accountHint?.trim()?.takeIf { it.isNotEmpty() }

        val savedId = if (request.id != null) {
            val existing = dao.getById(request.id)
            if (existing != null) {
                dao.update(
                    existing.copy(
                        sourceId = cleanedSourceId,
                        name = cleanedName,
                        titleContains = cleanedTitle,
                        templatePattern = cleanedPattern,
                        direction = request.direction.name,
                        transactionType = request.transactionType.name,
                        currency = normalizedCurrency,
                        numberLocale = request.numberLocale.name,
                        accountHint = cleanedHint,
                        enabled = request.enabled,
                        updatedAt = now,
                    ),
                )
                existing.id
            } else {
                dao.insert(
                    CustomParserRuleEntity(
                        sourceId = cleanedSourceId,
                        name = cleanedName,
                        titleContains = cleanedTitle,
                        templatePattern = cleanedPattern,
                        direction = request.direction.name,
                        transactionType = request.transactionType.name,
                        currency = normalizedCurrency,
                        numberLocale = request.numberLocale.name,
                        accountHint = cleanedHint,
                        enabled = request.enabled,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        } else {
            dao.insert(
                CustomParserRuleEntity(
                    sourceId = cleanedSourceId,
                    name = cleanedName,
                    titleContains = cleanedTitle,
                    templatePattern = cleanedPattern,
                    direction = request.direction.name,
                    transactionType = request.transactionType.name,
                    currency = normalizedCurrency,
                    numberLocale = request.numberLocale.name,
                    accountHint = cleanedHint,
                    enabled = request.enabled,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        ruleCache.refreshSnapshotNow()
        return savedId
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        holder.requireDatabase().customParserRuleDao().setEnabled(id, enabled, clock.nowEpochMillis())
        ruleCache.refreshSnapshotNow()
    }

    override suspend fun delete(id: Long) {
        holder.requireDatabase().customParserRuleDao().deleteById(id)
        ruleCache.refreshSnapshotNow()
    }

    override fun generateTemplateFromSample(
        sampleBody: String,
        merchantOrDescHint: String?,
        defaultCurrency: String,
    ): GeneratedParserTemplate? {
        val result = NotificationTemplateEngine.generateTemplateFromSample(
            sampleBody = sampleBody,
            merchantOrDescHint = merchantOrDescHint,
            defaultCurrency = defaultCurrency,
        ) ?: return null
        return GeneratedParserTemplate(
            templatePattern = result.templatePattern,
            currency = result.currency,
            numberLocale = result.numberLocale.toDomainLocale(),
            detectedMerchant = result.detectedMerchant,
            detectedAmountMinor = result.detectedAmountMinor,
        )
    }

    override fun testRuleAgainstSample(
        templatePattern: String,
        titleContains: String?,
        direction: Direction,
        transactionType: DomainTransactionType,
        currency: String,
        numberLocale: CustomNumberLocale,
        sampleTitle: String?,
        sampleBody: String,
    ): CustomParserTestOutcome {
        val validationError = NotificationTemplateEngine.validateTemplate(templatePattern)
        if (validationError != null) {
            return CustomParserTestOutcome.Rejected(validationError)
        }
        val normalizedCurrency = currency.trim().uppercase()
        if (normalizedCurrency.length != 3 || !normalizedCurrency.all { it in 'A'..'Z' }) {
            return CustomParserTestOutcome.Rejected("Currency must be a 3-letter ISO code.")
        }
        if (sampleBody.isBlank()) {
            return CustomParserTestOutcome.NotMatched("Enter a sample notification body to test.")
        }

        val spec = CustomParserRuleSpec(
            id = 0L,
            sourceId = "test.preview",
            name = "Preview",
            titleContains = titleContains?.trim()?.takeIf { it.isNotEmpty() },
            templatePattern = templatePattern.trim(),
            direction = direction,
            transactionType = transactionType.toIntegrationType(),
            currency = normalizedCurrency,
            numberLocale = numberLocale.toProviderLocale(),
            accountHint = null,
            updatedAt = 0L,
        )
        val event = NotificationEvent(
            sourceId = "test.preview",
            packageName = "test.preview",
            title = sampleTitle?.trim()?.takeIf { it.isNotEmpty() },
            text = sampleBody.trim(),
            bigText = null,
            subText = null,
            channelId = null,
            isGroupSummary = false,
            isContentRedacted = false,
            postedAtEpochMillis = clock.nowEpochMillis(),
            notificationKey = null,
            notificationId = 0,
            notificationTag = null,
            sourceEventReference = null,
        )

        return when (val result = NotificationTemplateEngine.parseWithRules(event, listOf(spec))) {
            is ParseResult.Success -> CustomParserTestOutcome.Matched(
                amountMinor = result.transaction.amountMinor,
                currency = result.transaction.currency,
                direction = result.transaction.direction,
                transactionType = result.transaction.type.toDomain(),
                merchantRaw = result.transaction.merchantRaw,
                description = result.transaction.description,
            )
            is ParseResult.NotTransaction -> CustomParserTestOutcome.Rejected(
                "Ignored by safety/non-transaction guard (${result.reason.name}).",
            )
            is ParseResult.NeedsReview -> CustomParserTestOutcome.Rejected(
                "Matched text, but number format failed (${result.reason.name}). Try switching ID/EN number locale.",
            )
            null -> CustomParserTestOutcome.NotMatched(
                "Pattern did not match the sample text. Check wording, placeholders, or optional Title filter.",
            )
        }
    }

    override suspend fun retryPendingReviewForSource(sourceId: String): Int {
        val retryRunner = onRetryEvent ?: return 0
        val db = holder.requireDatabase()
        val dao = db.notificationEventDao()
        val pending = dao.getAllOnce().filter {
            it.sourceId == sourceId && it.processingStatus == NotificationEventStatus.NEEDS_REVIEW.name
        }
        if (pending.isEmpty()) return 0

        var processedCount = 0
        val now = clock.nowEpochMillis()
        pending.forEach { entity ->
            dao.update(
                entity.copy(
                    processingStatus = NotificationEventStatus.RECEIVED.name,
                    failureReason = null,
                    attemptCount = 0,
                    updatedAt = now,
                ),
            )
            retryRunner(entity.id)
            val after = dao.getById(entity.id)
            if (after?.processingStatus == NotificationEventStatus.PROCESSED.name) {
                processedCount++
            }
        }
        return processedCount
    }
}
