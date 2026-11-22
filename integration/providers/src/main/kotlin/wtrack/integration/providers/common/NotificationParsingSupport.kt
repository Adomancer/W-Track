package wtrack.integration.providers.common

import wtrack.integration.NotificationEvent

data class CurrencyAmountMatch(
    val fullToken: String,
    val currency: String,
    val numberToken: String,
    val startIndex: Int,
    val endIndex: Int,
)

/**
 * Shared pure-Kotlin helpers for currency+amount token extraction, locale inference, and noise
 * trimming used by [wtrack.integration.providers.generic.GenericNotificationFallbackParser] and
 * [wtrack.integration.providers.custom.NotificationTemplateEngine].
 */
object NotificationParsingSupport {
    private val multiSpaceRegex = Regex("""\s+""")

    private val prefixCurrencyAmountRegex = Regex(
        """(?<![A-Za-z0-9])(US\$|S\$|Rp\.?|IDR|USD|SGD|MYR|RM|EUR|€|GBP|£|AUD|JPY|¥|KRW|₩|THB|HKD|CNY|\$)[\s\u00A0]*([0-9]+(?:[.,][0-9]+)*)(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE,
    )

    private val suffixCurrencyAmountRegex = Regex(
        """(?<![A-Za-z0-9.,])([0-9]+(?:[.,][0-9]+)*)[\s\u00A0]*(IDR|USD|SGD|MYR|EUR|JPY|KRW)(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE,
    )

    private val counterpartyPrepRegex = Regex(
        """\b(?:dari|ke|di|untuk|from|to|at)\s+(.+?)(?=(?:\s+(?:berhasil|sukses|successful|succeeded|completed|telah\s+diterima|telah\s+masuk|sudah\s+diterima|pada|tanggal|category|dengan|via|sebesar|senilai|saldo|balance|ref\b|id\b|\{IGNORE\})|[.,!?\n]|$))""",
        RegexOption.IGNORE_CASE,
    )

    fun extractBestBody(event: NotificationEvent): String =
        (event.bigText?.takeIf { it.isNotBlank() } ?: event.text)
            .replace(multiSpaceRegex, " ")
            .trim()

    fun combineTitleAndBody(event: NotificationEvent): String {
        val body = extractBestBody(event)
        return listOfNotNull(
            event.title?.replace(multiSpaceRegex, " ")?.trim()?.takeIf { it.isNotBlank() },
            body.takeIf { it.isNotBlank() },
        ).joinToString(" ").trim()
    }

    fun normalizeWhitespace(input: String): String =
        input.replace('\u00A0', ' ').replace(multiSpaceRegex, " ").trim()

    fun findCurrencyAmounts(text: String): List<CurrencyAmountMatch> {
        val matches = mutableListOf<CurrencyAmountMatch>()
        prefixCurrencyAmountRegex.findAll(text).forEach { match ->
            val rawCurrency = match.groupValues[1]
            val numberToken = match.groupValues[2]
            matches += CurrencyAmountMatch(
                fullToken = match.value,
                currency = mapCurrencySymbol(rawCurrency),
                numberToken = numberToken,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
            )
        }
        suffixCurrencyAmountRegex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            val overlapsExisting = matches.any { existing ->
                start < existing.endIndex && end > existing.startIndex
            }
            if (!overlapsExisting) {
                matches += CurrencyAmountMatch(
                    fullToken = match.value,
                    currency = mapCurrencySymbol(match.groupValues[2]),
                    numberToken = match.groupValues[1],
                    startIndex = start,
                    endIndex = end,
                )
            }
        }
        return matches.sortedBy { it.startIndex }
    }

    fun mapCurrencySymbol(rawCurrency: String): String = when (rawCurrency.trim().uppercase()) {
        "RP", "RP.", "IDR" -> "IDR"
        "US$", "$", "USD" -> "USD"
        "S$", "SGD" -> "SGD"
        "RM", "MYR" -> "MYR"
        "€", "EUR" -> "EUR"
        "£", "GBP" -> "GBP"
        "¥", "JPY" -> "JPY"
        "₩", "KRW" -> "KRW"
        else -> rawCurrency.trim().uppercase()
    }

    fun inferLocaleFromToken(
        numberToken: String,
        rawTokenOrPrefix: String,
        currency: String,
    ): ProviderNumberLocale {
        val hasDot = numberToken.contains('.')
        val hasComma = numberToken.contains(',')
        if (hasDot && hasComma) {
            return if (numberToken.lastIndexOf(',') > numberToken.lastIndexOf('.')) {
                ProviderNumberLocale.INDONESIAN
            } else {
                ProviderNumberLocale.ENGLISH
            }
        }
        if (hasComma && !hasDot) {
            val parts = numberToken.split(',')
            if (parts.size > 2 || (parts.size == 2 && parts[1].length == 3 && !parts[0].startsWith("0"))) {
                return ProviderNumberLocale.ENGLISH
            }
            return ProviderNumberLocale.INDONESIAN
        }
        if (hasDot && !hasComma) {
            val parts = numberToken.split('.')
            if (parts.size > 2) {
                return ProviderNumberLocale.INDONESIAN
            }
            val frac = parts[1]
            if (frac.length == 3 && !parts[0].startsWith("0") && parts[0].length <= 3) {
                return ProviderNumberLocale.INDONESIAN
            }
            if (frac.length <= 2) {
                return ProviderNumberLocale.ENGLISH
            }
        }
        return if (rawTokenOrPrefix.contains("rp", ignoreCase = true) || currency.equals("IDR", ignoreCase = true)) {
            ProviderNumberLocale.INDONESIAN
        } else {
            ProviderNumberLocale.ENGLISH
        }
    }

    fun parseAmountSmart(
        rawAmount: String,
        currency: String,
        preferredLocale: ProviderNumberLocale? = null,
    ): Pair<ParseAmountResult, ProviderNumberLocale> {
        val firstLocale = preferredLocale ?: if (currency.equals("IDR", ignoreCase = true)) {
            ProviderNumberLocale.INDONESIAN
        } else {
            ProviderNumberLocale.ENGLISH
        }
        val secondLocale = if (firstLocale == ProviderNumberLocale.INDONESIAN) {
            ProviderNumberLocale.ENGLISH
        } else {
            ProviderNumberLocale.INDONESIAN
        }
        val first = LocaleNumberParser.parse(rawAmount, currency, firstLocale)
        if (first is ParseAmountResult.Parsed) return first to firstLocale

        val second = LocaleNumberParser.parse(rawAmount, currency, secondLocale)
        if (second is ParseAmountResult.Parsed) return second to secondLocale

        return first to firstLocale
    }

    fun extractCounterpartyHint(text: String): String? {
        val match = counterpartyPrepRegex.find(text) ?: return null
        val candidate = trimTrailingNoise(match.groupValues[1])
        if (candidate.length < 2) return null
        if (candidate.contains("{") || candidate.contains("}")) return null
        val lower = candidate.lowercase()
        if (lower in setOf("akun", "rekening", "account", "saldo", "balance", "you", "anda", "kamu")) {
            return null
        }
        return candidate.take(60)
    }

    fun trimTrailingNoise(raw: String): String {
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
                cp == ':'.code ||
                cp == ';'.code ||
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
}
