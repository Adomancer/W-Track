package wtrack.integration.providers

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wtrack.integration.providers.common.LocaleNumberParser
import wtrack.integration.providers.common.ParseAmountResult
import wtrack.integration.providers.common.ProviderNumberLocale

class LocaleNumberParserTest {

    @Test
    fun parsesIndonesianRupiahWithMinorExponentTwoWithoutHundredFoldErrors() {
        assertThat(LocaleNumberParser.parse("Rp 50.000", "IDR", ProviderNumberLocale.INDONESIAN))
            .isEqualTo(ParseAmountResult.Parsed(5_000_000L))
        assertThat(LocaleNumberParser.parse("Rp50.000", "IDR", ProviderNumberLocale.INDONESIAN))
            .isEqualTo(ParseAmountResult.Parsed(5_000_000L))
        assertThat(LocaleNumberParser.parse("Rp 1.250.000,50", "IDR", ProviderNumberLocale.INDONESIAN))
            .isEqualTo(ParseAmountResult.Parsed(125_000_050L))
        assertThat(LocaleNumberParser.parse("IDR 1.250.000", "IDR", ProviderNumberLocale.INDONESIAN))
            .isEqualTo(ParseAmountResult.Parsed(125_000_000L))
        assertThat(LocaleNumberParser.parse("12500,5", "IDR", ProviderNumberLocale.INDONESIAN))
            .isEqualTo(ParseAmountResult.Parsed(1_250_050L))
        assertThat(LocaleNumberParser.parse("50000", "IDR", ProviderNumberLocale.UNKNOWN))
            .isEqualTo(ParseAmountResult.Parsed(5_000_000L))
    }

    @Test
    fun parsesEnglishFormattedAmountsIncludingMixedSeparatorsAndZeroExponentCurrencies() {
        assertThat(LocaleNumberParser.parse("$12.50", "USD", ProviderNumberLocale.ENGLISH))
            .isEqualTo(ParseAmountResult.Parsed(1_250L))
        assertThat(LocaleNumberParser.parse("IDR 1,250,000.50", "IDR", ProviderNumberLocale.ENGLISH))
            .isEqualTo(ParseAmountResult.Parsed(125_000_050L))
        assertThat(LocaleNumberParser.parse("IDR 1,500", "IDR", ProviderNumberLocale.ENGLISH))
            .isEqualTo(ParseAmountResult.Parsed(150_000L))
        assertThat(LocaleNumberParser.parse("JPY 1,500", "JPY", ProviderNumberLocale.ENGLISH))
            .isEqualTo(ParseAmountResult.Parsed(1_500L))
    }

    @Test
    fun rejectsUnDisambiguatedThreeDigitSeparatorsAndConflictingLocaleFormats() {
        // PAR-12: "1.500" / "1,500" with UNKNOWN locale must NEVER guess between 1,500 and 1.50
        assertThat(LocaleNumberParser.parse("1.500", "IDR", ProviderNumberLocale.UNKNOWN))
            .isInstanceOf(ParseAmountResult.Ambiguous::class.java)
        assertThat(LocaleNumberParser.parse("1,500", "IDR", ProviderNumberLocale.UNKNOWN))
            .isInstanceOf(ParseAmountResult.Ambiguous::class.java)

        // Conflicting locale + separator checks
        assertThat(LocaleNumberParser.parse("1.500", "USD", ProviderNumberLocale.ENGLISH))
            .isInstanceOf(ParseAmountResult.Ambiguous::class.java)
        assertThat(LocaleNumberParser.parse("1,500", "IDR", ProviderNumberLocale.INDONESIAN))
            .isInstanceOf(ParseAmountResult.Ambiguous::class.java)
        assertThat(LocaleNumberParser.parse("50.25", "IDR", ProviderNumberLocale.INDONESIAN))
            .isInstanceOf(ParseAmountResult.Ambiguous::class.java)
        // Malformed 4-digit prefix before dot must never silently multiply by 1,000
        assertThat(LocaleNumberParser.parse("1000.500", "IDR", ProviderNumberLocale.INDONESIAN))
            .isInstanceOf(ParseAmountResult.Ambiguous::class.java)
    }

    @Test
    fun rejectsZeroNegativeAndMalformedInputs() {
        assertThat(LocaleNumberParser.parse("Rp 0", "IDR", ProviderNumberLocale.INDONESIAN))
            .isInstanceOf(ParseAmountResult.Invalid::class.java)
        assertThat(LocaleNumberParser.parse("-50.000", "IDR", ProviderNumberLocale.INDONESIAN))
            .isInstanceOf(ParseAmountResult.Invalid::class.java)
        assertThat(LocaleNumberParser.parse("abc", "IDR", ProviderNumberLocale.INDONESIAN))
            .isInstanceOf(ParseAmountResult.Invalid::class.java)
    }
}
