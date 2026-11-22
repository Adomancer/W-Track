package wtrack.integration.providers.generic

import wtrack.integration.Direction
import wtrack.integration.NotTransactionReason
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionParser
import wtrack.integration.TransactionType
import wtrack.integration.providers.common.NotificationParsingSupport
import wtrack.integration.providers.common.ParseAmountResult
import wtrack.integration.providers.common.SensitiveContentGuard

/**
 * Provider-agnostic fallback parser for allowlisted banks/e-wallets that do not yet have a
 * dedicated parser or matching custom user rule.
 *
 * Security & correctness guarantees:
 * - Filters Android 16 group summaries, OTP/PIN security notices, failed attempts, and promos.
 * - Filters non-monetary pushes (no currency+amount token found) as [NotTransactionReason.OTHER].
 * - Never emits [ParseResult.Success]; monetary candidates always route to
 *   [ParseResult.NeedsReview] with pre-filled [PartialTransaction] hints for safe 1-tap confirmation.
 */
class GenericNotificationFallbackParser : TransactionParser {
    override val sourceId: String = SOURCE_ID
    override val version: Int = 0

    override fun supports(event: NotificationEvent): Boolean = true

    override suspend fun parse(event: NotificationEvent): ParseResult {
        if (event.isGroupSummary) {
            return ParseResult.NotTransaction(NotTransactionReason.OTHER)
        }
        SensitiveContentGuard.inspectPreconditions(event)?.let { return it }
        SensitiveContentGuard.inspectNonTransactionNotice(event)?.let { return it }

        val cleanBody = NotificationParsingSupport.extractBestBody(event)
        val combined = NotificationParsingSupport.combineTitleAndBody(event)
        val matches = NotificationParsingSupport.findCurrencyAmounts(combined)
        if (matches.isEmpty()) {
            return ParseResult.NotTransaction(NotTransactionReason.OTHER)
        }

        val primary = matches.first()
        val inferredLocale = NotificationParsingSupport.inferLocaleFromToken(
            numberToken = primary.numberToken,
            rawTokenOrPrefix = primary.fullToken,
            currency = primary.currency,
        )
        val (parsedAmount, _) = NotificationParsingSupport.parseAmountSmart(
            rawAmount = primary.fullToken,
            currency = primary.currency,
            preferredLocale = inferredLocale,
        )
        val amountMinor = (parsedAmount as? ParseAmountResult.Parsed)?.amountMinor
        val direction = detectDirectionHint(combined)
        val txType = detectTypeHint(combined, direction)
        val merchant = NotificationParsingSupport.extractCounterpartyHint(cleanBody)
        val description = cleanBody.ifBlank { combined }.take(120).ifBlank { null }

        return ParseResult.NeedsReview(
            reason = ReviewReason.UNKNOWN_FORMAT,
            partial = PartialTransaction(
                amountMinor = amountMinor,
                currency = primary.currency,
                direction = direction,
                type = txType,
                occurredAtEpochMillis = null,
                merchantRaw = merchant,
                description = description,
                accountHint = null,
                providerReference = null,
                reportedBalanceMinor = null,
            ),
        )
    }

    private fun detectDirectionHint(text: String): Direction? {
        val hasInflow = inflowPatterns.any { it.containsMatchIn(text) }
        val hasOutflow = outflowPatterns.any { it.containsMatchIn(text) }
        return when {
            hasInflow && !hasOutflow -> Direction.INFLOW
            hasOutflow && !hasInflow -> Direction.OUTFLOW
            else -> null
        }
    }

    private fun detectTypeHint(text: String, direction: Direction?): TransactionType? = when {
        feePatterns.any { it.containsMatchIn(text) } -> TransactionType.FEE
        direction == Direction.INFLOW && refundPatterns.any { it.containsMatchIn(text) } -> TransactionType.REFUND
        cashPatterns.any { it.containsMatchIn(text) } -> TransactionType.CASH
        transferPatterns.any { it.containsMatchIn(text) } -> TransactionType.TRANSFER
        purchasePatterns.any { it.containsMatchIn(text) } -> TransactionType.PURCHASE
        else -> null
    }

    companion object {
        const val SOURCE_ID = "*"

        private val inflowPatterns = listOf(
            Regex("""\b(?:diterima|terima|uang\s+masuk|dana\s+masuk|saldo\s+masuk|transfer\s+masuk)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:top[\s-]*up\s+berhasil|isi\s+saldo\s+berhasil|pengembalian\s+dana|cashback\s+diterima)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:received|has\s+sent|sent\s+you|credited|deposited|incoming|refunded)\b""", RegexOption.IGNORE_CASE),
            Regex("""\brefund\b""", RegexOption.IGNORE_CASE),
        )

        private val outflowPatterns = listOf(
            Regex("""\b(?:dibayar|pembayaran|bayar|uang\s+keluar|dana\s+keluar|pembelian|beli)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:transfer\s+ke|kirim\s+ke|dikirim\s+ke|tarik\s+tunai|terpotong|biaya\s+admin)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:spent|paid|payment|transferred|transfer\s+to|sent\s+to|debited|purchase|withdrew|withdrawal|charged)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bdebit\b""", RegexOption.IGNORE_CASE),
        )

        private val feePatterns = listOf(
            Regex("""\b(?:biaya\s+admin|admin\s+fee|biaya\s+transfer|service\s+fee)\b""", RegexOption.IGNORE_CASE),
        )
        private val refundPatterns = listOf(
            Regex("""\b(?:refund|pengembalian\s+dana|reversal)\b""", RegexOption.IGNORE_CASE),
        )
        private val cashPatterns = listOf(
            Regex("""\b(?:tarik\s+tunai|cash\s+withdrawal|setor\s+tunai|\batm\b)\b""", RegexOption.IGNORE_CASE),
        )
        private val transferPatterns = listOf(
            Regex("""\b(?:transfer|kirim|diterima\s+dari|terima\s+dari|telah\s+diterima|dana\s+masuk|saldo\s+masuk|uang\s+masuk|has\s+sent|top[\s-]*up|isi\s+saldo)\b""", RegexOption.IGNORE_CASE),
        )
        private val purchasePatterns = listOf(
            Regex("""\b(?:bayar|pembayaran|dibayar|qris|beli|pembelian|spent|paid|payment|purchase)\b""", RegexOption.IGNORE_CASE),
        )
    }
}
