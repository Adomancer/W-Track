package wtrack.app.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wtrack.app.data.backup.BackupCryptoConfig
import wtrack.app.data.backup.BackupCryptoContainer
import wtrack.app.data.backup.BackupService
import wtrack.app.data.backup.CsvExporter
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.BudgetEntity
import wtrack.app.data.local.entity.CaptureGapEntity
import wtrack.app.data.local.entity.CategoryEntity
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TagEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionRuleEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.entity.TransactionTagEntity
import wtrack.app.data.local.entity.TransferMatchEntity
import wtrack.app.data.local.mapper.LocalJson
import wtrack.app.data.repository.AccountRepositoryImpl
import wtrack.app.data.repository.CategoryRepositoryImpl
import wtrack.app.data.repository.SourceAndEventRepositoryImpl
import wtrack.app.data.repository.TransactionRepositoryImpl
import wtrack.app.data.worker.RetentionPurgeRunner
import wtrack.app.domain.exception.BackupRestoreException
import wtrack.app.domain.model.CaptureGapReason
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.model.ThemeMode
import wtrack.app.domain.parser.DefaultParserRegistry
import wtrack.integration.Direction
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionParser
import wtrack.integration.TransactionType

/**
 * Hardware verification on `iQOO Z10R` for production Argon2id (`64 MiB`, `3` iterations),
 * 13-table encrypted `.wtrackbackup` export/verification/atomic restore (`TEST-006`, `ADR-006`),
 * `RetentionPurgeRunner` (`SEC-002`), and `CsvExporter` (`DATA-11`).
 */
@RunWith(AndroidJUnit4::class)
class BackupAndRetentionInstrumentedTest {
    private lateinit var sourceHarness: InstrumentedTestHarness
    private lateinit var targetHarness: InstrumentedTestHarness

    @Before
    fun setUp() {
        sourceHarness = InstrumentedTestHarness()
        targetHarness = InstrumentedTestHarness(initialPermissionGranted = false)
    }

    @After
    fun tearDown() {
        sourceHarness.tearDown()
        targetHarness.tearDown()
    }

