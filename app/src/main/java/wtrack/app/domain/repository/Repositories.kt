package wtrack.app.domain.repository

import kotlinx.coroutines.flow.Flow
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountSource
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.Budget
import wtrack.app.domain.model.CaptureGap
import wtrack.app.domain.model.CaptureHealthSummary
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.CustomParserRule
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.Merchant
import wtrack.app.domain.model.NeedsReviewItem
import wtrack.app.domain.model.NotificationEventRecord
import wtrack.app.domain.model.Tag
import wtrack.app.domain.model.ThemeMode
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionRule
import wtrack.app.domain.model.TransactionSource
import wtrack.app.domain.model.TransferPair
import wtrack.integration.Direction

data class CreateAccountRequest(
    val name: String,
    val provider: String,
    val accountType: AccountType,
    val currency: String,
    val initialBalanceMinor: Long? = null,
    val initialBalanceAt: Long? = null,
)

data class UpdateAccountRequest(
    val id: Long,
    val name: String,
    val provider: String,
    val accountType: AccountType,
    val currency: String,
)

data class CreateManualTransactionRequest(
    val accountId: Long,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val transactionType: DomainTransactionType,
    val occurredAt: Long,
    val categoryId: Long? = null,
    val description: String? = null,
    val note: String? = null,
)

data class UpdateTransactionRequest(
    val id: Long,
    val accountId: Long,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val transactionType: DomainTransactionType,
    val occurredAt: Long,
    val categoryId: Long? = null,
    val description: String? = null,
    val note: String? = null,
    val categorySetByUser: Boolean = false,
)

data class SaveReviewItemRequest(
    val accountId: Long,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val transactionType: DomainTransactionType,
    val occurredAt: Long,
    val description: String?,
    val categoryId: Long? = null,
    val rememberAccountHint: Boolean = false,
)

data class LinkTransferRequest(
    val outgoingTransactionId: Long,
    val incomingTransactionId: Long,
)

interface AccountRepository {
    val accounts: Flow<List<Account>>
    suspend fun account(id: Long): Account?
    suspend fun getAllOnce(): List<Account>
    suspend fun create(request: CreateAccountRequest): Long
    suspend fun update(request: UpdateAccountRequest)
    suspend fun archive(id: Long)
    suspend fun restore(id: Long)
    suspend fun updateLastReconciledAt(accountId: Long, atEpochMillis: Long)
    val accountSources: Flow<List<AccountSource>>
    suspend fun getAccountSourcesForSource(sourceId: String): List<AccountSource>
    suspend fun linkAccountSource(accountId: Long, sourceId: String, accountHint: String?): Long
    suspend fun unlinkAccountSource(id: Long)
}

interface TransactionRepository {
    val transactions: Flow<List<Transaction>>
    suspend fun transaction(id: Long): Transaction?
    suspend fun getAllOnce(): List<Transaction>
    suspend fun insertManual(request: CreateManualTransactionRequest): Long
    suspend fun insertAdjustment(
        accountId: Long,
        amountMinor: Long,
        currency: String,
        direction: Direction,
        occurredAt: Long,
        description: String?,
    ): Long
    suspend fun update(request: UpdateTransactionRequest)
    suspend fun setCategory(transactionId: Long, categoryId: Long?, byUser: Boolean, ruleId: Long? = null)
    suspend fun voidTransaction(id: Long)
    suspend fun restoreTransaction(id: Long)
    suspend fun deleteManual(id: Long)
    suspend fun linkRefund(refundTransactionId: Long, originalTransactionId: Long?)
    suspend fun findByProviderReference(sourceId: String, providerReference: String): Transaction?
    suspend fun countUncategorizedForMerchant(merchantId: Long): Int
    suspend fun applyCategoryToUncategorizedMerchant(merchantId: Long, categoryId: Long, ruleId: Long): Int
}

interface NeedsReviewRepository {
    val items: Flow<List<NeedsReviewItem>>
    val pendingCount: Flow<Int>
    suspend fun item(id: Long): NeedsReviewItem?
    suspend fun saveResolved(itemId: Long, resolved: SaveReviewItemRequest): Long
    suspend fun ignore(itemId: Long)
    suspend fun retry(itemId: Long)
}

interface TransferRepository {
    val pairs: Flow<List<TransferPair>>
    suspend fun getAllOnce(): List<TransferPair>
    suspend fun findConfirmedForTransaction(transactionId: Long): TransferPair?
    suspend fun link(request: LinkTransferRequest, feeMinor: Long): Long
    suspend fun updateFee(pairId: Long, feeMinor: Long)
    suspend fun unlink(pairId: Long)
}

interface CategoryRepository {
    val categories: Flow<List<Category>>
    suspend fun getAllOnce(): List<Category>
    suspend fun findBySystemKey(systemKey: String): Category?
    suspend fun create(name: String, directionScope: Direction?, parentId: Long? = null): Long
    suspend fun rename(id: Long, newName: String)
    suspend fun setArchived(id: Long, archived: Boolean)
}

