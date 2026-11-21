package wtrack.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wtrack.app.domain.model.TransactionRule
import wtrack.integration.Direction

class CategoryRuleEngineTest {

    @Test
    fun normalizesMerchantKeysAcrossNoiseAndDiacritics() {
        assertThat(MerchantKeyNormalizer.normalize("Kopi Kenangan #102 Jakarta")).isEqualTo("kopi kenangan")
        assertThat(MerchantKeyNormalizer.normalize("  Café   Deli  Cabang Bandung  ")).isEqualTo("cafe deli")
        assertThat(MerchantKeyNormalizer.normalize(null)).isEmpty()
    }

    @Test
    fun selectsMostSpecificEnabledRuleAndBreaksTiesByRecency() {
        val matcher = CategoryRuleMatcher()
        val rules = listOf(
            // Disabled high-specificity rule must be ignored
            TransactionRule(1L, "Disabled", "kopi kenangan", null, 10L, Direction.OUTFLOW, categoryId = 99L, enabled = false, createdAt = 5000L, updatedAt = 5000L),
            // Description fragment only (+1)
            TransactionRule(2L, "Desc Older", null, "kopi", null, null, categoryId = 10L, enabled = true, createdAt = 1000L, updatedAt = 1000L),
            // Merchant key only (+2)
            TransactionRule(3L, "Merchant", "kopi kenangan", null, null, null, categoryId = 20L, enabled = true, createdAt = 2000L, updatedAt = 2000L),
            // Merchant key + account (+4) -> wins specificity
            TransactionRule(4L, "Merchant+Acc", "kopi kenangan", null, 10L, null, categoryId = 30L, enabled = true, createdAt = 1500L, updatedAt = 1500L),
        )

        val matchForAcc10 = matcher.findBestMatch(rules, "kopi kenangan", "kopi panas", accountId = 10L, direction = Direction.OUTFLOW)
        assertThat(matchForAcc10?.id).isEqualTo(4L)
        assertThat(matchForAcc10?.categoryId).isEqualTo(30L)

        // When accountId=20L, rule 4 doesn't match; rule 3 (score 2) beats rule 2 (score 1)
        val matchForAcc20 = matcher.findBestMatch(rules, "kopi kenangan", "kopi panas", accountId = 20L, direction = Direction.OUTFLOW)
        assertThat(matchForAcc20?.id).isEqualTo(3L)

        // Tie-break by newest createdAt
        val tiedRules = listOf(
            TransactionRule(10L, "Old", "indomaret", null, null, null, categoryId = 5L, enabled = true, createdAt = 1000L, updatedAt = 1000L),
            TransactionRule(11L, "New", "indomaret", null, null, null, categoryId = 6L, enabled = true, createdAt = 3000L, updatedAt = 3000L),
        )
        assertThat(matcher.findBestMatch(tiedRules, "indomaret", null, 1L, Direction.OUTFLOW)?.id).isEqualTo(11L)
    }
}
