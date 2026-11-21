package wtrack.app.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.DerivedFeeRow
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.model.TransferStatus
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.CategoryRepository
import wtrack.app.domain.repository.CreateManualTransactionRequest
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.TransferRepository
import wtrack.app.domain.repository.UpdateTransactionRequest
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.AggregateCalculator
import wtrack.app.domain.usecase.CreateManualTransactionUseCase
import wtrack.app.domain.usecase.CreateRuleWithBackfillUseCase
import wtrack.app.domain.usecase.DeleteManualTransactionUseCase
import wtrack.app.domain.usecase.LinkRefundUseCase
import wtrack.app.domain.usecase.LinkTransferUseCase
import wtrack.app.domain.usecase.UnlinkTransferUseCase
import wtrack.app.domain.usecase.UpdateTransactionUseCase
import wtrack.app.domain.usecase.VoidRestoreTransactionUseCase
import wtrack.app.feature.common.UiDateHelper
import wtrack.integration.Direction

enum class ActivityDateFilter(val label: String) {
    ALL("All dates"),
    THIS_MONTH("This month"),
    LAST_30_DAYS("Last 30d"),
    PREVIOUS_MONTH("Prev month"),
}

data class ActivityFilterState(
    val query: String = "",
    val dateFilter: ActivityDateFilter = ActivityDateFilter.ALL,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val type: DomainTransactionType? = null,
    val direction: Direction? = null,
    val showVoided: Boolean = false,
    val uncategorizedOnly: Boolean = false,
)

data class ActivityTransactionItem(
    val transaction: Transaction,
    val accountName: String,
    val categoryName: String,
    val transferPair: TransferPair?,
    val counterpartAccountName: String?,
)

