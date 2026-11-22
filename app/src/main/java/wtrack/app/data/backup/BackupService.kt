package wtrack.app.data.backup

import java.io.InputStream
import java.io.OutputStream
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import wtrack.app.core.logging.WtrackLogger
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.ingestion.AllowlistCache
import wtrack.app.data.ingestion.CaptureStatusTracker
import wtrack.app.data.ingestion.CustomParserRuleCache
import wtrack.app.data.local.WtrackDatabase
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.BudgetEntity
import wtrack.app.data.local.entity.CaptureGapEntity
import wtrack.app.data.local.entity.CategoryEntity
import wtrack.app.data.local.entity.CustomParserRuleEntity
import wtrack.app.data.local.entity.MerchantEntity
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TagEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionRuleEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.entity.TransactionTagEntity
import wtrack.app.data.local.entity.TransferMatchEntity
import wtrack.app.data.local.preferences.AppSettingsDataStore
import wtrack.app.domain.exception.BackupRestoreException
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.CaptureGapReason
import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.DatabaseHealthState
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.NotificationEventStatus
import wtrack.app.domain.model.ThemeMode
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferOrigin
import wtrack.app.domain.model.TransferStatus
import wtrack.app.domain.repository.BackupVerificationResult
import wtrack.app.domain.repository.RestoreResult
import wtrack.app.domain.time.Clock
import wtrack.integration.Direction

@Serializable
data class BackupSnapshotV1(
    val backupSchemaVersion: Int = 1,
    val roomSchemaVersion: Int = WtrackDatabase.SCHEMA_VERSION,
    val createdAtEpochMillisUtc: Long,
    val appSettings: AppSettingsDto,
    val transactionSources: List<TransactionSourceDto>,
    val accounts: List<AccountDto>,
    val accountSources: List<AccountSourceDto>,
    val categories: List<CategoryDto>,
    val merchants: List<MerchantDto>,
    val transactionRules: List<TransactionRuleDto>,
    val transactions: List<TransactionDto>,
    val tags: List<TagDto>,
    val transactionTags: List<TransactionTagDto>,
    val transferMatches: List<TransferMatchDto>,
    val captureGaps: List<CaptureGapDto>,
    val budgets: List<BudgetDto>,
    val eventIdentities: List<EventIdentityDto>,
    val customParserRules: List<CustomParserRuleDto> = emptyList(),
)

