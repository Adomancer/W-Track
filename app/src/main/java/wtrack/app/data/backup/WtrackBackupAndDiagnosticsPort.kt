package wtrack.app.data.backup

import java.io.InputStream
import java.io.OutputStream
import wtrack.app.core.logging.WtrackLogger
import wtrack.app.data.ingestion.AllowlistCache
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.BackupAndDiagnosticsPort
import wtrack.app.domain.repository.BackupVerificationResult
import wtrack.app.domain.repository.CategoryRepository
import wtrack.app.domain.repository.DatabaseHealthRepository
import wtrack.app.domain.repository.ProviderCatalogEntry
import wtrack.app.domain.repository.RestoreResult
import wtrack.app.domain.repository.TransactionRepository
import wtrack.integration.providers.catalog.ProviderCatalog

class WtrackBackupAndDiagnosticsPort(
    private val backupService: BackupService,
    private val csvExporter: CsvExporter,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val databaseHealthRepository: DatabaseHealthRepository,
    private val logger: WtrackLogger,
    private val allowlistCache: AllowlistCache,
) : BackupAndDiagnosticsPort {

    override suspend fun exportEncryptedBackup(
        passphrase: String,
        outputStream: OutputStream,
        verifyStreamOpener: (() -> InputStream?)?,
    ): BackupVerificationResult =
        backupService.exportEncryptedBackup(passphrase, outputStream, verifyStreamOpener)

    override suspend fun restoreEncryptedBackup(
        passphrase: String,
        inputStream: InputStream,
    ): RestoreResult = backupService.restoreEncryptedBackup(passphrase, inputStream)

    override suspend fun exportCsv(outputStream: OutputStream): Int {
        val transactions = transactionRepository.getAllOnce()
        val accounts = accountRepository.getAllOnce()
        val categories = categoryRepository.getAllOnce()
        return csvExporter.writeTransactionsCsv(outputStream, transactions, accounts, categories)
    }

    override suspend fun wipeAllDataAndReset() {
        databaseHealthRepository.wipeAllDataAndReset()
        logger.clearAll()
        allowlistCache.startObserving()
        allowlistCache.refreshSnapshotNow()
    }

    override fun readDiagnosticLogLines(limit: Int): List<String> = logger.readRecentLines(limit)

    override fun clearDiagnosticLogs() = logger.clearAll()

    override fun providerCatalogEntries(): List<ProviderCatalogEntry> =
        ProviderCatalog.initialPoCTargets.map { item ->
            ProviderCatalogEntry(
                priority = item.priority,
                id = item.id,
                displayName = item.displayName,
                category = item.category,
                candidatePackageIds = item.candidatePackageIds,
                verifiedPackageId = item.verifiedPackageId,
                verificationStatus = item.verificationStatus.name,
                notes = item.notes,
            )
        }

    override suspend fun refreshAllowlistCache() {
        allowlistCache.refreshSnapshotNow()
    }
}
