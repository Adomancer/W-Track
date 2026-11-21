package wtrack.integration.providers.bca

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
 * Production parser for myBCA (`com.bca.mybca.omni.android`) Financial Diary alerts, grounded in
 * verified `iQOO Z10R` Android 16 M3a captures (`docs/PROVIDERS.md`).
 *
 * Handled patterns (v1):
 * - Incoming: `You received IDR 50,000.00 from <SENDER> at <CATEGORY> category.` (or without `from <SENDER>`)
 * - Outgoing: `You spent IDR 50,000.00 at <CATEGORY>.`
 * - Android 16 auto-group summaries (`isGroupSummary = true`) -> [NotTransactionReason.OTHER].
 * - Unseen templates route safely to [ReviewReason.UNKNOWN_FORMAT] without guessing direction.
 */
class BcaTransactionParser : TransactionParser {
    override val sourceId: String = MYBCA_SOURCE_ID
    override val version: Int = 1

    override fun supports(event: NotificationEvent): Boolean = event.packageName == MYBCA_SOURCE_ID

    override suspend fun parse(event: NotificationEvent): ParseResult {
        if (event.isGroupSummary) {
            return ParseResult.NotTransaction(NotTransactionReason.OTHER)
        }
        SensitiveContentGuard.inspectPreconditions(event)?.let { return it }
        SensitiveContentGuard.inspectNonTransactionNotice(event)?.let { return it }

        val rawBody = (event.bigText?.takeIf { it.isNotBlank() } ?: event.text).trim()
        val cleanBody = rawBody.replace(multiSpaceRegex, " ").trim()

        receivedWithSenderRegex.find(cleanBody)?.let { match ->
            val currency = match.groupValues[1].uppercase()
            val numberToken = match.groupValues[2]
            val sender = match.groupValues[3].trim()
            val categoryLabel = match.groupValues[4].trim()
            return parseMoneyMatch(
                rawAmount = "$currency $numberToken",
                currency = currency,
                direction = Direction.INFLOW,
                type = classifyIncomingType(categoryLabel),
                merchantRaw = sender,
                description = "Received from $sender ($categoryLabel)",
            )
        }

        receivedWithoutSenderRegex.find(cleanBody)?.let { match ->
            val currency = match.groupValues[1].uppercase()
            val numberToken = match.groupValues[2]
            val categoryLabel = match.groupValues[3].trim()
            return parseMoneyMatch(
                rawAmount = "$currency $numberToken",
                currency = currency,
                direction = Direction.INFLOW,
                type = classifyIncomingType(categoryLabel),
                merchantRaw = categoryLabel,
                description = "Received ($categoryLabel)",
            )
        }

        spentRegex.find(cleanBody)?.let { match ->
            val currency = match.groupValues[1].uppercase()
            val numberToken = match.groupValues[2]
            val target = match.groupValues[3].trim()
            return parseMoneyMatch(
                rawAmount = "$currency $numberToken",
                currency = currency,
                direction = Direction.OUTFLOW,
                type = classifyOutgoingType(target),
                merchantRaw = target,
                description = "Spent at $target",
            )
        }

        val fallbackMatch = fallbackCurrencyAmountRegex.find(cleanBody)
        val partial = if (fallbackMatch != null) {
            val currency = fallbackMatch.groupValues[1].uppercase()
            val parsed = LocaleNumberParser.parse(
                rawAmount = "$currency ${fallbackMatch.groupValues[2]}",
                currencyCode = currency,
                locale = ProviderNumberLocale.ENGLISH,
            )
            if (parsed is ParseAmountResult.Parsed) {
                PartialTransaction(
                    amountMinor = parsed.amountMinor,
                    currency = currency,
                    description = cleanBody.ifBlank { null },
                )
            } else {
                null
            }
        } else {
            null
        }
        return ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, partial)
    }

    private fun parseMoneyMatch(
        rawAmount: String,
        currency: String,
        direction: Direction,
        type: TransactionType,
        merchantRaw: String,
        description: String,
    ): ParseResult = when (
        val amount = LocaleNumberParser.parse(rawAmount, currency, ProviderNumberLocale.ENGLISH)
    ) {
        is ParseAmountResult.Parsed -> ParseResult.Success(
            ParsedTransaction(
                amountMinor = amount.amountMinor,
                currency = currency,
                direction = direction,
                type = type,
                occurredAtEpochMillis = null,
                merchantRaw = merchantRaw,
                description = description,
                accountHint = null,
                providerReference = null,
                reportedBalanceMinor = null,
            ),
        )

        is ParseAmountResult.Ambiguous -> ParseResult.NeedsReview(
            reason = ReviewReason.AMBIGUOUS_FIELD,
            partial = PartialTransaction(
                currency = currency,
                direction = direction,
                type = type,
                merchantRaw = merchantRaw,
                description = description,
            ),
        )

        is ParseAmountResult.Invalid -> ParseResult.NeedsReview(
            reason = ReviewReason.INVALID_VALUE,
            partial = PartialTransaction(
                currency = currency,
                direction = direction,
                type = type,
                merchantRaw = merchantRaw,
                description = description,
            ),
        )
    }

    private fun classifyIncomingType(categoryLabel: String): TransactionType {
        val lower = categoryLabel.lowercase()
        return when {
            lower.contains("transfer") -> TransactionType.TRANSFER
            lower.contains("refund") || lower.contains("reversal") -> TransactionType.REFUND
            lower.contains("cash") || lower.contains("atm") -> TransactionType.CASH
            else -> TransactionType.OTHER
        }
    }

    private fun classifyOutgoingType(target: String): TransactionType {
        val lower = target.lowercase()
        return when {
            lower.contains("transfer") -> TransactionType.TRANSFER
            lower.contains("fee") || lower.contains("admin") || lower.contains("biaya") -> TransactionType.FEE
            lower.contains("atm") || lower.contains("cash withdrawal") || lower.contains("tarik tunai") -> TransactionType.CASH
            else -> TransactionType.PURCHASE
        }
    }

    companion object {
        const val MYBCA_SOURCE_ID = "com.bca.mybca.omni.android"

        private val multiSpaceRegex = Regex("""\s+""")
        private val receivedWithSenderRegex = Regex(
            """^You\s+received\s+([A-Z]{3})\s*([0-9]+(?:[.,][0-9]+)*)\s+from\s+(.+?)\s+at\s+(.+?)(?:\s+category)?\.?$""",
            RegexOption.IGNORE_CASE,
        )
        private val receivedWithoutSenderRegex = Regex(
            """^You\s+received\s+([A-Z]{3})\s*([0-9]+(?:[.,][0-9]+)*)\s+at\s+(.+?)(?:\s+category)?\.?$""",
            RegexOption.IGNORE_CASE,
        )
        private val spentRegex = Regex(
            """^You\s+spent\s+([A-Z]{3})\s*([0-9]+(?:[.,][0-9]+)*)\s+at\s+(.+?)(?:\s+category)?\.?$""",
            RegexOption.IGNORE_CASE,
        )
        private val fallbackCurrencyAmountRegex = Regex(
            """\b(IDR|USD|SGD|MYR|EUR|GBP|AUD|JPY|CNY|THB|HKD|KRW)\s*([0-9]+(?:[.,][0-9]+)*)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
