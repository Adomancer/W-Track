package wtrack.app.core.logging

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import wtrack.app.domain.time.Clock

/**
 * Privacy-safe non-sensitive diagnostics logger (PRD section 9.3, SECURITY.md section 6.1).
 *
 * NEVER log notification titles, text payloads, account numbers, account hints, merchant names,
 * transaction amounts, provider references, OTPs, or PINs.
 */
class WtrackLogger(
    private val logFile: File,
    private val clock: Clock,
) {
    private val lock = Any()

    fun logEvent(category: String, action: String, metadata: Map<String, String> = emptyMap()) {
        val now = clock.nowEpochMillis()
        val timestamp = formatIsoUtc(now)
        val safeFields = metadata
            .filterKeys { it in ALLOWED_KEYS }
            .entries
            .joinToString(" ") { "${it.key}=${sanitizeValue(it.value)}" }
        val line = buildString {
            append(timestamp)
            append(" [").append(sanitizeValue(category)).append("] ")
            append(sanitizeValue(action))
            if (safeFields.isNotEmpty()) {
                append(' ').append(safeFields)
            }
        }
        synchronized(lock) {
            runCatching {
                logFile.parentFile?.mkdirs()
                logFile.appendText("$line\n")
            }
        }
    }

    fun readRecentLines(limit: Int = 200): List<String> = synchronized(lock) {
        if (!logFile.exists()) return emptyList()
        runCatching { logFile.readLines().takeLast(limit) }.getOrDefault(emptyList())
    }

    fun pruneOlderThan(retentionDays: Int = DEFAULT_RETENTION_DAYS) {
        val cutoff = clock.nowEpochMillis() - retentionDays * MILLIS_PER_DAY
        synchronized(lock) {
            if (!logFile.exists()) return
            runCatching {
                val parser = utcFormat()
                val kept = logFile.readLines().filter { line ->
                    val tsPart = line.substringBefore(' ', "")
                    val parsed = runCatching { parser.parse(tsPart)?.time }.getOrNull()
                    parsed == null || parsed >= cutoff
                }
                logFile.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"))
            }
        }
    }

    fun clearAll() {
        synchronized(lock) {
            if (logFile.exists()) {
                logFile.delete()
            }
        }
    }

    private fun sanitizeValue(value: String): String =
        value.replace(Regex("[\\r\\n\\t]+"), "_").take(64)

    private fun formatIsoUtc(epochMillis: Long): String = utcFormat().format(Date(epochMillis))

    private fun utcFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 7
        private const val MILLIS_PER_DAY = 86_400_000L

        private val ALLOWED_KEYS = setOf(
            "eventId",
            "sourceId",
            "status",
            "reason",
            "attempt",
            "parserVersion",
            "gapReason",
            "redacted",
            "summary",
            "count",
        )
    }
}
