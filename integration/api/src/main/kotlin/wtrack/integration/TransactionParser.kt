package wtrack.integration

/**
 * The parser boundary contract (section 6.3). Provider parsers implement this interface; the
 * ingestion pipeline selects one through the parser registry. Parsers are pure Kotlin and depend
 * only on this module.
 */
interface TransactionParser {
    /** Provider/source identifier this parser handles, matching the notification source. */
    val sourceId: String

    /** Bumped when the parser changes its behavior; stored on every transaction it creates (TX-05). */
    val version: Int

    fun supports(event: NotificationEvent): Boolean

    suspend fun parse(event: NotificationEvent): ParseResult
}

/**
 * Deterministic parser output. The parser alone decides the result; the pipeline applies account
 * resolution and cross-field validation but does not re-score parser output (section 6.3).
 */
sealed interface ParseResult {
    data class Success(val transaction: ParsedTransaction) : ParseResult

    data class NeedsReview(
        val reason: ReviewReason,
        val partial: PartialTransaction?,
    ) : ParseResult

    data class NotTransaction(val reason: NotTransactionReason) : ParseResult
}