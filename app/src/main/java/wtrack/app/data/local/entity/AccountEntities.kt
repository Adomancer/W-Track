package wtrack.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val provider: String,
    @ColumnInfo(name = "account_type")
    val accountType: String,
    val currency: String,
    @ColumnInfo(name = "initial_balance_minor")
    val initialBalanceMinor: Long?,
    @ColumnInfo(name = "initial_balance_at")
    val initialBalanceAt: Long?,
    @ColumnInfo(name = "last_reconciled_at")
    val lastReconciledAt: Long?,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "transaction_sources",
    indices = [
        Index(value = ["package_name"], unique = true),
    ],
)
data class TransactionSourceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "is_allowlisted")
    val isAllowlisted: Boolean = false,
    @ColumnInfo(name = "parser_version")
    val parserVersion: Int?,
    @ColumnInfo(name = "sample_collection_until")
    val sampleCollectionUntil: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "account_sources",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["source_id"]),
    ],
)
data class AccountSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "account_hint")
    val accountHint: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
