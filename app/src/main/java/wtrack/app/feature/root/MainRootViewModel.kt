package wtrack.app.feature.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wtrack.app.R
import wtrack.app.core.coroutines.DispatcherProvider
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.CaptureHealthSummary
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.CurrencyRegistry
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.LockedReason
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.ThemeMode
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.BackupAndDiagnosticsPort
import wtrack.app.domain.repository.CaptureSelfCheckResult
import wtrack.app.domain.repository.CaptureStatusRepository
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.DatabaseHealthRepository
import wtrack.app.domain.repository.NeedsReviewRepository
import wtrack.app.domain.repository.ProviderCatalogEntry
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.AppLockStateMachine
import wtrack.app.feature.common.UiText

sealed interface RootUiState {
    data object Loading : RootUiState

    data class Locked(
        val reason: LockedReason,
        val detail: String,
        val isBusy: Boolean = false,
        val actionMessage: UiText? = null,
    ) : RootUiState

    data class Onboarding(
        val settings: AppSettings,
        val captureSummary: CaptureHealthSummary,
        val hasAccounts: Boolean,
        val supportedProviders: List<ProviderCatalogEntry> = emptyList(),
        val isBusy: Boolean = false,
        val actionMessage: UiText? = null,
        val selfCheckResult: CaptureSelfCheckResult? = null,
        val isAppLocked: Boolean = false,
    ) : RootUiState

