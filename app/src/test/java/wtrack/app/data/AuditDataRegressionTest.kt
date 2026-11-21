package wtrack.app.data

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wtrack.app.data.backup.BackupCryptoConfig
import wtrack.app.data.backup.BackupCryptoContainer
import wtrack.app.data.backup.BackupService
import wtrack.app.data.backup.CsvExporter
import wtrack.app.data.backup.WtrackBackupAndDiagnosticsPort
import wtrack.app.data.crypto.DatabaseKeyManager
import wtrack.app.data.crypto.EncryptedOpenHelperFactoryProvider
import wtrack.app.data.crypto.WrappedKeyStore
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.ingestion.CaptureStatusTracker
import wtrack.app.data.ingestion.InMemoryAesGcmCipher
import wtrack.app.data.ingestion.IngestionTestHarness
import wtrack.app.data.ingestion.StubTransactionParser
import wtrack.app.data.ingestion.sampleEnvelope
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.entity.TransferMatchEntity
import wtrack.app.data.repository.AccountRepositoryImpl
import wtrack.app.data.repository.CategoryRepositoryImpl
import wtrack.app.data.repository.DatabaseHealthRepositoryImpl
import wtrack.app.data.repository.NeedsReviewRepositoryImpl
import wtrack.app.data.repository.SourceAndEventRepositoryImpl
import wtrack.app.data.repository.TransactionRepositoryImpl
import wtrack.app.domain.exception.TransferPairEditBlockedException
import wtrack.app.domain.exception.ValidationException
import wtrack.app.domain.model.CaptureGapReason
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.LockedReason
import wtrack.app.domain.parser.DefaultParserRegistry
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.SaveReviewItemRequest
import wtrack.app.domain.repository.UpdateTransactionRequest
import wtrack.integration.Direction
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionType

