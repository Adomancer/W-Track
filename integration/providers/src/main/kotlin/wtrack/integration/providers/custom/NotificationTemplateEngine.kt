package wtrack.integration.providers.custom

import wtrack.integration.Direction
import wtrack.integration.NotTransactionReason
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionType
import wtrack.integration.providers.common.LocaleNumberParser
import wtrack.integration.providers.common.NotificationParsingSupport
import wtrack.integration.providers.common.ParseAmountResult
import wtrack.integration.providers.common.ProviderNumberLocale
import wtrack.integration.providers.common.SensitiveContentGuard

data class CustomParserRuleSpec(
    val id: Long,
    val sourceId: String,
    val name: String,
    val titleContains: String?,
    val templatePattern: String,
    val direction: Direction,
    val transactionType: TransactionType,
    val currency: String,
    val numberLocale: ProviderNumberLocale,
    val accountHint: String? = null,
    val updatedAt: Long = 0L,
)

data class TemplateSynthesisResult(
    val templatePattern: String,
    val currency: String,
    val numberLocale: ProviderNumberLocale,
    val detectedMerchant: String?,
    val detectedAmountMinor: Long?,
)

class CompiledNotificationTemplate internal constructor(
    val regex: Regex,
    val amountGroupIndex: Int,
    val merchantGroupIndex: Int?,
    val refGroupIndex: Int?,
    val literalCharCount: Int,
)

/**
 * Pure-Kotlin safe template compiler, auto-synthesizer, and matcher for user-taught notification
 * patterns (`{AMOUNT}`, `{MERCHANT}`, `{REF}`, `{IGNORE}`).
 *
 * Security & safety:
 * - User templates are tokenized on known placeholders and literal chunks are escaped with
 *   [Regex.escape] (zero raw user regex evaluation, preventing ReDoS).
 * - Templates that look like OTP/PIN/security notices or contain fewer than 3 literal characters
 *   are rejected at validation time.
 */
object NotificationTemplateEngine {
    const val PLACEHOLDER_AMOUNT = "{AMOUNT}"
    const val PLACEHOLDER_MERCHANT = "{MERCHANT}"
    const val PLACEHOLDER_REF = "{REF}"
    const val PLACEHOLDER_IGNORE = "{IGNORE}"

    private val placeholderRegex = Regex("""\{(AMOUNT|MERCHANT|REF|IGNORE)\}""")
    private val whitespaceRunRegex = Regex("""\s+""")

    // Captures either prefixed currency+amount, suffixed currency+amount, or bare numeric token.
    private const val AMOUNT_GROUP_PATTERN =
        """((?:(?:US\$|S\$|Rp\.?|IDR|USD|SGD|MYR|RM|EUR|€|GBP|£|AUD|JPY|¥|KRW|₩|THB|HKD|CNY|\$)[\s\u00A0]*)?[0-9]+(?:[.,][0-9]+)*(?:[\s\u00A0]*(?:IDR|USD|SGD|MYR|EUR|JPY|KRW))?)"""
    private const val MERCHANT_GROUP_PATTERN = """(.+?)"""
    private const val REF_GROUP_PATTERN = """([A-Za-z0-9._:/-]+)"""
    private const val IGNORE_GROUP_PATTERN = """(?:.+?)"""

