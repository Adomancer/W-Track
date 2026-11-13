package wtrack.integration

/**
 * A transaction parsed from a notification, before validation and persistence (section 6.3).
 */
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
 * The same fields as [ParsedTransaction], all nullable, carried on a Needs Review draft
 * (section 6.3). It is not a [Transaction]; the app builds one only on user confirmation.
 */
data class PartialTransaction(
    val amountMinor: Long?,
    val currency: String?,
    val direction: Direction?,
    val type: TransactionType?,
    val occurredAtEpochMillis: Long?,
    val merchantRaw: String?,
    val description: String?,
    val accountHint: String?,
    val providerReference: String?,
    val reportedBalanceMinor: Long?,
)

/**
 * Reasons a notification event may be routed to Needs Review. [PIPELINE_ERROR] is set by the
 * pipeline (ING-12), never by a parser.
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
 * Reasons a notification is classified as not a transaction (section 6.3, PAR-09).
 */
enum class NotTransactionReason {
    PROMOTION,
    ANNOUNCEMENT,
    SECURITY_NOTICE,
    FAILED_ATTEMPT,
    OTHER,
}