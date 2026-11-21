package wtrack.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import wtrack.app.data.local.dao.AccountDao
import wtrack.app.data.local.dao.AccountSourceDao
import wtrack.app.data.local.dao.BudgetDao
import wtrack.app.data.local.dao.CaptureGapDao
import wtrack.app.data.local.dao.CategoryDao
import wtrack.app.data.local.dao.MerchantDao
import wtrack.app.data.local.dao.NotificationEventDao
import wtrack.app.data.local.dao.TagDao
import wtrack.app.data.local.dao.TransactionDao
import wtrack.app.data.local.dao.TransactionRuleDao
import wtrack.app.data.local.dao.TransactionSourceDao
import wtrack.app.data.local.dao.TransferMatchDao
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.BudgetEntity
import wtrack.app.data.local.entity.CaptureGapEntity
import wtrack.app.data.local.entity.CategoryEntity
import wtrack.app.data.local.entity.MerchantEntity
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TagEntity
import wtrack.app.data.local.entity.TransactionEntity
import wtrack.app.data.local.entity.TransactionRuleEntity
import wtrack.app.data.local.entity.TransactionSourceEntity
import wtrack.app.data.local.entity.TransactionTagEntity
import wtrack.app.data.local.entity.TransferMatchEntity

@Database(
    entities = [
        AccountEntity::class,
        TransactionSourceEntity::class,
        AccountSourceEntity::class,
        NotificationEventEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        TransactionRuleEntity::class,
        TransferMatchEntity::class,
        CaptureGapEntity::class,
        TagEntity::class,
        TransactionTagEntity::class,
        BudgetEntity::class,
    ],
    version = WtrackDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class WtrackDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionSourceDao(): TransactionSourceDao
    abstract fun accountSourceDao(): AccountSourceDao
    abstract fun notificationEventDao(): NotificationEventDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun transactionRuleDao(): TransactionRuleDao
    abstract fun transferMatchDao(): TransferMatchDao
    abstract fun captureGapDao(): CaptureGapDao
    abstract fun tagDao(): TagDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        const val DATABASE_NAME = "wtrack.db"
        const val SCHEMA_VERSION = 1

        const val SYSTEM_KEY_UNCATEGORIZED = "UNCATEGORIZED"
        const val SYSTEM_KEY_FEES = "FEES"
        const val SYSTEM_KEY_CASH_WITHDRAWAL = "CASH_WITHDRAWAL"

        fun createCallback(): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                installPartialUniqueIndexes(db)
                installIntegrityTriggers(db)
                seedDefaultCategories(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Ensure partial unique indexes and triggers remain installed across restores.
                installPartialUniqueIndexes(db)
                installIntegrityTriggers(db)
            }
        }

        private fun installPartialUniqueIndexes(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_event_id_unique
                ON transactions(event_id)
                WHERE event_id IS NOT NULL
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_account_sources_source_hint
                ON account_sources(source_id, account_hint)
                WHERE account_hint IS NOT NULL
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_account_sources_source_null_hint
                ON account_sources(source_id)
                WHERE account_hint IS NULL
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_transfer_matches_outgoing_confirmed
                ON transfer_matches(outgoing_transaction_id)
                WHERE status = 'CONFIRMED'
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_transfer_matches_incoming_confirmed
                ON transfer_matches(incoming_transaction_id)
                WHERE status = 'CONFIRMED'
                """.trimIndent(),
            )
        }

        private fun installIntegrityTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_tx_amount_positive_insert
                BEFORE INSERT ON transactions
                FOR EACH ROW
                WHEN NEW.amount_minor <= 0
                BEGIN
                    SELECT RAISE(ABORT, 'transactions.amount_minor must be positive');
                END;
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_tx_amount_positive_update
                BEFORE UPDATE ON transactions
                FOR EACH ROW
                WHEN NEW.amount_minor <= 0
                BEGIN
                    SELECT RAISE(ABORT, 'transactions.amount_minor must be positive');
                END;
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_tm_invariant_insert
                BEFORE INSERT ON transfer_matches
                FOR EACH ROW
                WHEN NEW.fee_minor < 0 OR NEW.outgoing_transaction_id = NEW.incoming_transaction_id
                BEGIN
                    SELECT RAISE(ABORT, 'Invalid transfer_matches fee_minor or legs');
                END;
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_tm_invariant_update
                BEFORE UPDATE ON transfer_matches
                FOR EACH ROW
                WHEN NEW.fee_minor < 0 OR NEW.outgoing_transaction_id = NEW.incoming_transaction_id
                BEGIN
                    SELECT RAISE(ABORT, 'Invalid transfer_matches fee_minor or legs');
                END;
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_account_initial_balance_insert
                BEFORE INSERT ON accounts
                FOR EACH ROW
                WHEN (NEW.initial_balance_minor IS NULL AND NEW.initial_balance_at IS NOT NULL)
                  OR (NEW.initial_balance_minor IS NOT NULL AND NEW.initial_balance_at IS NULL)
                BEGIN
                    SELECT RAISE(ABORT, 'accounts initial_balance_minor and initial_balance_at must both be null or both set');
                END;
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_account_initial_balance_update
                BEFORE UPDATE ON accounts
                FOR EACH ROW
                WHEN (NEW.initial_balance_minor IS NULL AND NEW.initial_balance_at IS NOT NULL)
                  OR (NEW.initial_balance_minor IS NOT NULL AND NEW.initial_balance_at IS NULL)
                BEGIN
                    SELECT RAISE(ABORT, 'accounts initial_balance_minor and initial_balance_at must both be null or both set');
                END;
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_budget_limit_insert
                BEFORE INSERT ON budgets
                FOR EACH ROW
                WHEN NEW.limit_minor <= 0
                BEGIN
                    SELECT RAISE(ABORT, 'budgets.limit_minor must be positive');
                END;
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_budget_limit_update
                BEFORE UPDATE ON budgets
                FOR EACH ROW
                WHEN NEW.limit_minor <= 0
                BEGIN
                    SELECT RAISE(ABORT, 'budgets.limit_minor must be positive');
                END;
                """.trimIndent(),
            )
        }

        private fun seedDefaultCategories(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            data class Seed(
                val id: Long,
                val name: String,
                val systemKey: String?,
                val scope: String?,
                val isSystem: Int,
            )

            val defaults = listOf(
                Seed(1L, "Uncategorized", SYSTEM_KEY_UNCATEGORIZED, null, 1),
                Seed(2L, "Fees", SYSTEM_KEY_FEES, "OUTFLOW", 1),
                Seed(3L, "Cash withdrawal", SYSTEM_KEY_CASH_WITHDRAWAL, "OUTFLOW", 1),
                Seed(4L, "Food & Dining", null, "OUTFLOW", 0),
                Seed(5L, "Transport", null, "OUTFLOW", 0),
                Seed(6L, "Bills & Utilities", null, "OUTFLOW", 0),
                Seed(7L, "Shopping", null, "OUTFLOW", 0),
                Seed(8L, "Health", null, "OUTFLOW", 0),
                Seed(9L, "Entertainment", null, "OUTFLOW", 0),
                Seed(10L, "Salary", null, "INFLOW", 0),
                Seed(11L, "Other Income", null, "INFLOW", 0),
            )
            defaults.forEach { cat ->
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO categories (id, name, system_key, direction_scope, parent_id, is_system, is_archived, created_at)
                    VALUES (?, ?, ?, ?, NULL, ?, 0, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(cat.id, cat.name, cat.systemKey, cat.scope, cat.isSystem, now),
                )
            }
        }
    }
}
