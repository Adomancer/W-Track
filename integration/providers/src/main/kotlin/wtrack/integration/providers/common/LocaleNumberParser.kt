package wtrack.integration.providers.common

import java.math.BigDecimal

enum class ProviderNumberLocale {
    INDONESIAN,
    ENGLISH,
    UNKNOWN,
}

sealed interface ParseAmountResult {
    data class Parsed(val amountMinor: Long) : ParseAmountResult
    data class Ambiguous(val rawToken: String, val reason: String) : ParseAmountResult
    data class Invalid(val rawToken: String, val reason: String) : ParseAmountResult
}

/**
 * Pure-Kotlin locale-aware number parser converting provider notification amount tokens into
 * integer minor units (PRD PAR-12 / ADR-004).
 *
 * Explicitly rejects ambiguous 3-digit separator strings (e.g. "1.500" or "1,500" when the
 * provider's language locale is not established) rather than risking a 1,000x misread.
 */
object LocaleNumberParser {

    private val leadingCurrencyRegex = Regex(
        pattern = "^(?:IDR|Rp\\.?|USD|US\\$|SGD|S\\$|EUR|€|MYR|RM|JPY|¥|KRW|₩|\\$)[\\s\\u00A0]*",
        option = RegexOption.IGNORE_CASE,
    )
    private val trailingCurrencyRegex = Regex(
        pattern = "[\\s\\u00A0]*(?:IDR|Rp\\.?|USD|SGD|EUR|MYR|JPY|KRW)$",
        option = RegexOption.IGNORE_CASE,
    )
    private val pureDigitsRegex = Regex("^\\d+$")
    private val validTokenCharsRegex = Regex("^[0-9.,]+$")

    fun minorUnitDigitsFor(currencyCode: String): Int = when (currencyCode.trim().uppercase()) {
        "JPY", "KRW", "VND" -> 0
        "BHD", "JOD", "KWD", "OMR", "TND" -> 3
        else -> 2
    }

    fun parse(
        rawAmount: String,
        currencyCode: String = "IDR",
        locale: ProviderNumberLocale = ProviderNumberLocale.UNKNOWN,
    ): ParseAmountResult {
        val trimmed = rawAmount.replace('\u00A0', ' ').trim()
        if (trimmed.isEmpty()) {
            return ParseAmountResult.Invalid(rawAmount, "Empty amount")
        }
        val stripped = trimmed
            .replace(leadingCurrencyRegex, "")
            .replace(trailingCurrencyRegex, "")
            .replace(" ", "")
        if (stripped.isEmpty()) {
            return ParseAmountResult.Invalid(rawAmount, "Missing digits")
        }
        if (stripped.startsWith("-") || stripped.startsWith("+")) {
            return ParseAmountResult.Invalid(rawAmount, "Signed amounts must be represented by Direction")
        }
        if (!validTokenCharsRegex.matches(stripped)) {
            return ParseAmountResult.Invalid(rawAmount, "Unexpected characters in amount token")
        }

        val minorDigits = minorUnitDigitsFor(currencyCode)
        val dotCount = stripped.count { it == '.' }
        val commaCount = stripped.count { it == ',' }

        return when {
            dotCount == 0 && commaCount == 0 -> scaleIntegerToken(rawAmount, stripped, minorDigits)

            dotCount > 0 && commaCount > 0 -> parseMixedSeparators(rawAmount, stripped, minorDigits)

            dotCount > 0 -> parseDotOnly(rawAmount, stripped, dotCount, minorDigits, locale)

            else -> parseCommaOnly(rawAmount, stripped, commaCount, minorDigits, locale)
        }
    }

    private fun parseMixedSeparators(
        rawAmount: String,
        token: String,
        minorDigits: Int,
    ): ParseAmountResult {
        val lastDot = token.lastIndexOf('.')
        val lastComma = token.lastIndexOf(',')
        return if (lastComma > lastDot) {
            // Indonesian mixed grouping/decimal: 1.250.000,50
            val intPart = token.substring(0, lastComma)
            val fracPart = token.substring(lastComma + 1)
            if (intPart.contains(',') || !isValidThousandsGroups(intPart, '.')) {
                return ParseAmountResult.Ambiguous(rawAmount, "Malformed Indonesian grouping separators")
            }
            scaleDecimalToken(rawAmount, intPart.replace(".", ""), fracPart, minorDigits)
        } else {
            // English mixed grouping/decimal: 1,250,000.50
            val intPart = token.substring(0, lastDot)
            val fracPart = token.substring(lastDot + 1)
            if (intPart.contains('.') || !isValidThousandsGroups(intPart, ',')) {
                return ParseAmountResult.Ambiguous(rawAmount, "Malformed English grouping separators")
            }
            scaleDecimalToken(rawAmount, intPart.replace(",", ""), fracPart, minorDigits)
        }
    }

