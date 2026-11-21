package wtrack.app.data.backup

import java.io.InputStream
import java.io.OutputStream
import wtrack.app.core.logging.WtrackLogger
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.ingestion.AllowlistCache
import wtrack.app.data.ingestion.CaptureStatusTracker
import wtrack.app.data.ingestion.IngestionPipeline
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.parser.ParserRegistry
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.BackupAndDiagnosticsPort
import wtrack.app.domain.repository.BackupVerificationResult
import wtrack.app.domain.repository.CaptureSelfCheckResult
import wtrack.app.domain.repository.CategoryRepository
import wtrack.app.domain.repository.DatabaseHealthRepository
import wtrack.app.domain.repository.ProviderCatalogEntry
import wtrack.app.domain.repository.RestoreResult
import wtrack.app.domain.repository.SampleExportResult
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.time.Clock
import wtrack.integration.Direction
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.providers.catalog.ProviderCatalog

class WtrackBackupAndDiagnosticsPort(
    private val holder: WtrackDatabaseHolder,
    private val backupService: BackupService,
    private val csvExporter: CsvExporter,
    private val m3aSampleExporter: M3aSampleExporter,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val databaseHealthRepository: DatabaseHealthRepository,
    private val logger: WtrackLogger,
    private val allowlistCache: AllowlistCache,
    private val parserRegistry: ParserRegistry,
    private val ingestionPipeline: IngestionPipeline,
    private val clock: Clock,
    private val captureStatusTracker: CaptureStatusTracker? = null,
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

    override suspend fun countRetainedM3aSamples(): Int {
        if (holder.healthState.value !is DatabaseHealthState.Ready) return 0
        return holder.requireDatabase().notificationEventDao().countRetainedSamples()
    }

    override suspend fun exportAnonymizedM3aSamples(
        outputStream: OutputStream,
        redactTerms: List<String>,
    ): SampleExportResult {
        if (holder.healthState.value !is DatabaseHealthState.Ready) {
            return SampleExportResult(exportedCount = 0, skippedSecurityCount = 0)
        }
        val samples = holder.requireDatabase().notificationEventDao().getRetainedSamplesOnce()
        return m3aSampleExporter.export(samples, outputStream, redactTerms)
    }

    override suspend fun purgeRetainedM3aSamplesNow(): Int {
        if (holder.healthState.value !is DatabaseHealthState.Ready) return 0
        return holder.requireDatabase().notificationEventDao()
            .purgeRetainedSamplesNow(clock.nowEpochMillis())
    }

    override suspend fun reparseRetainedM3aSamples(): Int {
        if (holder.healthState.value !is DatabaseHealthState.Ready) return 0
        val db = holder.requireDatabase()
        val eventDao = db.notificationEventDao()
        val txDao = db.transactionDao()
        val samples = eventDao.getRetainedSamplesOnce()
        var reparsed = 0
        samples.forEach { entity ->
            if (parserRegistry.parserVersionForSource(entity.sourceId) != null &&
                txDao.findByEventId(entity.id) == null
            ) {
                eventDao.update(
                    entity.copy(
                        processingStatus = NotificationEventStatus.RECEIVED.name,
                        failureReason = null,
                        attemptCount = 0,
                        processedAt = null,
                        updatedAt = clock.nowEpochMillis(),
                    ),
                )
                ingestionPipeline.processEvent(entity.id)
                reparsed++
            }
        }
        return reparsed
    }

    override suspend fun runCaptureSelfCheck(): CaptureSelfCheckResult {
        val permissionGranted = captureStatusTracker?.isNotificationAccessGranted() == true
        val listenerConnected = captureStatusTracker?.isListenerConnected?.value == true
        val databaseReady = holder.healthState.value is DatabaseHealthState.Ready
        val parserCount = parserRegistry.registeredCount()
        val syntheticEvent = NotificationEvent(
            sourceId = "com.jago.digitalBanking",
            packageName = "com.jago.digitalBanking",
            title = "Jago",
            text = "SelfCheck has sent Rp50.000 to you.",
            bigText = null,
            subText = null,
            channelId = "foreground",
            isGroupSummary = false,
            isContentRedacted = false,
            postedAtEpochMillis = clock.nowEpochMillis(),
            notificationKey = "wtrack_self_check",
            notificationId = 0,
            notificationTag = null,
            sourceEventReference = null,
        )
        val dryRunResult = parserRegistry.resolve(syntheticEvent)?.parse(syntheticEvent)
        val dryRunPassed = dryRunResult is ParseResult.Success &&
            dryRunResult.transaction.amountMinor == 5_000_000L &&
            dryRunResult.transaction.direction == Direction.INFLOW

        val summary = when {
            !databaseReady -> "Encrypted database is not ready."
            !permissionGranted -> "Dry-run parser passed ($parserCount parsers active). Grant Notification Access to start background capture."
            !listenerConnected -> "Permission granted ($parserCount parsers active); awaiting Android listener service bind."
            !dryRunPassed -> "Listener bound, but synthetic parser dry-run did not pass."
            else -> "All capture checks passed ($parserCount parsers active)."
        }

        return CaptureSelfCheckResult(
            permissionGranted = permissionGranted,
            listenerConnected = listenerConnected,
            databaseReady = databaseReady,
            registeredParserCount = parserCount,
            dryRunParserPassed = dryRunPassed,
            summary = summary,
        )
    }
}
