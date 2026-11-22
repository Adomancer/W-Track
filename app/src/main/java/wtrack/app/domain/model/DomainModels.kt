package wtrack.app.domain.model

import wtrack.integration.Direction
import wtrack.integration.NotTransactionReason
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason

data class Account(
    val id: Long,
    val name: String,
    val provider: String,
    val accountType: AccountType,
    val currency: String,
    val initialBalanceMinor: Long?,
    val initialBalanceAt: Long?,
    val lastReconciledAt: Long?,
    val isActive: Boolean,
    val captureStatus: CaptureStatus = CaptureStatus.ATTENTION,
    val estimatedBalanceMinor: Long = 0L,
    val hasPossibleGap: Boolean = false,
    val unparsedLast30DaysCount: Int = 0,
)

data class OriginalTransactionSnapshot(
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val transactionType: DomainTransactionType,
    val occurredAt: Long,
    val accountId: Long,
    val merchantRaw: String?,
    val description: String?,
)

data class Transaction(
    val id: Long,
    val accountId: Long,
    val sourceId: String?,
    val eventId: Long?,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val transactionType: DomainTransactionType,
    val merchantId: Long?,
    val merchantName: String? = null,
    val description: String?,
    val note: String? = null,
    val occurredAt: Long,
    val categoryId: Long?,
    val categorySource: CategorySource?,
    val appliedRuleId: Long?,
    val status: TransactionStatus,
    val fingerprint: String?,
    val parserVersion: Int?,
    val originalSnapshot: OriginalTransactionSnapshot?,
    val providerReference: String?,
    val relatedTransactionId: Long?,
    val reportedBalanceMinor: Long?,
    val retainedPayloadPreview: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

data class NotificationEventRecord(
    val id: Long,
    val sourceId: String,
    val packageName: String,
    val stableEventKey: String,
    val notificationKey: String?,
    val notificationId: Int,
    val notificationTag: String?,
    val postedAt: Long,
    val hasPayload: Boolean,
    val payloadPreview: String?,
    val purgeAfter: Long?,
    val processingStatus: NotificationEventStatus,
    val attemptCount: Int,
    val failureReason: String?,
    val reviewDraft: PartialTransaction?,
    val receivedAt: Long,
    val processedAt: Long?,
    val updatedAt: Long,
)

data class NeedsReviewItem(
    val id: Long,
    val sourceId: String,
    val packageName: String,
    val postedAt: Long,
    val reviewReason: ReviewReason,
    val payloadText: String?,
    val draftAmountMinor: Long?,
    val draftCurrency: String?,
    val draftDirection: Direction?,
    val draftType: DomainTransactionType?,
    val draftOccurredAt: Long?,
    val draftMerchantRaw: String?,
    val draftDescription: String?,
    val draftAccountHint: String?,
    val draftAccountId: Long?,
    val draftProviderReference: String?,
    val draftReportedBalanceMinor: Long?,
    val payloadTitle: String? = null,
    val payloadBody: String? = null,
)

data class CustomParserRule(
    val id: Long,
    val sourceId: String,
    val name: String,
    val titleContains: String?,
    val templatePattern: String,
    val direction: Direction,
    val transactionType: DomainTransactionType,
    val currency: String,
    val numberLocale: CustomNumberLocale,
    val accountHint: String?,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class TransactionSource(
    val id: String,
    val packageName: String,
    val displayName: String,
    val isAllowlisted: Boolean,
    val parserVersion: Int?,
    val sampleCollectionUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class AccountSource(
    val id: Long,
    val accountId: Long,
    val sourceId: String,
    val accountHint: String?,
    val createdAt: Long,
)

data class Category(
    val id: Long,
    val name: String,
    val systemKey: String?,
    val directionScope: Direction?,
    val parentId: Long?,
    val isSystem: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
)

data class Merchant(
    val id: Long,
    val displayName: String,
    val key: String,
    val createdAt: Long,
)

data class TransactionRule(
    val id: Long,
    val name: String,
    val merchantKey: String?,
    val descriptionFragment: String?,
    val accountId: Long?,
    val direction: Direction?,
    val categoryId: Long,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class TransferPair(
    val id: Long,
    val outgoingTransactionId: Long,
    val incomingTransactionId: Long,
    val origin: TransferOrigin,
    val confidence: Double?,
    val status: TransferStatus,
    val feeMinor: Long,
    val createdAt: Long,
)

/**
 * Read-only derived fee row attributed in the Fees category to the outgoing leg's account and date (TRF-04).
 */
data class DerivedFeeRow(
    val transferPairId: Long,
    val outgoingTransactionId: Long,
    val accountId: Long,
    val occurredAt: Long,
    val amountMinor: Long,
    val currency: String,
)

data class CaptureGap(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val reason: CaptureGapReason,
)

data class Tag(
    val id: Long,
    val name: String,
    val createdAt: Long,
)

data class Budget(
    val id: Long,
    val categoryId: Long,
    val accountId: Long?,
    val limitMinor: Long,
    val period: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val baseCurrency: String = "IDR",
    val verificationWindowDays: Int = 7,
    val heartbeatStaleHours: Int = 3,
    val lastAliveAt: Long = 0L,
    val backupReminderDays: Int = 14,
    val backupReminderTxCount: Int = 100,
    val lastBackupAt: Long = 0L,
    val txCountSinceLastBackup: Int = 0,
    val backupReminderDismissedAt: Long = 0L,
    val onboardingCompleted: Boolean = false,
    val scheduledBackupFolderUri: String? = null,
    val scheduledBackupEnabled: Boolean = false,
    val scheduledBackupIntervalDays: Int = 7,
)

sealed interface DatabaseHealthState {
    data object Ready : DatabaseHealthState

    data class Locked(
        val reason: LockedReason,
        val detail: String,
    ) : DatabaseHealthState
}

enum class LockedReason {
    KEY_MISSING,
    KEY_INVALIDATED_OR_CORRUPT,
    DATABASE_OPEN_FAILED,
}

data class CaptureHealthSummary(
    val appStatus: CaptureStatus,
    val isPermissionGranted: Boolean,
    val isListenerConnected: Boolean,
    val openGap: CaptureGap?,
    val lastAliveAt: Long,
)

data class NonTransactionOutcome(
    val reason: NotTransactionReason,
    val eventId: Long,
)
