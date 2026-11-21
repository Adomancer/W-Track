package wtrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import wtrack.app.data.local.entity.BudgetEntity
import wtrack.app.data.local.entity.CaptureGapEntity
import wtrack.app.data.local.entity.CategoryEntity
import wtrack.app.data.local.entity.MerchantEntity
import wtrack.app.data.local.entity.TagEntity
import wtrack.app.data.local.entity.TransactionRuleEntity
import wtrack.app.data.local.entity.TransactionTagEntity
import wtrack.app.data.local.entity.TransferMatchEntity

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY is_system DESC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE system_key = :systemKey LIMIT 1")
    suspend fun findBySystemKey(systemKey: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CategoryEntity>)

    @Query("UPDATE categories SET name = :name WHERE id = :id AND is_system = 0")
    suspend fun renameNonSystem(id: Long, name: String)

    @Query("UPDATE categories SET is_archived = :archived WHERE id = :id AND is_system = 0")
    suspend fun setArchivedNonSystem(id: Long, archived: Boolean)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchants ORDER BY display_name ASC")
    fun observeAll(): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchants ORDER BY id ASC")
    suspend fun getAllOnce(): List<MerchantEntity>

    @Query("SELECT * FROM merchants WHERE `key` = :key LIMIT 1")
    suspend fun findByKey(key: String): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun getById(id: Long): MerchantEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MerchantEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MerchantEntity>)

    @Query("DELETE FROM merchants")
    suspend fun deleteAll()
}

@Dao
interface TransactionRuleDao {
    @Query("SELECT * FROM transaction_rules ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<TransactionRuleEntity>>

    @Query("SELECT * FROM transaction_rules ORDER BY id ASC")
    suspend fun getAllOnce(): List<TransactionRuleEntity>

    @Query("SELECT * FROM transaction_rules WHERE enabled = 1 ORDER BY created_at DESC, id DESC")
    suspend fun getEnabledOnce(): List<TransactionRuleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransactionRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TransactionRuleEntity>)

    @Query("UPDATE transaction_rules SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM transaction_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transaction_rules")
    suspend fun deleteAll()
}

@Dao
interface TransferMatchDao {
    @Query("SELECT * FROM transfer_matches ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<TransferMatchEntity>>

    @Query("SELECT * FROM transfer_matches ORDER BY id ASC")
    suspend fun getAllOnce(): List<TransferMatchEntity>

    @Query("SELECT * FROM transfer_matches WHERE id = :id")
    suspend fun getById(id: Long): TransferMatchEntity?

    @Query(
        """
        SELECT * FROM transfer_matches
        WHERE status = 'CONFIRMED'
          AND (outgoing_transaction_id = :transactionId OR incoming_transaction_id = :transactionId)
        LIMIT 1
        """,
    )
    suspend fun findConfirmedForTransaction(transactionId: Long): TransferMatchEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransferMatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TransferMatchEntity>)

    @Query("UPDATE transfer_matches SET fee_minor = :feeMinor WHERE id = :id")
    suspend fun updateFee(id: Long, feeMinor: Long)

    @Query("DELETE FROM transfer_matches WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfer_matches")
    suspend fun deleteAll()
}

@Dao
interface CaptureGapDao {
    @Query("SELECT * FROM capture_gaps ORDER BY started_at DESC, id DESC")
    fun observeAll(): Flow<List<CaptureGapEntity>>

    @Query("SELECT * FROM capture_gaps ORDER BY id ASC")
    suspend fun getAllOnce(): List<CaptureGapEntity>

    @Query("SELECT * FROM capture_gaps WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    suspend fun getLatestOpen(): CaptureGapEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CaptureGapEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CaptureGapEntity>)

    @Query("UPDATE capture_gaps SET ended_at = :endedAt WHERE reason = :reason AND ended_at IS NULL")
    suspend fun closeOpenByReason(reason: String, endedAt: Long)

    @Query("DELETE FROM capture_gaps")
    suspend fun deleteAll()
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY id ASC")
    suspend fun getAllTagsOnce(): List<TagEntity>

    @Query("SELECT * FROM transaction_tags ORDER BY transaction_id ASC, tag_id ASC")
    fun observeAllTransactionTags(): Flow<List<TransactionTagEntity>>

    @Query("SELECT * FROM transaction_tags ORDER BY transaction_id ASC, tag_id ASC")
    suspend fun getAllTransactionTagsOnce(): List<TransactionTagEntity>

    @Query("SELECT * FROM tags WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByNameIgnoreCase(name: String): TagEntity?

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN transaction_tags ON tags.id = transaction_tags.tag_id
        WHERE transaction_tags.transaction_id = :transactionId
        ORDER BY tags.name ASC
        """,
    )
    suspend fun getTagsForTransaction(transactionId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(entity: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTags(entities: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionTag(entity: TransactionTagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTransactionTags(entities: List<TransactionTagEntity>)

    @Query("DELETE FROM transaction_tags WHERE transaction_id = :transactionId AND tag_id = :tagId")
    suspend fun deleteTransactionTag(transactionId: Long, tagId: Long)

    @Query("DELETE FROM transaction_tags")
    suspend fun deleteAllTransactionTags()

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY id ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY id ASC")
    suspend fun getAllOnce(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BudgetEntity>)

    @Update
    suspend fun update(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