data class ActivityUiState(
    val filter: ActivityFilterState = ActivityFilterState(),
    val items: List<ActivityTransactionItem> = emptyList(),
    val allItems: List<ActivityTransactionItem> = emptyList(),
    val derivedFees: List<DerivedFeeRow> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val baseCurrency: String = "IDR",
    val actionMessage: String? = null,
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transferRepository: TransferRepository,
    settingsRepository: SettingsRepository,
    private val aggregateCalculator: AggregateCalculator,
    private val createManualTransactionUseCase: CreateManualTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
    private val voidRestoreTransactionUseCase: VoidRestoreTransactionUseCase,
    private val deleteManualTransactionUseCase: DeleteManualTransactionUseCase,
    private val linkTransferUseCase: LinkTransferUseCase,
    private val unlinkTransferUseCase: UnlinkTransferUseCase,
    private val linkRefundUseCase: LinkRefundUseCase,
    private val createRuleWithBackfillUseCase: CreateRuleWithBackfillUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val filterState = MutableStateFlow(ActivityFilterState())
    private val actionMessage = MutableStateFlow<String?>(null)

    private val dataSnapshot = combine(
        transactionRepository.transactions,
        accountRepository.accounts,
        categoryRepository.categories,
        transferRepository.pairs,
        settingsRepository.settings,
    ) { transactions, accounts, categories, pairs, settings ->
        ActivityDataSnapshot(transactions, accounts, categories, pairs, settings.baseCurrency)
    }

    val uiState: StateFlow<ActivityUiState> = combine(
        dataSnapshot,
        filterState,
        actionMessage,
    ) { snapshot, filter, message ->
        val accountMap = snapshot.accounts.associateBy { it.id }
        val categoryMap = snapshot.categories.associateBy { it.id }
        val txMap = snapshot.transactions.associateBy { it.id }
        val confirmedPairs = snapshot.pairs.filter { it.status == TransferStatus.CONFIRMED }
        val pairByTxId = buildMap {
            confirmedPairs.forEach { pair ->
                put(pair.outgoingTransactionId, pair)
                put(pair.incomingTransactionId, pair)
            }
        }
        val uncatId = snapshot.categories.firstOrNull { it.systemKey == "UNCATEGORIZED" }?.id
        val now = clock.nowEpochMillis()
        val thisMonth = UiDateHelper.monthWindow(now, 0)
        val (dateStart, dateEnd) = when (filter.dateFilter) {
            ActivityDateFilter.ALL -> Long.MIN_VALUE to Long.MAX_VALUE
            ActivityDateFilter.THIS_MONTH -> thisMonth.startMillis to thisMonth.endMillis
            ActivityDateFilter.LAST_30_DAYS -> (now - 30L * 86_400_000L) to Long.MAX_VALUE
            ActivityDateFilter.PREVIOUS_MONTH -> thisMonth.previousMonthRange
        }

        val allMapped = snapshot.transactions.map { tx ->
            val pair = pairByTxId[tx.id]
            val counterpartId = pair?.let {
                if (it.outgoingTransactionId == tx.id) it.incomingTransactionId else it.outgoingTransactionId
            }
            val counterpartAccountId = counterpartId?.let { txMap[it]?.accountId }
            ActivityTransactionItem(
                transaction = tx,
                accountName = accountMap[tx.accountId]?.name ?: "Account #${tx.accountId}",
                categoryName = categoryMap[tx.categoryId]?.name ?: "Uncategorized",
                transferPair = pair,
                counterpartAccountName = counterpartAccountId?.let { accountMap[it]?.name },
            )
        }

        val filtered = allMapped.filter { item ->
            val tx = item.transaction
            if (!filter.showVoided && tx.status == TransactionStatus.VOIDED) return@filter false
            if (tx.occurredAt < dateStart || tx.occurredAt >= dateEnd) return@filter false
            if (filter.accountId != null && tx.accountId != filter.accountId) return@filter false
            if (filter.categoryId != null && tx.categoryId != filter.categoryId) return@filter false
            if (filter.type != null && tx.transactionType != filter.type) return@filter false
            if (filter.direction != null && tx.direction != filter.direction) return@filter false
            if (filter.uncategorizedOnly && !(tx.categoryId == null || tx.categoryId == uncatId)) return@filter false
            if (filter.query.isNotBlank()) {
                val q = filter.query.trim().lowercase()
                val accName = item.accountName.lowercase()
                val catName = item.categoryName.lowercase()
                val match = (tx.merchantName?.lowercase()?.contains(q) == true) ||
                    (tx.description?.lowercase()?.contains(q) == true) ||
                    (tx.note?.lowercase()?.contains(q) == true) ||
                    accName.contains(q) ||
                    catName.contains(q)
                if (!match) return@filter false
            }
            true
        }

        val summary = aggregateCalculator.summarizePeriod(
            transactions = snapshot.transactions,
            transferPairs = snapshot.pairs,
            categories = snapshot.categories,
            baseCurrency = snapshot.baseCurrency,
            periodStartEpochMillis = dateStart,
            periodEndEpochMillis = dateEnd,
            accountIdFilter = filter.accountId,
        )

        ActivityUiState(
            filter = filter,
            items = filtered,
            allItems = allMapped,
            derivedFees = summary.derivedFees,
            accounts = snapshot.accounts.filter { it.isActive },
            categories = snapshot.categories.filter { !it.isArchived },
            baseCurrency = snapshot.baseCurrency,
            actionMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ActivityUiState(),
    )

    fun updateFilter(transform: (ActivityFilterState) -> ActivityFilterState) {
        filterState.value = transform(filterState.value)
    }

    fun createManualTransaction(
        accountId: Long,
        amountMajorInput: String,
        direction: Direction,
        type: DomainTransactionType,
        categoryId: Long?,
        description: String?,
        note: String?,
        occurredAtMillis: Long = clock.nowEpochMillis(),
    ) {
        viewModelScope.launch {
            val account = uiState.value.accounts.firstOrNull { it.id == accountId } ?: run {
                actionMessage.value = "Select an account."
                return@launch
            }
            val minor = MoneyFormatter.parseMajorInput(amountMajorInput, account.currency)
                ?.takeIf { it > 0L }
                ?: run {
                    actionMessage.value = "Invalid amount for ${account.currency}."
                    return@launch
                }
            runCatching {
                createManualTransactionUseCase(
                    CreateManualTransactionRequest(
                        accountId = accountId,
                        amountMinor = minor,
                        currency = account.currency,
                        direction = direction,
                        transactionType = type,
                        occurredAt = occurredAtMillis,
                        categoryId = categoryId,
                        description = description,
                        note = note,
                    ),
                )
            }.onSuccess {
                actionMessage.value = "Transaction added."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Could not create transaction."
            }
        }
    }

    fun updateTransaction(
        tx: Transaction,
        accountId: Long,
        amountMajorInput: String,
        direction: Direction,
        type: DomainTransactionType,
        occurredAtMillis: Long,
        categoryId: Long?,
        categoryChangedByUser: Boolean,
        description: String?,
        note: String?,
    ) {
        viewModelScope.launch {
            val minor = MoneyFormatter.parseMajorInput(amountMajorInput, tx.currency)
                ?.takeIf { it > 0L }
                ?: run {
                    actionMessage.value = "Invalid amount format for ${tx.currency}."
                    return@launch
                }
            runCatching {
                updateTransactionUseCase(
                    UpdateTransactionRequest(
                        id = tx.id,
                        accountId = accountId,
                        amountMinor = minor,
                        currency = tx.currency,
                        direction = direction,
                        transactionType = type,
                        occurredAt = occurredAtMillis,
                        categoryId = categoryId,
                        description = description,
                        note = note,
                        categorySetByUser = categoryChangedByUser,
                    ),
                )
            }.onSuccess {
                actionMessage.value = "Transaction updated."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Update failed."
            }
        }
    }

    fun setCategoryAndOptionalRule(
        tx: Transaction,
        categoryId: Long?,
        createRuleForMerchant: Boolean,
        backfillUncategorized: Boolean,
    ) {
        viewModelScope.launch {
            runCatching {
                transactionRepository.setCategory(tx.id, categoryId, byUser = true)
                if (createRuleForMerchant && categoryId != null) {
                    val fragment = tx.description?.trim()?.takeIf { it.isNotEmpty() }
                    val merchantKey = tx.merchantName?.trim()?.takeIf { it.isNotEmpty() }
                    if (merchantKey != null || fragment != null) {
                        val result = createRuleWithBackfillUseCase(
                            name = merchantKey ?: fragment ?: "Auto rule",
                            merchantId = tx.merchantId,
                            merchantKey = merchantKey,
                            descriptionFragment = if (merchantKey == null) fragment else null,
                            accountId = null,
                            direction = tx.direction,
                            categoryId = categoryId,
                            applyBackfill = backfillUncategorized,
                        )
                        "Category updated and rule saved (${result.backfilledCount} past updated)."
                    } else {
                        "Category updated."
                    }
                } else {
                    "Category updated."
                }
            }.onSuccess { msg ->
                actionMessage.value = msg
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Failed to update category."
            }
        }
    }

    fun voidTransaction(id: Long) {
        viewModelScope.launch {
            runCatching { voidRestoreTransactionUseCase.void(id) }
                .onSuccess { actionMessage.value = "Transaction voided." }
                .onFailure { actionMessage.value = it.message ?: "Failed to void transaction." }
        }
    }

    fun restoreTransaction(id: Long) {
        viewModelScope.launch {
            runCatching { voidRestoreTransactionUseCase.restore(id) }
                .onSuccess { actionMessage.value = "Transaction restored." }
                .onFailure { actionMessage.value = it.message ?: "Failed to restore transaction." }
        }
    }

    fun deleteManual(id: Long) {
        viewModelScope.launch {
            runCatching { deleteManualTransactionUseCase(id) }
                .onSuccess { actionMessage.value = "Manual transaction deleted." }
                .onFailure { actionMessage.value = it.message ?: "Cannot delete captured transaction; void instead." }
        }
    }

    fun linkTransfer(outgoingId: Long, incomingId: Long) {
        viewModelScope.launch {
            runCatching { linkTransferUseCase(outgoingId, incomingId) }
                .onSuccess { actionMessage.value = "Transfer pair linked." }
                .onFailure { actionMessage.value = it.message ?: "Failed to link transfer pair." }
        }
    }

    fun unlinkTransfer(pairId: Long) {
        viewModelScope.launch {
            runCatching { unlinkTransferUseCase(pairId) }
                .onSuccess { actionMessage.value = "Transfer pair unlinked." }
                .onFailure { actionMessage.value = it.message ?: "Failed to unlink transfer." }
        }
    }

    fun linkRefund(refundTxId: Long, originalTxId: Long?) {
        viewModelScope.launch {
            runCatching { linkRefundUseCase(refundTxId, originalTxId) }
                .onSuccess {
                    actionMessage.value = if (originalTxId == null) "Refund unlinked." else "Refund linked to original."
                }
                .onFailure { actionMessage.value = it.message ?: "Failed to link refund." }
        }
    }

    fun clearMessage() {
        actionMessage.value = null
    }

    private data class ActivityDataSnapshot(
        val transactions: List<Transaction>,
        val accounts: List<Account>,
        val categories: List<Category>,
        val pairs: List<TransferPair>,
        val baseCurrency: String,
    )
}
