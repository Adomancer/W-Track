package wtrack.app.data.backup

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wtrack.app.data.ingestion.IngestionTestHarness
import wtrack.app.data.ingestion.StubTransactionParser
import wtrack.app.data.ingestion.sampleEnvelope
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.BudgetEntity
import wtrack.app.data.local.entity.CategoryEntity
import wtrack.app.data.local.entity.CustomParserRuleEntity
import wtrack.app.data.local.entity.TagEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionRuleEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.entity.TransactionTagEntity
import wtrack.app.data.local.entity.TransferMatchEntity
import wtrack.app.data.local.mapper.LocalJson
import wtrack.app.domain.exception.BackupRestoreException
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.model.ThemeMode
import wtrack.app.domain.repository.BackupReminderEvaluator
import wtrack.integration.Direction
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupContainerAndRestoreTest {

    private lateinit var sourceHarness: IngestionTestHarness
    private lateinit var restoreHarness: IngestionTestHarness

    private val fastArgonConfig = BackupCryptoConfig(
        kdfId = BackupCryptoContainer.KDF_ARGON2ID,
        memoryKiB = 4_096,
        iterations = 1,
        parallelism = 1,
    )

    @Before
    fun setUp() {
        sourceHarness = IngestionTestHarness()
        restoreHarness = IngestionTestHarness(initialPermissionGranted = false)
    }

    @After
    fun tearDown() {
        sourceHarness.tearDown()
        restoreHarness.tearDown()
    }

    @Test
    fun encryptsWithArgon2idAndPbkdf2RejectsWrongPassphraseAndHeaderOrCiphertextTampering() {
        val sampleJson = """{"hello":"world","amount":5000000}"""
        val passphrase = "correct horse battery staple"

        val argonBytes = BackupCryptoContainer.encrypt(sampleJson, passphrase, fastArgonConfig)
        assertThat(BackupCryptoContainer.decrypt(argonBytes, passphrase)).isEqualTo(sampleJson)

        val pbkdf2Bytes = BackupCryptoContainer.encrypt(
            sampleJson,
            passphrase,
            BackupCryptoConfig(kdfId = BackupCryptoContainer.KDF_PBKDF2_SHA256, memoryKiB = 0, iterations = 1_000, parallelism = 0),
        )
        assertThat(BackupCryptoContainer.decrypt(pbkdf2Bytes, passphrase)).isEqualTo(sampleJson)

        // Wrong passphrase throws BackupRestoreException
        assertThat(
            runCatching { BackupCryptoContainer.decrypt(argonBytes, "wrong passphrase") }.exceptionOrNull(),
        ).isInstanceOf(BackupRestoreException::class.java)

        // Short passphrase rejected on encrypt
        assertThat(
            runCatching { BackupCryptoContainer.encrypt(sampleJson, "short", fastArgonConfig) }.exceptionOrNull(),
        ).isInstanceOf(BackupRestoreException::class.java)

        // Tamper reserved flags byte inside authenticated header (AAD check)
        val headerTampered = argonBytes.copyOf()
        headerTampered[headerTampered.size / 4] = (headerTampered[headerTampered.size / 4].toInt() xor 0x01).toByte()
        assertThat(
            runCatching { BackupCryptoContainer.decrypt(headerTampered, passphrase) }.exceptionOrNull(),
        ).isInstanceOf(BackupRestoreException::class.java)

        // Tamper last byte (ciphertext / GCM tag)
        val cipherTampered = argonBytes.copyOf()
        cipherTampered[cipherTampered.lastIndex] = (cipherTampered.last().toInt() xor 0xFF).toByte()
        assertThat(
            runCatching { BackupCryptoContainer.decrypt(cipherTampered, passphrase) }.exceptionOrNull(),
        ).isInstanceOf(BackupRestoreException::class.java)

        // Future backup format version rejected explicitly (byte 4..5 uint16 big endian = 99)
        val newerVersion = argonBytes.copyOf()
        newerVersion[4] = 0
        newerVersion[5] = 99
        val versionErr = runCatching { BackupCryptoContainer.decrypt(newerVersion, passphrase) }.exceptionOrNull()
        assertThat(versionErr).isInstanceOf(BackupRestoreException::class.java)
        assertThat(versionErr!!.message).contains("newer")
    }

    @Test
    fun fullBackupExcludesPayloadsAndNeedsReviewRestoresAtomicallyAndPreventsCatchUpDuplicates() = runTest {
        val db = sourceHarness.db()
        val now = sourceHarness.clock.now
        val srcId = "pkg.backup.bank"
        sourceHarness.settingsStore.setBaseCurrency("USD")
        sourceHarness.settingsStore.setThemeMode(ThemeMode.DARK)

        db.transactionSourceDao().upsert(TransactionSourceEntity(srcId, srcId, "Backup Bank", true, 2, null, now, now))
        val acc1 = db.accountDao().insert(AccountEntity(name = "Checking", provider = "Bank", accountType = "BANK", currency = "USD", initialBalanceMinor = 500_00L, initialBalanceAt = now - 10_000L, lastReconciledAt = null, createdAt = now, updatedAt = now))
        val acc2 = db.accountDao().insert(AccountEntity(name = "Savings", provider = "Bank", accountType = "BANK", currency = "USD", initialBalanceMinor = 0L, initialBalanceAt = now - 10_000L, lastReconciledAt = null, createdAt = now, updatedAt = now))
        db.accountSourceDao().insert(AccountSourceEntity(accountId = acc1, sourceId = srcId, accountHint = "1111", createdAt = now))

        // Nested custom parent/child categories
        val parentCatId = db.categoryDao().insert(CategoryEntity(name = "Travel", systemKey = null, directionScope = "OUTFLOW", parentId = null, createdAt = now))
        val childCatId = db.categoryDao().insert(CategoryEntity(name = "Flights", systemKey = null, directionScope = "OUTFLOW", parentId = parentCatId, createdAt = now))

        // Ingest 1 real processed tx + 1 NeedsReview event (which MUST be excluded from backup!)
        sourceHarness.allowlistCache.refreshSnapshotNow()
        val parser = StubTransactionParser(srcId, version = 2) { env ->
            if (env.text.contains("BAD")) {
                ParseResult.NeedsReview(ReviewReason.MISSING_AMOUNT, PartialTransaction(currency = "USD"))
            } else {
                ParseResult.Success(ParsedTransaction(25_00L, "USD", Direction.OUTFLOW, TransactionType.PURCHASE, now - 1_000L, "Airline", "Flight ticket", "1111", "REF-FLY-1", null))
            }
        }
        val sourcePipeline = sourceHarness.pipeline(parser)
        val processedEnv = sampleEnvelope(sourceId = srcId, text = "Debit $25 Airline", notificationId = 1, notificationKey = "k-fly")
        val reviewEnv = sampleEnvelope(sourceId = srcId, text = "BAD payload secret text", notificationId = 2, notificationKey = "k-bad")
        val processedEventId = sourcePipeline.ingest(processedEnv)!!
        sourcePipeline.ingest(reviewEnv)!!

        val capturedTx = db.transactionDao().findByEventId(processedEventId)!!
        // Linked refund referencing capturedTx via relatedTransactionId + tag + transfer + rule + budget
        val refundTxId = db.transactionDao().insert(
            TransactionEntity(accountId = acc1, sourceId = null, eventId = null, amountMinor = 5_00L, currency = "USD", direction = "INFLOW", transactionType = "REFUND", merchantId = capturedTx.merchantId, description = "Partial refund", note = "refunded", occurredAt = now, categoryId = childCatId, categorySource = "USER", appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = capturedTx.id, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        val outTransferId = db.transactionDao().insert(
            TransactionEntity(accountId = acc1, sourceId = null, eventId = null, amountMinor = 10_200L, currency = "USD", direction = "OUTFLOW", transactionType = "TRANSFER", merchantId = null, description = "Save", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        val inTransferId = db.transactionDao().insert(
            TransactionEntity(accountId = acc2, sourceId = null, eventId = null, amountMinor = 10_000L, currency = "USD", direction = "INFLOW", transactionType = "TRANSFER", merchantId = null, description = "Save", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        db.transferMatchDao().insert(TransferMatchEntity(outgoingTransactionId = outTransferId, incomingTransactionId = inTransferId, origin = "MANUAL", confidence = null, status = "CONFIRMED", feeMinor = 200L, createdAt = now))
        val tagId = db.tagDao().insertTag(TagEntity(name = "business", createdAt = now))
        db.tagDao().insertTransactionTag(TransactionTagEntity(capturedTx.id, tagId))
        db.transactionRuleDao().insert(
            TransactionRuleEntity(
                name = "Airline Rule", accountId = acc1, categoryId = childCatId,
                conditionJson = LocalJson.encodeRuleCondition("airline", null, acc1, Direction.OUTFLOW),
                actionJson = LocalJson.encodeRuleAction(childCatId), createdAt = now, updatedAt = now,
            ),
        )
        db.budgetDao().insert(BudgetEntity(categoryId = childCatId, accountId = acc1, limitMinor = 100_00L, period = "MONTHLY", isActive = true, createdAt = now, updatedAt = now))
        db.customParserRuleDao().insert(
            CustomParserRuleEntity(
                sourceId = "id.co.seabank.id",
                name = "SeaBank Out",
                titleContains = null,
                templatePattern = "Pembayaran {AMOUNT} ke {MERCHANT} berhasil",
                direction = Direction.OUTFLOW.name,
                transactionType = "PURCHASE",
                currency = "IDR",
                numberLocale = "INDONESIAN",
                accountHint = null,
                enabled = true,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val sourceBackupService = BackupService(
            sourceHarness.holder, sourceHarness.settingsStore, sourceHarness.allowlistCache,
            sourceHarness.captureStatusTracker, sourceHarness.clock, sourceHarness.logger, fastArgonConfig,
        )
        val passphrase = "strong-test-passphrase!"
        val outBytes = ByteArrayOutputStream()
        val verify = sourceBackupService.exportEncryptedBackup(passphrase, outBytes)
        val archiveBytes = outBytes.toByteArray()

        assertThat(verify.verified).isTrue()
        assertThat(verify.transactionCount).isEqualTo(4)
        assertThat(verify.accountCount).isEqualTo(2)

        // Inspect decrypted JSON to ensure zero raw payloads or NEEDS_REVIEW rows leaked into backup.
        val rawJson = BackupCryptoContainer.decrypt(archiveBytes, passphrase)
        assertThat(rawJson).doesNotContain("BAD payload secret text")
        assertThat(rawJson).doesNotContain("NEEDS_REVIEW")
        assertThat(rawJson).doesNotContain("Debit $25 Airline")

        // Seed existing data inside target restoreHarness to prove failed restore leaves it completely intact!
        val restoreDb = restoreHarness.db()
        restoreDb.accountDao().insert(AccountEntity(name = "PreExisting", provider = "Cash", accountType = "CASH", currency = "IDR", initialBalanceMinor = 1_00L, initialBalanceAt = now, lastReconciledAt = null, createdAt = now, updatedAt = now))

        val targetBackupService = BackupService(
            restoreHarness.holder, restoreHarness.settingsStore, restoreHarness.allowlistCache,
            restoreHarness.captureStatusTracker, restoreHarness.clock, restoreHarness.logger, fastArgonConfig,
        )

        // Wrong-passphrase restore must abort cleanly and keep PreExisting account untouched.
        assertThat(
            runCatching { targetBackupService.restoreEncryptedBackup("wrong-pw", ByteArrayInputStream(archiveBytes)) }.isFailure,
        ).isTrue()
        assertThat(restoreDb.accountDao().getAllOnce().single().name).isEqualTo("PreExisting")

        // Valid restore replaces DB atomically
        val restored = targetBackupService.restoreEncryptedBackup(passphrase, ByteArrayInputStream(archiveBytes))
        assertThat(restored.restoredTransactions).isEqualTo(4)
        assertThat(restored.restoredAccounts).isEqualTo(2)
        assertThat(restored.preservedEventIdentities).isEqualTo(1)

        // Verify restored settings & self-referencing refund link & no payloads on restored event identities
        val restoredSettings = restoreHarness.settingsStore.current()
        assertThat(restoredSettings.baseCurrency).isEqualTo("USD")
        assertThat(restoredSettings.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(restoreHarness.allowlistCache.isAllowlisted(srcId)).isTrue()
        assertThat(restoreDb.transactionDao().getById(refundTxId)?.relatedTransactionId).isEqualTo(capturedTx.id)
        assertThat(restoreDb.notificationEventDao().getAllOnce().single().payloadJson).isNull()
        assertThat(restoreDb.customParserRuleDao().getAllOnce()).hasSize(1)
        assertThat(restoreDb.customParserRuleDao().getAllOnce().single().templatePattern)
            .isEqualTo("Pembayaran {AMOUNT} ke {MERCHANT} berhasil")

        // Replaying the exact notification in shade after restore MUST NOT create a duplicate transaction!
        val restoredPipeline = restoreHarness.pipeline(parser)
        val replayResult = restoredPipeline.ingest(processedEnv)
        assertThat(replayResult).isNull()
        assertThat(restoreDb.transactionDao().getAllOnce()).hasSize(4)
    }

    @Test
    fun scheduledBackupConfigPersistsTriggersReminderAndMaintainsBackwardCompatibility() = runTest {
        sourceHarness.db()
        val folderUri = "content://com.android.externalstorage.documents/tree/primary%3ABackups"
        sourceHarness.settingsStore.setScheduledBackupConfig(folderUri, enabled = true, intervalDays = 7)
        val currentSettings = sourceHarness.settingsStore.current()
        assertThat(currentSettings.scheduledBackupFolderUri).isEqualTo(folderUri)
        assertThat(currentSettings.scheduledBackupEnabled).isTrue()
        assertThat(currentSettings.scheduledBackupIntervalDays).isEqualTo(7)

        // BackupReminderEvaluator triggers when scheduled folder is enabled and overdue
        val dueSettings = AppSettings(
            onboardingCompleted = true,
            lastBackupAt = 1_000_000L,
            scheduledBackupFolderUri = folderUri,
            scheduledBackupEnabled = true,
            scheduledBackupIntervalDays = 7,
        )
        assertThat(BackupReminderEvaluator.shouldShowReminder(dueSettings, 1_000_000L + 3L * 86_400_000L)).isFalse()
        assertThat(BackupReminderEvaluator.shouldShowReminder(dueSettings, 1_000_000L + 8L * 86_400_000L)).isTrue()

        // Backward-compatibility check: older v1 backup JSON missing scheduledBackup* fields must restore cleanly
        val legacySnapshotJson = """
            {
              "backupSchemaVersion": 1,
              "roomSchemaVersion": 1,
              "createdAtEpochMillisUtc": 1700000000000,
              "appSettings": {
                "themeMode": "SYSTEM",
                "baseCurrency": "IDR",
                "verificationWindowDays": 7,
                "heartbeatStaleHours": 3,
                "backupReminderDays": 14,
                "backupReminderTxCount": 100,
                "onboardingCompleted": true
              },
              "transactionSources": [],
              "accounts": [],
              "accountSources": [],
              "categories": [],
              "merchants": [],
              "transactionRules": [],
              "transactions": [],
              "tags": [],
              "transactionTags": [],
              "transferMatches": [],
              "captureGaps": [],
              "budgets": [],
              "eventIdentities": []
            }
        """.trimIndent()
        val encryptedLegacy = BackupCryptoContainer.encrypt(legacySnapshotJson, "legacy-passphrase", fastArgonConfig)
        val targetBackupService = BackupService(
            restoreHarness.holder, restoreHarness.settingsStore, restoreHarness.allowlistCache,
            restoreHarness.captureStatusTracker, restoreHarness.clock, restoreHarness.logger, fastArgonConfig,
        )
        targetBackupService.restoreEncryptedBackup("legacy-passphrase", ByteArrayInputStream(encryptedLegacy))
        val restored = restoreHarness.settingsStore.current()
        assertThat(restored.scheduledBackupEnabled).isFalse()
        assertThat(restored.scheduledBackupFolderUri).isNull()
        assertThat(restored.scheduledBackupIntervalDays).isEqualTo(7)
        assertThat(restored.appLockEnabled).isFalse()
        assertThat(restored.appLockTimeoutSeconds).isEqualTo(60)
        assertThat(restored.localAlertsEnabled).isFalse()
    }

    @Test
    fun appLockSettingsPersistThroughEncryptedBackupAndRestore() = runTest {
        sourceHarness.db()
        sourceHarness.settingsStore.setAppLock(enabled = true, timeoutSeconds = 300)
        sourceHarness.settingsStore.setLocalAlertsEnabled(true)
        val out = ByteArrayOutputStream()
        val service = BackupService(
            sourceHarness.holder, sourceHarness.settingsStore, sourceHarness.allowlistCache,
            sourceHarness.captureStatusTracker, sourceHarness.clock, sourceHarness.logger, fastArgonConfig,
        )
        service.exportEncryptedBackup("strong-lock-passphrase", out, verifyStreamOpener = { ByteArrayInputStream(out.toByteArray()) })

        restoreHarness.db()
        val restoreService = BackupService(
            restoreHarness.holder, restoreHarness.settingsStore, restoreHarness.allowlistCache,
            restoreHarness.captureStatusTracker, restoreHarness.clock, restoreHarness.logger, fastArgonConfig,
        )
        restoreService.restoreEncryptedBackup("strong-lock-passphrase", ByteArrayInputStream(out.toByteArray()))
        val restored = restoreHarness.settingsStore.current()
        assertThat(restored.appLockEnabled).isTrue()
        assertThat(restored.appLockTimeoutSeconds).isEqualTo(300)
        assertThat(restored.localAlertsEnabled).isTrue()
    }
}
