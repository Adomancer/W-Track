package wtrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import wtrack.app.data.local.entity.NotificationEventEntity
import wtrack.app.data.local.entity.TransactionEntity

@Dao
interface NotificationEventDao {
    @Query("SELECT * FROM notification_events ORDER BY posted_at DESC, id DESC")
    fun observeAll(): Flow<List<NotificationEventEntity>>

    @Query("SELECT * FROM notification_events WHERE processing_status = 'NEEDS_REVIEW' ORDER BY posted_at DESC, id DESC")
    fun observeNeedsReview(): Flow<List<NotificationEventEntity>>

    @Query("SELECT COUNT(*) FROM notification_events WHERE processing_status = 'NEEDS_REVIEW'")
    fun observeNeedsReviewCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_events WHERE processing_status = 'NEEDS_REVIEW'")
    suspend fun countNeedsReview(): Int

    @Query("SELECT * FROM notification_events ORDER BY id ASC")
    suspend fun getAllOnce(): List<NotificationEventEntity>

    @Query("SELECT * FROM notification_events WHERE id = :id")
    suspend fun getById(id: Long): NotificationEventEntity?

    @Query("SELECT * FROM notification_events WHERE stable_event_key = :stableEventKey LIMIT 1")
    suspend fun findByStableEventKey(stableEventKey: String): NotificationEventEntity?

    @Query(
        """
        SELECT * FROM notification_events
        WHERE processing_status IN ('RECEIVED', 'FAILED')
           OR (processing_status = 'PROCESSING' AND updated_at < :staleBeforeEpochMillis)
        ORDER BY received_at ASC, id ASC
        """,
    )
    suspend fun getUnfinishedForRecovery(staleBeforeEpochMillis: Long): List<NotificationEventEntity>

    @Query(
        """
        SELECT COUNT(*) FROM notification_events
        WHERE source_id = :sourceId
          AND failure_reason = 'NO_PARSER'
          AND received_at >= :sinceEpochMillis
        """,
    )
    suspend fun countNoParserSince(sourceId: String, sinceEpochMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: NotificationEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<NotificationEventEntity>)

    @Update
    suspend fun update(entity: NotificationEventEntity)

    @Query(
        """
        UPDATE notification_events
        SET processing_status = :newStatus,
            failure_reason = :failureReason,
            review_draft_json = :reviewDraftJson,
            payload_json = :payloadJson,
            purge_after = :purgeAfter,
            processed_at = :processedAt,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateOutcome(
        id: Long,
        newStatus: String,
        failureReason: String?,
        reviewDraftJson: String?,
        payloadJson: String?,
        purgeAfter: Long?,
        processedAt: Long?,
        updatedAt: Long,
    )

    @Query("UPDATE notification_events SET payload_json = NULL, purge_after = NULL, updated_at = :nowEpochMillis WHERE purge_after IS NOT NULL AND purge_after <= :nowEpochMillis")
    suspend fun purgeExpiredPayloads(nowEpochMillis: Long): Int

    @Query(
        """
        SELECT * FROM notification_events
        WHERE failure_reason = 'NO_PARSER' AND payload_json IS NOT NULL
        ORDER BY posted_at ASC, id ASC
        """,
    )
    suspend fun getRetainedSamplesOnce(): List<NotificationEventEntity>

    @Query(
        """
        SELECT COUNT(*) FROM notification_events
        WHERE failure_reason = 'NO_PARSER' AND payload_json IS NOT NULL
        """,
    )
    suspend fun countRetainedSamples(): Int

    @Query(
        """
        UPDATE notification_events
        SET payload_json = NULL,
            purge_after = NULL,
            updated_at = :nowEpochMillis
        WHERE failure_reason = 'NO_PARSER' AND payload_json IS NOT NULL
        """,
    )
    suspend fun purgeRetainedSamplesNow(nowEpochMillis: Long): Int

    @Query(
        """
        SELECT * FROM notification_events
        WHERE processing_status = 'PROCESSED' AND payload_json IS NOT NULL
        ORDER BY posted_at DESC, id DESC
        """,
    )
    suspend fun getRetainedProcessedEventsOnce(): List<NotificationEventEntity>

    @Query("DELETE FROM notification_events")
    suspend fun deleteAll()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurred_at DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY id ASC")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE event_id = :eventId LIMIT 1")
    suspend fun findByEventId(eventId: Long): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE source_id = :sourceId AND provider_reference = :providerReference
        LIMIT 1
        """,
    )
    suspend fun findByProviderReference(sourceId: String, providerReference: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE source_id = :sourceId
          AND provider_reference = :providerReference
          AND direction = :direction
        LIMIT 1
        """,
    )
    suspend fun findByProviderReferenceAndDirection(
        sourceId: String,
        providerReference: String,
        direction: String,
    ): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE source_id = :sourceId
          AND provider_reference = :providerReference
          AND direction = 'OUTFLOW'
          AND status = 'CONFIRMED'
        LIMIT 1
        """,
    )
    suspend fun findOutflowByProviderReference(
        sourceId: String,
        providerReference: String,
    ): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE fingerprint = :fingerprint
          AND occurred_at BETWEEN :windowStartMillis AND :windowEndMillis
        LIMIT 1
        """,
    )
    suspend fun findByFingerprintInWindow(
        fingerprint: String,
        windowStartMillis: Long,
        windowEndMillis: Long,
    ): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE account_id = :accountId
          AND status = 'CONFIRMED'
          AND reported_balance_minor IS NOT NULL
        ORDER BY occurred_at ASC, id ASC
        """,
    )
    suspend fun getReportedBalanceCheckpoints(accountId: Long): List<TransactionEntity>

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE merchant_id = :merchantId
          AND status = 'CONFIRMED'
          AND (category_id IS NULL OR category_id = :uncategorizedId)
          AND (category_source IS NULL OR category_source != 'USER')
        """,
    )
    suspend fun countUncategorizedForMerchant(merchantId: Long, uncategorizedId: Long): Int

    @Query(
        """
        UPDATE transactions
        SET category_id = :categoryId,
            category_source = 'RULE',
            applied_rule_id = :ruleId,
            updated_at = :updatedAt
        WHERE merchant_id = :merchantId
          AND status = 'CONFIRMED'
          AND (category_id IS NULL OR category_id = :uncategorizedId)
          AND (category_source IS NULL OR category_source != 'USER')
        """,
    )
    suspend fun applyCategoryToUncategorizedMerchant(
        merchantId: Long,
        uncategorizedId: Long,
        categoryId: Long,
        ruleId: Long,
        updatedAt: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("UPDATE transactions SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long)

    @Query("UPDATE transactions SET related_transaction_id = :relatedId, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateRelatedTransaction(id: Long, relatedId: Long?, updatedAt: Long)

    @Query(
        """
        UPDATE transactions
        SET category_id = :categoryId,
            category_source = :categorySource,
            applied_rule_id = :appliedRuleId,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateCategory(
        id: Long,
        categoryId: Long?,
        categorySource: String?,
        appliedRuleId: Long?,
        updatedAt: Long,
    )

    @Query("DELETE FROM transactions WHERE id = :id AND event_id IS NULL")
    suspend fun deleteManualById(id: Long): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
