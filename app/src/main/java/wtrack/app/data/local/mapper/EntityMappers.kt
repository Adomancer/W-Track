package wtrack.app.data.local.mapper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.BudgetEntity
import wtrack.app.data.local.entity.CaptureGapEntity
import wtrack.app.data.local.entity.CategoryEntity
import wtrack.app.data.local.entity.MerchantEntity
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TagEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionRuleEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.entity.TransferMatchEntity
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountSource
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.Budget
import wtrack.app.domain.model.CaptureGap
import wtrack.app.domain.model.CaptureGapReason
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.CategorySource
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.Merchant
import wtrack.app.domain.model.NeedsReviewItem
import wtrack.app.domain.model.NotificationEventRecord
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.model.OriginalTransactionSnapshot
import wtrack.app.domain.model.Tag
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionRule
import wtrack.app.domain.model.TransactionSource
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferOrigin
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.model.TransferStatus
import wtrack.app.domain.model.toDomain
import wtrack.integration.Direction
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason

@Serializable
data class NotificationPayloadJson(
    val title: String? = null,
    val text: String = "",
    val bigText: String? = null,
    val subText: String? = null,
    val channelId: String? = null,
    val isGroupSummary: Boolean = false,
    val isContentRedacted: Boolean = false,
)

@Serializable
data class OriginalSnapshotJson(
    val amountMinor: Long,
    val currency: String,
    val direction: String,
    val transactionType: String,
    val occurredAt: Long,
    val accountId: Long,
    val merchantRaw: String? = null,
    val description: String? = null,
)

@Serializable
data class RuleConditionJson(
    val merchantKey: String? = null,
    val descriptionFragment: String? = null,
    val accountId: Long? = null,
    val direction: String? = null,
)

@Serializable
data class RuleActionJson(
    val categoryId: Long,
)

object LocalJson {
    val codec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodePayload(payload: NotificationPayloadJson): String = codec.encodeToString(payload)

    fun decodePayload(json: String?): NotificationPayloadJson? =
        json?.let { runCatching { codec.decodeFromString<NotificationPayloadJson>(it) }.getOrNull() }

    fun encodeOriginalSnapshot(snapshot: OriginalTransactionSnapshot): String =
        codec.encodeToString(
            OriginalSnapshotJson(
                amountMinor = snapshot.amountMinor,
                currency = snapshot.currency,
                direction = snapshot.direction.name,
                transactionType = snapshot.transactionType.name,
                occurredAt = snapshot.occurredAt,
                accountId = snapshot.accountId,
                merchantRaw = snapshot.merchantRaw,
                description = snapshot.description,
            ),
        )

    fun decodeOriginalSnapshot(json: String?): OriginalTransactionSnapshot? {
        val raw = json ?: return null
        val dto = runCatching { codec.decodeFromString<OriginalSnapshotJson>(raw) }.getOrNull() ?: return null
        return OriginalTransactionSnapshot(
            amountMinor = dto.amountMinor,
            currency = dto.currency,
            direction = Direction.valueOf(dto.direction),
            transactionType = DomainTransactionType.valueOf(dto.transactionType),
            occurredAt = dto.occurredAt,
            accountId = dto.accountId,
            merchantRaw = dto.merchantRaw,
            description = dto.description,
        )
    }

    fun encodePartialDraft(partial: PartialTransaction?): String? =
        partial?.let { codec.encodeToString(it) }

    fun decodePartialDraft(json: String?): PartialTransaction? =
        json?.let { runCatching { codec.decodeFromString<PartialTransaction>(it) }.getOrNull() }

    fun encodeRuleCondition(
        merchantKey: String?,
        descriptionFragment: String?,
        accountId: Long?,
        direction: Direction?,
    ): String = codec.encodeToString(
        RuleConditionJson(
            merchantKey = merchantKey,
            descriptionFragment = descriptionFragment,
            accountId = accountId,
            direction = direction?.name,
        ),
    )

    fun decodeRuleCondition(json: String): RuleConditionJson =
        runCatching { codec.decodeFromString<RuleConditionJson>(json) }.getOrElse { RuleConditionJson() }

    fun encodeRuleAction(categoryId: Long): String = codec.encodeToString(RuleActionJson(categoryId))
}

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    provider = provider,
    accountType = AccountType.valueOf(accountType),
    currency = currency,
    initialBalanceMinor = initialBalanceMinor,
    initialBalanceAt = initialBalanceAt,
    lastReconciledAt = lastReconciledAt,
    isActive = isActive,
)

fun AccountSourceEntity.toDomain(): AccountSource = AccountSource(
    id = id,
    accountId = accountId,
    sourceId = sourceId,
    accountHint = accountHint,
    createdAt = createdAt,
)

