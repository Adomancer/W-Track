package wtrack.integration.providers.dana

import wtrack.integration.Direction
import wtrack.integration.NotTransactionReason
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionParser
import wtrack.integration.TransactionType
import wtrack.integration.providers.common.LocaleNumberParser
import wtrack.integration.providers.common.ParseAmountResult
import wtrack.integration.providers.common.ProviderNumberLocale
import wtrack.integration.providers.common.SensitiveContentGuard

/**
 * Production parser for DANA (`id.dana`), grounded in verified `iQOO Z10R` Android 16 M3a
 * captures (`docs/PROVIDERS.md`).
 *
 * Handled patterns (v1):
 * - Incoming transfer: `Rp2.000 telah diterima dari <SENDER> 💰`
 * - High-volume quest/mission/reward notifications on the same FCM channel -> [NotTransactionReason.PROMOTION]
 * - Android 16 auto-group summaries (`isGroupSummary = true`) -> [NotTransactionReason.OTHER]
 * - Unseen transaction templates (e.g., outgoing QRIS/transfers) route safely to [ReviewReason.UNKNOWN_FORMAT].
 */
class DanaTransactionParser : TransactionParser {
    override val sourceId: String = SOURCE_ID
    override val version: Int = 1

    override fun supports(event: NotificationEvent): Boolean = event.packageName == SOURCE_ID

    override suspend fun parse(event: NotificationEvent): ParseResult {
        if (event.isGroupSummary) {
            return ParseResult.NotTransaction(NotTransactionReason.OTHER)
        }
        SensitiveContentGuard.inspectPreconditions(event)?.let { return it }
        SensitiveContentGuard.inspectNonTransactionNotice(event)?.let { return it }

        val rawBody = (event.bigText?.takeIf { it.isNotBlank() } ?: event.text).trim()
        val cleanBody = rawBody.replace(multiSpaceRegex, " ").trim()

        incomingTransferRegex.find(cleanBody)?.let { match ->
            val rawAmount = match.groupValues[1]
            val counterparty = trimTrailingNoise(match.groupValues[2])
            if (counterparty.isEmpty()) {
                return ParseResult.NeedsReview(
                    ReviewReason.UNKNOWN_FORMAT,
                    PartialTransaction(
                        currency = "IDR",
                        direction = Direction.INFLOW,
                        type = TransactionType.TRANSFER,
                        description = cleanBody,
                    ),
                )
            }
            return parseIncomingTransfer(rawAmount = rawAmount, counterparty = counterparty)
        }

        val amountToken = anyCurrencyAmountRegex.find(cleanBody)?.groupValues?.get(1)
        if (amountToken == null && danaLoyaltyNoisePatterns.any { it.containsMatchIn(cleanBody) }) {
            return ParseResult.NotTransaction(NotTransactionReason.PROMOTION)
        }

        val parsedFallbackAmount = amountToken?.let {
            LocaleNumberParser.parse(it, "IDR", ProviderNumberLocale.INDONESIAN)
        }
        val partial = if (parsedFallbackAmount is ParseAmountResult.Parsed) {
            PartialTransaction(
                amountMinor = parsedFallbackAmount.amountMinor,
                currency = "IDR",
                description = cleanBody.ifBlank { null },
            )
        } else {
            null
        }
        return ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, partial)
    }

    private fun parseIncomingTransfer(
        rawAmount: String,
        counterparty: String,
    ): ParseResult = when (
        val amount = LocaleNumberParser.parse(rawAmount, "IDR", ProviderNumberLocale.INDONESIAN)
    ) {
        is ParseAmountResult.Parsed -> ParseResult.Success(
            ParsedTransaction(
                amountMinor = amount.amountMinor,
                currency = "IDR",
                direction = Direction.INFLOW,
                type = TransactionType.TRANSFER,
                occurredAtEpochMillis = null,
                merchantRaw = counterparty,
                description = "Diterima dari $counterparty",
                accountHint = null,
                providerReference = null,
                reportedBalanceMinor = null,
            ),
        )

        is ParseAmountResult.Ambiguous -> ParseResult.NeedsReview(
            reason = ReviewReason.AMBIGUOUS_FIELD,
            partial = PartialTransaction(
                currency = "IDR",
                direction = Direction.INFLOW,
                type = TransactionType.TRANSFER,
                merchantRaw = counterparty,
                description = "Diterima dari $counterparty",
            ),
        )

        is ParseAmountResult.Invalid -> ParseResult.NeedsReview(
            reason = ReviewReason.INVALID_VALUE,
            partial = PartialTransaction(
                currency = "IDR",
                direction = Direction.INFLOW,
                type = TransactionType.TRANSFER,
                merchantRaw = counterparty,
                description = "Diterima dari $counterparty",
            ),
        )
    }

    private fun trimTrailingNoise(raw: String): String {
        var end = raw.length
        while (end > 0) {
            val cp = Character.codePointBefore(raw, end)
            val charCount = Character.charCount(cp)
            val type = Character.getType(cp)
            val isNoise = Character.isWhitespace(cp) ||
                cp == '.'.code ||
                cp == ','.code ||
                cp == '!'.code ||
                cp == '?'.code ||
                cp == 0xFE0F ||
                cp == 0xFE0E ||
                type == Character.OTHER_SYMBOL.toInt() ||
                type == Character.MODIFIER_SYMBOL.toInt() ||
                type == Character.FORMAT.toInt()
            if (!isNoise) break
            end -= charCount
        }
        return raw.substring(0, end).trim()
    }

    companion object {
        const val SOURCE_ID = "id.dana"

        private val multiSpaceRegex = Regex("""\s+""")
        private val incomingTransferRegex = Regex(
            """^((?:Rp\.?|IDR)\s*[0-9]+(?:[.,][0-9]+)*)\s+telah\s+diterima\s+dari\s+(.+)$""",
            RegexOption.IGNORE_CASE,
        )
        private val anyCurrencyAmountRegex = Regex(
            """((?:Rp\.?|IDR)\s*[0-9]+(?:[.,][0-9]+)*)""",
            RegexOption.IGNORE_CASE,
        )
        private val danaLoyaltyNoisePatterns = listOf(
            Regex("""\bquest\b""", RegexOption.IGNORE_CASE),
            Regex("""selesaikan\s+(?:quest|misi)""", RegexOption.IGNORE_CASE),
            Regex("""misi\s+selesai""", RegexOption.IGNORE_CASE),
            Regex("""klaim\s+hadiah""", RegexOption.IGNORE_CASE),
            Regex("""\bhadiahmu\b""", RegexOption.IGNORE_CASE),
            Regex("""\bpoin\s+dana\b""", RegexOption.IGNORE_CASE),
            Regex("""\bdana\s+points?\b""", RegexOption.IGNORE_CASE),
            Regex("""\ba\+?\s*rewards?\b""", RegexOption.IGNORE_CASE),
        )
    }
}
