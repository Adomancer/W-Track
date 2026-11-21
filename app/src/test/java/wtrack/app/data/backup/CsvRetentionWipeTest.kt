package wtrack.app.data.backup

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wtrack.app.data.ingestion.IngestionTestHarness
import wtrack.app.data.ingestion.sampleEnvelope
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.repository.DatabaseHealthRepositoryImpl
import wtrack.app.data.repository.SourceAndEventRepositoryImpl
import wtrack.app.data.worker.RetentionPurgeRunner
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.repository.BackupAndDiagnosticsPort
import wtrack.app.domain.repository.BackupVerificationResult
import wtrack.app.domain.repository.ProviderCatalogEntry
import wtrack.app.domain.repository.RestoreResult
import wtrack.app.domain.repository.SampleExportResult
import wtrack.app.domain.usecase.WipeAllDataUseCase
import wtrack.integration.Direction

class CsvExporterTest {
    @Test
    fun exportsRfc4180CsvWithMinorToDecimalConversionAndFormulaInjectionNeutralization() {
        val accounts = listOf(
            Account(1L, "Bank =Evil", "Bank", AccountType.BANK, "IDR", null, null, null, true),
        )
        val categories = listOf(
            Category(4L, "Food, Dining", null, Direction.OUTFLOW, null, false, false, 1L),
        )
        val txs = listOf(
            Transaction(
                id = 1L,
                accountId = 1L,
                sourceId = null,
                eventId = null,
                amountMinor = 1_250_000_50L,
                currency = "IDR",
                direction = Direction.OUTFLOW,
                transactionType = DomainTransactionType.PURCHASE,
                merchantId = null,
                merchantName = "+CMD|'/C calc'!A0",
                description = "-10+20",
                note = "Line1\nLine2",
                occurredAt = 1_700_000_000_000L,
                categoryId = 4L,
                categorySource = null,
                appliedRuleId = null,
                status = TransactionStatus.CONFIRMED,
                fingerprint = null,
                parserVersion = null,
                originalSnapshot = null,
                providerReference = "@SUM(A1:A2)",
                relatedTransactionId = null,
                reportedBalanceMinor = null,
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L,
            ),
        )

        val baos = ByteArrayOutputStream()
        val count = CsvExporter().writeTransactionsCsv(baos, txs, accounts, categories)
        val csv = String(baos.toByteArray(), StandardCharsets.UTF_8)

        assertThat(count).isEqualTo(1)
        assertThat(csv).contains("1250000.50")
        assertThat(csv).contains("\"Food, Dining\"")
        assertThat(csv).contains("'+CMD|'/C calc'!A0")
        assertThat(csv).contains("'-10+20")
        assertThat(csv).contains("'@SUM(A1:A2)")
        assertThat(csv).contains("\"Line1\nLine2\"")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RetentionAndWipeTest {

    private lateinit var harness: IngestionTestHarness

    @Before
    fun setUp() {
        harness = IngestionTestHarness()
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    @Test
    fun retentionPurgeRunnerClearsExpiredPayloadsPrunesLogsAndRecordsHeartbeatGap() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val srcId = "pkg.ret"
        db.transactionSourceDao().upsert(TransactionSourceEntity(srcId, srcId, "Ret Bank", true, 1, null, now, now))

        val expiredId = db.notificationEventDao().insertIgnore(
            NotificationEventEntity(
                sourceId = srcId, packageName = srcId, stableEventKey = "exp", notificationKey = "k1",
                notificationId = 1, notificationTag = null, postedAt = now, payloadJson = "{\"text\":\"secret\"}",
                purgeAfter = now - 1L, processingStatus = "PROCESSED", attemptCount = 1, failureReason = null,
                reviewDraftJson = null, receivedAt = now - 10_000L, processedAt = now - 9_000L, updatedAt = now,
            ),
        )
        val retainedId = db.notificationEventDao().insertIgnore(
            NotificationEventEntity(
                sourceId = srcId, packageName = srcId, stableEventKey = "keep", notificationKey = "k2",
                notificationId = 2, notificationTag = null, postedAt = now, payloadJson = "{\"text\":\"keep\"}",
                purgeAfter = now + 86_400_000L, processingStatus = "NEEDS_REVIEW", attemptCount = 1, failureReason = "UNKNOWN_FORMAT",
                reviewDraftJson = null, receivedAt = now, processedAt = null, updatedAt = now,
            ),
        )

        harness.heartbeatMonitor.recordAlive()
        harness.clock.advanceBy(4L * 3_600_000L) // > default 3h threshold

        val sourceRepo = SourceAndEventRepositoryImpl(harness.holder, harness.clock)
        val runner = RetentionPurgeRunner(
            holder = harness.holder,
            sourceAndEventRepository = sourceRepo,
            heartbeatMonitor = harness.heartbeatMonitor,
            logger = harness.logger,
            clock = harness.clock,
        )
        val purgedCount = runner.run()

        assertThat(purgedCount).isEqualTo(1)
        assertThat(db.notificationEventDao().getById(expiredId)?.payloadJson).isNull()
        assertThat(db.notificationEventDao().getById(retainedId)?.payloadJson).contains("keep")
        assertThat(db.captureGapDao().getAllOnce()).hasSize(1)
    }

    @Test
    fun wipeAllDataClearsDatabaseSettingsAllowlistAndLogs() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        db.accountDao().insert(AccountEntity(name = "Main", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now))
        harness.settingsStore.setOnboardingCompleted(true)
        harness.logger.logEvent("TEST", "BEFORE_WIPE")
        assertThat(harness.logFile.exists()).isTrue()

        val dbHealthRepo = DatabaseHealthRepositoryImpl(harness.holder, harness.settingsStore)
        val port = object : BackupAndDiagnosticsPort {
            override suspend fun exportEncryptedBackup(passphrase: String, outputStream: java.io.OutputStream, verifyStreamOpener: (() -> java.io.InputStream?)?) =
                BackupVerificationResult(0, 0, 0, true, "")
            override suspend fun restoreEncryptedBackup(passphrase: String, inputStream: java.io.InputStream) =
                RestoreResult(0, 0, 0, 0)
            override suspend fun exportCsv(outputStream: java.io.OutputStream) = 0
            override suspend fun wipeAllDataAndReset() {
                dbHealthRepo.wipeAllDataAndReset()
                harness.logger.clearAll()
                harness.allowlistCache.refreshSnapshotNow()
            }
            override fun readDiagnosticLogLines(limit: Int) = harness.logger.readRecentLines(limit)
            override fun clearDiagnosticLogs() = harness.logger.clearAll()
            override fun providerCatalogEntries() = emptyList<ProviderCatalogEntry>()
            override suspend fun refreshAllowlistCache() = harness.allowlistCache.refreshSnapshotNow()
            override suspend fun countRetainedM3aSamples() = 0
            override suspend fun exportAnonymizedM3aSamples(
                outputStream: java.io.OutputStream,
                redactTerms: List<String>,
            ) = SampleExportResult(0, 0)
            override suspend fun purgeRetainedM3aSamplesNow() = 0
            override suspend fun reparseRetainedM3aSamples() = 0
            override suspend fun findReparseProposals() = emptyList<wtrack.app.domain.repository.ReparseProposal>()
            override suspend fun applyReparseProposal(proposal: wtrack.app.domain.repository.ReparseProposal) = false
            override suspend fun runCaptureSelfCheck() = wtrack.app.domain.repository.CaptureSelfCheckResult(
                permissionGranted = true,
                listenerConnected = true,
                databaseReady = true,
                registeredParserCount = 0,
                dryRunParserPassed = false,
                summary = "test",
            )
        }

        WipeAllDataUseCase(port).invoke()

        assertThat(harness.db().accountDao().getAllOnce()).isEmpty()
        assertThat(harness.settingsStore.current().onboardingCompleted).isFalse()
        assertThat(harness.logFile.exists()).isFalse()
        // System categories re-seeded cleanly on freshly initialized empty DB
        assertThat(harness.db().categoryDao().getAllOnce()).isNotEmpty()
        assertThat(sampleEnvelope()).isNotNull()
    }
}
