package wtrack.app.domain.repository

import java.io.InputStream
import java.io.OutputStream
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.DomainTransactionType
import wtrack.integration.Direction

data class BackupVerificationResult(
    val transactionCount: Int,
    val accountCount: Int,
    val sourceCount: Int,
    val verified: Boolean,
    val detail: String,
)

data class RestoreResult(
    val restoredTransactions: Int,
    val restoredAccounts: Int,
    val restoredSources: Int,
    val preservedEventIdentities: Int,
)

data class SampleExportResult(
    val exportedCount: Int,
    val skippedSecurityCount: Int,
)

data class ProviderCatalogEntry(
    val priority: Int,
    val id: String,
    val displayName: String,
    val category: String,
    val candidatePackageIds: List<String>,
    val verifiedPackageId: String?,
    val verificationStatus: String,
    val notes: String,
)

data class CaptureSelfCheckResult(
    val permissionGranted: Boolean,
    val listenerConnected: Boolean,
    val databaseReady: Boolean,
    val registeredParserCount: Int,
    val dryRunParserPassed: Boolean,
    val summary: String,
)

data class ReparseProposal(
    val eventId: Long,
    val transactionId: Long,
    val sourceId: String,
    val oldParserVersion: Int,
    val newParserVersion: Int,
    val currency: String,
    val currentAmountMinor: Long,
    val proposedAmountMinor: Long,
    val currentDirection: Direction,
    val proposedDirection: Direction,
    val currentType: DomainTransactionType,
    val proposedType: DomainTransactionType,
    val currentDescription: String?,
    val proposedDescription: String?,
)

/**
 * Pure-Kotlin domain port bridging encrypted backup/restore, CSV export, wipe, diagnostic logs,
 * and provider catalog inspection to presentation ViewModels without leaking data/crypto internals.
 */
interface BackupAndDiagnosticsPort {
    suspend fun exportEncryptedBackup(
        passphrase: String,
        outputStream: OutputStream,
        verifyStreamOpener: (() -> InputStream?)? = null,
    ): BackupVerificationResult
    suspend fun restoreEncryptedBackup(passphrase: String, inputStream: InputStream): RestoreResult
    suspend fun exportCsv(outputStream: OutputStream): Int
    suspend fun wipeAllDataAndReset()
    fun readDiagnosticLogLines(limit: Int = 200): List<String>
    fun clearDiagnosticLogs()
    fun providerCatalogEntries(): List<ProviderCatalogEntry>
    suspend fun refreshAllowlistCache()
    suspend fun countRetainedM3aSamples(): Int
    suspend fun exportAnonymizedM3aSamples(
        outputStream: OutputStream,
        redactTerms: List<String> = emptyList(),
    ): SampleExportResult
    suspend fun purgeRetainedM3aSamplesNow(): Int
    suspend fun reparseRetainedM3aSamples(): Int
    suspend fun findReparseProposals(): List<ReparseProposal>
    suspend fun applyReparseProposal(proposal: ReparseProposal): Boolean
    suspend fun runCaptureSelfCheck(): CaptureSelfCheckResult
}

object BackupReminderEvaluator {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun shouldShowReminder(settings: AppSettings, nowEpochMillis: Long): Boolean {
        if (!settings.onboardingCompleted) return false
        val dismissCooldown = 24L * 3_600_000L
        if (settings.backupReminderDismissedAt > 0L &&
            (nowEpochMillis - settings.backupReminderDismissedAt) < dismissCooldown
        ) {
            return false
        }
        val overdueByTx = settings.txCountSinceLastBackup >= settings.backupReminderTxCount
        val overdueByDays = settings.lastBackupAt > 0L &&
            (nowEpochMillis - settings.lastBackupAt) >= settings.backupReminderDays * MILLIS_PER_DAY
        val overdueByScheduledFolder = settings.scheduledBackupEnabled &&
            !settings.scheduledBackupFolderUri.isNullOrBlank() &&
            (settings.lastBackupAt == 0L ||
                (nowEpochMillis - settings.lastBackupAt) >= settings.scheduledBackupIntervalDays * MILLIS_PER_DAY)
        return overdueByTx || overdueByDays || overdueByScheduledFolder
    }
}
