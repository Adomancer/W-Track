package wtrack.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_events",
    foreignKeys = [
        ForeignKey(
            entity = TransactionSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["stable_event_key"], unique = true),
        Index(value = ["processing_status", "updated_at"]),
        Index(value = ["purge_after"]),
        Index(value = ["notification_key"]),
        Index(value = ["source_id", "failure_reason", "received_at"]),
    ],
)
data class NotificationEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "stable_event_key")
    val stableEventKey: String,
    @ColumnInfo(name = "notification_key")
    val notificationKey: String?,
    @ColumnInfo(name = "notification_id")
    val notificationId: Int,
    @ColumnInfo(name = "notification_tag")
    val notificationTag: String?,
    @ColumnInfo(name = "posted_at")
    val postedAt: Long,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String?,
    @ColumnInfo(name = "purge_after")
    val purgeAfter: Long?,
    @ColumnInfo(name = "processing_status")
    val processingStatus: String,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "failure_reason")
    val failureReason: String?,
    @ColumnInfo(name = "review_draft_json")
    val reviewDraftJson: String?,
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,
    @ColumnInfo(name = "processed_at")
    val processedAt: Long?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TransactionSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = NotificationEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchant_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TransactionRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["applied_rule_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["related_transaction_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["source_id"]),
        Index(value = ["occurred_at"]),
        Index(value = ["category_id"]),
        Index(value = ["merchant_id"]),
        Index(value = ["applied_rule_id"]),
        Index(value = ["fingerprint"]),
        Index(value = ["provider_reference"]),
        Index(value = ["related_transaction_id"]),
        Index(value = ["event_id"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "source_id")
    val sourceId: String?,
    @ColumnInfo(name = "event_id")
    val eventId: Long?,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    val currency: String,
    val direction: String,
    @ColumnInfo(name = "transaction_type")
    val transactionType: String,
    @ColumnInfo(name = "merchant_id")
    val merchantId: Long?,
    val description: String?,
    val note: String?,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long?,
    @ColumnInfo(name = "category_source")
    val categorySource: String?,
    @ColumnInfo(name = "applied_rule_id")
    val appliedRuleId: Long?,
    val status: String,
    val fingerprint: String?,
    @ColumnInfo(name = "parser_version")
    val parserVersion: Int?,
    @ColumnInfo(name = "original_json")
    val originalJson: String?,
    @ColumnInfo(name = "provider_reference")
    val providerReference: String?,
    @ColumnInfo(name = "related_transaction_id")
    val relatedTransactionId: Long?,
    @ColumnInfo(name = "reported_balance_minor")
    val reportedBalanceMinor: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