/**
 * Audit regression tests covering critical & high-severity defects identified and fixed during
 * the independent engineering audit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuditDataRegressionTest {

    private lateinit var harness: IngestionTestHarness
    private val fastArgonConfig = BackupCryptoConfig(
        kdfId = BackupCryptoContainer.KDF_ARGON2ID,
        memoryKiB = 4_096,
        iterations = 1,
        parallelism = 1,
    )

    @Before
    fun setUp() {
        harness = IngestionTestHarness()
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    @Test
    fun upsertSourcePreservesAccountSourcesEventsAndTransactionsWithoutFkViolation() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val srcId = "pkg.multi.bank"
        val sourceRepo = SourceAndEventRepositoryImpl(harness.holder, harness.clock)

        sourceRepo.upsertSource(srcId, "Bank One", isAllowlisted = true, parserVersion = 1)
        val acc1 = db.accountDao().insert(
            AccountEntity(name = "Pocket 1", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        val acc2 = db.accountDao().insert(
            AccountEntity(name = "Pocket 2", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        db.accountSourceDao().insert(AccountSourceEntity(accountId = acc1, sourceId = srcId, accountHint = "1111", createdAt = now))

        val parser = StubTransactionParser(srcId, version = 1) {
            ParseResult.Success(ParsedTransaction(50_000_00L, "IDR", Direction.OUTFLOW, TransactionType.PURCHASE, now, "Shop", "Desc", "1111", "REF-1", null))
        }
        val pipeline = harness.pipeline(parser)
        harness.allowlistCache.refreshSnapshotNow()
        val eventId = pipeline.ingest(sampleEnvelope(sourceId = srcId))!!
        val txBefore = db.transactionDao().findByEventId(eventId)!!
        assertThat(txBefore.sourceId).isEqualTo(srcId)

        // Upserting the same source when linking a 2nd account must NOT fail FK RESTRICT,
        // must NOT cascade-delete account_sources, and must NOT null out transactions.source_id.
        sourceRepo.upsertSource(srcId, "Bank One Renamed", isAllowlisted = true, parserVersion = 1)
        db.accountSourceDao().insert(AccountSourceEntity(accountId = acc2, sourceId = srcId, accountHint = "2222", createdAt = now))

        assertThat(db.accountSourceDao().getForSource(srcId)).hasSize(2)
        assertThat(db.notificationEventDao().getById(eventId)).isNotNull()
        assertThat(db.transactionDao().getById(txBefore.id)?.sourceId).isEqualTo(srcId)
        assertThat(db.transactionSourceDao().getById(srcId)?.displayName).isEqualTo("Bank One Renamed")
    }

    @Test
    fun transferLegEditRecomputesFeeWhenValidAndBlocksWhenInvalid() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val acc1 = db.accountDao().insert(AccountEntity(name = "Bank A", provider = "A", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now))
        val acc2 = db.accountDao().insert(AccountEntity(name = "Wallet B", provider = "B", accountType = "EWALLET", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now))
        val acc3 = db.accountDao().insert(AccountEntity(name = "Bank C", provider = "C", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now))

        val outId = db.transactionDao().insert(
            TransactionEntity(accountId = acc1, sourceId = null, eventId = null, amountMinor = 100_000_00L, currency = "IDR", direction = "OUTFLOW", transactionType = "TRANSFER", merchantId = null, description = "Topup", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = "{\"amountMinor\":10000000}", providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        val inId = db.transactionDao().insert(
            TransactionEntity(accountId = acc2, sourceId = null, eventId = null, amountMinor = 100_000_00L, currency = "IDR", direction = "INFLOW", transactionType = "TRANSFER", merchantId = null, description = "Topup", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        val pairId = db.transferMatchDao().insert(
            TransferMatchEntity(outgoingTransactionId = outId, incomingTransactionId = inId, origin = "MANUAL", confidence = null, status = "CONFIRMED", feeMinor = 0L, createdAt = now),
        )

        val txRepo = TransactionRepositoryImpl(harness.holder, harness.settingsStore, harness.clock)

        // Valid edit: increase outgoing amount -> fee recomputed to Rp2.500 and move outgoing leg to distinct account C
        txRepo.update(
            UpdateTransactionRequest(
                id = outId,
                accountId = acc3,
                amountMinor = 102_500_00L,
                currency = "IDR",
                direction = Direction.OUTFLOW,
                transactionType = DomainTransactionType.TRANSFER,
                occurredAt = now,
            ),
        )
        assertThat(db.transferMatchDao().getById(pairId)?.feeMinor).isEqualTo(250_000L)
        assertThat(db.transactionDao().getById(outId)?.accountId).isEqualTo(acc3)
        assertThat(db.transactionDao().getById(outId)?.originalJson).isEqualTo("{\"amountMinor\":10000000}")

        // Invalid edit: move outgoing leg to same account as incoming leg -> blocked
        assertThat(
            runCatching {
                txRepo.update(
                    UpdateTransactionRequest(outId, acc2, 102_500_00L, "IDR", Direction.OUTFLOW, DomainTransactionType.TRANSFER, now),
                )
            }.exceptionOrNull(),
        ).isInstanceOf(TransferPairEditBlockedException::class.java)

        // Invalid edit: currency mismatch -> blocked
        assertThat(
            runCatching {
                txRepo.update(
                    UpdateTransactionRequest(outId, acc3, 102_500_00L, "USD", Direction.OUTFLOW, DomainTransactionType.TRANSFER, now),
                )
            }.exceptionOrNull(),
        ).isInstanceOf(TransferPairEditBlockedException::class.java)
    }

    @Test
    fun restoreFromLockedDatabaseRejectsWrongPassphraseWithoutWipingAndRecoversWithValidPassphrase() = runTest {
        val srcDb = harness.db()
        val now = harness.clock.now
        srcDb.accountDao().insert(
            AccountEntity(name = "Backup Account", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = 5_000_000L, initialBalanceAt = now, lastReconciledAt = now, createdAt = now, updatedAt = now),
        )
        val sourceBackup = BackupService(
            harness.holder, harness.settingsStore, harness.allowlistCache,
            harness.captureStatusTracker, harness.clock, harness.logger, fastArgonConfig,
        )
        val out = ByteArrayOutputStream()
        sourceBackup.exportEncryptedBackup("strong-backup-pass", out)
        val archiveBytes = out.toByteArray()

        // Construct a separate target holder whose DB file exists on disk while wrapped key is MISSING -> Locked!
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = Files.createTempDirectory("wtrack_locked_restore").toFile()
        val lockedDbName = "wtrack_locked_${UUID.randomUUID()}.db"
        val lockedDbFile = context.getDatabasePath(lockedDbName)
        lockedDbFile.parentFile?.mkdirs()
        val sentinelBytes = "ORIGINAL_LOCKED_ENCRYPTED_DB_BYTES".toByteArray()
        lockedDbFile.writeBytes(sentinelBytes)

        try {
            val lockedKeyManager = DatabaseKeyManager(
                dbFile = lockedDbFile,
                wrappedKeyStore = WrappedKeyStore(File(tempDir, "missing_key.bin")),
                wrappingCipher = InMemoryAesGcmCipher(),
            )
            val lockedHolder = WtrackDatabaseHolder(
                context = context,
                keyManager = lockedKeyManager,
                helperFactoryProvider = EncryptedOpenHelperFactoryProvider { FrameworkSQLiteOpenHelperFactory() },
                databaseName = lockedDbName,
            )
            assertThat(lockedHolder.healthState.value).isInstanceOf(DatabaseHealthState.Locked::class.java)
            assertThat((lockedHolder.healthState.value as DatabaseHealthState.Locked).reason).isEqualTo(LockedReason.KEY_MISSING)

            val lockedTracker = CaptureStatusTracker(context, lockedHolder, harness.clock, harness.logger) { false }
            val lockedBackup = BackupService(
                lockedHolder, harness.settingsStore, harness.allowlistCache,
                lockedTracker, harness.clock, harness.logger, fastArgonConfig,
            )

            // Wrong passphrase MUST fail without touching the preserved locked DB file on disk!
            assertThat(
                runCatching { lockedBackup.restoreEncryptedBackup("wrong-password", ByteArrayInputStream(archiveBytes)) }.isFailure,
            ).isTrue()
            assertThat(lockedHolder.healthState.value).isInstanceOf(DatabaseHealthState.Locked::class.java)
            assertThat(lockedDbFile.readBytes()).isEqualTo(sentinelBytes)

            // Valid passphrase cleanly reinitializes storage, transitions to Ready, and restores data!
            val restored = lockedBackup.restoreEncryptedBackup("strong-backup-pass", ByteArrayInputStream(archiveBytes))
            assertThat(restored.restoredAccounts).isEqualTo(1)
            assertThat(lockedHolder.healthState.value).isEqualTo(DatabaseHealthState.Ready)
            assertThat(lockedHolder.requireDatabase().accountDao().getAllOnce().single().name).isEqualTo("Backup Account")

            runCatching { lockedHolder.requireDatabase().close() }
        } finally {
            listOf(lockedDbFile, File("${lockedDbFile.path}-wal"), File("${lockedDbFile.path}-shm")).forEach { it.delete() }
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun repositoryFlowsRebindAutomaticallyAfterWipeAndCsvIncludesMerchantName() = runTest {
        val accountRepo = AccountRepositoryImpl(harness.holder, harness.clock, harness.captureStatusTracker.isListenerConnected) { true }
        val txRepo = TransactionRepositoryImpl(harness.holder, harness.settingsStore, harness.clock)
        val catRepo = CategoryRepositoryImpl(harness.holder, harness.clock)
        val healthRepo = DatabaseHealthRepositoryImpl(harness.holder, harness.settingsStore)

        val now = harness.clock.now
        val srcId = "pkg.csv.bank"
        harness.db().transactionSourceDao().upsert(TransactionSourceEntity(srcId, srcId, "CSV Bank", true, 1, null, now, now))
        val accId = accountRepo.create(CreateAccountRequest("Main", "CSV Bank", wtrack.app.domain.model.AccountType.BANK, "IDR"))
        harness.db().accountSourceDao().insert(AccountSourceEntity(accountId = accId, sourceId = srcId, accountHint = null, createdAt = now))
        // Lazy cold-start check without calling refreshSnapshotNow first:
        assertThat(harness.allowlistCache.isAllowlisted(srcId)).isTrue()

        val parser = StubTransactionParser(srcId, version = 1) {
            ParseResult.Success(ParsedTransaction(50_000_00L, "IDR", Direction.OUTFLOW, TransactionType.PURCHASE, now, "Kopi Kenangan", "Latte", null, "CSV-1", null))
        }
        harness.pipeline(parser).ingest(sampleEnvelope(sourceId = srcId))

        // CSV export via port MUST include merchantName ("Kopi Kenangan")!
        val port = WtrackBackupAndDiagnosticsPort(
            backupService = BackupService(harness.holder, harness.settingsStore, harness.allowlistCache, harness.captureStatusTracker, harness.clock, harness.logger, fastArgonConfig),
            csvExporter = CsvExporter(),
            accountRepository = accountRepo,
            transactionRepository = txRepo,
            categoryRepository = catRepo,
            databaseHealthRepository = healthRepo,
            logger = harness.logger,
            allowlistCache = harness.allowlistCache,
        )
        val csvOut = ByteArrayOutputStream()
        assertThat(port.exportCsv(csvOut)).isEqualTo(1)
        assertThat(String(csvOut.toByteArray(), StandardCharsets.UTF_8)).contains("Kopi Kenangan")

        // After wipe, repository flows rebind to the new WtrackDatabase instance cleanly!
        port.wipeAllDataAndReset()
        assertThat(accountRepo.accounts.first()).isEmpty()
        val newAccId = accountRepo.create(CreateAccountRequest("Post-Wipe", "Bank", wtrack.app.domain.model.AccountType.BANK, "IDR"))
        assertThat(accountRepo.accounts.first().single().id).isEqualTo(newAccId)
    }

    @Test
    fun accountFlagsCheckpointDriftResolvesParserRegistryAndNeedsReviewPersistsMerchantAndFingerprint() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        val srcId = "pkg.registered.bank"
        // Source has parserVersion = null in DB yet, but parser IS registered in ParserRegistry!
        db.transactionSourceDao().upsert(TransactionSourceEntity(srcId, srcId, "Registered Bank", true, null, null, now, now))
        val accId = db.accountDao().insert(
            AccountEntity(name = "Primary", provider = "Registered", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        db.accountSourceDao().insert(AccountSourceEntity(accountId = accId, sourceId = srcId, accountHint = null, createdAt = now))

        // Insert two checkpoints with missed transaction drift (-15.000)
        db.transactionDao().insert(
            TransactionEntity(accountId = accId, sourceId = srcId, eventId = null, amountMinor = 10_000_00L, currency = "IDR", direction = "INFLOW", transactionType = "OTHER", merchantId = null, description = "CP1", note = null, occurredAt = now - 2_000L, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = 1, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = 100_000_00L, createdAt = now - 2_000L, updatedAt = now - 2_000L),
        )
        db.transactionDao().insert(
            TransactionEntity(accountId = accId, sourceId = srcId, eventId = null, amountMinor = 20_000_00L, currency = "IDR", direction = "OUTFLOW", transactionType = "PURCHASE", merchantId = null, description = "CP2", note = null, occurredAt = now - 1_000L, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = 1, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = 65_000_00L, createdAt = now - 1_000L, updatedAt = now - 1_000L),
        )

        val stubParser = StubTransactionParser(srcId, version = 2) {
            ParseResult.NeedsReview(ReviewReason.MISSING_AMOUNT, PartialTransaction(currency = "IDR", merchantRaw = "Toko Buku #12"))
        }
        val accountRepo = AccountRepositoryImpl(
            holder = harness.holder,
            clock = harness.clock,
            listenerConnectedFlow = harness.captureStatusTracker.isListenerConnected,
            parserRegistry = DefaultParserRegistry(setOf(stubParser)),
            permissionGrantedProvider = { true },
        )
        val accState = accountRepo.accounts.first().single { it.id == accId }
        assertThat(accState.captureStatus).isEqualTo(CaptureStatus.DISCONNECTED) // NOT UNSUPPORTED!
        assertThat(accState.hasPossibleGap).isTrue() // Flagged from ACC-07 checkpoint drift!

        // NeedsReview resolution rejects OUTFLOW+REFUND and persists merchantId + fingerprint when valid
        harness.allowlistCache.refreshSnapshotNow()
        val reviewEventId = harness.pipeline(stubParser).ingest(sampleEnvelope(sourceId = srcId))!!
        val reviewRepo = NeedsReviewRepositoryImpl(harness.holder, harness.settingsStore, harness.clock)

        assertThat(
            runCatching {
                reviewRepo.saveResolved(
                    reviewEventId,
                    SaveReviewItemRequest(accId, 50_000_00L, "IDR", Direction.OUTFLOW, DomainTransactionType.REFUND, now, "Bad"),
                )
            }.exceptionOrNull(),
        ).isInstanceOf(ValidationException::class.java)

        val savedTxId = reviewRepo.saveResolved(
            reviewEventId,
            SaveReviewItemRequest(accId, 50_000_00L, "IDR", Direction.OUTFLOW, DomainTransactionType.PURCHASE, now, "Book"),
        )
        val savedTx = db.transactionDao().getById(savedTxId)!!
        assertThat(savedTx.merchantId).isNotNull()
        assertThat(savedTx.fingerprint).isNotNull()
    }

    @Test
    fun captureStatusTrackerDetectsRevocationAndClosesGapAcrossFreshProcessRestart() = runTest {
        val db = harness.db()
        val now = harness.clock.now
        db.transactionSourceDao().upsert(TransactionSourceEntity("pkg.a", "pkg.a", "A", true, 1, null, now, now))

        // Fresh tracker instance (simulating cold start after OS kill while permission was revoked)
        val trackerAfterRevoke = CaptureStatusTracker(harness.context, harness.holder, harness.clock, harness.logger) { false }
        trackerAfterRevoke.onPermissionStateChecked()
        assertThat(db.captureGapDao().getLatestOpen()?.reason).isEqualTo(CaptureGapReason.ACCESS_REVOKED.name)

        // Fresh tracker instance after user re-grants permission and restarts app
        harness.clock.advanceBy(10_000L)
        val trackerAfterRestore = CaptureStatusTracker(harness.context, harness.holder, harness.clock, harness.logger) { true }
        trackerAfterRestore.onPermissionStateChecked()
        assertThat(db.captureGapDao().getLatestOpen()).isNull()
    }
}
