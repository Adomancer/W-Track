package wtrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import wtrack.app.data.local.entity.AccountEntity
import wtrack.app.data.local.entity.AccountSourceEntity
import wtrack.app.data.local.entity.TransactionSourceEntity

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY is_active DESC, name ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAllOnce(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<AccountEntity>)

    @Update
    suspend fun update(entity: AccountEntity)

    @Query("UPDATE accounts SET is_active = :isActive, updated_at = :updatedAt WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean, updatedAt: Long)

    @Query("UPDATE accounts SET last_reconciled_at = :atEpochMillis, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateLastReconciledAt(id: Long, atEpochMillis: Long, updatedAt: Long)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

@Dao
interface TransactionSourceDao {
    @Query("SELECT * FROM transaction_sources ORDER BY display_name ASC")
    fun observeAll(): Flow<List<TransactionSourceEntity>>

    @Query("SELECT * FROM transaction_sources ORDER BY id ASC")
    suspend fun getAllOnce(): List<TransactionSourceEntity>

    @Query("SELECT package_name FROM transaction_sources WHERE is_allowlisted = 1")
    fun observeAllowlistedPackages(): Flow<List<String>>

    @Query("SELECT * FROM transaction_sources WHERE id = :id")
    suspend fun getById(id: String): TransactionSourceEntity?

    @Upsert
    suspend fun upsert(entity: TransactionSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TransactionSourceEntity>)

    @Query("UPDATE transaction_sources SET is_allowlisted = :allowlisted, updated_at = :updatedAt WHERE package_name = :packageName")
    suspend fun setAllowlisted(packageName: String, allowlisted: Boolean, updatedAt: Long)

    @Query("UPDATE transaction_sources SET parser_version = :parserVersion, updated_at = :updatedAt WHERE id = :sourceId")
    suspend fun updateParserVersion(sourceId: String, parserVersion: Int?, updatedAt: Long)

    @Query("UPDATE transaction_sources SET sample_collection_until = :untilEpochMillis, updated_at = :updatedAt WHERE id = :sourceId")
    suspend fun setSampleCollectionUntil(sourceId: String, untilEpochMillis: Long?, updatedAt: Long)

    @Query("DELETE FROM transaction_sources")
    suspend fun deleteAll()
}

@Dao
interface AccountSourceDao {
    @Query("SELECT * FROM account_sources ORDER BY id ASC")
    fun observeAll(): Flow<List<AccountSourceEntity>>

    @Query("SELECT * FROM account_sources ORDER BY id ASC")
    suspend fun getAllOnce(): List<AccountSourceEntity>

    @Query("SELECT * FROM account_sources WHERE source_id = :sourceId ORDER BY id ASC")
    suspend fun getForSource(sourceId: String): List<AccountSourceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AccountSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<AccountSourceEntity>)

    @Query("DELETE FROM account_sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM account_sources")
    suspend fun deleteAll()
}
