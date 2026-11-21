package wtrack.app.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Explicit currency exponent registry (PRD section 7.1, ADR-004).
 * IDR uses ISO 4217 exponent 2 (1 IDR = 100 sen) as approved.
 */
data class CurrencySpec(
    val code: String,
    val minorUnitDigits: Int,
    val symbol: String,
)

object CurrencyRegistry {
    private val knownSpecs = listOf(
        CurrencySpec(code = "IDR", minorUnitDigits = 2, symbol = "Rp"),
        CurrencySpec(code = "USD", minorUnitDigits = 2, symbol = "$"),
        CurrencySpec(code = "EUR", minorUnitDigits = 2, symbol = "EUR "),
        CurrencySpec(code = "SGD", minorUnitDigits = 2, symbol = "S$"),
        CurrencySpec(code = "MYR", minorUnitDigits = 2, symbol = "RM"),
        CurrencySpec(code = "JPY", minorUnitDigits = 0, symbol = "JPY "),
        CurrencySpec(code = "KRW", minorUnitDigits = 0, symbol = "KRW "),
    ).associateBy { it.code }

    fun specFor(currencyCode: String): CurrencySpec {
        val normalized = currencyCode.trim().uppercase()
        return knownSpecs[normalized] ?: CurrencySpec(
            code = normalized.ifEmpty { "IDR" },
            minorUnitDigits = 2,
            symbol = "${normalized.ifEmpty { "IDR" }} ",
        )
    }

    fun scaleFactor(currencyCode: String): Long {
        val digits = specFor(currencyCode).minorUnitDigits
        var factor = 1L
        repeat(digits) { factor *= 10L }
        return factor
    }
}

