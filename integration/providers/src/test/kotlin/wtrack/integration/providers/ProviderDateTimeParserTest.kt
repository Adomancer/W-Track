package wtrack.integration.providers

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZonedDateTime
import org.junit.Test
import wtrack.integration.providers.common.ProviderDateTimeParser

class ProviderDateTimeParserTest {

    @Test
    fun parsesIndonesianTimezonesAndMonthAbbreviationsAccurately() {
        val wib = ProviderDateTimeParser.parseToUtcEpochMillis("20 Nov 2026 14:30 WIB")
        val wita = ProviderDateTimeParser.parseToUtcEpochMillis("20 Nov 2026 15:30 WITA")
        val wit = ProviderDateTimeParser.parseToUtcEpochMillis("20 Nov 2026 16:30 WIT")

        val expected = ZonedDateTime.parse("2026-11-20T07:30:00Z").toInstant().toEpochMilli()
        assertThat(wib).isEqualTo(expected)
        assertThat(wita).isEqualTo(expected)
        assertThat(wit).isEqualTo(expected)

        val mei = ProviderDateTimeParser.parseToUtcEpochMillis("15 Mei 2026 09:05:10 WIB")
        assertThat(mei).isEqualTo(Instant.parse("2026-05-15T02:05:10Z").toEpochMilli())

        val des = ProviderDateTimeParser.parseToUtcEpochMillis("25 Des 2026 23:59 WITA")
        assertThat(des).isEqualTo(Instant.parse("2026-12-25T15:59:00Z").toEpochMilli())
    }

    @Test
    fun parsesIsoAndNumericDatesAndReturnsNullForInvalidStringsForPostedAtFallback() {
        assertThat(ProviderDateTimeParser.parseToUtcEpochMillis("2026-11-20T07:30:00Z"))
            .isEqualTo(Instant.parse("2026-11-20T07:30:00Z").toEpochMilli())
        assertThat(ProviderDateTimeParser.parseToUtcEpochMillis("20/11/2026 14:30"))
            .isEqualTo(Instant.parse("2026-11-20T07:30:00Z").toEpochMilli())

        assertThat(ProviderDateTimeParser.parseToUtcEpochMillis(null)).isNull()
        assertThat(ProviderDateTimeParser.parseToUtcEpochMillis("")).isNull()
        assertThat(ProviderDateTimeParser.parseToUtcEpochMillis("hari ini")).isNull()
    }
}
