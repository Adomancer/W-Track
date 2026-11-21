package wtrack.app.domain.usecase

import java.text.Normalizer
import java.util.Locale
import wtrack.app.domain.model.TransactionRule
import wtrack.integration.Direction

/**
 * Normalizes merchant display names into deterministic keys (PRD section 4.9) by removing accents,
 * collapsing whitespace, and stripping trailing store/terminal/branch/city tokens.
 */
object MerchantKeyNormalizer {
    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val nonAlnumRegex = Regex("[^a-z0-9\\s]+")
    private val whitespaceRegex = Regex("\\s+")
    private val trailingNoiseRegex = Regex("(?:\\s+(?:#?\\d{2,6}|cabang|branch|outlet|store|id|jakarta|bandung|surabaya|medan|tangerang|bekasi|depok|bogor))+$")

    fun normalize(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFKD)
        val ascii = diacriticsRegex.replace(decomposed, "")
            .lowercase(Locale.ROOT)
        val alnum = nonAlnumRegex.replace(ascii, " ")
            .replace(whitespaceRegex, " ")
            .trim()
        val stripped = trailingNoiseRegex.replace(alnum, "").trim()
        return stripped.ifEmpty { alnum }
    }
}

/**
 * Matches enabled categorization rules (CAT-02, PRD section 4.9):
 * Precedence:
 * 1. User-set category (`USER`) is never overwritten by rules (enforced by caller/repository).
 * 2. Most specific matching enabled rule (`accountId` +2, `merchantKey` +2, `descriptionFragment` +1, `direction` +1).
 * 3. Tie-breaker: most recently created rule (`createdAt DESC, id DESC`).
 */
class CategoryRuleMatcher {
    fun findBestMatch(
        rules: List<TransactionRule>,
        merchantKey: String?,
        description: String?,
        accountId: Long,
        direction: Direction,
    ): TransactionRule? {
        val normalizedDesc = description?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val normalizedMerchant = merchantKey?.trim()?.lowercase(Locale.ROOT).orEmpty()

        return rules
            .asSequence()
            .filter { it.enabled }
            .filter { rule -> rule.accountId == null || rule.accountId == accountId }
            .filter { rule -> rule.direction == null || rule.direction == direction }
            .filter { rule ->
                val matchesMerchant = !rule.merchantKey.isNullOrBlank() &&
                    normalizedMerchant == rule.merchantKey.trim().lowercase(Locale.ROOT)
                val matchesFragment = !rule.descriptionFragment.isNullOrBlank() &&
                    normalizedDesc.contains(rule.descriptionFragment.trim().lowercase(Locale.ROOT))
                matchesMerchant || matchesFragment
            }
            .sortedWith(
                compareByDescending<TransactionRule> { specificityScore(it) }
                    .thenByDescending { it.createdAt }
                    .thenByDescending { it.id },
            )
            .firstOrNull()
    }

    private fun specificityScore(rule: TransactionRule): Int {
        var score = 0
        if (!rule.merchantKey.isNullOrBlank()) score += 2
        if (!rule.descriptionFragment.isNullOrBlank()) score += 1
        if (rule.accountId != null) score += 2
        if (rule.direction != null) score += 1
        return score
    }
}
