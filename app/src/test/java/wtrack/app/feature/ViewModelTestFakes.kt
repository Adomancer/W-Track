package wtrack.app.feature

import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import wtrack.app.core.coroutines.DispatcherProvider
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountSource
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.Budget
import wtrack.app.domain.model.CaptureGap
import wtrack.app.domain.model.CaptureGapReason
import wtrack.app.domain.model.CaptureHealthSummary
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.CategorySource
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.NeedsReviewItem
import wtrack.app.domain.model.NotificationEventRecord
import wtrack.app.domain.model.ThemeMode
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionRule
import wtrack.app.domain.model.TransactionSource
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferOrigin
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.model.TransferStatus
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.BackupAndDiagnosticsPort
import wtrack.app.domain.repository.BackupVerificationResult
import wtrack.app.domain.repository.BudgetRepository
import wtrack.app.domain.repository.CaptureStatusRepository
import wtrack.app.domain.repository.CategoryRepository
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.CreateManualTransactionRequest
import wtrack.app.domain.repository.LinkTransferRequest
import wtrack.app.domain.repository.NeedsReviewRepository
import wtrack.app.domain.repository.ProviderCatalogEntry
import wtrack.app.domain.repository.RestoreResult
import wtrack.app.domain.repository.RuleRepository
import wtrack.app.domain.repository.SampleExportResult
import wtrack.app.domain.repository.SaveReviewItemRequest
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.repository.SourceAndEventRepository
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.TransferRepository
import wtrack.app.domain.repository.UpdateAccountRequest
import wtrack.app.domain.repository.UpdateTransactionRequest
import wtrack.app.domain.time.Clock
import wtrack.integration.Direction

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class TestDispatcherProvider(
    private val dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

class FakeClock(var now: Long = 1_795_000_000_000L) : Clock {
    override fun nowEpochMillis(): Long = now
}

class FakeAccountRepository(
    initialAccounts: List<Account> = emptyList(),
) : AccountRepository {
    val accountsFlow = MutableStateFlow(initialAccounts)
    val accountSourcesFlow = MutableStateFlow<List<AccountSource>>(emptyList())
    private var nextId = (initialAccounts.maxOfOrNull { it.id } ?: 0L) + 1L
    private var nextLinkId = 1L

    override val accounts: Flow<List<Account>> = accountsFlow
    override suspend fun account(id: Long): Account? = accountsFlow.value.firstOrNull { it.id == id }
    override suspend fun getAllOnce(): List<Account> = accountsFlow.value
    override suspend fun create(request: CreateAccountRequest): Long {
        val id = nextId++
        accountsFlow.value = accountsFlow.value + Account(
            id = id,
            name = request.name,
            provider = request.provider,
            accountType = request.accountType,
            currency = request.currency,
            initialBalanceMinor = request.initialBalanceMinor,
            initialBalanceAt = request.initialBalanceAt,
            lastReconciledAt = request.initialBalanceAt,
            isActive = true,
            estimatedBalanceMinor = request.initialBalanceMinor ?: 0L,
        )
        return id
    }
    override suspend fun update(request: UpdateAccountRequest) {
        accountsFlow.value = accountsFlow.value.map {
            if (it.id == request.id) it.copy(name = request.name, provider = request.provider, accountType = request.accountType, currency = request.currency) else it
        }
    }
    override suspend fun archive(id: Long) { accountsFlow.value = accountsFlow.value.map { if (it.id == id) it.copy(isActive = false) else it } }
    override suspend fun restore(id: Long) { accountsFlow.value = accountsFlow.value.map { if (it.id == id) it.copy(isActive = true) else it } }
    override suspend fun updateLastReconciledAt(accountId: Long, atEpochMillis: Long) {
        accountsFlow.value = accountsFlow.value.map { if (it.id == accountId) it.copy(lastReconciledAt = atEpochMillis) else it }
    }
    override val accountSources: Flow<List<AccountSource>> = accountSourcesFlow
    override suspend fun getAccountSourcesForSource(sourceId: String): List<AccountSource> = accountSourcesFlow.value.filter { it.sourceId == sourceId }
    override suspend fun linkAccountSource(accountId: Long, sourceId: String, accountHint: String?): Long {
        val id = nextLinkId++
        accountSourcesFlow.value = accountSourcesFlow.value + AccountSource(id, accountId, sourceId, accountHint, 1_000L)
        return id
    }
    override suspend fun unlinkAccountSource(id: Long) { accountSourcesFlow.value = accountSourcesFlow.value.filterNot { it.id == id } }
}

class FakeTransactionRepository(
    initial: List<Transaction> = emptyList(),
) : TransactionRepository {
    val txsFlow = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    override val transactions: Flow<List<Transaction>> = txsFlow
    override suspend fun transaction(id: Long): Transaction? = txsFlow.value.firstOrNull { it.id == id }
    override suspend fun getAllOnce(): List<Transaction> = txsFlow.value
    override suspend fun insertManual(request: CreateManualTransactionRequest): Long {
        val id = nextId++
        txsFlow.value = txsFlow.value + Transaction(
            id = id, accountId = request.accountId, sourceId = null, eventId = null,
            amountMinor = request.amountMinor, currency = request.currency, direction = request.direction,
            transactionType = request.transactionType, merchantId = null, description = request.description,
            note = request.note, occurredAt = request.occurredAt, categoryId = request.categoryId,
            categorySource = request.categoryId?.let { CategorySource.USER }, appliedRuleId = null,
            status = TransactionStatus.CONFIRMED, fingerprint = null, parserVersion = null,
            originalSnapshot = null, providerReference = null, relatedTransactionId = null,
            reportedBalanceMinor = null, createdAt = request.occurredAt, updatedAt = request.occurredAt,
        )
        return id
    }
    override suspend fun insertAdjustment(accountId: Long, amountMinor: Long, currency: String, direction: Direction, occurredAt: Long, description: String?): Long {
        val id = nextId++
        txsFlow.value = txsFlow.value + Transaction(
            id = id, accountId = accountId, sourceId = null, eventId = null,
            amountMinor = amountMinor, currency = currency, direction = direction,
            transactionType = DomainTransactionType.ADJUSTMENT, merchantId = null, description = description,
            occurredAt = occurredAt, categoryId = null, categorySource = null, appliedRuleId = null,
            status = TransactionStatus.CONFIRMED, fingerprint = null, parserVersion = null,
            originalSnapshot = null, providerReference = null, relatedTransactionId = null,
            reportedBalanceMinor = null, createdAt = occurredAt, updatedAt = occurredAt,
        )
        return id
    }
    override suspend fun update(request: UpdateTransactionRequest) {
        txsFlow.value = txsFlow.value.map {
            if (it.id == request.id) {
                it.copy(
                    accountId = request.accountId,
                    amountMinor = request.amountMinor,
                    currency = request.currency,
                    direction = request.direction,
                    transactionType = request.transactionType,
                    occurredAt = request.occurredAt,
                    description = request.description,
                    note = request.note,
                    categoryId = if (request.categorySetByUser) request.categoryId else it.categoryId,
                )
            } else it
        }
    }
    override suspend fun setCategory(transactionId: Long, categoryId: Long?, byUser: Boolean, ruleId: Long?) {
        txsFlow.value = txsFlow.value.map {
            if (it.id == transactionId) it.copy(categoryId = categoryId, categorySource = if (byUser) CategorySource.USER else CategorySource.RULE) else it
        }
    }
    override suspend fun voidTransaction(id: Long) {
        txsFlow.value = txsFlow.value.map { if (it.id == id) it.copy(status = TransactionStatus.VOIDED) else it }
    }
    override suspend fun restoreTransaction(id: Long) {
        txsFlow.value = txsFlow.value.map { if (it.id == id) it.copy(status = TransactionStatus.CONFIRMED) else it }
    }
    override suspend fun deleteManual(id: Long) { txsFlow.value = txsFlow.value.filterNot { it.id == id } }
    override suspend fun linkRefund(refundTransactionId: Long, originalTransactionId: Long?) {
        txsFlow.value = txsFlow.value.map { if (it.id == refundTransactionId) it.copy(relatedTransactionId = originalTransactionId) else it }
    }
    override suspend fun findByProviderReference(sourceId: String, providerReference: String): Transaction? = null
    override suspend fun countUncategorizedForMerchant(merchantId: Long): Int = 0
    override suspend fun applyCategoryToUncategorizedMerchant(merchantId: Long, categoryId: Long, ruleId: Long): Int = 0
}

class FakeTransferRepository : TransferRepository {
    val pairsFlow = MutableStateFlow<List<TransferPair>>(emptyList())
    private var nextId = 1L
    override val pairs: Flow<List<TransferPair>> = pairsFlow
    override suspend fun getAllOnce(): List<TransferPair> = pairsFlow.value
    override suspend fun findConfirmedForTransaction(transactionId: Long): TransferPair? =
        pairsFlow.value.firstOrNull { it.status == TransferStatus.CONFIRMED && (it.outgoingTransactionId == transactionId || it.incomingTransactionId == transactionId) }
    override suspend fun link(request: LinkTransferRequest, feeMinor: Long): Long {
        val id = nextId++
        pairsFlow.value = pairsFlow.value + TransferPair(id, request.outgoingTransactionId, request.incomingTransactionId, TransferOrigin.MANUAL, null, TransferStatus.CONFIRMED, feeMinor, 1_000L)
        return id
    }
    override suspend fun updateFee(pairId: Long, feeMinor: Long) {}
    override suspend fun unlink(pairId: Long) { pairsFlow.value = pairsFlow.value.filterNot { it.id == pairId } }
}

class FakeCategoryRepository : CategoryRepository {
    val catsFlow = MutableStateFlow(
        listOf(
            Category(1L, "Uncategorized", "UNCATEGORIZED", null, null, true, false, 1L),
            Category(2L, "Fees", "FEES", Direction.OUTFLOW, null, true, false, 1L),
            Category(3L, "Cash withdrawal", "CASH_WITHDRAWAL", Direction.OUTFLOW, null, true, false, 1L),
            Category(4L, "Food & Dining", null, Direction.OUTFLOW, null, false, false, 1L),
        ),
    )
    private var nextId = 5L
    override val categories: Flow<List<Category>> = catsFlow
    override suspend fun getAllOnce(): List<Category> = catsFlow.value
    override suspend fun findBySystemKey(systemKey: String): Category? = catsFlow.value.firstOrNull { it.systemKey == systemKey }
    override suspend fun create(name: String, directionScope: Direction?, parentId: Long?): Long {
        val id = nextId++
        catsFlow.value = catsFlow.value + Category(id, name, null, directionScope, parentId, false, false, 1_000L)
        return id
    }
    override suspend fun rename(id: Long, newName: String) {
        catsFlow.value = catsFlow.value.map { if (it.id == id && !it.isSystem) it.copy(name = newName) else it }
    }
    override suspend fun setArchived(id: Long, archived: Boolean) {
        catsFlow.value = catsFlow.value.map { if (it.id == id && !it.isSystem) it.copy(isArchived = archived) else it }
    }
}

class FakeRuleRepository : RuleRepository {
    val rulesFlow = MutableStateFlow<List<TransactionRule>>(emptyList())
    private var nextId = 1L
    override val rules: Flow<List<TransactionRule>> = rulesFlow
    override suspend fun getEnabledOnce(): List<TransactionRule> = rulesFlow.value.filter { it.enabled }
    override suspend fun createRule(name: String, merchantKey: String?, descriptionFragment: String?, accountId: Long?, direction: Direction?, categoryId: Long): Long {
        val id = nextId++
        rulesFlow.value = rulesFlow.value + TransactionRule(id, name, merchantKey, descriptionFragment, accountId, direction, categoryId, true, 1_000L, 1_000L)
        return id
    }
    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        rulesFlow.value = rulesFlow.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }
    override suspend fun delete(id: Long) { rulesFlow.value = rulesFlow.value.filterNot { it.id == id } }
}

