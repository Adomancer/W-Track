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
     * Parses a user-entered major-unit input string (supports Indonesian `,` or standard `.` decimal)
     * into exact minor units according to [CurrencyRegistry]. Returns null when malformed or > allowed scale.
     */
    fun parseMajorInput(input: String, currencyCode: String): Long? {
        val cleaned = input.trim().replace(" ", "")
        if (cleaned.isEmpty()) return null
        val spec = CurrencyRegistry.specFor(currencyCode)

        // Accept either plain digits, Indonesian format (50.000 / 1.250.000,50), or plain dot decimal (1250000.50).
        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') -> {
                // Only valid if dots precede comma (e.g. 1.250.000,50) or commas precede dot (1,250,000.50).
                val lastDot = cleaned.lastIndexOf('.')
                val lastComma = cleaned.lastIndexOf(',')
                if (lastComma > lastDot) {
                    cleaned.replace(".", "").replace(',', '.')
                } else {
                    cleaned.replace(",", "")
                }
            }
            cleaned.contains(',') -> {
                val parts = cleaned.split(',')
                if (parts.size != 2 || parts[1].length > spec.minorUnitDigits) return null
                "${parts[0]}.${parts[1]}"
            }
            cleaned.count { it == '.' } > 1 -> {
                // Multiple dots: Indonesian thousands separator (e.g., 1.250.000)
                if (!cleaned.matches(Regex("^\\d{1,3}(\\.\\d{3})+$"))) return null
                cleaned.replace(".", "")
            }
            cleaned.contains('.') -> {
                val parts = cleaned.split('.')
                if (parts.size != 2) return null
                // If user typed Rp grouping like 50.000 (3 trailing digits) for IDR, disallow silent ambiguity or require exact scale:
                if (parts[1].length == 3 && spec.minorUnitDigits == 2) return null
                if (parts[1].length > spec.minorUnitDigits) return null
                cleaned
            }
            else -> cleaned
        }

        val bd = normalized.toBigDecimalOrNull() ?: return null
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
