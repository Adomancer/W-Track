package wtrack.app.data.ingestion

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wtrack.app.core.logging.WtrackLogger
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.local.WtrackDatabase
import wtrack.app.data.local.entity.MerchantEntity
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.mapper.LocalJson
import wtrack.app.data.local.mapper.NotificationPayloadJson
import wtrack.app.data.local.mapper.toDomain
import wtrack.app.data.local.preferences.AppSettingsDataStore
import wtrack.app.domain.model.CategorySource
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.model.OriginalTransactionSnapshot
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.parser.ParserRegistry
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.AccountResolutionResult
import wtrack.app.domain.usecase.AccountResolver
import wtrack.app.domain.usecase.CategoryRuleMatcher
import wtrack.app.domain.usecase.IdentityHashGenerator
import wtrack.app.domain.usecase.MerchantKeyNormalizer
import wtrack.app.domain.usecase.TransactionValidator
import wtrack.app.domain.usecase.ValidationOutcome
import wtrack.integration.Direction
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason

/**
 * Orchestrates the 16-step notification ingestion pipeline (PRD section 6.2):
 * Stage 1 persists accepted events before parsing so process death cannot lose them.
 * Stage 2 serializes parsing, validation, deduplication, and atomic ledger writes.
 */
class IngestionPipeline(
    private val holder: WtrackDatabaseHolder,
    private val allowlistCache: AllowlistCache,
    private val parserRegistry: ParserRegistry,
    private val settingsStore: AppSettingsDataStore,
    private val heartbeatMonitor: HeartbeatMonitor,
    private val clock: Clock,
    private val logger: WtrackLogger,
    private val validator: TransactionValidator = TransactionValidator(),
    private val accountResolver: AccountResolver = AccountResolver(),
    private val deduplicationEngine: DeduplicationEngine = DeduplicationEngine(),
    private val ruleMatcher: CategoryRuleMatcher = CategoryRuleMatcher(),
) {
    private val pipelineMutex = Mutex()

    suspend fun ingest(envelope: NotificationEvent, enforceAllowlist: Boolean = true): Long? {
        if (holder.healthState.value !is DatabaseHealthState.Ready) {
            logger.logEvent("INGEST", "SKIPPED_DB_LOCKED", mapOf("sourceId" to envelope.sourceId))
            return null
        }
        if (enforceAllowlist && !allowlistCache.isAllowlisted(envelope.packageName)) {
            return null
        }

        val eventId = persistIncomingEvent(envelope) ?: return null
        processEvent(eventId)
        return eventId
    }

    suspend fun persistIncomingEvent(envelope: NotificationEvent): Long? {
        val db = holder.requireDatabase()
        val now = clock.nowEpochMillis()
        val stableKey = IdentityHashGenerator.stableEventKey(envelope)
        val payloadJson = LocalJson.encodePayload(
            NotificationPayloadJson(
                title = envelope.title,
                text = envelope.text,
                bigText = envelope.bigText,
                subText = envelope.subText,
                channelId = envelope.channelId,
                isGroupSummary = envelope.isGroupSummary,
                isContentRedacted = envelope.isContentRedacted,
            ),
        )

        var insertedId = -1L
        db.withTransaction {
            val sourceDao = db.transactionSourceDao()
            if (sourceDao.getById(envelope.sourceId) == null) {
                sourceDao.upsert(
                    TransactionSourceEntity(
                        id = envelope.sourceId,
                        packageName = envelope.packageName,
                        displayName = envelope.packageName,
                        isAllowlisted = true,
                        parserVersion = null,
                        sampleCollectionUntil = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

            insertedId = db.notificationEventDao().insertIgnore(
                NotificationEventEntity(
                    sourceId = envelope.sourceId,
                    packageName = envelope.packageName,
                    stableEventKey = stableKey,
                    notificationKey = envelope.notificationKey,
                    notificationId = envelope.notificationId,
                    notificationTag = envelope.notificationTag,
                    postedAt = envelope.postedAtEpochMillis,
                    payloadJson = payloadJson,
                    purgeAfter = null,
                    processingStatus = NotificationEventStatus.RECEIVED.name,
                    attemptCount = 0,
                    failureReason = null,
                    reviewDraftJson = null,
                    receivedAt = now,
                    processedAt = null,
                    updatedAt = now,
                ),
            )
        }
        return if (insertedId > 0L) insertedId else null
    }

    suspend fun processEvent(eventId: Long) {
        pipelineMutex.withLock {
            val db = holder.requireDatabase()
            val eventDao = db.notificationEventDao()
            val event = eventDao.getById(eventId) ?: return
            if (event.processingStatus == NotificationEventStatus.PROCESSED.name ||
                event.processingStatus == NotificationEventStatus.IGNORED.name
            ) {
                return
            }

            val now = clock.nowEpochMillis()
            if (event.attemptCount >= MAX_ATTEMPTS) {
                escalateToPipelineError(db, event, now)
                return
            }

            val attemptNumber = event.attemptCount + 1
            eventDao.update(
                event.copy(
                    processingStatus = NotificationEventStatus.PROCESSING.name,
                    attemptCount = attemptNumber,
                    updatedAt = now,
                ),
            )

            try {
                executePipelineAttempt(db, event.copy(attemptCount = attemptNumber), now)
            } catch (t: Throwable) {
                val failNow = clock.nowEpochMillis()
                if (attemptNumber >= MAX_ATTEMPTS) {
                    escalateToPipelineError(db, event.copy(attemptCount = attemptNumber), failNow)
                } else {
                    eventDao.update(
                        event.copy(
                            processingStatus = NotificationEventStatus.FAILED.name,
                            attemptCount = attemptNumber,
                            failureReason = t::class.java.simpleName,
                            updatedAt = failNow,
                        ),
                    )
                    logger.logEvent(
                        "INGEST",
                        "ATTEMPT_FAILED",
                        mapOf(
                            "eventId" to eventId.toString(),
                            "sourceId" to event.sourceId,
                            "attempt" to attemptNumber.toString(),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun executePipelineAttempt(
        db: WtrackDatabase,
        event: NotificationEventEntity,
        now: Long,
    ) {
        val payload = LocalJson.decodePayload(event.payloadJson)
        val envelope = NotificationEvent(
            sourceId = event.sourceId,
            packageName = event.packageName,
            title = payload?.title,
            text = payload?.text.orEmpty(),
            bigText = payload?.bigText,
            subText = payload?.subText,
            channelId = payload?.channelId,
            isGroupSummary = payload?.isGroupSummary ?: false,
            isContentRedacted = payload?.isContentRedacted ?: true,
            postedAtEpochMillis = event.postedAt,
            notificationKey = event.notificationKey,
            notificationId = event.notificationId,
            notificationTag = event.notificationTag,
            sourceEventReference = null,
        )

        val parser = parserRegistry.resolve(envelope)
        if (parser == null) {
            // ING-13: allowlisted source without compatible parser -> IGNORED (NO_PARSER) + immediate purge
            // unless explicit M3a Sample Collection is enabled for this source (<= 14d).
            val source = db.transactionSourceDao().getById(event.sourceId)
            val sampleUntil = source?.sampleCollectionUntil
            val keepSample = sampleUntil != null && sampleUntil > now
            val sampleCap = now + MAX_SAMPLE_COLLECTION_DAYS * MILLIS_PER_DAY
            db.notificationEventDao().updateOutcome(
                id = event.id,
                newStatus = NotificationEventStatus.IGNORED.name,
                failureReason = "NO_PARSER",
                reviewDraftJson = null,
                payloadJson = if (keepSample) event.payloadJson else null,
                purgeAfter = if (keepSample) minOf(sampleUntil, sampleCap) else null,
                processedAt = now,
                updatedAt = now,
            )
            logger.logEvent(
                "INGEST",
                "IGNORED_NO_PARSER",
                mapOf("eventId" to event.id.toString(), "sourceId" to event.sourceId),
            )
            return
        }

        val parseResult = parser.parse(envelope)
        when (parseResult) {
            is ParseResult.NotTransaction -> {
                db.notificationEventDao().updateOutcome(
                    id = event.id,
                    newStatus = NotificationEventStatus.IGNORED.name,
                    failureReason = parseResult.reason.name,
                    reviewDraftJson = null,
                    payloadJson = null,
                    purgeAfter = null,
                    processedAt = now,
                    updatedAt = now,
                )
                logger.logEvent(
                    "INGEST",
                    "IGNORED_NON_TX",
                    mapOf(
                        "eventId" to event.id.toString(),
                        "sourceId" to event.sourceId,
                        "reason" to parseResult.reason.name,
                    ),
                )
            }

            is ParseResult.NeedsReview -> {
                routeToNeedsReview(
                    db = db,
                    event = event,
                    reason = parseResult.reason,
                    partial = parseResult.partial,
                    now = now,
                )
            }

            is ParseResult.Success -> {
                if (envelope.isContentRedacted) {
                    routeToNeedsReview(
                        db = db,
                        event = event,
                        reason = ReviewReason.UNKNOWN_FORMAT,
                        partial = null,
                        now = now,
                    )
                    return
                }
                handleParsedTransaction(db, event, parseResult.transaction, parser.version, now)
            }
        }
    }

    private suspend fun handleParsedTransaction(
        db: WtrackDatabase,
        event: NotificationEventEntity,
        parsed: ParsedTransaction,
        parserVersion: Int,
        now: Long,
    ) {
        val validation = validator.validate(parsed, event.postedAt)
        val valid = when (validation) {
            is ValidationOutcome.Invalid -> {
                routeToNeedsReview(db, event, validation.reason, validation.partial, now)
                return
            }
            is ValidationOutcome.Valid -> validation
        }

        val sourceLinks = db.accountSourceDao().getForSource(event.sourceId).map { it.toDomain() }
        val resolvedAccountId = when (val res = accountResolver.resolve(sourceLinks, parsed.accountHint)) {
            is AccountResolutionResult.NeedsReview -> {
                routeToNeedsReview(
                    db = db,
                    event = event,
                    reason = res.reason,
                    partial = parsed.toPartial(valid.occurredAtEpochMillis),
                    now = now,
                )
                return
            }
            is AccountResolutionResult.Resolved -> res.accountId
        }

        val normalizedMerchantKey = MerchantKeyNormalizer.normalize(parsed.merchantRaw)
        val merchantOrDescKey = normalizedMerchantKey.ifEmpty {
            parsed.description?.trim()?.lowercase().orEmpty()
        }
        val notifIdentityFallback = event.notificationKey ?: "${event.notificationId}:${event.notificationTag.orEmpty()}"

        val (dupMatch, fingerprint) = deduplicationEngine.findDuplicate(
            db = db,
            sourceId = event.sourceId,
            eventId = event.id,
            accountId = resolvedAccountId,
            amountMinor = valid.amountMinor,
            currency = valid.currency,
            direction = valid.direction,
            occurredAtEpochMillis = valid.occurredAtEpochMillis,
            merchantKeyOrDesc = merchantOrDescKey,
            providerReference = parsed.providerReference,
            notificationKeyOrId = notifIdentityFallback,
        )

        if (dupMatch != null) {
            db.notificationEventDao().updateOutcome(
                id = event.id,
                newStatus = NotificationEventStatus.IGNORED.name,
                failureReason = "DUPLICATE_${dupMatch.tier.name}",
                reviewDraftJson = null,
                payloadJson = null,
                purgeAfter = null,
                processedAt = now,
                updatedAt = now,
            )
            logger.logEvent(
                "INGEST",
                "IGNORED_DUPLICATE",
                mapOf(
                    "eventId" to event.id.toString(),
                    "sourceId" to event.sourceId,
                    "reason" to dupMatch.tier.name,
                ),
            )
            return
        }

        val settings = settingsStore.current()
        val purgeAfter = if (settings.verificationWindowDays == 0) {
            null
        } else {
            now + settings.verificationWindowDays * MILLIS_PER_DAY
        }

        val enabledRules = db.transactionRuleDao().getEnabledOnce().map { it.toDomain() }
        val matchedRule = ruleMatcher.findBestMatch(
            rules = enabledRules,
            merchantKey = normalizedMerchantKey.ifEmpty { null },
            description = parsed.description,
            accountId = resolvedAccountId,
            direction = valid.direction,
        )

        val defaultSystemCategoryId: Long? = when {
            matchedRule != null -> matchedRule.categoryId
            valid.transactionType == DomainTransactionType.FEE ->
                db.categoryDao().findBySystemKey(WtrackDatabase.SYSTEM_KEY_FEES)?.id
            else -> null
        }

        val providerRef = parsed.providerReference?.trim()?.takeIf { it.isNotEmpty() }
        val relatedTxId = if (valid.transactionType == DomainTransactionType.REFUND &&
            valid.direction == Direction.INFLOW &&
            providerRef != null
        ) {
            db.transactionDao().findOutflowByProviderReference(event.sourceId, providerRef)?.id
        } else {
            null
        }

        val snapshot = OriginalTransactionSnapshot(
            amountMinor = valid.amountMinor,
            currency = valid.currency,
            direction = valid.direction,
            transactionType = valid.transactionType,
            occurredAt = valid.occurredAtEpochMillis,
            accountId = resolvedAccountId,
            merchantRaw = parsed.merchantRaw,
            description = parsed.description,
        )

        db.withTransaction {
            val merchantId = if (normalizedMerchantKey.isNotEmpty()) {
                val existingMerchant = db.merchantDao().findByKey(normalizedMerchantKey)
                existingMerchant?.id ?: db.merchantDao().insert(
                    MerchantEntity(
                        displayName = parsed.merchantRaw?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedMerchantKey,
                        key = normalizedMerchantKey,
                        createdAt = now,
                    ),
                )
            } else {
                null
            }

            db.transactionDao().insert(
                TransactionEntity(
                    accountId = resolvedAccountId,
                    sourceId = event.sourceId,
                    eventId = event.id,
                    amountMinor = valid.amountMinor,
                    currency = valid.currency,
                    direction = valid.direction.name,
                    transactionType = valid.transactionType.name,
                    merchantId = merchantId,
                    description = parsed.description?.trim(),
                    note = null,
                    occurredAt = valid.occurredAtEpochMillis,
                    categoryId = defaultSystemCategoryId,
                    categorySource = if (matchedRule != null) CategorySource.RULE.name else null,
                    appliedRuleId = matchedRule?.id,
                    status = TransactionStatus.CONFIRMED.name,
                    fingerprint = fingerprint,
                    parserVersion = parserVersion,
                    originalJson = LocalJson.encodeOriginalSnapshot(snapshot),
                    providerReference = parsed.providerReference?.trim(),
                    relatedTransactionId = relatedTxId,
                    reportedBalanceMinor = parsed.reportedBalanceMinor,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            db.notificationEventDao().updateOutcome(
                id = event.id,
                newStatus = NotificationEventStatus.PROCESSED.name,
                failureReason = null,
                reviewDraftJson = null,
                payloadJson = if (purgeAfter == null) null else event.payloadJson,
                purgeAfter = purgeAfter,
                processedAt = now,
                updatedAt = now,
            )

            db.transactionSourceDao().updateParserVersion(event.sourceId, parserVersion, now)
        }

        settingsStore.incrementTxCountSinceBackup()
        heartbeatMonitor.recordAlive()
        logger.logEvent(
            "INGEST",
            "TRANSACTION_SAVED",
            mapOf(
                "eventId" to event.id.toString(),
                "sourceId" to event.sourceId,
                "parserVersion" to parserVersion.toString(),
            ),
        )
    }

    private suspend fun routeToNeedsReview(
        db: WtrackDatabase,
        event: NotificationEventEntity,
        reason: ReviewReason,
        partial: PartialTransaction?,
        now: Long,
    ) {
        val reviewExpiry = event.receivedAt + MAX_REVIEW_RETENTION_DAYS * MILLIS_PER_DAY
        db.notificationEventDao().updateOutcome(
            id = event.id,
            newStatus = NotificationEventStatus.NEEDS_REVIEW.name,
            failureReason = reason.name,
            reviewDraftJson = LocalJson.encodePartialDraft(partial),
            payloadJson = event.payloadJson,
            purgeAfter = reviewExpiry,
            processedAt = null,
            updatedAt = now,
        )
        logger.logEvent(
            "INGEST",
            "NEEDS_REVIEW",
            mapOf(
                "eventId" to event.id.toString(),
                "sourceId" to event.sourceId,
                "reason" to reason.name,
            ),
        )
    }

    private suspend fun escalateToPipelineError(
        db: WtrackDatabase,
        event: NotificationEventEntity,
        now: Long,
    ) {
        val reviewExpiry = event.receivedAt + MAX_REVIEW_RETENTION_DAYS * MILLIS_PER_DAY
        db.notificationEventDao().updateOutcome(
            id = event.id,
            newStatus = NotificationEventStatus.NEEDS_REVIEW.name,
            failureReason = ReviewReason.PIPELINE_ERROR.name,
            reviewDraftJson = event.reviewDraftJson,
            payloadJson = event.payloadJson,
            purgeAfter = reviewExpiry,
            processedAt = null,
            updatedAt = now,
        )
        logger.logEvent(
            "INGEST",
            "ESCALATED_PIPELINE_ERROR",
            mapOf(
                "eventId" to event.id.toString(),
                "sourceId" to event.sourceId,
                "attempt" to event.attemptCount.toString(),
            ),
        )
    }

    private fun ParsedTransaction.toPartial(resolvedOccurredAt: Long): PartialTransaction = PartialTransaction(
        amountMinor = amountMinor,
        currency = currency,
        direction = direction,
        type = type,
        occurredAtEpochMillis = resolvedOccurredAt,
        merchantRaw = merchantRaw,
        description = description,
        accountHint = accountHint,
        providerReference = providerReference,
        reportedBalanceMinor = reportedBalanceMinor,
    )

    companion object {
        const val MAX_ATTEMPTS = 3
        const val MAX_REVIEW_RETENTION_DAYS = 30L
        const val MAX_SAMPLE_COLLECTION_DAYS = 14L
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