class FakeNeedsReviewRepository(initial: List<NeedsReviewItem> = emptyList()) : NeedsReviewRepository {
    val itemsFlow = MutableStateFlow(initial)
    val savedCalls = mutableListOf<Pair<Long, SaveReviewItemRequest>>()
    val ignoredIds = mutableListOf<Long>()
    val retriedIds = mutableListOf<Long>()

    override val items: Flow<List<NeedsReviewItem>> = itemsFlow
    override val pendingCount: Flow<Int> = itemsFlow.map { it.size }
    override suspend fun item(id: Long): NeedsReviewItem? = itemsFlow.value.firstOrNull { it.id == id }
    override suspend fun saveResolved(itemId: Long, resolved: SaveReviewItemRequest): Long {
        savedCalls += itemId to resolved
        itemsFlow.value = itemsFlow.value.filterNot { it.id == itemId }
        return 100L + itemId
    }
    override suspend fun ignore(itemId: Long) {
        ignoredIds += itemId
        itemsFlow.value = itemsFlow.value.filterNot { it.id == itemId }
    }
    override suspend fun retry(itemId: Long) { retriedIds += itemId }
}

class FakeSourceAndEventRepository : SourceAndEventRepository {
    val sourcesFlow = MutableStateFlow<List<TransactionSource>>(emptyList())
    override val sources: Flow<List<TransactionSource>> = sourcesFlow
    override suspend fun getSource(id: String): TransactionSource? = sourcesFlow.value.firstOrNull { it.id == id }
    override suspend fun upsertSource(packageName: String, displayName: String, isAllowlisted: Boolean, parserVersion: Int?) {
        sourcesFlow.value = sourcesFlow.value.filterNot { it.id == packageName } +
            TransactionSource(packageName, packageName, displayName, isAllowlisted, parserVersion, null, 1_000L, 1_000L)
    }
    override suspend fun setAllowlisted(packageName: String, allowlisted: Boolean) {
        sourcesFlow.value = sourcesFlow.value.map { if (it.packageName == packageName) it.copy(isAllowlisted = allowlisted) else it }
    }
    override suspend fun setSampleCollectionUntil(sourceId: String, untilEpochMillis: Long?) {
        sourcesFlow.value = sourcesFlow.value.map { if (it.id == sourceId) it.copy(sampleCollectionUntil = untilEpochMillis) else it }
    }
    override suspend fun countNoParserLast30Days(sourceId: String, sinceEpochMillis: Long): Int = 0
    override suspend fun getAllEventsOnce(): List<NotificationEventRecord> = emptyList()
    override suspend fun purgeExpiredPayloads(nowEpochMillis: Long): Int = 0
}

