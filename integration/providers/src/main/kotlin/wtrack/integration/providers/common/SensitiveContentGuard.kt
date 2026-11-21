package wtrack.integration.providers.common

import wtrack.integration.NotTransactionReason
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ReviewReason

/**
 * Pure-Kotlin privacy guard rejecting OTP, PIN, password, login alerts, and OS-redacted lock-screen
 * notifications before monetary parsing (PRD ING-06, PAR-09, section 9.1).
 */
object SensitiveContentGuard {
    private val securityPatterns = listOf(
        Regex("\\botp\\b", RegexOption.IGNORE_CASE),
        Regex("one[\\s-]*time\\s*(?:password|passcode|pin|code)", RegexOption.IGNORE_CASE),
        Regex("kode\\s*(?:verifikasi|otp|rahasia|keamanan|akses)", RegexOption.IGNORE_CASE),
        Regex("verification\\s*code", RegexOption.IGNORE_CASE),
        Regex("(?:jangan\\s*(?:berikan|bagikan|sebar)|rahasia|do\\s*not\\s*share)", RegexOption.IGNORE_CASE),
        Regex("\\b(?:kata\\s*sandi|password|passcode|mpin|cvv|cvc)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:reset|ubah|ganti|salah|wrong|enter|masukkan|blokir|terblokir)\\s+pin\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpin\\s+(?:anda|kamu|salah|diblokir|blocked|expired|reset)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:new\\s+(?:device\\s+)?login|login\\s+(?:baru|terdeteksi|perangkat)|security\\s+alert|peringatan\\s+keamanan)\\b", RegexOption.IGNORE_CASE),
    )

    private val failedAttemptPatterns = listOf(
        Regex("\\b(?:gagal|failed|ditolak|declined|unsuccessful|dibatalkan|insufficient\\s+balance|saldo\\s+tidak\\s+cukup)\\b", RegexOption.IGNORE_CASE),
    )

    private val promoPatterns = listOf(
        Regex("\\b(?:promo|diskon|discount|cashback\\s+hingga|voucher|kupon|hadiah|undian)\\b", RegexOption.IGNORE_CASE),
    )

    fun inspectPreconditions(event: NotificationEvent): ParseResult? {
        if (event.isContentRedacted) {
            return ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, null)
        }
        val combined = listOfNotNull(event.title, event.text, event.bigText, event.subText)
            .joinToString(" ")
        if (combined.isBlank()) {
            return ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, null)
        }
        if (securityPatterns.any { it.containsMatchIn(combined) }) {
            return ParseResult.NotTransaction(NotTransactionReason.SECURITY_NOTICE)
        }
        return null
    }

    fun inspectNonTransactionNotice(event: NotificationEvent): ParseResult.NotTransaction? {
        val combined = listOfNotNull(event.title, event.text, event.bigText, event.subText)
            .joinToString(" ")
        return when {
            failedAttemptPatterns.any { it.containsMatchIn(combined) } ->
                ParseResult.NotTransaction(NotTransactionReason.FAILED_ATTEMPT)

            promoPatterns.any { it.containsMatchIn(combined) } ->
                ParseResult.NotTransaction(NotTransactionReason.PROMOTION)

            else -> null
        }
    }
}
