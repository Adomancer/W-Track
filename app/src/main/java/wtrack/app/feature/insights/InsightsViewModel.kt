package wtrack.app.feature.insights

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
import wtrack.app.domain.model.Budget
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.CurrencyRegistry
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.BudgetRepository
import wtrack.app.domain.repository.CategoryRepository
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.TransferRepository
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.AggregateCalculator
import wtrack.app.domain.usecase.PeriodAggregateSummary
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.UiText

data class BudgetProgressUi(
    val budget: Budget,
    val categoryName: String,
    val accountName: String?,
    val spentMinor: Long,
    val progressRatio: Float,
)

data class InsightsUiState(
    val monthLabel: String = "",
    val canAdvanceMonth: Boolean = false,
    val baseCurrency: String = CurrencyRegistry.DEFAULT_CURRENCY,
    val summary: PeriodAggregateSummary = PeriodAggregateSummary(
        baseCurrency = CurrencyRegistry.DEFAULT_CURRENCY,
        totalIncomeMinor = 0L,
        totalExpenseMinor = 0L,
        netCashFlowMinor = 0L,
        unreconciledAdjustmentNetMinor = 0L,
        excludedNonBaseCurrencyCount = 0,
        categoryBreakdowns = emptyList(),
        derivedFees = emptyList(),
    ),
    val budgets: List<BudgetProgressUi> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val actionMessage: UiText? = null,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    transferRepository: TransferRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    settingsRepository: SettingsRepository,
    private val aggregateCalculator: AggregateCalculator,
    private val clock: Clock,
) : ViewModel() {

    private val monthOffset = MutableStateFlow(0)
    private val actionMessage = MutableStateFlow<UiText?>(null)

    private val snapshotFlow = combine(
        transactionRepository.transactions,
        transferRepository.pairs,
        categoryRepository.categories,
        accountRepository.accounts,
        budgetRepository.budgets,
    ) { txs, pairs, cats, accounts, budgets ->
        InsightsSnapshot(txs, pairs, cats, accounts, budgets)
    }

    val uiState: StateFlow<InsightsUiState> = combine(
        snapshotFlow,
        settingsRepository.settings,
        monthOffset,
        actionMessage,
    ) { snap, settings, offset, message ->
        val window = UiDateHelper.monthWindow(clock.nowEpochMillis(), offset)
        val summary = aggregateCalculator.summarizePeriod(
            transactions = snap.transactions,
            transferPairs = snap.pairs,
            categories = snap.categories,
            baseCurrency = settings.baseCurrency,
            periodStartEpochMillis = window.startMillis,
            periodEndEpochMillis = window.endMillis,
            previousMonthRange = window.previousMonthRange,
        )

        val categoryMap = snap.categories.associateBy { it.id }
        val accountMap = snap.accounts.associateBy { it.id }

        val budgetItems = snap.budgets.filter { it.isActive }.map { budget ->
            val scopedSummary = if (budget.accountId != null) {
                aggregateCalculator.summarizePeriod(
                    transactions = snap.transactions,
                    transferPairs = snap.pairs,
                    categories = snap.categories,
                    baseCurrency = settings.baseCurrency,
                    periodStartEpochMillis = window.startMillis,
                    periodEndEpochMillis = window.endMillis,
                    accountIdFilter = budget.accountId,
                )
            } else {
                summary
            }
            val spent = scopedSummary.categoryBreakdowns
                .firstOrNull { it.categoryId == budget.categoryId }
                ?.netSpendMinor ?: 0L
            val ratio = if (budget.limitMinor > 0L) (spent.toFloat() / budget.limitMinor.toFloat()) else 0f
            BudgetProgressUi(
                budget = budget,
                categoryName = categoryMap[budget.categoryId]?.name.orEmpty(),
                accountName = budget.accountId?.let { accountMap[it]?.name },
                spentMinor = spent,
                progressRatio = ratio.coerceAtLeast(0f),
            )
        }

        InsightsUiState(
            monthLabel = window.label,
            canAdvanceMonth = offset < 0,
            baseCurrency = settings.baseCurrency,
            summary = summary,
            budgets = budgetItems,
            categories = snap.categories.filter { !it.isArchived },
            accounts = snap.accounts.filter { it.isActive },
            actionMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = InsightsUiState(),
    )

    fun previousMonth() {
        monthOffset.value -= 1
    }

    fun nextMonth() {
        if (monthOffset.value < 0) monthOffset.value += 1
    }

    fun upsertBudget(categoryId: Long, accountId: Long?, limitMajorInput: String) {
        viewModelScope.launch {
            val baseCurrency = uiState.value.baseCurrency
            val minor = MoneyFormatter.parseMajorInput(limitMajorInput, baseCurrency)
                ?.takeIf { it > 0L }
                ?: run {
                    actionMessage.value = UiText.Resource(R.string.msg_invalid_budget_limit, baseCurrency)
                    return@launch
                }
            runCatching {
                budgetRepository.upsertBudget(categoryId, accountId, minor, "MONTHLY")
            }.onSuccess {
                actionMessage.value = UiText.Resource(R.string.msg_budget_saved)
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_budget_save_failed)
            }
        }
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch {
            budgetRepository.deleteBudget(id)
            actionMessage.value = UiText.Resource(R.string.msg_budget_deleted)
        }
    }

    fun clearMessage() {
        actionMessage.value = null
    }

    private data class InsightsSnapshot(
        val transactions: List<Transaction>,
        val pairs: List<TransferPair>,
        val categories: List<Category>,
        val accounts: List<Account>,
        val budgets: List<Budget>,
    )
}