class FakeCaptureStatusRepository : CaptureStatusRepository {
    val summaryFlow = MutableStateFlow(CaptureHealthSummary(CaptureStatus.ACTIVE, true, true, null, 1_000L))
    val gapsFlow = MutableStateFlow<List<CaptureGap>>(emptyList())
    override val healthSummary: Flow<CaptureHealthSummary> = summaryFlow
    override val captureGaps: Flow<List<CaptureGap>> = gapsFlow
    override suspend fun refreshStatus() {}
    override suspend fun recordGap(startedAt: Long, endedAt: Long?, reason: CaptureGapReason) {}
    override suspend fun closeOpenGaps(reason: CaptureGapReason, endedAt: Long) {}
}

class FakeSettingsRepository(initial: AppSettings = AppSettings(onboardingCompleted = true)) : SettingsRepository {
    val settingsFlow = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = settingsFlow
    override suspend fun current(): AppSettings = settingsFlow.value
    override suspend fun setThemeMode(mode: ThemeMode) { settingsFlow.value = settingsFlow.value.copy(themeMode = mode) }
    override suspend fun setBaseCurrency(currency: String) { settingsFlow.value = settingsFlow.value.copy(baseCurrency = currency) }
    override suspend fun setVerificationWindowDays(days: Int) { settingsFlow.value = settingsFlow.value.copy(verificationWindowDays = days) }
    override suspend fun setHeartbeatStaleHours(hours: Int) { settingsFlow.value = settingsFlow.value.copy(heartbeatStaleHours = hours) }
    override suspend fun updateLastAliveAt(epochMillis: Long) { settingsFlow.value = settingsFlow.value.copy(lastAliveAt = epochMillis) }
    override suspend fun recordBackupCompleted(atEpochMillis: Long) {
        settingsFlow.value = settingsFlow.value.copy(lastBackupAt = atEpochMillis, txCountSinceLastBackup = 0)
    }
    override suspend fun dismissBackupReminder(atEpochMillis: Long) {
        settingsFlow.value = settingsFlow.value.copy(backupReminderDismissedAt = atEpochMillis)
    }
    override suspend fun setBackupReminderThresholds(days: Int, txCount: Int) {
        settingsFlow.value = settingsFlow.value.copy(backupReminderDays = days, backupReminderTxCount = txCount)
    }
    override suspend fun setOnboardingCompleted(completed: Boolean) {
        settingsFlow.value = settingsFlow.value.copy(onboardingCompleted = completed)
    }
}

