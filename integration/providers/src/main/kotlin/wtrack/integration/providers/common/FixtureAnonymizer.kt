package wtrack.integration.providers.common

/**
 * Deterministic anonymizer for M3a captured raw notification samples before committing synthetic test
 * fixtures (PRD section 11, SECURITY.md section 6.2).
 */
object FixtureAnonymizer {
    private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phoneRegex = Regex("(?:\\+62|62|08)[\\d\\s-]{7,14}\\d")
    private val cardNumberRegex = Regex("\\b\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}\\b")
    private val otpCodeRegex = Regex("(?<=\\b(?:OTP|PIN|KODE|CODE)\\b[:\\s-]{0,5})\\d{4,8}\\b", RegexOption.IGNORE_CASE)
    private val longDigitRunRegex = Regex("\\b\\d{8,19}\\b")

    fun anonymize(
        rawText: String,
        personNamesOrAliases: List<String> = emptyList(),
    ): String {
        var result = rawText
        personNamesOrAliases
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 2 }
            .sortedByDescending { it.length }
            .forEach { secret ->
                result = result.replace(secret, "<REDACTED_NAME>", ignoreCase = true)
            }
        result = emailRegex.replace(result, "<EMAIL>")
        result = phoneRegex.replace(result, "<PHONE>")
        result = cardNumberRegex.replace(result) { match ->
            val digitsOnly = match.value.filter { it.isDigit() }
            "****${digitsOnly.takeLast(4)}"
        }
        result = otpCodeRegex.replace(result, "<OTP>")
        result = longDigitRunRegex.replace(result) { match ->
            "****${match.value.takeLast(4)}"
        }
        return result
    }
}
