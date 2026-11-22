package wtrack.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.CustomParserRuleEntity
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.entity.TransferMatchEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WtrackDatabaseConstraintTest {
    private lateinit var db: WtrackDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WtrackDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(WtrackDatabase.createCallback())
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedsRequiredSystemCategoriesOnCreate() = runTest {
        val uncat = db.categoryDao().findBySystemKey(WtrackDatabase.SYSTEM_KEY_UNCATEGORIZED)
        val fees = db.categoryDao().findBySystemKey(WtrackDatabase.SYSTEM_KEY_FEES)
        val cash = db.categoryDao().findBySystemKey(WtrackDatabase.SYSTEM_KEY_CASH_WITHDRAWAL)

        assertThat(uncat).isNotNull()
        assertThat(uncat!!.isSystem).isTrue()
        assertThat(fees).isNotNull()
        assertThat(fees!!.isSystem).isTrue()
        assertThat(cash).isNotNull()
    }

    @Test
    fun enforcesAtMostOneNullHintPerSourceWhileAllowingDistinctNonNullHints() = runTest {
        val now = 1_700_000_000_000L
        db.transactionSourceDao().upsert(
            TransactionSourceEntity("src.bank", "src.bank", "Bank", true, 1, null, now, now),
        )
        val acc1 = db.accountDao().insert(
            AccountEntity(name = "Main", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        val acc2 = db.accountDao().insert(
            AccountEntity(name = "Pocket", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )

        db.accountSourceDao().insert(AccountSourceEntity(accountId = acc1, sourceId = "src.bank", accountHint = null, createdAt = now))
        val secondNullFailed = runCatching {
            db.accountSourceDao().insert(AccountSourceEntity(accountId = acc2, sourceId = "src.bank", accountHint = null, createdAt = now))
        }.isFailure
        assertThat(secondNullFailed).isTrue()

        db.accountSourceDao().insert(AccountSourceEntity(accountId = acc2, sourceId = "src.bank", accountHint = "9901", createdAt = now))
        assertThat(db.accountSourceDao().getForSource("src.bank")).hasSize(2)
    }

    @Test
    fun enforcesNonNegativeFeeAndSingleConfirmedPairPerTransaction() = runTest {
        val now = 1_700_000_000_000L
        val acc1 = db.accountDao().insert(
            AccountEntity(name = "Bank", provider = "Bank", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        val acc2 = db.accountDao().insert(
            AccountEntity(name = "Wallet", provider = "Wallet", accountType = "EWALLET", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        val acc3 = db.accountDao().insert(
            AccountEntity(name = "Other", provider = "Other", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )

        val outTx = db.transactionDao().insert(
            TransactionEntity(accountId = acc1, sourceId = null, eventId = null, amountMinor = 5_650_000L, currency = "IDR", direction = "OUTFLOW", transactionType = "TRANSFER", merchantId = null, description = "Topup", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        val inTx = db.transactionDao().insert(
            TransactionEntity(accountId = acc2, sourceId = null, eventId = null, amountMinor = 5_000_000L, currency = "IDR", direction = "INFLOW", transactionType = "TRANSFER", merchantId = null, description = "Topup", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )
        val inTx2 = db.transactionDao().insert(
            TransactionEntity(accountId = acc3, sourceId = null, eventId = null, amountMinor = 5_000_000L, currency = "IDR", direction = "INFLOW", transactionType = "TRANSFER", merchantId = null, description = "Dup", note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now),
        )

        val negFeeFailed = runCatching {
            db.transferMatchDao().insert(TransferMatchEntity(outgoingTransactionId = outTx, incomingTransactionId = inTx, origin = "MANUAL", confidence = null, status = "CONFIRMED", feeMinor = -100L, createdAt = now))
        }.exceptionOrNull()
        assertThat(negFeeFailed).isInstanceOf(SQLiteException::class.java)

        db.transferMatchDao().insert(TransferMatchEntity(outgoingTransactionId = outTx, incomingTransactionId = inTx, origin = "MANUAL", confidence = null, status = "CONFIRMED", feeMinor = 650_000L, createdAt = now))

        val doublePairFailed = runCatching {
            db.transferMatchDao().insert(TransferMatchEntity(outgoingTransactionId = outTx, incomingTransactionId = inTx2, origin = "MANUAL", confidence = null, status = "CONFIRMED", feeMinor = 0L, createdAt = now))
        }.exceptionOrNull()
        assertThat(doublePairFailed).isInstanceOf(SQLiteConstraintException::class.java)
    }

    @Test
    fun enforcesAtMostOneTransactionPerNotificationEvent() = runTest {
        val now = 1_700_000_000_000L
        db.transactionSourceDao().upsert(TransactionSourceEntity("src", "src", "Src", true, 1, null, now, now))
        val accId = db.accountDao().insert(
            AccountEntity(name = "A", provider = "P", accountType = "BANK", currency = "IDR", initialBalanceMinor = null, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
        )
        val eventId = db.notificationEventDao().insertIgnore(
            NotificationEventEntity(sourceId = "src", packageName = "src", stableEventKey = "key1", notificationKey = "k", notificationId = 1, notificationTag = null, postedAt = now, payloadJson = null, purgeAfter = null, processingStatus = "PROCESSED", attemptCount = 1, failureReason = null, reviewDraftJson = null, receivedAt = now, processedAt = now, updatedAt = now),
        )

        val tx = TransactionEntity(accountId = accId, sourceId = "src", eventId = eventId, amountMinor = 100_000L, currency = "IDR", direction = "OUTFLOW", transactionType = "PURCHASE", merchantId = null, description = null, note = null, occurredAt = now, categoryId = null, categorySource = null, appliedRuleId = null, status = "CONFIRMED", fingerprint = null, parserVersion = null, originalJson = null, providerReference = null, relatedTransactionId = null, reportedBalanceMinor = null, createdAt = now, updatedAt = now)
        db.transactionDao().insert(tx)

        val secondTxForSameEvent = runCatching { db.transactionDao().insert(tx) }.exceptionOrNull()
        assertThat(secondTxForSameEvent).isInstanceOf(SQLiteConstraintException::class.java)
    }

    @Test
    fun rejectsInitialBalanceWithoutInitialBalanceAt() = runTest {
        val now = 1_700_000_000_000L
        val failed = runCatching {
            db.accountDao().insert(
                AccountEntity(name = "Bad", provider = "P", accountType = "BANK", currency = "IDR", initialBalanceMinor = 10_000L, initialBalanceAt = null, lastReconciledAt = null, createdAt = now, updatedAt = now),
            )
        }.exceptionOrNull()
        assertThat(failed).isInstanceOf(SQLiteException::class.java)
    }

    @Test
    fun migratesV1DatabaseToV2WithCustomParserRulesTableIntact() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_${UUID.randomUUID()}.db"
        val dbFile = context.getDatabasePath(dbName)
        try {
            // Create a minimal v1 SQLite database using the framework helper directly
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbName)
                    .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            listOf(
                                "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `provider` TEXT NOT NULL, `account_type` TEXT NOT NULL, `currency` TEXT NOT NULL, `initial_balance_minor` INTEGER, `initial_balance_at` INTEGER, `last_reconciled_at` INTEGER, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
                                "CREATE TABLE IF NOT EXISTS `transaction_sources` (`id` TEXT NOT NULL, `package_name` TEXT NOT NULL, `display_name` TEXT NOT NULL, `is_allowlisted` INTEGER NOT NULL, `parser_version` INTEGER, `sample_collection_until` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                                "CREATE TABLE IF NOT EXISTS `account_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `account_id` INTEGER NOT NULL, `source_id` TEXT NOT NULL, `account_hint` TEXT, `created_at` INTEGER NOT NULL, FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`source_id`) REFERENCES `transaction_sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                                "CREATE TABLE IF NOT EXISTS `notification_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `source_id` TEXT NOT NULL, `package_name` TEXT NOT NULL, `stable_event_key` TEXT NOT NULL, `notification_key` TEXT, `notification_id` INTEGER NOT NULL, `notification_tag` TEXT, `posted_at` INTEGER NOT NULL, `payload_json` TEXT, `purge_after` INTEGER, `processing_status` TEXT NOT NULL, `attempt_count` INTEGER NOT NULL, `failure_reason` TEXT, `review_draft_json` TEXT, `received_at` INTEGER NOT NULL, `processed_at` INTEGER, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`source_id`) REFERENCES `transaction_sources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
                                "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `account_id` INTEGER NOT NULL, `source_id` TEXT, `event_id` INTEGER, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `direction` TEXT NOT NULL, `transaction_type` TEXT NOT NULL, `merchant_id` INTEGER, `description` TEXT, `note` TEXT, `occurred_at` INTEGER NOT NULL, `category_id` INTEGER, `category_source` TEXT, `applied_rule_id` INTEGER, `status` TEXT NOT NULL, `fingerprint` TEXT, `parser_version` INTEGER, `original_json` TEXT, `provider_reference` TEXT, `related_transaction_id` INTEGER, `reported_balance_minor` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`source_id`) REFERENCES `transaction_sources`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`event_id`) REFERENCES `notification_events`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`merchant_id`) REFERENCES `merchants`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`applied_rule_id`) REFERENCES `transaction_rules`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`related_transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                                "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `system_key` TEXT, `direction_scope` TEXT, `parent_id` INTEGER, `is_system` INTEGER NOT NULL, `is_archived` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`parent_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                                "CREATE TABLE IF NOT EXISTS `merchants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `display_name` TEXT NOT NULL, `key` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
                                "CREATE TABLE IF NOT EXISTS `transaction_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `account_id` INTEGER, `category_id` INTEGER NOT NULL, `condition_json` TEXT NOT NULL, `action_json` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                                "CREATE TABLE IF NOT EXISTS `transfer_matches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `outgoing_transaction_id` INTEGER NOT NULL, `incoming_transaction_id` INTEGER NOT NULL, `origin` TEXT NOT NULL, `confidence` REAL, `status` TEXT NOT NULL, `fee_minor` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`outgoing_transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`incoming_transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                                "CREATE TABLE IF NOT EXISTS `capture_gaps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `started_at` INTEGER NOT NULL, `ended_at` INTEGER, `reason` TEXT NOT NULL)",
                                "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
                                "CREATE TABLE IF NOT EXISTS `transaction_tags` (`transaction_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL, PRIMARY KEY(`transaction_id`, `tag_id`), FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                                "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category_id` INTEGER NOT NULL, `account_id` INTEGER, `limit_minor` INTEGER NOT NULL, `period` TEXT NOT NULL, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                                "CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_sources_package_name` ON `transaction_sources` (`package_name`)",
                                "CREATE INDEX IF NOT EXISTS `index_account_sources_account_id` ON `account_sources` (`account_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_account_sources_source_id` ON `account_sources` (`source_id`)",
                                "CREATE UNIQUE INDEX IF NOT EXISTS `index_notification_events_stable_event_key` ON `notification_events` (`stable_event_key`)",
                                "CREATE INDEX IF NOT EXISTS `index_notification_events_processing_status_updated_at` ON `notification_events` (`processing_status`, `updated_at`)",
                                "CREATE INDEX IF NOT EXISTS `index_notification_events_purge_after` ON `notification_events` (`purge_after`)",
                                "CREATE INDEX IF NOT EXISTS `index_notification_events_notification_key` ON `notification_events` (`notification_key`)",
                                "CREATE INDEX IF NOT EXISTS `index_notification_events_source_id_failure_reason_received_at` ON `notification_events` (`source_id`, `failure_reason`, `received_at`)",
                                "CREATE INDEX IF NOT EXISTS `index_transactions_account_id` ON `transactions` (`account_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_transactions_source_id` ON `transactions` (`source_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_transactions_occurred_at` ON `transactions` (`occurred_at`)",
                                "CREATE INDEX IF NOT EXISTS `index_transactions_category_id` ON `transactions` (`category_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_transactions_merchant_id` ON `transactions` (`merchant_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_transactions_applied_rule_id` ON `transactions` (`applied_rule_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_transactions_fingerprint` ON `transactions` (`fingerprint`)",
                                "CREATE INDEX IF NOT EXISTS `index_transactions_provider_reference` ON `transactions` (`provider_reference`)",
                                "CREATE INDEX IF NOT EXISTS `index_transactions_related_transaction_id` ON `transactions` (`related_transaction_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_transactions_event_id` ON `transactions` (`event_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_categories_parent_id` ON `categories` (`parent_id`)",
                                "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_system_key` ON `categories` (`system_key`)",
                                "CREATE UNIQUE INDEX IF NOT EXISTS `index_merchants_key` ON `merchants` (`key`)",
                                "CREATE INDEX IF NOT EXISTS `index_transaction_rules_account_id` ON `transaction_rules` (`account_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_transaction_rules_category_id` ON `transaction_rules` (`category_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_transfer_matches_outgoing_transaction_id` ON `transfer_matches` (`outgoing_transaction_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_transfer_matches_incoming_transaction_id` ON `transfer_matches` (`incoming_transaction_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_capture_gaps_started_at_ended_at` ON `capture_gaps` (`started_at`, `ended_at`)",
                                "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)",
                                "CREATE INDEX IF NOT EXISTS `index_transaction_tags_tag_id` ON `transaction_tags` (`tag_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_budgets_category_id` ON `budgets` (`category_id`)",
                                "CREATE INDEX IF NOT EXISTS `index_budgets_account_id` ON `budgets` (`account_id`)",
                                "INSERT INTO accounts VALUES (1, 'Existing', 'Bank', 'BANK', 'IDR', NULL, NULL, NULL, 1, 1000, 1000)",
                            ).forEach { sql -> db.execSQL(sql) }
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build(),
            )
            helper.writableDatabase.close()
            helper.close()

            // Open with Room v2 + MIGRATION_1_2 and verify custom_parser_rules works and existing data survived
            val migrated = Room.databaseBuilder(context, WtrackDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .addMigrations(WtrackDatabase.MIGRATION_1_2)
                .build()
            try {
                assertThat(migrated.accountDao().getAllOnce().single().name).isEqualTo("Existing")
                val id = migrated.customParserRuleDao().insert(
                    CustomParserRuleEntity(
                        sourceId = "com.gojek.app",
                        name = "GoPay",
                        titleContains = null,
                        templatePattern = "Bayar {AMOUNT} ke {MERCHANT}",
                        direction = "OUTFLOW",
                        transactionType = "PURCHASE",
                        currency = "IDR",
                        numberLocale = "INDONESIAN",
                        accountHint = null,
                        enabled = true,
                        createdAt = 1_000L,
                        updatedAt = 1_000L,
                    ),
                )
                assertThat(id).isGreaterThan(0L)
            } finally {
                migrated.close()
            }
        } finally {
            dbFile.delete()
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
        }
    }
}
