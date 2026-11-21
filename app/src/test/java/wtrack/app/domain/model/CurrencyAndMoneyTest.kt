package wtrack.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CurrencyAndMoneyTest {
    @Test
    fun idrUsesIso4217Exponent2() {
        val spec = CurrencyRegistry.specFor("IDR")
        assertThat(spec.minorUnitDigits).isEqualTo(2)
        assertThat(CurrencyRegistry.scaleFactor("IDR")).isEqualTo(100L)
    }

    @Test
    fun formatsIdrWithoutTrailingZeroCentsAndPreservesNonZeroSen() {
        assertThat(MoneyFormatter.format(5_000_000L, "IDR")).isEqualTo("Rp50.000")
        assertThat(MoneyFormatter.format(125_000_050L, "IDR")).isEqualTo("Rp1.250.000,50")
        assertThat(MoneyFormatter.format(-250_000L, "IDR")).isEqualTo("-Rp2.500")
        assertThat(MoneyFormatter.format(250_000L, "IDR", signed = true)).isEqualTo("+Rp2.500")
    }

    @Test
    fun formatsZeroDecimalAndStandardDecimalCurrenciesCorrectly() {
        assertThat(MoneyFormatter.format(1500L, "JPY")).isEqualTo("JPY 1,500")
        assertThat(MoneyFormatter.format(1250L, "USD")).isEqualTo("$12.50")
    }

    @Test
    fun parsesMajorInputAcrossGroupedAndPlainFormatsWithoutScalingErrors() {
        assertThat(MoneyFormatter.parseMajorInput("50000", "IDR")).isEqualTo(5_000_000L)
        assertThat(MoneyFormatter.parseMajorInput("50.000", "IDR")).isEqualTo(5_000_000L)
        assertThat(MoneyFormatter.parseMajorInput("50,000", "IDR")).isEqualTo(5_000_000L)
        assertThat(MoneyFormatter.parseMajorInput("1.250.000", "IDR")).isEqualTo(125_000_000L)
        assertThat(MoneyFormatter.parseMajorInput("1,250,000", "IDR")).isEqualTo(125_000_000L)
        assertThat(MoneyFormatter.parseMajorInput("1.250.000,50", "IDR")).isEqualTo(125_000_050L)
        assertThat(MoneyFormatter.parseMajorInput("1,250,000.50", "IDR")).isEqualTo(125_000_050L)
        assertThat(MoneyFormatter.parseMajorInput("50000.25", "IDR")).isEqualTo(5_000_025L)
        assertThat(MoneyFormatter.parseMajorInput("50000,25", "IDR")).isEqualTo(5_000_025L)
        assertThat(MoneyFormatter.parseMajorInput("Rp 50.000", "IDR")).isEqualTo(5_000_000L)
        assertThat(MoneyFormatter.parseMajorInput("IDR 50,000.00", "IDR")).isEqualTo(5_000_000L)
        assertThat(MoneyFormatter.parseMajorInput("0", "IDR")).isEqualTo(0L)
        assertThat(MoneyFormatter.parseMajorInput("-50.000", "IDR")).isEqualTo(-5_000_000L)

        // USD / JPY rules
        assertThat(MoneyFormatter.parseMajorInput("50.000", "USD")).isNull()
        assertThat(MoneyFormatter.parseMajorInput("50,000", "USD")).isEqualTo(5_000_000L)
        assertThat(MoneyFormatter.parseMajorInput("50.25", "USD")).isEqualTo(5_025L)
        assertThat(MoneyFormatter.parseMajorInput("1,500", "JPY")).isEqualTo(1_500L)

        // Malformed inputs rejected cleanly
        assertThat(MoneyFormatter.parseMajorInput("0.500", "IDR")).isNull()
        assertThat(MoneyFormatter.parseMajorInput("1.2345", "IDR")).isNull()
        assertThat(MoneyFormatter.parseMajorInput("50.00.0", "IDR")).isNull()
        assertThat(MoneyFormatter.parseMajorInput("50,0000", "IDR")).isNull()
        assertThat(MoneyFormatter.parseMajorInput("abc", "IDR")).isNull()
    }

    @Test
    fun formatForInputRoundTripsThroughParseMajorInput() {
        val cases = listOf(
            5_000_000L to "IDR",
            125_000_050L to "IDR",
            -5_000_000L to "IDR",
            1_250L to "USD",
            1_500L to "JPY",
        )
        cases.forEach { (minor, currency) ->
            val formatted = MoneyFormatter.formatForInput(minor, currency)
            assertThat(MoneyFormatter.parseMajorInput(formatted, currency)).isEqualTo(minor)
        }
    }

    @Test
    fun formatsCsvMajorDecimalLosslessly() {
        assertThat(MoneyFormatter.formatForCsv(5_000_000L, "IDR")).isEqualTo("50000.00")
        assertThat(MoneyFormatter.formatForCsv(125_000_050L, "IDR")).isEqualTo("1250000.50")
        assertThat(MoneyFormatter.formatForCsv(1500L, "JPY")).isEqualTo("1500")
    }
}