interface MerchantRepository {
    val merchants: Flow<List<Merchant>>
    suspend fun findByKey(key: String): Merchant?
    suspend fun getOrCreate(displayName: String, normalizedKey: String): Long
}

interface RuleRepository {
    val rules: Flow<List<TransactionRule>>
    suspend fun getEnabledOnce(): List<TransactionRule>
    suspend fun createRule(
        name: String,
        merchantKey: String?,
        descriptionFragment: String?,
        accountId: Long?,
        direction: Direction?,
        categoryId: Long,
    ): Long
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun delete(id: Long)
}

interface TagRepository {
    val tags: Flow<List<Tag>>
    fun observeAllTransactionTags(): Flow<Map<Long, List<Tag>>>
    suspend fun getTagsForTransaction(transactionId: Long): List<Tag>
    suspend fun addTagToTransaction(transactionId: Long, tagName: String)
    suspend fun removeTagFromTransaction(transactionId: Long, tagId: Long)
}

interface BudgetRepository {
    val budgets: Flow<List<Budget>>
    suspend fun upsertBudget(categoryId: Long, accountId: Long?, limitMinor: Long, period: String): Long
    suspend fun deleteBudget(id: Long)
}

interface SourceAndEventRepository {
    val sources: Flow<List<TransactionSource>>
    suspend fun getSource(id: String): TransactionSource?
    suspend fun upsertSource(packageName: String, displayName: String, isAllowlisted: Boolean, parserVersion: Int?)
    suspend fun setAllowlisted(packageName: String, allowlisted: Boolean)
    suspend fun setSampleCollectionUntil(sourceId: String, untilEpochMillis: Long?)
    suspend fun countNoParserLast30Days(sourceId: String, sinceEpochMillis: Long): Int
    suspend fun getAllEventsOnce(): List<NotificationEventRecord>
    suspend fun purgeExpiredPayloads(nowEpochMillis: Long): Int
}

interface CaptureStatusRepository {
    val healthSummary: Flow<CaptureHealthSummary>
    val captureGaps: Flow<List<CaptureGap>>
    suspend fun refreshStatus()
    suspend fun recordGap(startedAt: Long, endedAt: Long?, reason: wtrack.app.domain.model.CaptureGapReason)
    suspend fun closeOpenGaps(reason: wtrack.app.domain.model.CaptureGapReason, endedAt: Long)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setBaseCurrency(currency: String)
    suspend fun setVerificationWindowDays(days: Int)
    suspend fun setHeartbeatStaleHours(hours: Int)
    suspend fun updateLastAliveAt(epochMillis: Long)
    suspend fun recordBackupCompleted(atEpochMillis: Long)
    suspend fun dismissBackupReminder(atEpochMillis: Long)
    suspend fun setBackupReminderThresholds(days: Int, txCount: Int)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setScheduledBackupConfig(folderUri: String?, enabled: Boolean, intervalDays: Int)
}

interface DatabaseHealthRepository {
    val state: Flow<DatabaseHealthState>
    suspend fun wipeAllDataAndReset()
}

data class UpsertCustomParserRuleRequest(
    val id: Long? = null,
    val sourceId: String,
    val name: String,
    val titleContains: String? = null,
    val templatePattern: String,
    val direction: Direction,
    val transactionType: DomainTransactionType,
    val currency: String = "IDR",
    val numberLocale: CustomNumberLocale = CustomNumberLocale.INDONESIAN,
    val accountHint: String? = null,
    val enabled: Boolean = true,
)

data class GeneratedParserTemplate(
    val templatePattern: String,
    val currency: String,
    val numberLocale: CustomNumberLocale,
    val detectedMerchant: String?,
    val detectedAmountMinor: Long?,
)

sealed interface CustomParserTestOutcome {
    data class Matched(
        val amountMinor: Long,
        val currency: String,
        val direction: Direction,
        val transactionType: DomainTransactionType,
        val merchantRaw: String?,
        val description: String?,
    ) : CustomParserTestOutcome

    data class Rejected(val reason: String) : CustomParserTestOutcome

    data class NotMatched(val detail: String) : CustomParserTestOutcome
}

interface CustomParserRuleRepository {
    val rules: Flow<List<CustomParserRule>>
    suspend fun getEnabledOnce(): List<CustomParserRule>
    suspend fun upsertRule(request: UpsertCustomParserRuleRequest): Long
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun delete(id: Long)
    fun generateTemplateFromSample(
        sampleBody: String,
        merchantOrDescHint: String? = null,
        defaultCurrency: String = "IDR",
    ): GeneratedParserTemplate?

    fun testRuleAgainstSample(
        templatePattern: String,
        titleContains: String?,
        direction: Direction,
        transactionType: DomainTransactionType,
        currency: String,
        numberLocale: CustomNumberLocale,
        sampleTitle: String?,
        sampleBody: String,
    ): CustomParserTestOutcome

    suspend fun retryPendingReviewForSource(sourceId: String): Int
}
