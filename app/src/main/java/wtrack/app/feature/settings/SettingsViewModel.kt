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
    val actionMessage: String? = null,
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
    private val actionMessage = MutableStateFlow<String?>(null)
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
            actionMessage.value = "Base currency updated to ${currency.uppercase()}."
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
            actionMessage.value = "Backup reminder thresholds updated."
        }
    }

    fun setScheduledBackupConfig(folderUri: String?, enabled: Boolean, intervalDays: Int) {
        viewModelScope.launch {
            settingsRepository.setScheduledBackupConfig(folderUri, enabled, intervalDays)
            actionMessage.value = if (folderUri.isNullOrBlank()) {
                "Scheduled backup folder cleared."
            } else {
                "Scheduled backup settings updated."
            }
        }
    }

    fun setAppLock(enabled: Boolean, timeoutSeconds: Int) {
        viewModelScope.launch {
            settingsRepository.setAppLock(enabled, timeoutSeconds)
            actionMessage.value = if (enabled) {
                "App Lock enabled."
            } else {
                "App Lock disabled."
            }
        }
    }

    fun setLocalAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLocalAlertsEnabled(enabled)
            actionMessage.value = if (enabled) {
                "Local status alerts enabled."
            } else {
                "Local status alerts disabled."
            }
        }
    }

    fun createCategory(name: String, directionScope: Direction?) {
        viewModelScope.launch {
            runCatching { categoryRepository.create(name, directionScope) }
                .onSuccess { actionMessage.value = "Category created." }
                .onFailure { actionMessage.value = it.message ?: "Failed to create category." }
        }
    }

    fun renameCategory(id: Long, newName: String) {
        viewModelScope.launch {
            runCatching { categoryRepository.rename(id, newName) }
                .onSuccess { actionMessage.value = "Category renamed." }
                .onFailure { actionMessage.value = it.message ?: "Rename failed." }
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
            actionMessage.value = "Rule deleted."
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
                actionMessage.value = "Rule saved."
            }.onFailure {
                actionMessage.value = it.message ?: "Failed to save rule."
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
                    val stream = streamOpener() ?: error("Could not open sample export destination.")
                    stream.use { output -> backupPort.exportAnonymizedM3aSamples(output, redactTerms) }
                }
            }.onSuccess { result ->
                sampleCountFlow.value = runCatching { backupPort.countRetainedM3aSamples() }.getOrDefault(0)
                actionMessage.value = buildString {
                    append("Exported ${result.exportedCount} anonymized sample(s)")
                    if (result.skippedSecurityCount > 0) {
                        append(" (skipped ${result.skippedSecurityCount} security notice(s))")
                    }
                    append(".")
                }
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Sample export failed."
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
                actionMessage.value = "Purged $purged retained sample payload(s) from encrypted storage."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Failed to purge retained samples."
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
                    "Re-parsed $reparsed retained sample(s) using active parsers."
                } else {
                    "No retained samples matched an active parser yet."
                }
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Sample re-parse failed."
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
                    "No parser update proposals for retained events."
                } else {
                    "Found ${proposals.size} parser update proposal(s) (PAR-11)."
                }
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Scan for re-parse proposals failed."
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
                    actionMessage.value = "Applied parser v${proposal.newParserVersion} update to transaction #${proposal.transactionId}."
                } else {
                    reparseProposalsFlow.value = reparseProposalsFlow.value.filterNot { it.eventId == proposal.eventId }
                    actionMessage.value = "Proposal skipped (already applied or transaction edited by user)."
                }
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Could not apply re-parse proposal."
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
        actionMessage.value = "Diagnostic logs cleared."
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
                    val stream = streamOpener() ?: error("Could not open backup destination.")
                    backupPort.exportEncryptedBackup(passphrase, stream, verifyStreamOpener)
                }
                settingsRepository.recordBackupCompleted(clock.nowEpochMillis())
                res
            }.onSuccess { res ->
                actionMessage.value = "Backup exported & verified: ${res.transactionCount} transactions, ${res.accountCount} accounts."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Backup export failed."
            }
            isBusyFlow.value = false
        }
    }

    fun restoreEncryptedBackup(passphrase: String, streamOpener: () -> InputStream?) {
        viewModelScope.launch {
            isBusyFlow.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    val stream = streamOpener() ?: error("Could not open backup file.")
                    stream.use { input -> backupPort.restoreEncryptedBackup(passphrase, input) }
                }
            }.onSuccess { res ->
                captureStatusRepository.refreshStatus()
                postRestorePrompt.value = true
                actionMessage.value = "Restored ${res.restoredAccounts} accounts, ${res.restoredTransactions} transactions. Re-grant Notification Access to resume capture."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Restore failed."
            }
            isBusyFlow.value = false
        }
    }

    fun exportCsv(streamOpener: () -> OutputStream?) {
        viewModelScope.launch {
            isBusyFlow.value = true
            runCatching {
                withContext(dispatcherProvider.io) {
                    val stream = streamOpener() ?: error("Could not open CSV destination.")
                    stream.use { output -> backupPort.exportCsv(output) }
                }
            }.onSuccess { count ->
                actionMessage.value = "Exported $count transactions to CSV."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "CSV export failed."
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
                actionMessage.value = "All local data wiped."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Wipe failed."
            }
            isBusyFlow.value = false
        }
    }

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