    private val dateTimeVolatileRegex = Regex(
        """\b(?:\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2}|\d{1,2}:\d{2}(?::\d{2})?(?:\s*(?:WIB|WITA|WIT|UTC|GMT))?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val refVolatileRegex = Regex(
        """\b(?:ref(?:erence)?|no\.?\s*ref|trx\s*id|id\s*transaksi)\s*[:#-]?\s*[A-Za-z0-9._:/-]{4,}""",
        RegexOption.IGNORE_CASE,
    )
    private val longNumericVolatileRegex = Regex("""(?:#\d{4,}|\b\d{6,}\b)""")
    private val repeatedIgnoreRegex = Regex("""(?:\{IGNORE\}\s*){2,}""")
    private val securityTriggerRegexes = listOf(
        Regex("""\botp\b""", RegexOption.IGNORE_CASE),
        Regex("""verification\s*code""", RegexOption.IGNORE_CASE),
        Regex("""kode\s*(?:verifikasi|otp|rahasia|keamanan)""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:password|passcode|mpin|cvv|cvc)\b""", RegexOption.IGNORE_CASE),
    )

    fun validateTemplate(templatePattern: String): String? {
        val normalized = NotificationParsingSupport.normalizeWhitespace(templatePattern)
        if (normalized.isBlank()) return "Template pattern cannot be empty."

        val tokens = placeholderRegex.findAll(normalized).map { it.value }.toList()
        val amountCount = tokens.count { it == PLACEHOLDER_AMOUNT }
        if (amountCount != 1) {
            return "Template must include $PLACEHOLDER_AMOUNT exactly once."
        }
        if (tokens.count { it == PLACEHOLDER_MERCHANT } > 1) {
            return "Template can include $PLACEHOLDER_MERCHANT at most once."
        }
        if (tokens.count { it == PLACEHOLDER_REF } > 1) {
            return "Template can include $PLACEHOLDER_REF at most once."
        }

        val literalOnly = placeholderRegex.replace(normalized, " ")
            .replace(whitespaceRunRegex, "")
            .trim()
        if (literalOnly.length < 3) {
            return "Template must contain at least 3 non-placeholder characters so it does not match unrelated notifications."
        }
        if (securityTriggerRegexes.any { it.containsMatchIn(literalOnly) }) {
            return "Templates cannot target OTP, PIN, or security verification alerts."
        }
        return null
    }

    fun compileTemplate(templatePattern: String): CompiledNotificationTemplate? {
        if (validateTemplate(templatePattern) != null) return null
        val normalized = NotificationParsingSupport.normalizeWhitespace(templatePattern)

        val sb = StringBuilder("^")
        var cursor = 0
        var groupIndex = 0
        var amountGroup: Int? = null
        var merchantGroup: Int? = null
        var refGroup: Int? = null
        var literalChars = 0

        placeholderRegex.findAll(normalized).forEach { match ->
            if (match.range.first > cursor) {
                val literal = normalized.substring(cursor, match.range.first)
                literalChars += literal.count { !it.isWhitespace() }
                appendEscapedLiteral(sb, literal)
            }
            when (match.value) {
                PLACEHOLDER_AMOUNT -> {
                    groupIndex++
                    amountGroup = groupIndex
                    sb.append(AMOUNT_GROUP_PATTERN)
                }
                PLACEHOLDER_MERCHANT -> {
                    groupIndex++
                    merchantGroup = groupIndex
                    sb.append(MERCHANT_GROUP_PATTERN)
                }
                PLACEHOLDER_REF -> {
                    groupIndex++
                    refGroup = groupIndex
                    sb.append(REF_GROUP_PATTERN)
                }
                PLACEHOLDER_IGNORE -> sb.append(IGNORE_GROUP_PATTERN)
            }
            cursor = match.range.last + 1
        }
        if (cursor < normalized.length) {
            val tail = normalized.substring(cursor)
            literalChars += tail.count { !it.isWhitespace() }
            appendEscapedLiteral(sb, tail)
        }
        // Tolerate optional trailing punctuation/emojis/whitespace at the end of a notification body.
        sb.append("""[\s.,!?\p{So}\uFE0E\uFE0F]*$""")

        val resolvedAmountGroup = amountGroup ?: return null
        return CompiledNotificationTemplate(
            regex = Regex(sb.toString(), setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            amountGroupIndex = resolvedAmountGroup,
            merchantGroupIndex = merchantGroup,
            refGroupIndex = refGroup,
            literalCharCount = literalChars,
        )
    }

    /**
     * Synthesizes a reusable template pattern from a real sample notification body by replacing
     * the primary currency amount with `{AMOUNT}`, optional secondary balances/dates/refs with
     * `{IGNORE}`, and detected merchant/counterparty with `{MERCHANT}`.
     */
    fun generateTemplateFromSample(
        sampleBody: String,
        merchantOrDescHint: String? = null,
        defaultCurrency: String = "IDR",
    ): TemplateSynthesisResult? {
        val cleanSample = NotificationParsingSupport.trimTrailingNoise(
            NotificationParsingSupport.normalizeWhitespace(sampleBody),
        )
        if (cleanSample.isBlank()) return null

        val amounts = NotificationParsingSupport.findCurrencyAmounts(cleanSample)
        if (amounts.isEmpty()) return null

        val primary = amounts.first()
        val inferredLocale = NotificationParsingSupport.inferLocaleFromToken(
            numberToken = primary.numberToken,
            rawTokenOrPrefix = primary.fullToken,
            currency = primary.currency,
        )
        val (parsedPrimary, resolvedLocale) = NotificationParsingSupport.parseAmountSmart(
            rawAmount = primary.fullToken,
            currency = primary.currency,
            preferredLocale = inferredLocale,
        )
        val detectedAmountMinor = (parsedPrimary as? ParseAmountResult.Parsed)?.amountMinor
        val currency = primary.currency.ifBlank { defaultCurrency.uppercase() }

        // Build template working backwards from offsets so replacements never shift earlier indices.
        val builder = StringBuilder(cleanSample)
        val secondaryAmounts = amounts.drop(1)
        for (sec in secondaryAmounts.asReversed()) {
            builder.replace(sec.startIndex, sec.endIndex, PLACEHOLDER_IGNORE)
        }
        builder.replace(primary.startIndex, primary.endIndex, PLACEHOLDER_AMOUNT)

        var working = builder.toString()
        working = dateTimeVolatileRegex.replace(working, PLACEHOLDER_IGNORE)
        working = refVolatileRegex.replace(working, PLACEHOLDER_IGNORE)
        working = longNumericVolatileRegex.replace(working, PLACEHOLDER_IGNORE)
        working = repeatedIgnoreRegex.replace(working, "$PLACEHOLDER_IGNORE ")

        var detectedMerchant: String? = null
        val cleanedHint = merchantOrDescHint
            ?.let { NotificationParsingSupport.trimTrailingNoise(it) }
            ?.takeIf { hint ->
                hint.length >= 2 &&
                    !hint.contains('{') &&
                    !hint.contains('}') &&
                    hint.lowercase() != cleanSample.lowercase()
            }

        if (cleanedHint != null) {
            val hintRegex = Regex("""(?<![A-Za-z0-9])${Regex.escape(cleanedHint)}(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)
            val hintMatch = hintRegex.find(working)
            if (hintMatch != null && !insidePlaceholder(working, hintMatch.range.first)) {
                working = working.substring(0, hintMatch.range.first) +
                    PLACEHOLDER_MERCHANT +
                    working.substring(hintMatch.range.last + 1)
                detectedMerchant = cleanedHint
            }
        }

        if (detectedMerchant == null) {
            val candidate = NotificationParsingSupport.extractCounterpartyHint(working)
            if (candidate != null) {
                val candRegex = Regex("""(?<![A-Za-z0-9])${Regex.escape(candidate)}(?![A-Za-z0-9])""")
                val candMatch = candRegex.find(working)
                if (candMatch != null && !insidePlaceholder(working, candMatch.range.first)) {
                    working = working.substring(0, candMatch.range.first) +
                        PLACEHOLDER_MERCHANT +
                        working.substring(candMatch.range.last + 1)
                    detectedMerchant = candidate
                }
            }
        }

        val normalizedTemplate = NotificationParsingSupport.normalizeWhitespace(
            repeatedIgnoreRegex.replace(working, "$PLACEHOLDER_IGNORE "),
        )

        val candidateTemplate = if (validateTemplate(normalizedTemplate) == null &&
            matchesSample(normalizedTemplate, cleanSample, currency, resolvedLocale)
        ) {
            normalizedTemplate
        } else {
            val minimalBuilder = StringBuilder(cleanSample)
            for (sec in secondaryAmounts.asReversed()) {
                minimalBuilder.replace(sec.startIndex, sec.endIndex, PLACEHOLDER_IGNORE)
            }
            minimalBuilder.replace(primary.startIndex, primary.endIndex, PLACEHOLDER_AMOUNT)
            val minimal = NotificationParsingSupport.normalizeWhitespace(minimalBuilder.toString())
            if (validateTemplate(minimal) != null) return null
            detectedMerchant = NotificationParsingSupport.extractCounterpartyHint(cleanSample)
            minimal
        }

        return TemplateSynthesisResult(
            templatePattern = candidateTemplate,
            currency = currency,
            numberLocale = resolvedLocale,
            detectedMerchant = detectedMerchant,
            detectedAmountMinor = detectedAmountMinor,
        )
    }

    fun parseWithRules(
        event: NotificationEvent,
        rules: List<CustomParserRuleSpec>,
    ): ParseResult? {
        if (rules.isEmpty()) return null
        if (event.isGroupSummary) return ParseResult.NotTransaction(NotTransactionReason.OTHER)
        SensitiveContentGuard.inspectPreconditions(event)?.let { return it }
        SensitiveContentGuard.inspectNonTransactionNotice(event)?.let { return it }

        val cleanBody = NotificationParsingSupport.extractBestBody(event)
        if (cleanBody.isBlank()) return null

        val sortedRules = rules
            .mapNotNull { rule ->
                val compiled = compileTemplate(rule.templatePattern) ?: return@mapNotNull null
                rule to compiled
            }
            .sortedWith(
                compareByDescending<Pair<CustomParserRuleSpec, CompiledNotificationTemplate>> {
                    !it.first.titleContains.isNullOrBlank()
                }
                    .thenByDescending { it.second.literalCharCount }
                    .thenByDescending { it.first.updatedAt }
                    .thenByDescending { it.first.id },
            )

        for ((rule, compiled) in sortedRules) {
            val titleFilter = rule.titleContains?.trim()?.takeIf { it.isNotEmpty() }
            if (titleFilter != null) {
                val eventTitle = event.title.orEmpty()
                if (!eventTitle.contains(titleFilter, ignoreCase = true)) {
                    continue
                }
            }

            val match = compiled.regex.matchEntire(cleanBody) ?: continue
            val rawAmount = match.groupValues.getOrNull(compiled.amountGroupIndex)?.trim().orEmpty()
            val rawMerchant = compiled.merchantGroupIndex
                ?.let { idx -> match.groupValues.getOrNull(idx) }
                ?.let { NotificationParsingSupport.trimTrailingNoise(it) }
                ?.takeIf { it.isNotEmpty() }
            val rawRef = compiled.refGroupIndex
                ?.let { idx -> match.groupValues.getOrNull(idx) }
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            val detectedInlineCurrency = NotificationParsingSupport.findCurrencyAmounts(rawAmount)
                .firstOrNull()
                ?.currency
            val targetCurrency = (detectedInlineCurrency ?: rule.currency).trim().uppercase()
            val description = when {
                rawMerchant != null -> "${rule.name}: $rawMerchant"
                else -> cleanBody.take(120)
            }

            return when (
                val amountResult = LocaleNumberParser.parse(rawAmount, targetCurrency, rule.numberLocale)
            ) {
                is ParseAmountResult.Parsed -> ParseResult.Success(
                    ParsedTransaction(
                        amountMinor = amountResult.amountMinor,
                        currency = targetCurrency,
                        direction = rule.direction,
                        type = rule.transactionType,
                        occurredAtEpochMillis = null,
                        merchantRaw = rawMerchant,
                        description = description,
                        accountHint = rule.accountHint?.trim()?.takeIf { it.isNotEmpty() },
                        providerReference = rawRef,
                        reportedBalanceMinor = null,
                    ),
                )

                is ParseAmountResult.Ambiguous -> ParseResult.NeedsReview(
                    reason = ReviewReason.AMBIGUOUS_FIELD,
                    partial = PartialTransaction(
                        currency = targetCurrency,
                        direction = rule.direction,
                        type = rule.transactionType,
                        merchantRaw = rawMerchant,
                        description = description,
                        accountHint = rule.accountHint,
                        providerReference = rawRef,
                    ),
                )

                is ParseAmountResult.Invalid -> ParseResult.NeedsReview(
                    reason = ReviewReason.INVALID_VALUE,
                    partial = PartialTransaction(
                        currency = targetCurrency,
                        direction = rule.direction,
                        type = rule.transactionType,
                        merchantRaw = rawMerchant,
                        description = description,
                        accountHint = rule.accountHint,
                        providerReference = rawRef,
                    ),
                )
            }
        }
        return null
    }

    private fun matchesSample(
        templatePattern: String,
        cleanSample: String,
        currency: String,
        locale: ProviderNumberLocale,
    ): Boolean {
        val compiled = compileTemplate(templatePattern) ?: return false
        val match = compiled.regex.matchEntire(cleanSample) ?: return false
        val rawAmount = match.groupValues.getOrNull(compiled.amountGroupIndex)?.trim().orEmpty()
        return LocaleNumberParser.parse(rawAmount, currency, locale) is ParseAmountResult.Parsed
    }

    private fun insidePlaceholder(text: String, index: Int): Boolean {
        val lastOpen = text.lastIndexOf('{', index)
        if (lastOpen < 0) return false
        val lastClose = text.lastIndexOf('}', index)
        return lastOpen > lastClose
    }

    private fun appendEscapedLiteral(sb: StringBuilder, literal: String) {
        var cursor = 0
        whitespaceRunRegex.findAll(literal).forEach { ws ->
            if (ws.range.first > cursor) {
                sb.append(Regex.escape(literal.substring(cursor, ws.range.first)))
            }
            sb.append("""\s+""")
            cursor = ws.range.last + 1
        }
        if (cursor < literal.length) {
            sb.append(Regex.escape(literal.substring(cursor)))
        }
    }
}