class FakeBackupAndDiagnosticsPort : BackupAndDiagnosticsPort {
    var wiped = false
    var logs = mutableListOf("2026-11-21T00:00:00Z [INIT] OK")
    override suspend fun exportEncryptedBackup(
        passphrase: String,
        outputStream: OutputStream,
        verifyStreamOpener: (() -> InputStream?)?,
    ) = BackupVerificationResult(0, 0, 0, true, "ok")
    override suspend fun restoreEncryptedBackup(passphrase: String, inputStream: InputStream) =
        RestoreResult(0, 0, 0, 0)
    override suspend fun exportCsv(outputStream: OutputStream): Int = 0
    override suspend fun wipeAllDataAndReset() { wiped = true; logs.clear() }
    override fun readDiagnosticLogLines(limit: Int): List<String> = logs.takeLast(limit)
    override fun clearDiagnosticLogs() { logs.clear() }
    override fun providerCatalogEntries(): List<ProviderCatalogEntry> = emptyList()
    override suspend fun refreshAllowlistCache() {}
    override suspend fun countRetainedM3aSamples(): Int = 0
    override suspend fun exportAnonymizedM3aSamples(
        outputStream: OutputStream,
        redactTerms: List<String>,
    ) = SampleExportResult(0, 0)
    override suspend fun purgeRetainedM3aSamplesNow(): Int = 0
    override suspend fun reparseRetainedM3aSamples(): Int = 0
}

class FakeBudgetRepository : BudgetRepository {
    val budgetsFlow = MutableStateFlow<List<Budget>>(emptyList())
    override val budgets: Flow<List<Budget>> = budgetsFlow
    override suspend fun upsertBudget(categoryId: Long, accountId: Long?, limitMinor: Long, period: String): Long = 1L
    override suspend fun deleteBudget(id: Long) {}
}
