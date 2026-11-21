package wtrack.integration.providers.jago

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
 * Production parser for Bank Jago (`com.jago.digitalBanking`), grounded in verified `iQOO Z10R`
 * Android 16 M3a captures (`docs/PROVIDERS.md`).
 *
 * Handled patterns (v2):
 * - Incoming transfer: `<SENDER> has sent Rp50.000 to you.` -> `INFLOW`
 * - Outgoing transfer: `You've transferred Rp50.000 to <RECIPIENT>.` -> `OUTFLOW`
 * - Pocket-to-Pocket movements (`passive`: `Rp250.000 has been moved from ... to ...`,
 *   `active`: `You've moved Rp1.000 from ... to ...`, and `out-of-pocket`: `You've moved Rp250.000
 *   out of your <Pocket>`): monitors `Main Pocket` (`Kantong Utama`) exclusively (`OUTFLOW` when
 *   leaving `Main Pocket`, `INFLOW` when entering or returning to `Main Pocket`), while movements
 *   between two non-Main pockets are ignored cleanly as [NotTransactionReason.OTHER].
 * - Android 16 auto-group summaries (`isGroupSummary = true`) -> [NotTransactionReason.OTHER].
 * - Unseen Jago templates route safely to [ReviewReason.UNKNOWN_FORMAT] without guessing direction.
 */
class JagoTransactionParser : TransactionParser {
    override val sourceId: String = SOURCE_ID
    override val version: Int = 2

    override fun supports(event: NotificationEvent): Boolean = event.packageName == SOURCE_ID

