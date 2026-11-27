package wtrack.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wtrack.app.R
import wtrack.app.core.coroutines.DispatcherProvider
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.CaptureGap
import wtrack.app.domain.model.CaptureHealthSummary
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.ThemeMode
import wtrack.app.domain.model.TransactionRule
import wtrack.app.domain.repository.BackupAndDiagnosticsPort
import wtrack.app.domain.repository.CaptureStatusRepository
import wtrack.app.domain.repository.CategoryRepository
import wtrack.app.domain.repository.CustomParserRuleRepository
import wtrack.app.domain.repository.NeedsReviewRepository
import wtrack.app.domain.repository.ProviderCatalogEntry
import wtrack.app.domain.repository.ReparseProposal
import wtrack.app.domain.repository.RuleRepository
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.GateG1Evaluator
import wtrack.app.domain.usecase.GateG1Report
import wtrack.app.domain.usecase.ProviderMeasurementInput
import wtrack.app.feature.common.UiText
import wtrack.integration.Direction

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val categories: List<Category> = emptyList(),
    val rules: List<TransactionRule> = emptyList(),
    val captureSummary: CaptureHealthSummary = CaptureHealthSummary(
        appStatus = CaptureStatus.ATTENTION,
        isPermissionGranted = false,
        isListenerConnected = false,
        openGap = null,
        lastAliveAt = 0L,
    ),
    val captureGaps: List<CaptureGap> = emptyList(),
    val pendingReviewCount: Int = 0,
    val showPostRestorePermissionPrompt: Boolean = false,
    val diagnosticLogs: List<String> = emptyList(),
    val providerCatalog: List<ProviderCatalogEntry> = emptyList(),
    val gateG1Report: GateG1Report? = null,
    val retainedSampleCount: Int = 0,
    val customParserRuleCount: Int = 0,
    val reparseProposals: List<ReparseProposal> = emptyList(),
    val isBusy: Boolean = false,
    val actionMessage: UiText? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val categoryRepository: CategoryRepository,
    private val ruleRepository: RuleRepository,
    private val captureStatusRepository: CaptureStatusRepository,
    private val needsReviewRepository: NeedsReviewRepository,
    customParserRuleRepository: CustomParserRuleRepository,
    private val backupPort: BackupAndDiagnosticsPort,
    private val gateG1Evaluator: GateG1Evaluator,
    private val clock: Clock,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val logsFlow = MutableStateFlow(backupPort.readDiagnosticLogLines(200))
    private val gateReportFlow = MutableStateFlow<GateG1Report?>(null)
    private val reparseProposalsFlow = MutableStateFlow<List<ReparseProposal>>(emptyList())
    private val sampleCountFlow = MutableStateFlow(0)
    private val isBusyFlow = MutableStateFlow(false)
    private val actionMessage = MutableStateFlow<UiText?>(null)
    private val postRestorePrompt = MutableStateFlow(false)

    init {
        refreshSampleCount()
    }

    private val repoSnapshot = combine(
        settingsRepository.settings,
        categoryRepository.categories,
        ruleRepository.rules,
        captureStatusRepository.healthSummary,
        captureStatusRepository.captureGaps,
    ) { settings, categories, rules, health, gaps ->
        SettingsSnapshot(settings, categories, rules, health, gaps)
    }

    private val reportAndProposalsFlow = combine(
        gateReportFlow,
        reparseProposalsFlow,
    ) { report, proposals ->
        report to proposals
    }

    private val auxiliarySignals = combine(
        needsReviewRepository.pendingCount,
        postRestorePrompt,
        logsFlow,
        reportAndProposalsFlow,
        sampleCountFlow,
    ) { pendingReviewCount, restorePrompt, logs, (report, proposals), retainedSampleCount ->
        AuxSnapshot(pendingReviewCount, restorePrompt, logs, report, proposals, retainedSampleCount)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        repoSnapshot,
        auxiliarySignals,
        customParserRuleRepository.rules,
        isBusyFlow,
        actionMessage,
    ) { snap, aux, customParserRules, busy, message ->
        SettingsUiState(
            settings = snap.settings,
            categories = snap.categories,
            rules = snap.rules,
            captureSummary = snap.captureSummary,
            captureGaps = snap.captureGaps,
            pendingReviewCount = aux.pendingReviewCount,
            showPostRestorePermissionPrompt = aux.restorePrompt && !snap.captureSummary.isPermissionGranted,
            diagnosticLogs = aux.logs,
            providerCatalog = backupPort.providerCatalogEntries(),
            gateG1Report = aux.report,
            retainedSampleCount = aux.retainedSampleCount,
            customParserRuleCount = customParserRules.size,
            reparseProposals = aux.reparseProposals,
            isBusy = busy,
            actionMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setBaseCurrency(currency: String) {
        viewModelScope.launch {
            settingsRepository.setBaseCurrency(currency)
            actionMessage.value = UiText.Resource(R.string.msg_base_currency_updated, currency.uppercase())
        }
    }

    fun setVerificationWindowDays(days: Int) {
        viewModelScope.launch { settingsRepository.setVerificationWindowDays(days) }
    }

    fun setHeartbeatStaleHours(hours: Int) {
        viewModelScope.launch { settingsRepository.setHeartbeatStaleHours(hours) }
    }

    fun setBackupReminderThresholds(days: Int, txCount: Int) {
        viewModelScope.launch {
            settingsRepository.setBackupReminderThresholds(days, txCount)
            actionMessage.value = UiText.Resource(R.string.msg_backup_thresholds_updated)
        }
    }

    fun setScheduledBackupConfig(folderUri: String?, enabled: Boolean, intervalDays: Int) {
        viewModelScope.launch {
            settingsRepository.setScheduledBackupConfig(folderUri, enabled, intervalDays)
            actionMessage.value = if (folderUri.isNullOrBlank()) {
                UiText.Resource(R.string.msg_scheduled_folder_cleared)
            } else {
                UiText.Resource(R.string.msg_scheduled_settings_updated)
            }
        }
    }

    fun setAppLock(enabled: Boolean, timeoutSeconds: Int) {
        viewModelScope.launch {
            settingsRepository.setAppLock(enabled, timeoutSeconds)
            actionMessage.value = if (enabled) {
                UiText.Resource(R.string.msg_app_lock_enabled)
            } else {
                UiText.Resource(R.string.msg_app_lock_disabled)
            }
        }
    }

    fun setLocalAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLocalAlertsEnabled(enabled)
            actionMessage.value = if (enabled) {
                UiText.Resource(R.string.msg_local_alerts_enabled)
            } else {
                UiText.Resource(R.string.msg_local_alerts_disabled)
            }
        }
    }

    fun createCategory(name: String, directionScope: Direction?) {
        viewModelScope.launch {
            runCatching { categoryRepository.create(name, directionScope) }
                .onSuccess { actionMessage.value = UiText.Resource(R.string.msg_category_created) }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_category_create_failed) }
        }
    }

    fun renameCategory(id: Long, newName: String) {
        viewModelScope.launch {
            runCatching { categoryRepository.rename(id, newName) }
                .onSuccess { actionMessage.value = UiText.Resource(R.string.msg_category_renamed) }
                .onFailure { actionMessage.value = UiText.fromError(it, R.string.msg_category_rename_failed) }
        }
    }

    fun setCategoryArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            categoryRepository.setArchived(id, archived)
        }
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { ruleRepository.setEnabled(id, enabled) }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            ruleRepository.delete(id)
            actionMessage.value = UiText.Resource(R.string.msg_rule_deleted)
        }
    }

    fun createRule(merchantKey: String?, descriptionFragment: String?, categoryId: Long, direction: Direction?) {
        viewModelScope.launch {
            runCatching {
                ruleRepository.createRule(
                    name = merchantKey?.takeIf { it.isNotBlank() } ?: descriptionFragment ?: "Rule",
                    merchantKey = merchantKey?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                    descriptionFragment = descriptionFragment?.trim()?.takeIf { it.isNotEmpty() },
                    accountId = null,
                    direction = direction,
                    categoryId = categoryId,
                )
            }.onSuccess {
                actionMessage.value = UiText.Resource(R.string.msg_rule_saved)
            }.onFailure {
                actionMessage.value = UiText.fromError(it, R.string.msg_rule_save_failed)
            }
        }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            captureStatusRepository.refreshStatus()
            logsFlow.value = backupPort.readDiagnosticLogLines(200)
            sampleCountFlow.value = runCatching { backupPort.countRetainedM3aSamples() }.getOrDefault(0)
        }
    }

    private fun refreshSampleCount() {
        viewModelScope.launch {
            sampleCountFlow.value = runCatching { backupPort.countRetainedM3aSamples() }.getOrDefault(0)
        }
    }

    fun exportAnonymizedM3aSamples(redactTermsCsv: String, streamOpener: () -> OutputStream?) {
        val redactTerms = redactTermsCsv
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        viewModelScope.launch {
            isBusyFlow.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    val stream = streamOpener() ?: throw StreamOpenFailedException()
                    stream.use { output -> backupPort.exportAnonymizedM3aSamples(output, redactTerms) }
                }
            }.onSuccess { result ->
                sampleCountFlow.value = runCatching { backupPort.countRetainedM3aSamples() }.getOrDefault(0)
                actionMessage.value = if (result.skippedSecurityCount > 0) {
                    UiText.Resource(
                        R.string.msg_sample_exported_with_skipped,
                        result.exportedCount,
                        result.skippedSecurityCount,
                    )
                } else {
                    UiText.Resource(R.string.msg_sample_exported, result.exportedCount)
                }
            }.onFailure { err ->
                actionMessage.value = if (err is StreamOpenFailedException) {
                    UiText.Resource(R.string.msg_sample_export_open_failed)
                } else {
                    UiText.fromError(err, R.string.msg_sample_export_failed)
                }
            }
            isBusyFlow.value = false
        }
    }

    fun purgeRetainedM3aSamplesNow() {
        viewModelScope.launch {
            isBusyFlow.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    backupPort.purgeRetainedM3aSamplesNow()
                }
            }.onSuccess { purged ->
                sampleCountFlow.value = 0
                actionMessage.value = UiText.Resource(R.string.msg_sample_purged, purged)
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_sample_purge_failed)
            }
            isBusyFlow.value = false
        }
    }

    fun reparseRetainedM3aSamples() {
        viewModelScope.launch {
            isBusyFlow.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    backupPort.reparseRetainedM3aSamples()
                }
            }.onSuccess { reparsed ->
                sampleCountFlow.value = runCatching { backupPort.countRetainedM3aSamples() }.getOrDefault(0)
                logsFlow.value = backupPort.readDiagnosticLogLines(200)
                actionMessage.value = if (reparsed > 0) {
                    UiText.Resource(R.string.msg_sample_reparsed, reparsed)
                } else {
                    UiText.Resource(R.string.msg_sample_reparse_none)
                }
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_sample_reparse_failed)
            }
            isBusyFlow.value = false
        }
    }

    fun scanReparseProposals() {
        viewModelScope.launch {
            isBusyFlow.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    backupPort.findReparseProposals()
                }
            }.onSuccess { proposals ->
                reparseProposalsFlow.value = proposals
                actionMessage.value = if (proposals.isEmpty()) {
                    UiText.Resource(R.string.msg_reparse_none)
                } else {
                    UiText.Resource(R.string.msg_reparse_found, proposals.size)
                }
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_reparse_scan_failed)
            }
            isBusyFlow.value = false
        }
    }

    fun applyReparseProposal(proposal: ReparseProposal) {
        viewModelScope.launch {
            isBusyFlow.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    backupPort.applyReparseProposal(proposal)
                }
            }.onSuccess { applied ->
                if (applied) {
                    reparseProposalsFlow.value = reparseProposalsFlow.value.filterNot { it.eventId == proposal.eventId }
                    logsFlow.value = backupPort.readDiagnosticLogLines(200)
                    actionMessage.value = UiText.Resource(
                        R.string.msg_reparse_applied,
                        proposal.newParserVersion,
                        proposal.transactionId,
                    )
                } else {
                    reparseProposalsFlow.value = reparseProposalsFlow.value.filterNot { it.eventId == proposal.eventId }
                    actionMessage.value = UiText.Resource(R.string.msg_reparse_skipped)
                }
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_reparse_apply_failed)
            }
            isBusyFlow.value = false
        }
    }

    fun dismissReparseProposal(eventId: Long) {
        reparseProposalsFlow.value = reparseProposalsFlow.value.filterNot { it.eventId == eventId }
    }

    fun clearDiagnosticLogs() {
        backupPort.clearDiagnosticLogs()
        logsFlow.value = emptyList()
        actionMessage.value = UiText.Resource(R.string.msg_logs_cleared)
    }

    fun evaluateGateG1(inputs: List<ProviderMeasurementInput>) {
        gateReportFlow.value = gateG1Evaluator.evaluate(inputs)
    }

    fun exportEncryptedBackup(
        passphrase: String,
        streamOpener: () -> OutputStream?,
        verifyStreamOpener: (() -> InputStream?)? = null,
    ) {
        viewModelScope.launch {
            isBusyFlow.value = true
            runCatching {
                val res = withContext(dispatcherProvider.io) {
                    val stream = streamOpener() ?: throw StreamOpenFailedException()
                    backupPort.exportEncryptedBackup(passphrase, stream, verifyStreamOpener)
                }
                settingsRepository.recordBackupCompleted(clock.nowEpochMillis())
                res
            }.onSuccess { res ->
                actionMessage.value = UiText.Resource(
                    R.string.msg_backup_exported_verified,
                    res.transactionCount,
                    res.accountCount,
                )
            }.onFailure { err ->
                actionMessage.value = if (err is StreamOpenFailedException) {
                    UiText.Resource(R.string.msg_backup_dest_open_failed)
                } else {
                    UiText.fromError(err, R.string.msg_backup_export_failed)
                }
            }
            isBusyFlow.value = false
        }
    }

    fun restoreEncryptedBackup(passphrase: String, streamOpener: () -> InputStream?) {
        viewModelScope.launch {
            isBusyFlow.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    val stream = streamOpener() ?: throw StreamOpenFailedException()
                    stream.use { input -> backupPort.restoreEncryptedBackup(passphrase, input) }
                }
            }.onSuccess { res ->
                captureStatusRepository.refreshStatus()
                postRestorePrompt.value = true
                actionMessage.value = UiText.Resource(
                    R.string.msg_restore_success_settings,
                    res.restoredAccounts,
                    res.restoredTransactions,
                )
            }.onFailure { err ->
                actionMessage.value = if (err is StreamOpenFailedException) {
                    UiText.Resource(R.string.msg_backup_file_open_failed)
                } else {
                    UiText.fromError(err, R.string.msg_restore_failed)
                }
            }
            isBusyFlow.value = false
        }
    }

    fun exportCsv(streamOpener: () -> OutputStream?) {
        viewModelScope.launch {
            isBusyFlow.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    val stream = streamOpener() ?: throw StreamOpenFailedException()
                    stream.use { output -> backupPort.exportCsv(output) }
                }
            }.onSuccess { count ->
                actionMessage.value = UiText.Resource(R.string.msg_csv_exported, count)
            }.onFailure { err ->
                actionMessage.value = if (err is StreamOpenFailedException) {
                    UiText.Resource(R.string.msg_csv_dest_open_failed)
                } else {
                    UiText.fromError(err, R.string.msg_csv_export_failed)
                }
            }
            isBusyFlow.value = false
        }
    }

    fun wipeAllData() {
        viewModelScope.launch {
            isBusyFlow.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    backupPort.wipeAllDataAndReset()
                }
            }.onSuccess {
                logsFlow.value = emptyList()
                sampleCountFlow.value = 0
                actionMessage.value = UiText.Resource(R.string.msg_all_local_data_wiped)
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_wipe_failed)
            }
            isBusyFlow.value = false
        }
    }

    private class StreamOpenFailedException : IllegalStateException()

    fun clearMessage() {
        actionMessage.value = null
    }

    fun dismissPostRestorePrompt() {
        postRestorePrompt.value = false
    }

    private data class SettingsSnapshot(
        val settings: AppSettings,
        val categories: List<Category>,
        val rules: List<TransactionRule>,
        val captureSummary: CaptureHealthSummary,
        val captureGaps: List<CaptureGap>,
    )

    private data class AuxSnapshot(
        val pendingReviewCount: Int,
        val restorePrompt: Boolean,
        val logs: List<String>,
        val report: GateG1Report?,
        val reparseProposals: List<ReparseProposal>,
        val retainedSampleCount: Int,
    )
}