fun TransactionSourceEntity.toDomain(): TransactionSource = TransactionSource(
    id = id,
    packageName = packageName,
    displayName = displayName,
    isAllowlisted = isAllowlisted,
    parserVersion = parserVersion,
    sampleCollectionUntil = sampleCollectionUntil,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TransactionEntity.toDomain(
    merchantName: String? = null,
    retainedPayloadPreview: String? = null,
): Transaction = Transaction(
    id = id,
    accountId = accountId,
    sourceId = sourceId,
    eventId = eventId,
    amountMinor = amountMinor,
    currency = currency,
    direction = Direction.valueOf(direction),
    transactionType = DomainTransactionType.valueOf(transactionType),
    merchantId = merchantId,
    merchantName = merchantName,
    description = description,
    note = note,
    occurredAt = occurredAt,
    categoryId = categoryId,
    categorySource = categorySource?.let { CategorySource.valueOf(it) },
    appliedRuleId = appliedRuleId,
    status = TransactionStatus.valueOf(status),
    fingerprint = fingerprint,
    parserVersion = parserVersion,
    originalSnapshot = LocalJson.decodeOriginalSnapshot(originalJson),
    providerReference = providerReference,
    relatedTransactionId = relatedTransactionId,
    reportedBalanceMinor = reportedBalanceMinor,
    retainedPayloadPreview = retainedPayloadPreview,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun NotificationEventEntity.toDomainRecord(): NotificationEventRecord {
    val payload = LocalJson.decodePayload(payloadJson)
    return NotificationEventRecord(
        id = id,
        sourceId = sourceId,
        packageName = packageName,
        stableEventKey = stableEventKey,
        notificationKey = notificationKey,
        notificationId = notificationId,
        notificationTag = notificationTag,
        postedAt = postedAt,
        hasPayload = payloadJson != null,
        payloadPreview = payload?.let(::formatPayloadPreview),
        purgeAfter = purgeAfter,
        processingStatus = NotificationEventStatus.valueOf(processingStatus),
        attemptCount = attemptCount,
        failureReason = failureReason,
        reviewDraft = LocalJson.decodePartialDraft(reviewDraftJson),
        receivedAt = receivedAt,
        processedAt = processedAt,
        updatedAt = updatedAt,
    )
}

fun NotificationEventEntity.toNeedsReviewItem(resolvedAccountId: Long? = null): NeedsReviewItem {
    val draft = LocalJson.decodePartialDraft(reviewDraftJson)
    val payload = LocalJson.decodePayload(payloadJson)
    val reason = failureReason?.let { raw ->
        runCatching { ReviewReason.valueOf(raw) }.getOrNull()
    } ?: ReviewReason.UNKNOWN_FORMAT
    return NeedsReviewItem(
        id = id,
        sourceId = sourceId,
        packageName = packageName,
        postedAt = postedAt,
        reviewReason = reason,
        payloadText = payload?.let(::formatPayloadPreview),
        draftAmountMinor = draft?.amountMinor,
        draftCurrency = draft?.currency,
        draftDirection = draft?.direction,
        draftType = draft?.type?.toDomain(),
        draftOccurredAt = draft?.occurredAtEpochMillis,
        draftMerchantRaw = draft?.merchantRaw,
        draftDescription = draft?.description,
        draftAccountHint = draft?.accountHint,
        draftAccountId = resolvedAccountId,
        draftProviderReference = draft?.providerReference,
        draftReportedBalanceMinor = draft?.reportedBalanceMinor,
    )
}

fun formatPayloadPreview(payload: NotificationPayloadJson): String {
    val body = payload.bigText?.takeIf { it.isNotBlank() } ?: payload.text
    return listOfNotNull(
        payload.title?.takeIf { it.isNotBlank() },
        body.takeIf { it.isNotBlank() },
    ).joinToString("\n").ifBlank { "[Empty or redacted content]" }
}

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    systemKey = systemKey,
    directionScope = directionScope?.let { Direction.valueOf(it) },
    parentId = parentId,
    isSystem = isSystem,
    isArchived = isArchived,
    createdAt = createdAt,
)

fun MerchantEntity.toDomain(): Merchant = Merchant(
    id = id,
    displayName = displayName,
    key = key,
    createdAt = createdAt,
)

fun TransactionRuleEntity.toDomain(): TransactionRule {
    val cond = LocalJson.decodeRuleCondition(conditionJson)
    return TransactionRule(
        id = id,
        name = name,
        merchantKey = cond.merchantKey,
        descriptionFragment = cond.descriptionFragment,
        accountId = accountId ?: cond.accountId,
        direction = cond.direction?.let { Direction.valueOf(it) },
        categoryId = categoryId,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun TransferMatchEntity.toDomain(): TransferPair = TransferPair(
    id = id,
    outgoingTransactionId = outgoingTransactionId,
    incomingTransactionId = incomingTransactionId,
    origin = TransferOrigin.valueOf(origin),
    confidence = confidence,
    status = TransferStatus.valueOf(status),
    feeMinor = feeMinor,
    createdAt = createdAt,
)

fun CaptureGapEntity.toDomain(): CaptureGap = CaptureGap(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    reason = CaptureGapReason.valueOf(reason),
)

fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    createdAt = createdAt,
)

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = categoryId,
    accountId = accountId,
    limitMinor = limitMinor,
    period = period,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
