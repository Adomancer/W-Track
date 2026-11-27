package wtrack.app.feature

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SwapHoriz
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import wtrack.app.BuildConfig
import wtrack.app.core.config.AppLinks
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.feature.accounts.AccountCardUiModel
import wtrack.app.feature.accounts.resolvePrimaryBalanceSummary
import wtrack.app.feature.common.ProviderPresets
import wtrack.app.feature.common.v2CategoryIcon
import wtrack.app.feature.settings.parseGateG1FormInput
import wtrack.integration.providers.catalog.ProviderCatalog

class HardcodeEliminationAndPresetsTest {

    @Test
    fun resolvePrimaryBalanceSummaryPrioritizesBaseCurrencyAndFallsBackCleanly() {
        val usdAcc1 = accountCard(id = 1L, currency = "USD", balanceMinor = 1_250L)
        val usdAcc2 = accountCard(id = 2L, currency = "usd", balanceMinor = 750L)
        val idrAcc = accountCard(id = 3L, currency = "IDR", balanceMinor = 5_000_000L)

        // Empty accounts uses normalized base currency
        assertThat(resolvePrimaryBalanceSummary(emptyList(), " usd ")).isEqualTo("USD" to 0L)
        assertThat(resolvePrimaryBalanceSummary(emptyList(), "   ")).isEqualTo("IDR" to 0L)

        // Mixed accounts picks base currency when present and sums case-insensitively
        assertThat(resolvePrimaryBalanceSummary(listOf(idrAcc, usdAcc1, usdAcc2), "USD"))
            .isEqualTo("USD" to 2_000L)

        // Only IDR accounts while baseCurrency is USD falls back to first account's currency instead of $0.00
        assertThat(resolvePrimaryBalanceSummary(listOf(idrAcc), "USD"))
            .isEqualTo("IDR" to 5_000_000L)
    }