    data class Ready(
        val themeMode: ThemeMode,
        val baseCurrency: String,
        val pendingReviewCount: Int,
        val captureSummary: CaptureHealthSummary,
        val showPostRestorePermissionPrompt: Boolean = false,
        val actionMessage: UiText? = null,
        val isAppLocked: Boolean = false,
    ) : RootUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainRootViewModel @Inject constructor(
    databaseHealthRepository: DatabaseHealthRepository,
    private val settingsRepository: SettingsRepository,
    private val captureStatusRepository: CaptureStatusRepository,
    needsReviewRepository: NeedsReviewRepository,
    private val accountRepository: AccountRepository,
    private val backupPort: BackupAndDiagnosticsPort,
    private val clock: Clock,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val isBusy = MutableStateFlow(false)
    private val actionMessage = MutableStateFlow<UiText?>(null)
    private val postRestorePrompt = MutableStateFlow(false)
    private val selfCheckFlow = MutableStateFlow<CaptureSelfCheckResult?>(null)
    private val appLockStateMachine = AppLockStateMachine()
    private val isAppLockedFlow = MutableStateFlow(false)
    private var latestObservedSettings = AppSettings()

    private val operationalSignals = combine(
        settingsRepository.settings,
        captureStatusRepository.healthSummary,
        needsReviewRepository.pendingCount,
        accountRepository.accounts,
    ) { settings, capture, pendingCount, accounts ->
        OperationalSnapshot(settings, capture, pendingCount, accounts.isNotEmpty())
    }.onEach { ops ->
        latestObservedSettings = ops.settings
        isAppLockedFlow.value = appLockStateMachine.onSettingsObserved(ops.settings)
    }

    private val readyAuxFlow = combine(
        isBusy,
        actionMessage,
        postRestorePrompt,
        selfCheckFlow,
        isAppLockedFlow,
    ) { busy, message, restorePrompt, selfCheck, isAppLocked ->
        ReadyAuxState(busy, message, restorePrompt, selfCheck, isAppLocked)
    }

    val uiState: StateFlow<RootUiState> = databaseHealthRepository.state
        .flatMapLatest { health ->
            when (health) {
                is DatabaseHealthState.Locked -> combine(isBusy, actionMessage) { busy, message ->
                    RootUiState.Locked(
                        reason = health.reason,
                        detail = health.detail,
                        isBusy = busy,
                        actionMessage = message,
                    )
                }

                is DatabaseHealthState.Ready -> combine(
                    operationalSignals,
                    readyAuxFlow,
                ) { ops, aux ->
                    if (!ops.settings.onboardingCompleted) {
                        RootUiState.Onboarding(
                            settings = ops.settings,
                            captureSummary = ops.captureSummary,
                            hasAccounts = ops.hasAccounts,
                            supportedProviders = backupPort.providerCatalogEntries(),
                            isBusy = aux.isBusy,
                            actionMessage = aux.actionMessage,
                            selfCheckResult = aux.selfCheckResult,
                            isAppLocked = aux.isAppLocked,
                        )
                    } else {
                        RootUiState.Ready(
                            themeMode = ops.settings.themeMode,
                            baseCurrency = ops.settings.baseCurrency,
                            pendingReviewCount = ops.pendingReviewCount,
                            captureSummary = ops.captureSummary,
                            showPostRestorePermissionPrompt = aux.showPostRestorePrompt && !ops.captureSummary.isPermissionGranted,
                            actionMessage = aux.actionMessage,
                            isAppLocked = aux.isAppLocked,
                        )
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = RootUiState.Loading,
        )

    fun refreshCaptureStatus() {
        viewModelScope.launch {
            runCatching { captureStatusRepository.refreshStatus() }
        }
    }

    fun onAppBackgrounded() {
        isAppLockedFlow.value = appLockStateMachine.onAppBackgrounded(
            nowMillis = clock.nowEpochMillis(),
            settings = latestObservedSettings,
        )
    }

    fun onAppForegrounded() {
        isAppLockedFlow.value = appLockStateMachine.onAppForegrounded(
            nowMillis = clock.nowEpochMillis(),
            settings = latestObservedSettings,
        )
    }

    fun unlockApp() {
        isAppLockedFlow.value = appLockStateMachine.onUnlocked()
    }

    fun runCaptureSelfCheck() {
        viewModelScope.launch {
            runCatching {
                captureStatusRepository.refreshStatus()
                backupPort.runCaptureSelfCheck()
            }.onSuccess { res ->
                selfCheckFlow.value = res
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_capture_self_check_failed)
            }
        }
    }

    fun selectBaseCurrency(currency: String) {
        viewModelScope.launch {
            settingsRepository.setBaseCurrency(currency)
        }
    }

    fun createInitialAccount(
        name: String,
        provider: String,
        accountType: AccountType,
        currency: String,
        openingBalanceMajorInput: String,
    ) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) {
                actionMessage.value = UiText.Resource(R.string.msg_account_name_required)
                return@launch
            }
            val defaultCurrency = settingsRepository.current().baseCurrency.ifBlank { CurrencyRegistry.DEFAULT_CURRENCY }
            val normalizedCurrency = currency.trim().uppercase().ifEmpty { defaultCurrency }
            val openingMinor = if (openingBalanceMajorInput.isBlank()) {
                null
            } else {
                MoneyFormatter.parseMajorInput(openingBalanceMajorInput, normalizedCurrency) ?: run {
                    actionMessage.value = UiText.Resource(R.string.msg_invalid_opening_balance, normalizedCurrency)
                    return@launch
                }
            }
            val now = if (openingMinor != null) clock.nowEpochMillis() else null
            runCatching {
                accountRepository.create(
                    CreateAccountRequest(
                        name = trimmedName,
                        provider = provider.trim().ifEmpty { accountType.name },
                        accountType = accountType,
                        currency = normalizedCurrency,
                        initialBalanceMinor = openingMinor,
                        initialBalanceAt = now,
                    ),
                )
                settingsRepository.setBaseCurrency(normalizedCurrency)
            }.onSuccess {
                actionMessage.value = UiText.Resource(R.string.msg_account_added_named, trimmedName)
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_failed_create_account)
            }
        }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    fun restoreFromBackup(passphrase: String, streamOpener: () -> InputStream?) {
        if (passphrase.isBlank()) {
            actionMessage.value = UiText.Resource(R.string.msg_enter_backup_passphrase)
            return
        }
        viewModelScope.launch {
            isBusy.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    val stream = streamOpener() ?: throw MissingBackupStreamException()
                    stream.use { input ->
                        backupPort.restoreEncryptedBackup(passphrase, input)
                    }
                }
            }.onSuccess { res ->
                settingsRepository.setOnboardingCompleted(true)
                captureStatusRepository.refreshStatus()
                postRestorePrompt.value = true
                actionMessage.value = UiText.Resource(
                    R.string.msg_restore_success_onboarding,
                    res.restoredAccounts,
                    res.restoredTransactions,
                )
            }.onFailure { err ->
                actionMessage.value = if (err is MissingBackupStreamException) {
                    UiText.Resource(R.string.msg_unable_open_backup_file)
                } else {
                    UiText.fromError(err, R.string.msg_backup_restore_failed)
                }
            }
            isBusy.value = false
        }
    }

    fun dismissPostRestorePrompt() {
        postRestorePrompt.value = false
        actionMessage.value = null
    }

    fun wipeAllDataFromLocked() {
        viewModelScope.launch {
            isBusy.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    backupPort.wipeAllDataAndReset()
                }
            }.onSuccess {
                actionMessage.value = UiText.Resource(R.string.msg_local_data_wiped_cleanly)
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_failed_wipe_storage)
            }
            isBusy.value = false
        }
    }

    fun clearMessage() {
        actionMessage.value = null
    }

    private class MissingBackupStreamException : IllegalStateException()

    private data class OperationalSnapshot(
        val settings: AppSettings,
        val captureSummary: CaptureHealthSummary,
        val pendingReviewCount: Int,
        val hasAccounts: Boolean,
    )

    private data class ReadyAuxState(
        val isBusy: Boolean,
        val actionMessage: UiText?,
        val showPostRestorePrompt: Boolean,
        val selfCheckResult: CaptureSelfCheckResult?,
        val isAppLocked: Boolean,
    )
}