    @Test
    fun benchmarksProductionArgon2idAndVerifiesAtomic13TableBackupRestore() = runBlocking {
        withTimeout(120_000L) {
        val prodConfig = BackupCryptoConfig()
        val benchmarkPayload = "{\"bench\":\"payload\",\"items\":[${(1..80).joinToString(",") { "{\"id\":$it,\"amt\":5000000}" }}]}"
        BackupCryptoContainer.encrypt("{}", "WarmupPass1!", BackupCryptoConfig(memoryKiB = 4_096, iterations = 2))
        val t0 = System.nanoTime()
        val encryptedBench = BackupCryptoContainer.encrypt(benchmarkPayload, "StrongHardwarePass1!", prodConfig)
        val encryptMs = (System.nanoTime() - t0) / 1_000_000L

        val t1 = System.nanoTime()
        val decryptedBench = BackupCryptoContainer.decrypt(encryptedBench, "StrongHardwarePass1!")
        val decryptMs = (System.nanoTime() - t1) / 1_000_000L
        assertThat(decryptedBench).isEqualTo(benchmarkPayload)
        Log.i(
            "WTrackBenchmark",
            "Argon2id_${prodConfig.memoryKiB}KiB_${prodConfig.iterations}iter encryptMs=$encryptMs decryptMs=$decryptMs cipherBytes=${encryptedBench.size}",
        )
        assertThat(encryptMs).isLessThan(30_000L)
        assertThat(decryptMs).isLessThan(30_000L)

        // Seed full 13-table ledger in sourceHarness
        val db = sourceHarness.db()
        val now = sourceHarness.clock.now
        val srcId = "pkg.hw.backup"
        sourceHarness.settingsStore.setBaseCurrency("USD")
        sourceHarness.settingsStore.setThemeMode(ThemeMode.DARK)
        sourceHarness.settingsStore.setScheduledBackupConfig("content://tree/backup", true, 14)

        db.transactionSourceDao().upsert(TransactionSourceEntity(srcId, srcId, "HW Bank", true, 2, null, now, now))
        val acc1 = db.accountDao().insert(AccountEntity(name = "Checking", provider = "Bank", accountType = "BANK", currency = "USD", initialBalanceMinor = 50_000L, initialBalanceAt = now - 10_000L, lastReconciledAt = null, createdAt = now, updatedAt = now))
        val acc2 = db.accountDao().insert(AccountEntity(name = "Wallet", provider = "EWallet", accountType = "EWALLET", currency = "USD", initialBalanceMinor = 0L, initialBalanceAt = now - 10_000L, lastReconciledAt = null, createdAt = now, updatedAt = now))
        db.accountSourceDao().insert(AccountSourceEntity(accountId = acc1, sourceId = srcId, accountHint = "4242", createdAt = now))

        val parentCat = db.categoryDao().insert(CategoryEntity(name = "Travel", systemKey = null, directionScope = "OUTFLOW", parentId = null, createdAt = now))
        val childCat = db.categoryDao().insert(CategoryEntity(name = "Hotel", systemKey = null, directionScope = "OUTFLOW", parentId = parentCat, createdAt = now))

        sourceHarness.allowlistCache.refreshSnapshotNow()
        val parser = object : TransactionParser {
            override val sourceId: String = srcId
            override val version: Int = 2
            override fun supports(event: NotificationEvent) = event.sourceId == srcId
            override suspend fun parse(event: NotificationEvent): ParseResult = if (event.text.contains("FAIL")) {
                ParseResult.NeedsReview(ReviewReason.MISSING_AMOUNT, PartialTransaction(currency = "USD"))
            } else {
                ParseResult.Success(ParsedTransaction(2_500L, "USD", Direction.OUTFLOW, TransactionType.PURCHASE, now - 1_000L, "Hotel", "Stay", "4242", "HW-REF-1", null))
            }
        }
        val sourcePipe = sourceHarness.pipeline(parser)
        val processedEnv = NotificationEvent(srcId, srcId, "Bank", "Paid $25 Hotel", null, null, "ch", false, false, now - 1_000L, "k-hw-1", 1, null, null)
        val reviewEnv = NotificationEvent(srcId, srcId, "Bank", "FAIL secret body", null, null, "ch", false, false, now - 500L, "k-hw-2", 2, null, null)
        val processedEventId = sourcePipe.ingest(processedEnv)!!
        sourcePipe.ingest(reviewEnv)!!

        val capturedTx = db.transactionDao().findByEventId(processedEventId)!!
        val refundId = db.transactionDao().insert(
            TransactionEntity(accountId = acc1, sourceId = null, eventId = null, amountMinor = 500L, currency = "USD", direction = "INFLOW", transactionType = "REFUND", merchantId = capturedTx.merchantId, description = "Refund", note = null, occurredAt = now, categoryId = childCat, categorySource = "USER", appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = capturedTx.id, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        val outId = db.transactionDao().insert(
            TransactionEntity(accountId = acc1, sourceId = null, eventId = null, amountMinor = 10_200L, currency = "USD", direction = "OUTFLOW", transactionType = "TRANSFER", merchantId = null, description = "Topup", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        val inId = db.transactionDao().insert(
            TransactionEntity(accountId = acc2, sourceId = null, eventId = null, amountMinor = 10_000L, currency = "USD", direction = "INFLOW", transactionType = "TRANSFER", merchantId = null, description = "Topup", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        db.transferMatchDao().insert(TransferMatchEntity(outgoingTransactionId = outId, incomingTransactionId = inId, origin = "MANUAL", confidence = null, status = "CONFIRMED", feeMinor = 200L, createdAt = now))
        val tagId = db.tagDao().insertTag(TagEntity(name = "work", createdAt = now))
        db.tagDao().insertTransactionTag(TransactionTagEntity(capturedTx.id, tagId))
        db.transactionRuleDao().insert(
            TransactionRuleEntity(name = "Hotel Rule", accountId = acc1, categoryId = childCat, conditionJson = LocalJson.encodeRuleCondition("hotel", null, acc1, Direction.OUTFLOW), actionJson = LocalJson.encodeRuleAction(childCat), createdAt = now, updatedAt = now),
        )
        db.budgetDao().insert(BudgetEntity(categoryId = childCat, accountId = acc1, limitMinor = 50_000L, period = "MONTHLY", isActive = true, createdAt = now, updatedAt = now))
        db.captureGapDao().insert(CaptureGapEntity(startedAt = now - 5_000L, endedAt = now - 2_000L, reason = CaptureGapReason.ACCESS_REVOKED.name))

        val functionalConfig = BackupCryptoConfig(memoryKiB = 4_096, iterations = 1)
        val sourceBackup = BackupService(sourceHarness.holder, sourceHarness.settingsStore, sourceHarness.allowlistCache, sourceHarness.captureStatusTracker, sourceHarness.clock, sourceHarness.logger, functionalConfig)
        val out = ByteArrayOutputStream()
        var exportedArchive = ByteArray(0)
        val verification = sourceBackup.exportEncryptedBackup(
            passphrase = "StrongHardwarePass1!",
            outputStream = out,
            verifyStreamOpener = { ByteArrayInputStream(out.toByteArray()) },
        )
        exportedArchive = out.toByteArray()
        assertThat(verification.verified).isTrue()
        assertThat(verification.transactionCount).isEqualTo(4)

        val decryptedJson = BackupCryptoContainer.decrypt(exportedArchive, "StrongHardwarePass1!")
        assertThat(decryptedJson).doesNotContain("FAIL secret body")
        assertThat(decryptedJson).doesNotContain("NEEDS_REVIEW")
        assertThat(decryptedJson).doesNotContain("Paid $25 Hotel")

        // Target DB pre-seeded to assert failed restores write zero partial changes
        val targetDb = targetHarness.db()
        targetDb.accountDao().insert(AccountEntity(name = "KeepIntact", provider = "Cash", accountType = "CASH", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now))
        val targetBackup = BackupService(targetHarness.holder, targetHarness.settingsStore, targetHarness.allowlistCache, targetHarness.captureStatusTracker, targetHarness.clock, targetHarness.logger, functionalConfig)

        assertThat(runCatching { targetBackup.restoreEncryptedBackup("wrong-pw-123", ByteArrayInputStream(exportedArchive)) }.exceptionOrNull()).isInstanceOf(BackupRestoreException::class.java)
        val tamperedHeader = exportedArchive.copyOf().also { it[10] = (it[10].toInt() xor 0x01).toByte() }
        assertThat(runCatching { targetBackup.restoreEncryptedBackup("StrongHardwarePass1!", ByteArrayInputStream(tamperedHeader)) }.exceptionOrNull()).isInstanceOf(BackupRestoreException::class.java)
        val newerVersion = exportedArchive.copyOf().also { it[4] = 0; it[5] = 99 }
        assertThat(runCatching { targetBackup.restoreEncryptedBackup("StrongHardwarePass1!", ByteArrayInputStream(newerVersion)) }.exceptionOrNull()).isInstanceOf(BackupRestoreException::class.java)
        assertThat(targetDb.accountDao().getAllOnce().single().name).isEqualTo("KeepIntact")

        // Valid restore
        val restored = targetBackup.restoreEncryptedBackup("StrongHardwarePass1!", ByteArrayInputStream(exportedArchive))
        assertThat(restored.restoredTransactions).isEqualTo(4)
        assertThat(restored.restoredAccounts).isEqualTo(2)
        assertThat(restored.preservedEventIdentities).isEqualTo(1)
        assertThat(targetDb.transactionDao().getById(refundId)?.relatedTransactionId).isEqualTo(capturedTx.id)
        assertThat(targetDb.tagDao().getTagsForTransaction(capturedTx.id).map { it.name }).containsExactly("work")
        assertThat(targetDb.budgetDao().getAllOnce()).hasSize(1)
        assertThat(targetDb.captureGapDao().getAllOnce()).isNotEmpty()
        assertThat(targetDb.notificationEventDao().getAllOnce().single().payloadJson).isNull()

        val restoredSettings = targetHarness.settingsStore.current()
        assertThat(restoredSettings.baseCurrency).isEqualTo("USD")
        assertThat(restoredSettings.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(restoredSettings.scheduledBackupFolderUri).isEqualTo("content://tree/backup")

        // Post-restore catch-up scan creates zero duplicates
        assertThat(targetHarness.pipeline(parser).ingest(processedEnv)).isNull()
        assertThat(targetDb.transactionDao().getAllOnce()).hasSize(4)
        }
    }

    @Test
    fun verifiesRetentionPurgeUnderSqlCipherAndCsvFormulaNeutralization() = runBlocking {
        withTimeout(120_000L) {
        val db = sourceHarness.db()
        val now = sourceHarness.clock.now
        val srcId = "pkg.hw.retention"
        db.transactionSourceDao().upsert(TransactionSourceEntity(srcId, srcId, "Ret Bank", true, 1, null, now, now))

        val dayMs = 86_400_000L
        val keepProcessedId = db.notificationEventDao().insertIgnore(
            NotificationEventEntity(sourceId = srcId, packageName = srcId, stableEventKey = "ret-keep", notificationKey = "k1", notificationId = 1, notificationTag = null, postedAt = now, payloadJson = "{\"text\":\"keep\"}", purgeAfter = now + 7 * dayMs, processingStatus = NotificationEventStatus.PROCESSED.name, failureReason = null, reviewDraftJson = null, receivedAt = now, processedAt = now, updatedAt = now),
        )
        val expiredProcessedId = db.notificationEventDao().insertIgnore(
            NotificationEventEntity(sourceId = srcId, packageName = srcId, stableEventKey = "ret-exp", notificationKey = "k2", notificationId = 2, notificationTag = null, postedAt = now, payloadJson = "{\"text\":\"expire\"}", purgeAfter = now + 7 * dayMs, processingStatus = NotificationEventStatus.PROCESSED.name, failureReason = null, reviewDraftJson = null, receivedAt = now, processedAt = now, updatedAt = now),
        )
        val reviewId = db.notificationEventDao().insertIgnore(
            NotificationEventEntity(sourceId = srcId, packageName = srcId, stableEventKey = "ret-rev", notificationKey = "k3", notificationId = 3, notificationTag = null, postedAt = now, payloadJson = "{\"text\":\"review\"}", purgeAfter = now + 30 * dayMs, processingStatus = NotificationEventStatus.NEEDS_REVIEW.name, failureReason = null, reviewDraftJson = null, receivedAt = now, processedAt = null, updatedAt = now),
        )

        val runner = RetentionPurgeRunner(
            holder = sourceHarness.holder,
            sourceAndEventRepository = SourceAndEventRepositoryImpl(sourceHarness.holder, sourceHarness.clock),
            heartbeatMonitor = sourceHarness.heartbeatMonitor,
            logger = sourceHarness.logger,
            clock = sourceHarness.clock,
        )

        // Day 0: nothing expired yet
        assertThat(runner.run()).isEqualTo(0)

        // Day 8: PROCESSED payloads purged, NEEDS_REVIEW stays
        sourceHarness.clock.advanceBy(8 * dayMs)
        // Update keepProcessedId's purgeAfter to simulate a newer event inside window
        db.notificationEventDao().update(db.notificationEventDao().getById(keepProcessedId)!!.copy(purgeAfter = sourceHarness.clock.now + 2 * dayMs))
        assertThat(runner.run()).isEqualTo(1)
        assertThat(db.notificationEventDao().getById(expiredProcessedId)?.payloadJson).isNull()
        assertThat(db.notificationEventDao().getById(keepProcessedId)?.payloadJson).contains("keep")
        assertThat(db.notificationEventDao().getById(reviewId)?.payloadJson).contains("review")

        // Day 31: NEEDS_REVIEW expires too
        sourceHarness.clock.advanceBy(23 * dayMs)
        assertThat(runner.run()).isEqualTo(2)
        assertThat(db.notificationEventDao().getById(reviewId)?.payloadJson).isNull()

        // CSV formula neutralization check (DATA-11)
        val accountRepo = AccountRepositoryImpl(sourceHarness.holder, sourceHarness.clock, sourceHarness.captureStatusTracker.isListenerConnected, DefaultParserRegistry(emptySet())) { true }
        val txRepo = TransactionRepositoryImpl(sourceHarness.holder, sourceHarness.settingsStore, sourceHarness.clock)
        val catRepo = CategoryRepositoryImpl(sourceHarness.holder, sourceHarness.clock)
        val accId = accountRepo.create(wtrack.app.domain.repository.CreateAccountRequest("=SUM(A1)", "+EvilBank", wtrack.app.domain.model.AccountType.BANK, "IDR"))
        txRepo.insertManual(wtrack.app.domain.repository.CreateManualTransactionRequest(accId, 5_000_00L, "IDR", Direction.OUTFLOW, wtrack.app.domain.model.DomainTransactionType.PURCHASE, now, description = "@cmd", note = "-1+1"))
        val csvOut = ByteArrayOutputStream()
        val count = CsvExporter().writeTransactionsCsv(csvOut, txRepo.getAllOnce(), accountRepo.getAllOnce(), catRepo.getAllOnce())
        assertThat(count).isEqualTo(1)
        val csvText = String(csvOut.toByteArray(), StandardCharsets.UTF_8)
        assertThat(csvText).contains("'=SUM(A1)")
        assertThat(csvText).contains("'@cmd")
        assertThat(csvText).contains("'-1+1")
        }
    }
}