    override suspend fun parse(event: NotificationEvent): ParseResult {
        if (event.isGroupSummary) {
            return ParseResult.NotTransaction(NotTransactionReason.OTHER)
        }
        SensitiveContentGuard.inspectPreconditions(event)?.let { return it }
        SensitiveContentGuard.inspectNonTransactionNotice(event)?.let { return it }

        val rawBody = (event.bigText?.takeIf { it.isNotBlank() } ?: event.text).trim()
        val cleanBody = rawBody
            .replace(tanyaJagoFooterRegex, "")
            .replace(addMoneyBannerRegex, "")
            .replace(multiSpaceRegex, " ")
            .trim()

        incomingTransferRegex.find(cleanBody)?.let { match ->
            val counterparty = match.groupValues[1].trim()
            return parseTransferMatch(
                rawAmount = match.groupValues[2],
                counterparty = counterparty,
                direction = Direction.INFLOW,
                description = "Transfer from $counterparty",
            )
        }

        outgoingTransferRegex.find(cleanBody)?.let { match ->
            val counterparty = match.groupValues[2].trim()
            return parseTransferMatch(
                rawAmount = match.groupValues[1],
                counterparty = counterparty,
                direction = Direction.OUTFLOW,
                description = "Transfer to $counterparty",
            )
        }

        passivePocketMoveRegex.find(cleanBody)?.let { match ->
            return resolvePocketToPocketMove(
                rawAmount = match.groupValues[1],
                rawSource = match.groupValues[2],
                rawDest = match.groupValues[3],
            )
        }

        activePocketMoveRegex.find(cleanBody)?.let { match ->
            return resolvePocketToPocketMove(
                rawAmount = match.groupValues[1],
                rawSource = match.groupValues[2],
                rawDest = match.groupValues[3],
            )
        }

        outOfPocketRegex.find(cleanBody)?.let { match ->
            val sourcePocket = normalizePocketName(match.groupValues[2])
            val (direction, counterparty, description) = if (isMainPocket(sourcePocket)) {
                Triple(Direction.OUTFLOW, "Jago Pocket", "Moved out of $MAIN_POCKET_HINT")
            } else {
                Triple(Direction.INFLOW, sourcePocket, "Moved from $sourcePocket to $MAIN_POCKET_HINT")
            }
            return parseTransferMatch(
                rawAmount = match.groupValues[1],
                counterparty = counterparty,
                direction = direction,
                description = description,
                accountHint = MAIN_POCKET_HINT,
            )
        }

        val amountToken = anyCurrencyAmountRegex.find(cleanBody)?.groupValues?.get(1)
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

    private fun resolvePocketToPocketMove(
        rawAmount: String,
        rawSource: String,
        rawDest: String,
    ): ParseResult {
        val source = normalizePocketName(rawSource)
        val dest = normalizePocketName(rawDest)
        val sourceIsMain = isMainPocket(source)
        val destIsMain = isMainPocket(dest)
        return when {
            sourceIsMain && !destIsMain -> parseTransferMatch(
                rawAmount = rawAmount,
                counterparty = dest,
                direction = Direction.OUTFLOW,
                description = "Moved from $MAIN_POCKET_HINT to $dest",
                accountHint = MAIN_POCKET_HINT,
            )

            !sourceIsMain && destIsMain -> parseTransferMatch(
                rawAmount = rawAmount,
                counterparty = source,
                direction = Direction.INFLOW,
                description = "Moved from $source to $MAIN_POCKET_HINT",
                accountHint = MAIN_POCKET_HINT,
            )

            else -> ParseResult.NotTransaction(NotTransactionReason.OTHER)
        }
    }

    private fun normalizePocketName(raw: String): String {
        var normalized = raw.trim().trimEnd('.').trim()
        if (normalized.startsWith("your ", ignoreCase = true)) {
            normalized = normalized.substring(5).trim()
        }
        if (isMainPocket(normalized)) {
            return if (normalized.equals(MAIN_POCKET_HINT, ignoreCase = true)) MAIN_POCKET_HINT else normalized
        }
        if (normalized.endsWith(" Pocket", ignoreCase = true)) {
            normalized = normalized.dropLast(7).trim()
        }
        return if (normalized.equals(MAIN_POCKET_HINT, ignoreCase = true)) MAIN_POCKET_HINT else normalized
    }

    private fun isMainPocket(normalizedName: String): Boolean {
        return normalizedName.equals(MAIN_POCKET_HINT, ignoreCase = true) ||
            normalizedName.equals("Kantong Utama", ignoreCase = true)
    }

    private fun parseTransferMatch(
        rawAmount: String,
        counterparty: String,
        direction: Direction,
        description: String,
        accountHint: String? = null,
    ): ParseResult = when (
        val amount = LocaleNumberParser.parse(rawAmount, "IDR", ProviderNumberLocale.INDONESIAN)
    ) {
        is ParseAmountResult.Parsed -> ParseResult.Success(
            ParsedTransaction(
                amountMinor = amount.amountMinor,
                currency = "IDR",
                direction = direction,
                type = TransactionType.TRANSFER,
                occurredAtEpochMillis = null,
                merchantRaw = counterparty,
                description = description,
                accountHint = accountHint,
                providerReference = null,
                reportedBalanceMinor = null,
            ),
        )

        is ParseAmountResult.Ambiguous -> ParseResult.NeedsReview(
            reason = ReviewReason.AMBIGUOUS_FIELD,
            partial = PartialTransaction(
                currency = "IDR",
                direction = direction,
                type = TransactionType.TRANSFER,
                merchantRaw = counterparty,
                description = description,
                accountHint = accountHint,
            ),
        )

        is ParseAmountResult.Invalid -> ParseResult.NeedsReview(
            reason = ReviewReason.INVALID_VALUE,
            partial = PartialTransaction(
                currency = "IDR",
                direction = direction,
                type = TransactionType.TRANSFER,
                merchantRaw = counterparty,
                description = description,
                accountHint = accountHint,
            ),
        )
    }

    companion object {
        const val SOURCE_ID = "com.jago.digitalBanking"
        const val MAIN_POCKET_HINT = "Main Pocket"

        private val tanyaJagoFooterRegex = Regex(
            """\s*(?:Need\s+help\?\s+Contact\s+Tanya\s+Jago.*|Butuh\s+bantuan\?\s+Hubungi\s+Tanya\s+Jago.*)$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val addMoneyBannerRegex = Regex(
            """\s*Click\s+here\s+to\s+see\s+how\s+to\s+add\s+money\s+to\s+Jago\.?""",
            RegexOption.IGNORE_CASE,
        )
        private val multiSpaceRegex = Regex("""\s+""")
        private val incomingTransferRegex = Regex(
            """^(.+?)\s+has\s+sent\s+((?:Rp\.?|IDR)\s*[0-9]+(?:[.,][0-9]+)*)\s+to\s+you\.?$""",
            RegexOption.IGNORE_CASE,
        )
        private val outgoingTransferRegex = Regex(
            """^You(?:'ve|’ve|\s+have)\s+transferred\s+((?:Rp\.?|IDR)\s*[0-9]+(?:[.,][0-9]+)*)\s+to\s+(.+?)\.?$""",
            RegexOption.IGNORE_CASE,
        )
        private val passivePocketMoveRegex = Regex(
            """^((?:Rp\.?|IDR)\s*[0-9]+(?:[.,][0-9]+)*)\s+has\s+been\s+moved\s+from\s+(?:your\s+)?(.+?)\s+to\s+(?:your\s+)?(.+?)\.?$""",
            RegexOption.IGNORE_CASE,
        )
        private val activePocketMoveRegex = Regex(
            """^You(?:'ve|’ve|\s+have)\s+moved\s+((?:Rp\.?|IDR)\s*[0-9]+(?:[.,][0-9]+)*)\s+from\s+(?:your\s+)?(.+?)\s+to\s+(?:your\s+)?(.+?)\.?$""",
            RegexOption.IGNORE_CASE,
        )
        private val outOfPocketRegex = Regex(
            """^You(?:'ve|’ve|\s+have)\s+moved\s+((?:Rp\.?|IDR)\s*[0-9]+(?:[.,][0-9]+)*)\s+out\s+of\s+(?:your\s+)?(.+?)\.?$""",
            RegexOption.IGNORE_CASE,
        )
        private val anyCurrencyAmountRegex = Regex(
            """((?:Rp\.?|IDR)\s*[0-9]+(?:[.,][0-9]+)*)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
