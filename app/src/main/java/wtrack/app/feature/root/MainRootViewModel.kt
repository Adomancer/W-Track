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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wtrack.app.core.coroutines.DispatcherProvider
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.CaptureHealthSummary
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.LockedReason
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.ThemeMode
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.BackupAndDiagnosticsPort
import wtrack.app.domain.repository.CaptureStatusRepository
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.DatabaseHealthRepository
import wtrack.app.domain.repository.NeedsReviewRepository
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.time.Clock

sealed interface RootUiState {
    data object Loading : RootUiState

    data class Locked(
        val reason: LockedReason,
        val detail: String,
        val isBusy: Boolean = false,
        val actionMessage: String? = null,
    ) : RootUiState

    data class Onboarding(
        val settings: AppSettings,
        val captureSummary: CaptureHealthSummary,
        val hasAccounts: Boolean,
        val isBusy: Boolean = false,
        val actionMessage: String? = null,
    ) : RootUiState

    data class Ready(
        val themeMode: ThemeMode,
        val baseCurrency: String,
        val pendingReviewCount: Int,
        val captureSummary: CaptureHealthSummary,
        val showPostRestorePermissionPrompt: Boolean = false,
        val actionMessage: String? = null,
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
    private val actionMessage = MutableStateFlow<String?>(null)
    private val postRestorePrompt = MutableStateFlow(false)

    private val operationalSignals = combine(
        settingsRepository.settings,
        captureStatusRepository.healthSummary,
        needsReviewRepository.pendingCount,
        accountRepository.accounts,
    ) { settings, capture, pendingCount, accounts ->
        OperationalSnapshot(settings, capture, pendingCount, accounts.isNotEmpty())
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
                    isBusy,
                    actionMessage,
                    postRestorePrompt,
                ) { ops, busy, message, restorePrompt ->
                    if (!ops.settings.onboardingCompleted) {
                        RootUiState.Onboarding(
                            settings = ops.settings,
                            captureSummary = ops.captureSummary,
                            hasAccounts = ops.hasAccounts,
                            isBusy = busy,
                            actionMessage = message,
                        )
                    } else {
                        RootUiState.Ready(
                            themeMode = ops.settings.themeMode,
                            baseCurrency = ops.settings.baseCurrency,
                            pendingReviewCount = ops.pendingReviewCount,
                            captureSummary = ops.captureSummary,
                            showPostRestorePermissionPrompt = restorePrompt && !ops.captureSummary.isPermissionGranted,
                            actionMessage = message,
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
                actionMessage.value = "Account name is required."
                return@launch
            }
            val normalizedCurrency = currency.trim().uppercase().ifEmpty { "IDR" }
            val openingMinor = if (openingBalanceMajorInput.isBlank()) {
                null
            } else {
                MoneyFormatter.parseMajorInput(openingBalanceMajorInput, normalizedCurrency) ?: run {
                    actionMessage.value = "Invalid opening balance format for $normalizedCurrency."
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
                actionMessage.value = "Account \"$trimmedName\" added."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Failed to create account."
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
            actionMessage.value = "Enter backup passphrase."
            return
        }
        viewModelScope.launch {
            isBusy.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    val stream = streamOpener() ?: error("Unable to open selected backup file.")
                    stream.use { input ->
                        backupPort.restoreEncryptedBackup(passphrase, input)
                    }
                }
            }.onSuccess { res ->
                settingsRepository.setOnboardingCompleted(true)
                captureStatusRepository.refreshStatus()
                postRestorePrompt.value = true
                actionMessage.value = "Restored ${res.restoredAccounts} accounts and ${res.restoredTransactions} transactions. Re-grant Notification Access to resume capture."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Backup restore failed."
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
                actionMessage.value = "Local data wiped cleanly."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Failed to wipe local storage."
            }
            isBusy.value = false
        }
    }

    fun clearMessage() {
        actionMessage.value = null
    }

    private data class OperationalSnapshot(
        val settings: AppSettings,
        val captureSummary: CaptureHealthSummary,
        val pendingReviewCount: Int,
        val hasAccounts: Boolean,
    )
}
