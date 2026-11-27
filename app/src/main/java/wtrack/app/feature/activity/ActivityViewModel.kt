package wtrack.app.feature.activity

import androidx.annotation.StringRes
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
import wtrack.app.R
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.CurrencyRegistry
import wtrack.app.domain.model.DerivedFeeRow
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.Tag
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.model.TransferStatus
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.CategoryRepository
import wtrack.app.domain.repository.CreateManualTransactionRequest
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.repository.TagRepository
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
import wtrack.app.domain.usecase.TransferSuggestion
import wtrack.app.domain.usecase.TransferSuggestionEngine
import wtrack.app.domain.usecase.UnlinkTransferUseCase
import wtrack.app.domain.usecase.UpdateTransactionUseCase
import wtrack.app.domain.usecase.VoidRestoreTransactionUseCase
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.UiText
import wtrack.integration.Direction

enum class ActivityDateFilter(@param:StringRes val labelRes: Int) {
    ALL(R.string.date_filter_all),
    THIS_MONTH(R.string.date_filter_this_month),
    LAST_30_DAYS(R.string.date_filter_last_30_days),
    PREVIOUS_MONTH(R.string.date_filter_previous_month),
}

data class ActivityFilterState(
    val query: String = "",
    val dateFilter: ActivityDateFilter = ActivityDateFilter.ALL,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val tagId: Long? = null,
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
    val tags: List<Tag> = emptyList(),
)

data class ActivityTransferSuggestionItem(
    val suggestion: TransferSuggestion,
    val outgoingAccountName: String,
    val incomingAccountName: String,
)