    private fun parseDotOnly(
        rawAmount: String,
        token: String,
        dotCount: Int,
        minorDigits: Int,
        locale: ProviderNumberLocale,
    ): ParseAmountResult {
        if (dotCount > 1) {
            if (!isValidThousandsGroups(token, '.')) {
                return ParseAmountResult.Invalid(rawAmount, "Invalid multi-dot grouping")
            }
            return scaleIntegerToken(rawAmount, token.replace(".", ""), minorDigits)
        }

        val parts = token.split('.')
        val intPart = parts[0]
        val fracPart = parts[1]
        if (intPart.isEmpty() || fracPart.isEmpty()) {
            return ParseAmountResult.Invalid(rawAmount, "Dangling dot separator")
        }

        if (fracPart.length == 3 && !intPart.startsWith("0") && intPart.length <= 3) {
            return when (locale) {
                ProviderNumberLocale.INDONESIAN ->
                    scaleIntegerToken(rawAmount, intPart + fracPart, minorDigits)

                ProviderNumberLocale.ENGLISH,
                ProviderNumberLocale.UNKNOWN ->
                    ParseAmountResult.Ambiguous(
                        rawAmount,
                        "Dot followed by 3 digits is ambiguous without established Indonesian provider locale",
                    )
            }
        }

        if (fracPart.length <= minorDigits) {
            return when (locale) {
                ProviderNumberLocale.ENGLISH ->
                    scaleDecimalToken(rawAmount, intPart, fracPart, minorDigits)

                ProviderNumberLocale.UNKNOWN ->
                    ParseAmountResult.Ambiguous(
                        rawAmount,
                        "Single dot separator without established provider locale cannot be guessed safely",
                    )

                ProviderNumberLocale.INDONESIAN ->
                    ParseAmountResult.Ambiguous(
                        rawAmount,
                        "Indonesian locale expects comma decimals or 3-digit dot thousands groups",
                    )
            }
        }

        return ParseAmountResult.Ambiguous(rawAmount, "Ambiguous dot separator pattern")
    }

    private fun parseCommaOnly(
        rawAmount: String,
        token: String,
        commaCount: Int,
        minorDigits: Int,
        locale: ProviderNumberLocale,
    ): ParseAmountResult {
        if (commaCount > 1) {
            if (!isValidThousandsGroups(token, ',')) {
                return ParseAmountResult.Invalid(rawAmount, "Invalid multi-comma grouping")
            }
            return scaleIntegerToken(rawAmount, token.replace(",", ""), minorDigits)
        }

        val parts = token.split(',')
        val intPart = parts[0]
        val fracPart = parts[1]
        if (intPart.isEmpty() || fracPart.isEmpty()) {
            return ParseAmountResult.Invalid(rawAmount, "Dangling comma separator")
        }

        if (fracPart.length == 3 && !intPart.startsWith("0") && intPart.length <= 3) {
            return when (locale) {
                ProviderNumberLocale.ENGLISH ->
                    scaleIntegerToken(rawAmount, intPart + fracPart, minorDigits)

                ProviderNumberLocale.INDONESIAN,
                ProviderNumberLocale.UNKNOWN ->
                    ParseAmountResult.Ambiguous(
                        rawAmount,
                        "Comma followed by 3 digits is ambiguous without established English provider locale",
                    )
            }
        }

        if (fracPart.length <= minorDigits) {
            return when (locale) {
                ProviderNumberLocale.INDONESIAN,
                ProviderNumberLocale.UNKNOWN ->
                    scaleDecimalToken(rawAmount, intPart, fracPart, minorDigits)

                ProviderNumberLocale.ENGLISH ->
                    ParseAmountResult.Ambiguous(
                        rawAmount,
                        "English locale expects dot decimals or 3-digit comma thousands groups",
                    )
            }
        }

        return ParseAmountResult.Ambiguous(rawAmount, "Ambiguous comma separator pattern")
    }

    private fun isValidThousandsGroups(token: String, separator: Char): Boolean {
        val groups = token.split(separator)
        if (groups.size < 2) return false
        val first = groups.first()
        if (first.isEmpty() || first.length > 3 || !pureDigitsRegex.matches(first)) return false
        if (first.startsWith("0")) return false
        return groups.drop(1).all { it.length == 3 && pureDigitsRegex.matches(it) }
    }

    private fun scaleIntegerToken(rawAmount: String, digits: String, minorDigits: Int): ParseAmountResult {
        if (!pureDigitsRegex.matches(digits)) {
            return ParseAmountResult.Invalid(rawAmount, "Non-digit integer token")
        }
        return runCatching {
            val minor = BigDecimal(digits).movePointRight(minorDigits).longValueExact()
            if (minor <= 0L) {
                ParseAmountResult.Invalid(rawAmount, "Amount must be positive")
            } else {
                ParseAmountResult.Parsed(minor)
            }
        }.getOrElse {
            ParseAmountResult.Invalid(rawAmount, "Amount out of Long range")
        }
    }

    private fun scaleDecimalToken(
        rawAmount: String,
        intDigits: String,
        fracDigits: String,
        minorDigits: Int,
    ): ParseAmountResult {
        if (!pureDigitsRegex.matches(intDigits) || !pureDigitsRegex.matches(fracDigits)) {
            return ParseAmountResult.Invalid(rawAmount, "Non-digit decimal components")
        }
        if (fracDigits.length > minorDigits) {
            return ParseAmountResult.Ambiguous(rawAmount, "Fractional precision exceeds currency exponent $minorDigits")
        }
        return runCatching {
            val minor = BigDecimal("$intDigits.$fracDigits").movePointRight(minorDigits).longValueExact()
            if (minor <= 0L) {
                ParseAmountResult.Invalid(rawAmount, "Amount must be positive")
            } else {
                ParseAmountResult.Parsed(minor)
            }
        }.getOrElse {
            ParseAmountResult.Invalid(rawAmount, "Amount out of Long range")
        }
    }
}
