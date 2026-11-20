package wtrack.integration

import kotlinx.serialization.Serializable

/**
 * A transaction parsed from a notification, before domain validation and persistence (PRD section 6.3).
 */
@Serializable
data class ParsedTransaction(
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val type: TransactionType,
    val occurredAtEpochMillis: Long?,
    val merchantRaw: String?,
    val description: String?,
    val accountHint: String?,
    val providerReference: String?,
    val reportedBalanceMinor: Long?,
)

/**
 * Nullable draft of [ParsedTransaction] carried on a Needs Review event (PRD section 4.4, 6.3).
 * This is never persisted as a domain Transaction until explicitly resolved by the user.
 */
@Serializable
data class PartialTransaction(
    val amountMinor: Long? = null,
    val currency: String? = null,
    val direction: Direction? = null,
    val type: TransactionType? = null,
    val occurredAtEpochMillis: Long? = null,
    val merchantRaw: String? = null,
    val description: String? = null,
    val accountHint: String? = null,
    val providerReference: String? = null,
    val reportedBalanceMinor: Long? = null,
)

/**
 * Reasons a notification event may be routed to Needs Review (PRD section 6.3).
 * [PIPELINE_ERROR] is assigned solely by the pipeline (ING-12), never by a parser.
 */
enum class ReviewReason {
    MISSING_AMOUNT,
    MISSING_DIRECTION,
    INVALID_VALUE,
    AMBIGUOUS_FIELD,
    UNKNOWN_FORMAT,
    ACCOUNT_UNMAPPED,
    ACCOUNT_AMBIGUOUS,
    VALIDATION_FAILED,
    PIPELINE_ERROR,
}

/**
 * Reasons a notification is classified as not a transaction (PRD section 6.3, PAR-09).
 */
enum class NotTransactionReason {
    PROMOTION,
    ANNOUNCEMENT,
    SECURITY_NOTICE,
    FAILED_ATTEMPT,
    OTHER,
}