data class ActivityUiState(
    val filter: ActivityFilterState = ActivityFilterState(),
    val items: List<ActivityTransactionItem> = emptyList(),
    val allItems: List<ActivityTransactionItem> = emptyList(),
    val transferSuggestions: List<ActivityTransferSuggestionItem> = emptyList(),
    val derivedFees: List<DerivedFeeRow> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val baseCurrency: String = CurrencyRegistry.DEFAULT_CURRENCY,
    val actionMessage: UiText? = null,
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val transferRepository: TransferRepository,
    private val tagRepository: TagRepository,
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
    private val actionMessage = MutableStateFlow<UiText?>(null)
    private val transferSuggestionEngine = TransferSuggestionEngine()

    private val dataSnapshot = combine(
        transactionRepository.transactions,
        accountRepository.accounts,
        categoryRepository.categories,
        transferRepository.pairs,
        settingsRepository.settings,
    ) { transactions, accounts, categories, pairs, settings ->
        ActivityDataSnapshot(transactions, accounts, categories, pairs, settings.baseCurrency)
    }

    private val tagSnapshot = combine(
        tagRepository.tags,
        tagRepository.observeAllTransactionTags(),
    ) { tags, txTags ->
        tags to txTags
    }

    val uiState: StateFlow<ActivityUiState> = combine(
        dataSnapshot,
        tagSnapshot,
        filterState,
        actionMessage,
    ) { snapshot, (allTags, txTagsMap), filter, message ->
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
                accountName = accountMap[tx.accountId]?.name.orEmpty(),
                categoryName = categoryMap[tx.categoryId]?.name.orEmpty(),
                transferPair = pair,
                counterpartAccountName = counterpartAccountId?.let { accountMap[it]?.name },
                tags = txTagsMap[tx.id].orEmpty(),
            )
        }

        val filtered = allMapped.filter { item ->
            val tx = item.transaction
            if (!filter.showVoided && tx.status == TransactionStatus.VOIDED) return@filter false
            if (tx.occurredAt < dateStart || tx.occurredAt >= dateEnd) return@filter false
            if (filter.accountId != null && tx.accountId != filter.accountId) return@filter false
            if (filter.categoryId != null && tx.categoryId != filter.categoryId) return@filter false
            if (filter.tagId != null && item.tags.none { it.id == filter.tagId }) return@filter false
            if (filter.type != null && tx.transactionType != filter.type) return@filter false
            if (filter.direction != null && tx.direction != filter.direction) return@filter false
            if (filter.uncategorizedOnly && !(tx.categoryId == null || tx.categoryId == uncatId)) return@filter false
            if (filter.query.isNotBlank()) {
                val q = filter.query.trim().lowercase()
                val accName = item.accountName.lowercase()
                val catName = item.categoryName.lowercase()
                val tagMatch = item.tags.any { it.name.lowercase().contains(q) }
                val match = (tx.merchantName?.lowercase()?.contains(q) == true) ||
                    (tx.description?.lowercase()?.contains(q) == true) ||
                    (tx.note?.lowercase()?.contains(q) == true) ||
                    accName.contains(q) ||
                    catName.contains(q) ||
                    tagMatch
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

        val suggestions = transferSuggestionEngine
            .findSuggestions(snapshot.transactions, snapshot.pairs)
            .map { suggestion ->
                ActivityTransferSuggestionItem(
                    suggestion = suggestion,
                    outgoingAccountName = accountMap[suggestion.outgoing.accountId]?.name.orEmpty(),
                    incomingAccountName = accountMap[suggestion.incoming.accountId]?.name.orEmpty(),
                )
            }

        ActivityUiState(
            filter = filter,
            items = filtered,
            allItems = allMapped,
            transferSuggestions = suggestions,
            derivedFees = summary.derivedFees,
            accounts = snapshot.accounts.filter { it.isActive },
            categories = snapshot.categories.filter { !it.isArchived },
            tags = allTags,
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
                actionMessage.value = UiText.Resource(R.string.msg_select_account)
                return@launch
            }
            val minor = MoneyFormatter.parseMajorInput(amountMajorInput, account.currency)
                ?.takeIf { it > 0L }
                ?: run {
                    actionMessage.value = UiText.Resource(R.string.msg_invalid_amount_for_currency, account.currency)
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
                actionMessage.value = UiText.Resource(R.string.msg_tx_added)
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_tx_create_failed)
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
                    actionMessage.value = UiText.Resource(R.string.msg_invalid_amount_format, tx.currency)
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
                actionMessage.value = UiText.Resource(R.string.msg_tx_updated)
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_update_failed)
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
                        UiText.Resource(R.string.msg_category_updated_and_rule, result.backfilledCount)
                    } else {
                        UiText.Resource(R.string.msg_category_updated)
                    }
                } else {
                    UiText.Resource(R.string.msg_category_updated)
                }
            }.onSuccess { msg ->
                actionMessage.value = msg
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_category_update_failed)
            }
        }
    }

    fun voidTransaction(id: Long) {
        viewModelScope.launch {
            runCatching { voidRestoreTransactionUseCase.void(id) }
                .onSuccess { actionMessage.value = UiText.Resource(R.string.msg_tx_voided) }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_tx_void_failed) }
        }
    }

    fun restoreTransaction(id: Long) {
        viewModelScope.launch {
            runCatching { voidRestoreTransactionUseCase.restore(id) }
                .onSuccess { actionMessage.value = UiText.Resource(R.string.msg_tx_restored) }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_tx_restore_failed) }
        }
    }

    fun deleteManual(id: Long) {
        viewModelScope.launch {
            runCatching { deleteManualTransactionUseCase(id) }
                .onSuccess { actionMessage.value = UiText.Resource(R.string.msg_manual_tx_deleted) }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_delete_captured_blocked) }
        }
    }

    fun linkTransfer(outgoingId: Long, incomingId: Long) {
        viewModelScope.launch {
            runCatching { linkTransferUseCase(outgoingId, incomingId) }
                .onSuccess { actionMessage.value = UiText.Resource(R.string.msg_transfer_linked) }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_transfer_link_failed) }
        }
    }

    fun unlinkTransfer(pairId: Long) {
        viewModelScope.launch {
            runCatching { unlinkTransferUseCase(pairId) }
                .onSuccess { actionMessage.value = UiText.Resource(R.string.msg_transfer_unlinked) }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_transfer_unlink_failed) }
        }
    }

    fun confirmTransferSuggestion(suggestion: TransferSuggestion) {
        viewModelScope.launch {
            runCatching { linkTransferUseCase(suggestion.outgoing.id, suggestion.incoming.id) }
                .onSuccess { actionMessage.value = UiText.Resource(R.string.msg_suggested_transfer_linked) }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_suggested_transfer_failed) }
        }
    }

    fun dismissTransferSuggestion(outgoingId: Long, incomingId: Long) {
        viewModelScope.launch {
            runCatching { transferRepository.dismissSuggestedPair(outgoingId, incomingId) }
                .onSuccess { actionMessage.value = UiText.Resource(R.string.msg_suggestion_dismissed) }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_suggestion_dismiss_failed) }
        }
    }

    fun linkRefund(refundTxId: Long, originalTxId: Long?) {
        viewModelScope.launch {
            runCatching { linkRefundUseCase(refundTxId, originalTxId) }
                .onSuccess {
                    actionMessage.value = if (originalTxId == null) {
                        UiText.Resource(R.string.msg_refund_unlinked)
                    } else {
                        UiText.Resource(R.string.msg_refund_linked)
                    }
                }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_refund_link_failed) }
        }
    }

    fun addTagToTransaction(txId: Long, tagName: String) {
        val cleaned = tagName.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            runCatching { tagRepository.addTagToTransaction(txId, cleaned) }
                .onSuccess { actionMessage.value = UiText.Resource(R.string.msg_tag_added, cleaned) }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_tag_add_failed) }
        }
    }

    fun removeTagFromTransaction(txId: Long, tagId: Long) {
        viewModelScope.launch {
            runCatching { tagRepository.removeTagFromTransaction(txId, tagId) }
                .onSuccess { actionMessage.value = UiText.Resource(R.string.msg_tag_removed) }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_tag_remove_failed) }
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
