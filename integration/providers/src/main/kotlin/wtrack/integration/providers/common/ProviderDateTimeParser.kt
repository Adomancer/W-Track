package wtrack.integration.providers.common

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure-Kotlin timestamp parser for notification texts (PRD PAR-12).
 *
 * Parses explicit notification timestamps in Indonesian/provider timezones (`WIB`, `WITA`, `WIT`,
 * or ISO offsets) to UTC epoch milliseconds. Returns `null` on missing or unparseable timestamps so
 * callers strictly fall back to `NotificationEvent.postedAtEpochMillis` (never `received_at`).
 */
object ProviderDateTimeParser {
    val ZONE_WIB: ZoneId = ZoneId.of("Asia/Jakarta")
    val ZONE_WITA: ZoneId = ZoneId.of("Asia/Makassar")
    val ZONE_WIT: ZoneId = ZoneId.of("Asia/Jayapura")
    val ZONE_UTC: ZoneId = ZoneId.of("UTC")

    private val indonesianMonths = mapOf(
        "januari" to "Jan",
        "februari" to "Feb",
        "maret" to "Mar",
        "april" to "Apr",
        "mei" to "May",
        "juni" to "Jun",
        "juli" to "Jul",
        "agustus" to "Aug",
        "agu" to "Aug",
        "agt" to "Aug",
        "september" to "Sep",
        "oktober" to "Oct",
        "okt" to "Oct",
        "november" to "Nov",
        "desember" to "Dec",
        "des" to "Dec",
    )

    private val localFormats = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.US),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US),
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.US),
    )

    fun parseToUtcEpochMillis(
        rawTimestamp: String?,
        defaultZone: ZoneId = ZONE_WIB,
    ): Long? {
        val raw = rawTimestamp?.trim().orEmpty()
        if (raw.isEmpty()) return null

        // 1. ISO-8601 instant or offset datetime.
        runCatching { return Instant.parse(raw).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(raw).toInstant().toEpochMilli() }

        // 2. Detect explicit Indonesian/UTC timezone token at end of string.
        val zoneTokenRegex = Regex("\\b(WIB|WITA|WIT|UTC|GMT)\\b$", RegexOption.IGNORE_CASE)
        val zoneMatch = zoneTokenRegex.find(raw)
        val effectiveZone = when (zoneMatch?.value?.uppercase(Locale.ROOT)) {
            "WIB" -> ZONE_WIB
            "WITA" -> ZONE_WITA
            "WIT" -> ZONE_WIT
            "UTC", "GMT" -> ZONE_UTC
            else -> defaultZone
        }
        val withoutZone = (if (zoneMatch != null) raw.removeRange(zoneMatch.range) else raw)
            .replace(",", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val englishNormalized = normalizeIndonesianMonthNames(withoutZone)
        for (formatter in localFormats) {
            val parsed = runCatching {
                LocalDateTime.parse(englishNormalized, formatter)
                    .atZone(effectiveZone)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun normalizeIndonesianMonthNames(input: String): String {
        val words = input.split(" ")
        return words.joinToString(" ") { word ->
            val mapped = indonesianMonths[word.lowercase(Locale.ROOT)]
            mapped ?: word
        }
    }
}
