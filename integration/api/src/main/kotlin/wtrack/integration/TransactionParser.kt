package wtrack.integration

/**
 * Pure-Kotlin parser boundary contract (PRD section 6.3).
 * Provider parsers implement this interface in `:integration:providers` and depend only on `:integration:api`.
 */
interface TransactionParser {
    /** Provider/source identifier this parser handles. */
    val sourceId: String

    /** Monotonic parser version stored on every captured transaction (TX-05). */
    val version: Int

    fun supports(event: NotificationEvent): Boolean

    suspend fun parse(event: NotificationEvent): ParseResult
}

/**
 * Deterministic parser output (PRD section 6.3).
 */
sealed interface ParseResult {
    data class Success(val transaction: ParsedTransaction) : ParseResult

    data class NeedsReview(
        val reason: ReviewReason,
        val partial: PartialTransaction?,
    ) : ParseResult

    data class NotTransaction(val reason: NotTransactionReason) : ParseResult
}