@Serializable
data class CustomParserRuleDto(
    val id: Long,
    val sourceId: String,
    val name: String,
    val titleContains: String? = null,
    val templatePattern: String,
    val direction: String,
    val transactionType: String,
    val currency: String,
    val numberLocale: String,
    val accountHint: String? = null,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AppSettingsDto(
    val themeMode: String,
    val baseCurrency: String,
    val verificationWindowDays: Int,
    val heartbeatStaleHours: Int,
    val backupReminderDays: Int,
    val backupReminderTxCount: Int,
    val onboardingCompleted: Boolean,
    val scheduledBackupFolderUri: String? = null,
    val scheduledBackupEnabled: Boolean = false,
    val scheduledBackupIntervalDays: Int = 7,
    val appLockEnabled: Boolean = false,
    val appLockTimeoutSeconds: Int = 60,
)

@Serializable
data class TransactionSourceDto(
    val id: String,
    val packageName: String,
    val displayName: String,
    val isAllowlisted: Boolean,
    val parserVersion: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AccountDto(
    val id: Long,
    val name: String,
    val provider: String,
    val accountType: String,
    val currency: String,
    val initialBalanceMinor: Long?,
    val initialBalanceAt: Long?,
    val lastReconciledAt: Long?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AccountSourceDto(
    val id: Long,
    val accountId: Long,
    val sourceId: String,
    val accountHint: String?,
    val createdAt: Long,
)

@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    val systemKey: String?,
    val directionScope: String?,
    val parentId: Long?,
    val isSystem: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
)

@Serializable
data class MerchantDto(
    val id: Long,
    val displayName: String,
    val key: String,
    val createdAt: Long,
)

@Serializable
data class TransactionRuleDto(
    val id: Long,
    val name: String,
    val accountId: Long?,
    val categoryId: Long,
    val conditionJson: String,
    val actionJson: String,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class TransactionDto(
    val id: Long,
    val accountId: Long,
    val sourceId: String?,
    val eventId: Long?,
    val amountMinor: Long,
    val currency: String,
    val direction: String,
    val transactionType: String,
    val merchantId: Long?,
    val description: String?,
    val note: String?,
    val occurredAt: Long,
    val categoryId: Long?,
    val categorySource: String?,
    val appliedRuleId: Long?,
    val status: String,
    val fingerprint: String?,
    val parserVersion: Int?,
    val originalJson: String?,
    val providerReference: String?,
    val relatedTransactionId: Long?,
    val reportedBalanceMinor: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class TagDto(val id: Long, val name: String, val createdAt: Long)

@Serializable
data class TransactionTagDto(val transactionId: Long, val tagId: Long)

@Serializable
data class TransferMatchDto(
    val id: Long,
    val outgoingTransactionId: Long,
    val incomingTransactionId: Long,
    val origin: String,
    val confidence: Double?,
    val status: String,
    val feeMinor: Long,
    val createdAt: Long,
)

@Serializable
data class CaptureGapDto(val id: Long, val startedAt: Long, val endedAt: Long?, val reason: String)

@Serializable
data class BudgetDto(
    val id: Long,
    val categoryId: Long,
    val accountId: Long?,
    val limitMinor: Long,
    val period: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class EventIdentityDto(
    val id: Long,
    val sourceId: String,
    val packageName: String,
    val stableEventKey: String,
    val notificationKey: String?,
    val notificationId: Int,
    val notificationTag: String?,
    val postedAt: Long,
    val processingStatus: String,
    val attemptCount: Int,
    val failureReason: String?,
    val receivedAt: Long,
    val processedAt: Long?,
    val updatedAt: Long,
)

/**
 * Coordinates encrypted full backup export, post-write verification (`DATA-13`), and atomic
 * version-checked database restore (`DATA-05`, `DATA-09`, `ADR-006`).
 */
class BackupService(
    private val holder: WtrackDatabaseHolder,
    private val settingsStore: AppSettingsDataStore,
    private val allowlistCache: AllowlistCache,
    private val captureStatusTracker: CaptureStatusTracker,
    private val clock: Clock,
    private val logger: WtrackLogger,
    private val cryptoConfig: BackupCryptoConfig = BackupCryptoConfig(),
    private val customParserRuleCache: CustomParserRuleCache? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    suspend fun exportEncryptedBackup(
        passphrase: String,
        outputStream: OutputStream,
        verifyStreamOpener: (() -> InputStream?)? = null,
    ): BackupVerificationResult {
        val db = holder.requireDatabase()
        val settings = settingsStore.current()
        val now = clock.nowEpochMillis()

        val snapshot = db.withTransaction {
            val allSources = db.transactionSourceDao().getAllOnce()
            val allAccounts = db.accountDao().getAllOnce()
            val allAccountSources = db.accountSourceDao().getAllOnce()
            val allCategories = db.categoryDao().getAllOnce()
            val allMerchants = db.merchantDao().getAllOnce()
            val allRules = db.transactionRuleDao().getAllOnce()
            val allTransactions = db.transactionDao().getAllOnce()
            val allTags = db.tagDao().getAllTagsOnce()
            val allTxTags = db.tagDao().getAllTransactionTagsOnce()
            val allTransfers = db.transferMatchDao().getAllOnce()
            val allGaps = db.captureGapDao().getAllOnce()
            val allBudgets = db.budgetDao().getAllOnce()
            val allCustomParserRules = db.customParserRuleDao().getAllOnce()
            // Exclude NEEDS_REVIEW and unfinished (RECEIVED/PROCESSING/FAILED) rows plus all raw
            // payloads/drafts strictly per DATA-05 / section 8.1 / ADR-006.
            val preservedEvents = db.notificationEventDao().getAllOnce()
                .filter {
                    it.processingStatus == NotificationEventStatus.PROCESSED.name ||
                        it.processingStatus == NotificationEventStatus.IGNORED.name
                }

            BackupSnapshotV1(
                backupSchemaVersion = SUPPORTED_SNAPSHOT_VERSION,
                roomSchemaVersion = WtrackDatabase.SCHEMA_VERSION,
                createdAtEpochMillisUtc = now,
                appSettings = AppSettingsDto(
                    themeMode = settings.themeMode.name,
                    baseCurrency = settings.baseCurrency,
                    verificationWindowDays = settings.verificationWindowDays,
                    heartbeatStaleHours = settings.heartbeatStaleHours,
                    backupReminderDays = settings.backupReminderDays,
                    backupReminderTxCount = settings.backupReminderTxCount,
                    onboardingCompleted = settings.onboardingCompleted,
                    scheduledBackupFolderUri = settings.scheduledBackupFolderUri,
                    scheduledBackupEnabled = settings.scheduledBackupEnabled,
                    scheduledBackupIntervalDays = settings.scheduledBackupIntervalDays,
                    appLockEnabled = settings.appLockEnabled,
                    appLockTimeoutSeconds = settings.appLockTimeoutSeconds,
                ),
                transactionSources = allSources.map {
                    TransactionSourceDto(it.id, it.packageName, it.displayName, it.isAllowlisted, it.parserVersion, it.createdAt, it.updatedAt)
                },
                accounts = allAccounts.map {
                    AccountDto(it.id, it.name, it.provider, it.accountType, it.currency, it.initialBalanceMinor, it.initialBalanceAt, it.lastReconciledAt, it.isActive, it.createdAt, it.updatedAt)
                },
                accountSources = allAccountSources.map {
                    AccountSourceDto(it.id, it.accountId, it.sourceId, it.accountHint, it.createdAt)
                },
                categories = allCategories.map {
                    CategoryDto(it.id, it.name, it.systemKey, it.directionScope, it.parentId, it.isSystem, it.isArchived, it.createdAt)
                },
                merchants = allMerchants.map {
                    MerchantDto(it.id, it.displayName, it.key, it.createdAt)
                },
                transactionRules = allRules.map {
                    TransactionRuleDto(it.id, it.name, it.accountId, it.categoryId, it.conditionJson, it.actionJson, it.enabled, it.createdAt, it.updatedAt)
                },
                transactions = allTransactions.map {
                    TransactionDto(
                        it.id, it.accountId, it.sourceId, it.eventId, it.amountMinor, it.currency, it.direction,
                        it.transactionType, it.merchantId, it.description, it.note, it.occurredAt, it.categoryId,
                        it.categorySource, it.appliedRuleId, it.status, it.fingerprint, it.parserVersion, it.originalJson,
                        it.providerReference, it.relatedTransactionId, it.reportedBalanceMinor, it.createdAt, it.updatedAt,
                    )
                },
                tags = allTags.map { TagDto(it.id, it.name, it.createdAt) },
                transactionTags = allTxTags.map { TransactionTagDto(it.transactionId, it.tagId) },
                transferMatches = allTransfers.map {
                    TransferMatchDto(it.id, it.outgoingTransactionId, it.incomingTransactionId, it.origin, it.confidence, it.status, it.feeMinor, it.createdAt)
                },
                captureGaps = allGaps.map { CaptureGapDto(it.id, it.startedAt, it.endedAt, it.reason) },
                budgets = allBudgets.map { BudgetDto(it.id, it.categoryId, it.accountId, it.limitMinor, it.period, it.isActive, it.createdAt, it.updatedAt) },
                eventIdentities = preservedEvents.map {
                    EventIdentityDto(
                        it.id, it.sourceId, it.packageName, it.stableEventKey, it.notificationKey,
                        it.notificationId, it.notificationTag, it.postedAt, it.processingStatus,
                        it.attemptCount, it.failureReason, it.receivedAt, it.processedAt, it.updatedAt,
                    )
                },
                customParserRules = allCustomParserRules.map {
                    CustomParserRuleDto(
                        it.id, it.sourceId, it.name, it.titleContains, it.templatePattern,
                        it.direction, it.transactionType, it.currency, it.numberLocale,
                        it.accountHint, it.enabled, it.createdAt, it.updatedAt,
                    )
                },
            )
        }

        val encodedJson = json.encodeToString(snapshot)
        val encrypted = BackupCryptoContainer.encrypt(encodedJson, passphrase, cryptoConfig)
        outputStream.use { out ->
            out.write(encrypted)
            out.flush()
        }

        // DATA-13 post-write verification: re-read from SAF if provided, otherwise verify encrypted bytes.
        val bytesToVerify = verifyStreamOpener?.invoke()?.use { it.readBytes() } ?: encrypted
        val decryptedJson = BackupCryptoContainer.decrypt(bytesToVerify, passphrase)
        val decoded = json.decodeFromString<BackupSnapshotV1>(decryptedJson)

        val matchesCounts = decoded.transactions.size == snapshot.transactions.size &&
            decoded.accounts.size == snapshot.accounts.size &&
            decoded.transactionSources.size == snapshot.transactionSources.size
        if (!matchesCounts) {
            throw BackupRestoreException("Backup post-write verification count mismatch.")
        }

        logger.logEvent("BACKUP", "EXPORT_VERIFIED", mapOf("count" to decoded.transactions.size.toString()))
        return BackupVerificationResult(
            transactionCount = decoded.transactions.size,
            accountCount = decoded.accounts.size,
            sourceCount = decoded.transactionSources.size,
            verified = true,
            detail = "Backup verified successfully.",
        )
    }

    suspend fun restoreEncryptedBackup(passphrase: String, inputStream: InputStream): RestoreResult {
        val rawBytes = inputStream.use { it.readBytes() }
        val decryptedJson = BackupCryptoContainer.decrypt(rawBytes, passphrase)
        val snapshot = runCatching {
            json.decodeFromString<BackupSnapshotV1>(decryptedJson)
        }.getOrElse { err ->
            throw BackupRestoreException("Backup JSON structure is corrupted or incompatible.", err)
        }

        if (snapshot.backupSchemaVersion <= 0 || snapshot.backupSchemaVersion > SUPPORTED_SNAPSHOT_VERSION) {
            throw BackupRestoreException(
                "Backup schema version ${snapshot.backupSchemaVersion} is newer than supported version $SUPPORTED_SNAPSHOT_VERSION.",
            )
        }
        if (snapshot.roomSchemaVersion > WtrackDatabase.SCHEMA_VERSION) {
            throw BackupRestoreException(
                "Backup database version ${snapshot.roomSchemaVersion} requires a newer version of W-Track.",
            )
        }

        validateSnapshotInvariants(snapshot)

        // If the local DB is currently locked (ADR-005 recovery path from DatabaseLockedScreen),
        // ONLY reinitialize fresh encrypted storage AFTER the backup has passed full decryption,
        // authentication, version verification, and invariant checks above.
        if (holder.healthState.value is DatabaseHealthState.Locked) {
            holder.wipeAllDataAndReinitialize()
        }

        val db = holder.requireDatabase()
        val orderedCategories = orderCategoriesParentFirst(snapshot.categories)
        val safeEvents = snapshot.eventIdentities
            .filter {
                it.processingStatus == NotificationEventStatus.PROCESSED.name ||
                    it.processingStatus == NotificationEventStatus.IGNORED.name
            }

        db.withTransaction {
            db.transferMatchDao().deleteAll()
            db.tagDao().deleteAllTransactionTags()
            db.budgetDao().deleteAll()
            db.captureGapDao().deleteAll()
            db.transactionDao().deleteAll()
            db.notificationEventDao().deleteAll()
            db.transactionRuleDao().deleteAll()
            db.customParserRuleDao().deleteAll()
            db.accountSourceDao().deleteAll()
            db.tagDao().deleteAllTags()
            db.merchantDao().deleteAll()
            db.categoryDao().deleteAll()
            db.accountDao().deleteAll()
            db.transactionSourceDao().deleteAll()

            db.transactionSourceDao().insertAll(
                snapshot.transactionSources.map {
                    TransactionSourceEntity(
                        id = it.id,
                        packageName = it.packageName,
                        displayName = it.displayName,
                        isAllowlisted = it.isAllowlisted,
                        parserVersion = it.parserVersion,
                        sampleCollectionUntil = null,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                    )
                },
            )
            db.accountDao().insertAll(
                snapshot.accounts.map {
                    AccountEntity(
                        id = it.id,
                        name = it.name,
                        provider = it.provider,
                        accountType = it.accountType,
                        currency = it.currency,
                        initialBalanceMinor = it.initialBalanceMinor,
                        initialBalanceAt = it.initialBalanceAt,
                        lastReconciledAt = it.lastReconciledAt,
                        isActive = it.isActive,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                    )
                },
            )
            orderedCategories.forEach { cat ->
                db.categoryDao().insertAll(
                    listOf(
                        CategoryEntity(
                            id = cat.id,
                            name = cat.name,
                            systemKey = cat.systemKey,
                            directionScope = cat.directionScope,
                            parentId = cat.parentId,
                            isSystem = cat.isSystem,
                            isArchived = cat.isArchived,
                            createdAt = cat.createdAt,
                        ),
                    ),
                )
            }
            db.merchantDao().insertAll(
                snapshot.merchants.map { MerchantEntity(it.id, it.displayName, it.key, it.createdAt) },
            )
            db.tagDao().insertAllTags(
                snapshot.tags.map { TagEntity(it.id, it.name, it.createdAt) },
            )
            db.accountSourceDao().insertAll(
                snapshot.accountSources.map { AccountSourceEntity(it.id, it.accountId, it.sourceId, it.accountHint, it.createdAt) },
            )
            db.transactionRuleDao().insertAll(
                snapshot.transactionRules.map {
                    TransactionRuleEntity(it.id, it.name, it.accountId, it.categoryId, it.conditionJson, it.actionJson, it.enabled, it.createdAt, it.updatedAt)
                },
            )
            db.notificationEventDao().insertAll(
                safeEvents.map {
                    NotificationEventEntity(
                        id = it.id,
                        sourceId = it.sourceId,
                        packageName = it.packageName,
                        stableEventKey = it.stableEventKey,
                        notificationKey = it.notificationKey,
                        notificationId = it.notificationId,
                        notificationTag = it.notificationTag,
                        postedAt = it.postedAt,
                        payloadJson = null,
                        purgeAfter = null,
                        processingStatus = it.processingStatus,
                        attemptCount = it.attemptCount,
                        failureReason = it.failureReason,
                        reviewDraftJson = null,
                        receivedAt = it.receivedAt,
                        processedAt = it.processedAt,
                        updatedAt = it.updatedAt,
                    )
                },
            )

            // Two-pass transaction insert so self-referencing relatedTransactionId never trips FK ordering.
            db.transactionDao().insertAll(
                snapshot.transactions.map {
                    TransactionEntity(
                        id = it.id, accountId = it.accountId, sourceId = it.sourceId, eventId = it.eventId,
                        amountMinor = it.amountMinor, currency = it.currency, direction = it.direction,
                        transactionType = it.transactionType, merchantId = it.merchantId, description = it.description,
                        note = it.note, occurredAt = it.occurredAt, categoryId = it.categoryId,
                        categorySource = it.categorySource, appliedRuleId = it.appliedRuleId, status = it.status,
                        fingerprint = it.fingerprint, parserVersion = it.parserVersion, originalJson = it.originalJson,
                        providerReference = it.providerReference, relatedTransactionId = null,
                        reportedBalanceMinor = it.reportedBalanceMinor, createdAt = it.createdAt, updatedAt = it.updatedAt,
                    )
                },
            )
            snapshot.transactions.filter { it.relatedTransactionId != null }.forEach {
                db.transactionDao().updateRelatedTransaction(it.id, it.relatedTransactionId, it.updatedAt)
            }

            db.captureGapDao().insertAll(snapshot.captureGaps.map { CaptureGapEntity(it.id, it.startedAt, it.endedAt, it.reason) })
            db.budgetDao().insertAll(snapshot.budgets.map { BudgetEntity(it.id, it.categoryId, it.accountId, it.limitMinor, it.period, it.isActive, it.createdAt, it.updatedAt) })
            db.tagDao().insertAllTransactionTags(snapshot.transactionTags.map { TransactionTagEntity(it.transactionId, it.tagId) })
            db.transferMatchDao().insertAll(
                snapshot.transferMatches.map {
                    TransferMatchEntity(it.id, it.outgoingTransactionId, it.incomingTransactionId, it.origin, it.confidence, it.status, it.feeMinor, it.createdAt)
                },
            )
            db.customParserRuleDao().insertAll(
                snapshot.customParserRules.map {
                    CustomParserRuleEntity(
                        id = it.id,
                        sourceId = it.sourceId,
                        name = it.name,
                        titleContains = it.titleContains,
                        templatePattern = it.templatePattern,
                        direction = it.direction,
                        transactionType = it.transactionType,
                        currency = it.currency,
                        numberLocale = it.numberLocale,
                        accountHint = it.accountHint,
                        enabled = it.enabled,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                    )
                },
            )
        }

        settingsStore.replaceAll(
            AppSettings(
                themeMode = runCatching { ThemeMode.valueOf(snapshot.appSettings.themeMode) }.getOrDefault(ThemeMode.SYSTEM),
                baseCurrency = snapshot.appSettings.baseCurrency,
                verificationWindowDays = snapshot.appSettings.verificationWindowDays,
                heartbeatStaleHours = snapshot.appSettings.heartbeatStaleHours,
                lastAliveAt = 0L,
                backupReminderDays = snapshot.appSettings.backupReminderDays,
                backupReminderTxCount = snapshot.appSettings.backupReminderTxCount,
                lastBackupAt = clock.nowEpochMillis(),
                txCountSinceLastBackup = 0,
                backupReminderDismissedAt = 0L,
                onboardingCompleted = true,
                scheduledBackupFolderUri = snapshot.appSettings.scheduledBackupFolderUri,
                scheduledBackupEnabled = snapshot.appSettings.scheduledBackupEnabled &&
                    !snapshot.appSettings.scheduledBackupFolderUri.isNullOrBlank(),
                scheduledBackupIntervalDays = snapshot.appSettings.scheduledBackupIntervalDays,
                appLockEnabled = snapshot.appSettings.appLockEnabled,
                appLockTimeoutSeconds = snapshot.appSettings.appLockTimeoutSeconds,
            ),
        )
        allowlistCache.refreshSnapshotNow()
        customParserRuleCache?.refreshSnapshotNow()
        captureStatusTracker.onPermissionStateChecked()
        logger.logEvent("BACKUP", "RESTORE_COMPLETED", mapOf("count" to snapshot.transactions.size.toString()))

        return RestoreResult(
            restoredTransactions = snapshot.transactions.size,
            restoredAccounts = snapshot.accounts.size,
            restoredSources = snapshot.transactionSources.size,
            preservedEventIdentities = safeEvents.size,
        )
    }

    private fun validateSnapshotInvariants(snapshot: BackupSnapshotV1) {
        snapshot.accounts.forEach { acc ->
            runCatching { AccountType.valueOf(acc.accountType) }.getOrElse {
                throw BackupRestoreException("Backup contains invalid account type: ${acc.accountType}")
            }
            if ((acc.initialBalanceMinor == null) != (acc.initialBalanceAt == null)) {
                throw BackupRestoreException("Backup contains invalid initial balance anchor on account ${acc.id}.")
            }
        }
        snapshot.transactions.forEach { tx ->
            if (tx.amountMinor <= 0L) throw BackupRestoreException("Backup contains non-positive transaction amount.")
            runCatching { Direction.valueOf(tx.direction) }.getOrElse {
                throw BackupRestoreException("Backup contains invalid transaction direction: ${tx.direction}")
            }
            runCatching { DomainTransactionType.valueOf(tx.transactionType) }.getOrElse {
                throw BackupRestoreException("Backup contains invalid transaction type: ${tx.transactionType}")
            }
            runCatching { TransactionStatus.valueOf(tx.status) }.getOrElse {
                throw BackupRestoreException("Backup contains invalid transaction status: ${tx.status}")
            }
        }
        snapshot.transferMatches.forEach { pair ->
            if (pair.feeMinor < 0L || pair.outgoingTransactionId == pair.incomingTransactionId) {
                throw BackupRestoreException("Backup contains invalid transfer match invariants.")
            }
            runCatching { TransferOrigin.valueOf(pair.origin) }.getOrElse {
                throw BackupRestoreException("Backup contains invalid transfer origin: ${pair.origin}")
            }
            runCatching { TransferStatus.valueOf(pair.status) }.getOrElse {
                throw BackupRestoreException("Backup contains invalid transfer status: ${pair.status}")
            }
        }
        snapshot.captureGaps.forEach { gap ->
            runCatching { CaptureGapReason.valueOf(gap.reason) }.getOrElse {
                throw BackupRestoreException("Backup contains invalid capture gap reason: ${gap.reason}")
            }
        }
        snapshot.budgets.forEach { budget ->
            if (budget.limitMinor <= 0L) throw BackupRestoreException("Backup contains non-positive budget limit.")
        }
        snapshot.customParserRules.forEach { rule ->
            if (rule.sourceId.isBlank() || rule.templatePattern.isBlank()) {
                throw BackupRestoreException("Backup contains invalid custom parser rule.")
            }
            runCatching { Direction.valueOf(rule.direction) }.getOrElse {
                throw BackupRestoreException("Backup contains invalid custom rule direction: ${rule.direction}")
            }
            runCatching { DomainTransactionType.valueOf(rule.transactionType) }.getOrElse {
                throw BackupRestoreException("Backup contains invalid custom rule type: ${rule.transactionType}")
            }
            runCatching { CustomNumberLocale.valueOf(rule.numberLocale) }.getOrElse {
                throw BackupRestoreException("Backup contains invalid custom rule locale: ${rule.numberLocale}")
            }
        }
    }

    private fun orderCategoriesParentFirst(categories: List<CategoryDto>): List<CategoryDto> {
        val remaining = categories.toMutableList()
        val ordered = mutableListOf<CategoryDto>()
        val placedIds = mutableSetOf<Long>()
        while (remaining.isNotEmpty()) {
            val batch = remaining.filter { it.parentId == null || it.parentId in placedIds }
            if (batch.isEmpty()) {
                ordered += remaining
                break
            }
            ordered += batch
            batch.forEach { placedIds += it.id }
            remaining.removeAll(batch.toSet())
        }
        return ordered
    }

    companion object {
        const val SUPPORTED_SNAPSHOT_VERSION = 1
    }
}