object MoneyFormatter {
    /**
     * Formats integer minor units cleanly for display.
     * For IDR (exponent 2): elides ",00" when fractional sen is zero (`5_000_000L` -> `Rp50.000`),
     * and preserves exact two decimals otherwise (`125_000_050L` -> `Rp1.250.000,50`).
     */
    fun format(amountMinor: Long, currencyCode: String, signed: Boolean = false): String {
        val spec = CurrencyRegistry.specFor(currencyCode)
        val negative = amountMinor < 0L
        val absMinor = if (amountMinor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(amountMinor)
        val prefix = when {
            negative -> "-"
            signed && amountMinor > 0L -> "+"
            else -> ""
        }

        if (spec.minorUnitDigits == 0) {
            return "$prefix${spec.symbol}${groupThousands(absMinor.toString(), if (spec.code == "IDR") '.' else ',')}"
        }

        val factor = CurrencyRegistry.scaleFactor(spec.code)
        val major = absMinor / factor
        val minor = absMinor % factor
        val useIndonesianGrouping = spec.code == "IDR"
        val thousandsSep = if (useIndonesianGrouping) '.' else ','
        val decimalSep = if (useIndonesianGrouping) ',' else '.'
        val majorGrouped = groupThousands(major.toString(), thousandsSep)

        return if (useIndonesianGrouping && minor == 0L) {
            "$prefix${spec.symbol}$majorGrouped"
        } else {
            val minorStr = minor.toString().padStart(spec.minorUnitDigits, '0')
            "$prefix${spec.symbol}$majorGrouped$decimalSep$minorStr"
        }
    }

    /**
     * Formats minor units to canonical decimal representation for CSV export (`50000.00` or `1250000.50`).
     */
    fun formatForCsv(amountMinor: Long, currencyCode: String): String {
        val spec = CurrencyRegistry.specFor(currencyCode)
        return BigDecimal.valueOf(amountMinor)
            .movePointLeft(spec.minorUnitDigits)
            .setScale(spec.minorUnitDigits, RoundingMode.UNNECESSARY)
            .toPlainString()
    }

    /**
     * Formats minor units for pre-filling editable amount text fields without thousands clutter.
     */
    fun formatForInput(amountMinor: Long, currencyCode: String): String {
        val spec = CurrencyRegistry.specFor(currencyCode)
        if (spec.minorUnitDigits == 0) {
            return amountMinor.toString()
        }
        val factor = CurrencyRegistry.scaleFactor(spec.code)
        val negative = amountMinor < 0L
        val absMinor = if (amountMinor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(amountMinor)
        val major = absMinor / factor
        val minor = absMinor % factor
        val prefix = if (negative) "-" else ""
        return when {
            spec.code == "IDR" && minor == 0L -> "$prefix$major"
            spec.code == "IDR" -> "$prefix$major,${minor.toString().padStart(spec.minorUnitDigits, '0')}"
            else -> "$prefix$major.${minor.toString().padStart(spec.minorUnitDigits, '0')}"
        }
    }

    private val leadingCurrencyPrefixRegex = Regex(
        pattern = "^(?:IDR|Rp\\.?|USD|US\\$|SGD|S\\$|EUR|€|MYR|RM|JPY|¥|KRW|₩|\\$)+",
        option = RegexOption.IGNORE_CASE,
    )
    private val indonesianGroupedIntRegex = Regex("^\\d{1,3}(\\.\\d{3})+$")
    private val englishGroupedIntRegex = Regex("^\\d{1,3}(,\\d{3})+$")
    private val digitsOnlyRegex = Regex("^\\d+$")

    /**
     * Parses user-entered major-unit input strings into exact minor units according to [CurrencyRegistry].
     * Accepts plain digits (`50000`), Indonesian grouping (`50.000`, `1.250.000,50`), English grouping
     * (`50,000`, `1,250,000.50`), optional pasted currency prefixes (`Rp 50.000`), and decimal sen/cents.
     */
    fun parseMajorInput(input: String, currencyCode: String): Long? {
        var cleaned = input.replace('\u00A0', ' ').replace(" ", "").trim()
        if (cleaned.isEmpty()) return null

        var negative = false
        if (cleaned.startsWith("-") || cleaned.startsWith("+")) {
            negative = cleaned.startsWith("-")
            cleaned = cleaned.substring(1)
        }
        cleaned = cleaned.replace(leadingCurrencyPrefixRegex, "")
        if (!negative && (cleaned.startsWith("-") || cleaned.startsWith("+"))) {
            negative = cleaned.startsWith("-")
            cleaned = cleaned.substring(1)
        }
        if (cleaned.isEmpty() || !cleaned.matches(Regex("^[0-9.,]+$"))) return null

        val spec = CurrencyRegistry.specFor(currencyCode)
        val dotCount = cleaned.count { it == '.' }
        val commaCount = cleaned.count { it == ',' }

        val normalized = when {
            dotCount > 0 && commaCount > 0 -> {
                if (spec.minorUnitDigits == 0) return null
                val lastDot = cleaned.lastIndexOf('.')
                val lastComma = cleaned.lastIndexOf(',')
                if (lastComma > lastDot) {
                    val intPart = cleaned.substring(0, lastComma)
                    val fracPart = cleaned.substring(lastComma + 1)
                    if (!indonesianGroupedIntRegex.matches(intPart) ||
                        !digitsOnlyRegex.matches(fracPart) ||
                        fracPart.length !in 1..spec.minorUnitDigits
                    ) {
                        return null
                    }
                    "${intPart.replace(".", "")}.$fracPart"
                } else {
                    val intPart = cleaned.substring(0, lastDot)
                    val fracPart = cleaned.substring(lastDot + 1)
                    if (!englishGroupedIntRegex.matches(intPart) ||
                        !digitsOnlyRegex.matches(fracPart) ||
                        fracPart.length !in 1..spec.minorUnitDigits
                    ) {
                        return null
                    }
                    "${intPart.replace(",", "")}.$fracPart"
                }
            }

            dotCount > 1 -> {
                if (!indonesianGroupedIntRegex.matches(cleaned)) return null
                cleaned.replace(".", "")
            }

            commaCount > 1 -> {
                if (!englishGroupedIntRegex.matches(cleaned)) return null
                cleaned.replace(",", "")
            }

            dotCount == 1 -> {
                val parts = cleaned.split('.')
                val intPart = parts[0]
                val fracPart = parts[1]
                if (!digitsOnlyRegex.matches(intPart) || !digitsOnlyRegex.matches(fracPart)) return null
                when {
                    fracPart.length == 3 && intPart.length in 1..3 && !intPart.startsWith("0") -> {
                        if (spec.code == "IDR" || spec.minorUnitDigits == 0) {
                            intPart + fracPart
                        } else {
                            return null
                        }
                    }
                    fracPart.length in 1..spec.minorUnitDigits -> cleaned
                    else -> return null
                }
            }

            commaCount == 1 -> {
                val parts = cleaned.split(',')
                val intPart = parts[0]
                val fracPart = parts[1]
                if (!digitsOnlyRegex.matches(intPart) || !digitsOnlyRegex.matches(fracPart)) return null
                when {
                    fracPart.length == 3 && intPart.length in 1..3 && !intPart.startsWith("0") -> {
                        intPart + fracPart
                    }
                    fracPart.length in 1..spec.minorUnitDigits -> "$intPart.$fracPart"
                    else -> return null
                }
            }

            else -> {
                if (!digitsOnlyRegex.matches(cleaned)) return null
                cleaned
            }
        }

        val signedNormalized = if (negative) "-$normalized" else normalized
        val bd = signedNormalized.toBigDecimalOrNull() ?: return null
        if (bd.scale() > spec.minorUnitDigits) return null
        return try {
            bd.movePointRight(spec.minorUnitDigits).longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun groupThousands(digits: String, separator: Char): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder(digits.length + digits.length / 3)
        var count = 0
        for (i in digits.length - 1 downTo 0) {
            if (count > 0 && count % 3 == 0) {
                sb.append(separator)
            }
            sb.append(digits[i])
            count++
        }
        return sb.reverse().toString()
    }
}