    @Test
    fun parseGateG1FormInputValidatesProviderAndNonNegativeNumbers() {
        assertThat(parseGateG1FormInput("", "14", "35", "34", "33", "32", "0")).isNull()
        assertThat(parseGateG1FormInput("Bank Jago", "", "35", "34", "33", "32", "0")).isNull()
        assertThat(parseGateG1FormInput("Bank Jago", "-1", "35", "34", "33", "32", "0")).isNull()
        assertThat(parseGateG1FormInput("Bank Jago", "14", "abc", "34", "33", "32", "0")).isNull()

        val parsed = parseGateG1FormInput("  Bank Jago  ", "14", "35", "34", "33", "32", "0")
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.providerName).isEqualTo("Bank Jago")
        assertThat(parsed.days).isEqualTo(14)
        assertThat(parsed.statementTxCountA).isEqualTo(35)
        assertThat(parsed.capturedTxNotificationsB).isEqualTo(34)
        assertThat(parsed.usableNotificationsC).isEqualTo(33)
        assertThat(parsed.matchedStatementTxD).isEqualTo(32)
        assertThat(parsed.unexplainedWrongAmountOrDirection).isEqualTo(0)
    }

    @Test
    fun providerPresetsAreUniqueCompleteAndNonOverlappingWithBuiltInCatalog() {
        val quickProviders = ProviderPresets.quickAccountProviders
        assertThat(quickProviders).isNotEmpty()
        assertThat(quickProviders.map { it.displayName }.distinct()).hasSize(quickProviders.size)
        assertThat(quickProviders.map { it.accountType }.toSet())
            .containsAtLeast(AccountType.BANK, AccountType.EWALLET, AccountType.CASH)

        val walletPresets = ProviderPresets.commonWalletPackages
        assertThat(walletPresets).isNotEmpty()
        assertThat(walletPresets.map { it.displayName }.distinct()).hasSize(walletPresets.size)
        assertThat(walletPresets.map { it.packageName }.distinct()).hasSize(walletPresets.size)
        walletPresets.forEach { preset ->
            assertThat(preset.packageName).matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
        }

        val builtInPkgs = ProviderCatalog.initialPoCTargets.flatMap { it.candidatePackageIds }.toSet()
        assertThat(walletPresets.map { it.packageName }.none { it in builtInPkgs }).isTrue()
    }

    @Test
    fun categoryIconsMatchBothEnglishAndIndonesianKeywords() {
        assertThat(v2CategoryIcon("Transfer", isTransfer = true)).isEqualTo(Icons.Outlined.SwapHoriz)
        assertThat(v2CategoryIcon("Pindah Saldo")).isEqualTo(Icons.Outlined.SwapHoriz)
        assertThat(v2CategoryIcon("Gaji Bulanan")).isEqualTo(Icons.Outlined.SouthWest)
        assertThat(v2CategoryIcon("Needs category", isUncategorized = true)).isEqualTo(Icons.Outlined.LocalOffer)
        assertThat(v2CategoryIcon("Belum dikategorikan")).isEqualTo(Icons.Outlined.LocalOffer)
        assertThat(v2CategoryIcon("Food & Dining")).isEqualTo(Icons.Outlined.LocalCafe)
        assertThat(v2CategoryIcon("Makan & Kopi")).isEqualTo(Icons.Outlined.LocalCafe)
        assertThat(v2CategoryIcon("Transport")).isEqualTo(Icons.Outlined.DirectionsCar)
        assertThat(v2CategoryIcon("Bensin & Parkir")).isEqualTo(Icons.Outlined.DirectionsCar)
        assertThat(v2CategoryIcon("Bills & Utilities")).isEqualTo(Icons.Outlined.Bolt)
        assertThat(v2CategoryIcon("Tagihan Listrik PLN")).isEqualTo(Icons.Outlined.Bolt)
        assertThat(v2CategoryIcon("Shopping")).isEqualTo(Icons.Outlined.ShoppingBag)
        assertThat(v2CategoryIcon("Belanja Bulanan")).isEqualTo(Icons.Outlined.ShoppingBag)
        assertThat(v2CategoryIcon("Kesehatan & Obat")).isEqualTo(Icons.Outlined.FavoriteBorder)
        assertThat(v2CategoryIcon("Hiburan & Nonton")).isEqualTo(Icons.Outlined.Movie)
        assertThat(v2CategoryIcon("Lain-lain")).isEqualTo(Icons.AutoMirrored.Outlined.ReceiptLong)
    }

    @Test
    fun appLinksAndBuildConfigVersionAreConsistent() {
        assertThat(BuildConfig.VERSION_NAME).isNotEmpty()
        assertThat(BuildConfig.VERSION_NAME).isNotEqualTo("1.0.0")
        assertThat(AppLinks.repositoryUrl).startsWith("https://")
        val derived = listOf(AppLinks.releasesUrl, AppLinks.issuesUrl, AppLinks.licenseUrl)
        derived.forEach { url ->
            assertThat(url).startsWith(AppLinks.repositoryUrl + "/")
            assertThat(url.removePrefix("https://")).doesNotContain("//")
        }
    }

    @Test
    fun sourceCodeGuardsPreventRegressionToHardcodedLiteralsAndDuplicates() {
        val repoRoot = locateRepoRoot()
        val featureFiles = File(repoRoot, "app/src/main/java/wtrack/app/feature")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertThat(featureFiles).isNotEmpty()

        // 1. No Color(0x...) literals in feature UI layer
        featureFiles.forEach { file ->
            assertThat(file.readText()).doesNotContain("Color(0x")
        }

        // 2. No dev device markers or "Provider Sample" in non-comment main code
        val mainCodeFiles = (
            File(repoRoot, "app/src/main/java").walkTopDown() +
                File(repoRoot, "integration/providers/src/main/kotlin").walkTopDown()
            ).filter { it.isFile && it.extension == "kt" }.toList()
        val forbiddenRuntimeStrings = listOf("iQOO", "Z10R", "Provider Sample")
        val violations = mutableListOf<String>()
        mainCodeFiles.forEach { file ->
            file.readLines().forEachIndexed { idx, line ->
                val trimmed = line.trim()
                val isComment = trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
                if (!isComment) {
                    forbiddenRuntimeStrings.forEach { marker ->
                        if (line.contains(marker)) {
                            violations += "${file.name}:${idx + 1}: forbidden literal `$marker`"
                        }
                    }
                }
            }
        }
        assertThat(violations).isEmpty()

        // 3. No duplicated local preset/currency lists in OnboardingScreen, AccountsScreen, CustomParsersScreen, SettingsScreen
        val onboarding = File(repoRoot, "app/src/main/java/wtrack/app/feature/root/OnboardingScreen.kt").readText()
        val accounts = File(repoRoot, "app/src/main/java/wtrack/app/feature/accounts/AccountsScreen.kt").readText()
        val customParsers = File(repoRoot, "app/src/main/java/wtrack/app/feature/settings/CustomParsersScreen.kt").readText()
        val settings = File(repoRoot, "app/src/main/java/wtrack/app/feature/settings/SettingsScreen.kt").readText()

        assertThat(onboarding).doesNotContain("val quickProviders =")
        assertThat(accounts).doesNotContain("val quickProviders =")
        assertThat(accounts).doesNotContain("val otherIndonesianApps =")
        assertThat(customParsers).doesNotContain("commonWalletQuickPackages")
        assertThat(onboarding).doesNotContain("""listOf("IDR", "USD"""")
        assertThat(settings).doesNotContain("""listOf("IDR", "USD"""")
    }

    private fun accountCard(id: Long, currency: String, balanceMinor: Long): AccountCardUiModel =
        AccountCardUiModel(
            account = Account(
                id = id,
                name = "Acc $id",
                provider = "Bank",
                accountType = AccountType.BANK,
                currency = currency,
                initialBalanceMinor = balanceMinor,
                initialBalanceAt = 1_000L,
                lastReconciledAt = 1_000L,
                isActive = true,
                estimatedBalanceMinor = balanceMinor,
            ),
            linkedSources = emptyList(),
            checkpoint = null,
        )

    private fun locateRepoRoot(): File {
        var cur: File? = File("").absoluteFile
        while (cur != null) {
            if (File(cur, "settings.gradle.kts").isFile) return cur
            cur = cur.parentFile
        }
        error("Could not locate repo root from ${File("").absolutePath}")
    }
}
