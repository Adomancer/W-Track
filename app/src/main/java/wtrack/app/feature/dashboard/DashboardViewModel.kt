package wtrack.app.feature.dashboard

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
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.CaptureHealthSummary
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.BackupReminderEvaluator
import wtrack.app.domain.repository.CaptureStatusRepository
import wtrack.app.domain.repository.CategoryRepository
import wtrack.app.domain.repository.NeedsReviewRepository
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.TransferRepository
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.AggregateCalculator
import wtrack.app.domain.usecase.PeriodAggregateSummary
import wtrack.app.feature.common.UiDateHelper

data class DashboardUiState(
    val isLoading: Boolean = true,
    val monthLabel: String = "",
    val canAdvanceMonth: Boolean = false,
    val baseCurrency: String = "IDR",
    val summary: PeriodAggregateSummary = PeriodAggregateSummary(
        baseCurrency = "IDR",
        totalIncomeMinor = 0L,
        totalExpenseMinor = 0L,
        netCashFlowMinor = 0L,
        unreconciledAdjustmentNetMinor = 0L,
        excludedNonBaseCurrencyCount = 0,
        categoryBreakdowns = emptyList(),
        derivedFees = emptyList(),
    ),
    val activeAccounts: List<Account> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val accountNames: Map<Long, String> = emptyMap(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val pendingReviewCount: Int = 0,
    val captureSummary: CaptureHealthSummary = CaptureHealthSummary(
        appStatus = CaptureStatus.ATTENTION,
        isPermissionGranted = false,
        isListenerConnected = false,
        openGap = null,
        lastAliveAt = 0L,
    ),
    val showBackupReminder: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    transferRepository: TransferRepository,
    categoryRepository: CategoryRepository,
    needsReviewRepository: NeedsReviewRepository,
    captureStatusRepository: CaptureStatusRepository,
    private val settingsRepository: SettingsRepository,
    private val aggregateCalculator: AggregateCalculator,
    private val clock: Clock,
) : ViewModel() {

    private val monthOffset = MutableStateFlow(0)

    private val ledgerSnapshot = combine(
        accountRepository.accounts,
        transactionRepository.transactions,
        transferRepository.pairs,
        categoryRepository.categories,
    ) { accounts, transactions, pairs, categories ->
        LedgerSnapshot(accounts, transactions, pairs, categories)
    }

    private val statusSnapshot = combine(
        needsReviewRepository.pendingCount,
        captureStatusRepository.healthSummary,
        settingsRepository.settings,
    ) { pending, capture, settings ->
        StatusSnapshot(pending, capture, settings)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        ledgerSnapshot,
        statusSnapshot,
        monthOffset,
    ) { ledger, status, offset ->
        val now = clock.nowEpochMillis()
        val window = UiDateHelper.monthWindow(now, offset)
        val summary = aggregateCalculator.summarizePeriod(
            transactions = ledger.transactions,
            transferPairs = ledger.pairs,
            categories = ledger.categories,
            baseCurrency = status.settings.baseCurrency,
            periodStartEpochMillis = window.startMillis,
            periodEndEpochMillis = window.endMillis,
            previousMonthRange = window.previousMonthRange,
        )
        val recent = ledger.transactions
            .asSequence()
            .filter { it.status == TransactionStatus.CONFIRMED }
            .sortedByDescending { it.occurredAt }
            .take(8)
            .toList()

        DashboardUiState(
            isLoading = false,
            monthLabel = window.label,
            canAdvanceMonth = offset < 0,
            baseCurrency = status.settings.baseCurrency,
            summary = summary,
            activeAccounts = ledger.accounts.filter { it.isActive },
            recentTransactions = recent,
            accountNames = ledger.accounts.associate { it.id to it.name },
            categoryNames = ledger.categories.associate { it.id to it.name },
            pendingReviewCount = status.pendingReviewCount,
            captureSummary = status.captureSummary,
            showBackupReminder = BackupReminderEvaluator.shouldShowReminder(status.settings, now),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardUiState(),
    )

    fun previousMonth() {
        monthOffset.value -= 1
    }

    fun nextMonth() {
        if (monthOffset.value < 0) {
            monthOffset.value += 1
        }
    }

    fun dismissBackupReminder() {
        viewModelScope.launch {
            settingsRepository.dismissBackupReminder(clock.nowEpochMillis())
        }
    }

    private data class LedgerSnapshot(
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val pairs: List<TransferPair>,
        val categories: List<Category>,
    )

    private data class StatusSnapshot(
        val pendingReviewCount: Int,
        val captureSummary: CaptureHealthSummary,
        val settings: AppSettings,
    )
}
