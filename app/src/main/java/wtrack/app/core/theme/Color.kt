package wtrack.app.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import wtrack.app.domain.model.AccountType

private val mdLightPrimary = Color(0xFF0B6E63)
private val mdLightOnPrimary = Color(0xFFFFFFFF)
private val mdLightPrimaryContainer = Color(0xFFE6F3F1)
private val mdLightOnPrimaryContainer = Color(0xFF06403A)
private val mdLightSecondary = Color(0xFF4E615D)
private val mdLightOnSecondary = Color(0xFFFFFFFF)
private val mdLightSecondaryContainer = Color(0xFFF2F5F4)
private val mdLightOnSecondaryContainer = Color(0xFF101C1A)
private val mdLightTertiary = Color(0xFF5E7C78)
private val mdLightOnTertiary = Color(0xFFFFFFFF)
private val mdLightTertiaryContainer = Color(0xFFEBF1F0)
private val mdLightOnTertiaryContainer = Color(0xFF101C1A)
private val mdLightError = Color(0xFFC93B3B)
private val mdLightOnError = Color(0xFFFFFFFF)
private val mdLightErrorContainer = Color(0xFFFDECEC)
private val mdLightOnErrorContainer = Color(0xFF8C1D1D)
private val mdLightBackground = Color(0xFFFFFFFF)
private val mdLightOnBackground = Color(0xFF101C1A)
private val mdLightSurface = Color(0xFFFFFFFF)
private val mdLightOnSurface = Color(0xFF101C1A)
private val mdLightSurfaceVariant = Color(0xFFF2F5F4)
private val mdLightOnSurfaceVariant = Color(0xFF6B7D79)
private val mdLightOutline = Color(0xFFCBD7D4)
private val mdLightOutlineVariant = Color(0xFFE6ECEA)

private val mdDarkPrimary = Color(0xFF1FA392)
private val mdDarkOnPrimary = Color(0xFF042B26)
private val mdDarkPrimaryContainer = Color(0xFF153430)
private val mdDarkOnPrimaryContainer = Color(0xFF7CF0E1)
private val mdDarkSecondary = Color(0xFF8A9E9A)
private val mdDarkOnSecondary = Color(0xFF0B1413)
private val mdDarkSecondaryContainer = Color(0xFF152220)
private val mdDarkOnSecondaryContainer = Color(0xFFF2F7F6)
private val mdDarkTertiary = Color(0xFF5E7C78)
private val mdDarkOnTertiary = Color(0xFF082238)
private val mdDarkTertiaryContainer = Color(0xFF152220)
private val mdDarkOnTertiaryContainer = Color(0xFFCBE5FF)
private val mdDarkError = Color(0xFFE56B6B)
private val mdDarkOnError = Color(0xFF450A0A)
private val mdDarkErrorContainer = Color(0xFF381616)
private val mdDarkOnErrorContainer = Color(0xFFFECACA)
private val mdDarkBackground = Color(0xFF0B1413)
private val mdDarkOnBackground = Color(0xFFF2F7F6)
private val mdDarkSurface = Color(0xFF0B1413)
private val mdDarkOnSurface = Color(0xFFF2F7F6)
private val mdDarkSurfaceVariant = Color(0xFF152220)
private val mdDarkOnSurfaceVariant = Color(0xFF8A9E9A)
private val mdDarkOutline = Color(0xFF243331)
private val mdDarkOutlineVariant = Color(0xFF1C2B29)

/** Material 3 light color scheme aligned with V2 Minimal. */
val LightColors = lightColorScheme(
    primary = mdLightPrimary,
    onPrimary = mdLightOnPrimary,
    primaryContainer = mdLightPrimaryContainer,
    onPrimaryContainer = mdLightOnPrimaryContainer,
    secondary = mdLightSecondary,
    onSecondary = mdLightOnSecondary,
    secondaryContainer = mdLightSecondaryContainer,
    onSecondaryContainer = mdLightOnSecondaryContainer,
    tertiary = mdLightTertiary,
    onTertiary = mdLightOnTertiary,
    tertiaryContainer = mdLightTertiaryContainer,
    onTertiaryContainer = mdLightOnTertiaryContainer,
    error = mdLightError,
    onError = mdLightOnError,
    errorContainer = mdLightErrorContainer,
    onErrorContainer = mdLightOnErrorContainer,
    background = mdLightBackground,
    onBackground = mdLightOnBackground,
    surface = mdLightSurface,
    onSurface = mdLightOnSurface,
    surfaceVariant = mdLightSurfaceVariant,
    onSurfaceVariant = mdLightOnSurfaceVariant,
    outline = mdLightOutline,
    outlineVariant = mdLightOutlineVariant,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9F8),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF2F5F4),
    surfaceContainerHighest = Color(0xFFE6ECEA),
)

/** Material 3 dark color scheme aligned with V2 Minimal Dark Mode. */
val DarkColors = darkColorScheme(
    primary = mdDarkPrimary,
    onPrimary = mdDarkOnPrimary,
    primaryContainer = mdDarkPrimaryContainer,
    onPrimaryContainer = mdDarkOnPrimaryContainer,
    secondary = mdDarkSecondary,
    onSecondary = mdDarkOnSecondary,
    secondaryContainer = mdDarkSecondaryContainer,
    onSecondaryContainer = mdDarkOnSecondaryContainer,
    tertiary = mdDarkTertiary,
    onTertiary = mdDarkOnTertiary,
    tertiaryContainer = mdDarkTertiaryContainer,
    onTertiaryContainer = mdDarkOnTertiaryContainer,
    error = mdDarkError,
    onError = mdDarkOnError,
    errorContainer = mdDarkErrorContainer,
    onErrorContainer = mdDarkOnErrorContainer,
    background = mdDarkBackground,
    onBackground = mdDarkOnBackground,
    surface = mdDarkSurface,
    onSurface = mdDarkOnSurface,
    surfaceVariant = mdDarkSurfaceVariant,
    onSurfaceVariant = mdDarkOnSurfaceVariant,
    outline = mdDarkOutline,
    outlineVariant = mdDarkOutlineVariant,
    surfaceContainerLowest = Color(0xFF0B1413),
    surfaceContainerLow = Color(0xFF101C1A),
    surfaceContainer = Color(0xFF131E1D),
    surfaceContainerHigh = Color(0xFF152220),
    surfaceContainerHighest = Color(0xFF1C2B29),
)

/**
 * Extended semantic and surface tokens derived from the FigCraft design system.
 */
@Immutable
data class WtrackExtendedColors(
    val heroGradientStart: Color,
    val heroGradientEnd: Color,
    val heroBorder: Color,
    val heroLabel: Color,
    val heroMutedText: Color,
    val heroIncomeText: Color,
    val heroExpenseText: Color,
    val heroPillContainer: Color,
    val heroPillText: Color,
    val inflow: Color,
    val inflowContainer: Color,
    val onInflowContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val warningBorder: Color,
    val warningBadgeBg: Color,
    val onWarningContainer: Color,
    val transfer: Color,
    val transferContainer: Color,
    val onTransferContainer: Color,
    val mutedText: Color,
    val divider: Color,
    val cardBorder: Color,
    val navBarSurface: Color,
    val totalBalanceCardBg: Color,
    val totalBalanceCardMuted: Color,
    val infoTintBg: Color,
    val infoTintBorder: Color,
    val infoTintText: Color,
    val categoryPalette: List<Color>,
    val uncategorizedChartColor: Color,
)

val LightWtrackExtendedColors = WtrackExtendedColors(
    heroGradientStart = Color(0xFF0B6E63),
    heroGradientEnd = Color(0xFF0B6E63),
    heroBorder = Color(0xFF0B6E63),
    heroLabel = Color(0xFFB9E8E1),
    heroMutedText = Color(0xFF9AD6CE),
    heroIncomeText = Color(0xFF74F8E7),
    heroExpenseText = Color(0xFFFFD5D0),
    heroPillContainer = Color(0x22FFFFFF),
    heroPillText = Color(0xFF74F8E7),
    inflow = Color(0xFF0B6E63),
    inflowContainer = Color(0xFFE6F3F1),
    onInflowContainer = Color(0xFF06403A),
    warning = Color(0xFFC87A0A),
    warningContainer = Color(0xFFFEF6E6),
    warningBorder = Color(0xFFF3D59B),
    warningBadgeBg = Color(0xFFF9DDA2),
    onWarningContainer = Color(0xFF7A4600),
    transfer = Color(0xFF4E615D),
    transferContainer = Color(0xFFF2F5F4),
    onTransferContainer = Color(0xFF101C1A),
    mutedText = Color(0xFF8A9995),
    divider = Color(0xFFE6ECEA),
    cardBorder = Color(0xFFE6ECEA),
    navBarSurface = Color(0xFFFFFFFF),
    totalBalanceCardBg = Color(0xFF0E2421),
    totalBalanceCardMuted = Color(0xFF9BB5B0),
    infoTintBg = Color(0xFFF7F9F8),
    infoTintBorder = Color(0xFFE6ECEA),
    infoTintText = Color(0xFF101C1A),
    categoryPalette = listOf(
        Color(0xFF0B6E63),
        Color(0xFF5E7C78),
        Color(0xFFCBD7D4),
        Color(0xFF8FA8A4),
        Color(0xFF3E635E),
        Color(0xFFB3C4C0),
    ),
    uncategorizedChartColor = Color(0xFFC87A0A),
)

val DarkWtrackExtendedColors = WtrackExtendedColors(
    heroGradientStart = Color(0xFF0B6E63),
    heroGradientEnd = Color(0xFF0B6E63),
    heroBorder = Color(0xFF1C2B29),
    heroLabel = Color(0xFF9AD6CE),
    heroMutedText = Color(0xFF8A9E9A),
    heroIncomeText = Color(0xFF1FA392),
    heroExpenseText = Color(0xFFE56B6B),
    heroPillContainer = Color(0x18FFFFFF),
    heroPillText = Color(0xFF1FA392),
    inflow = Color(0xFF1FA392),
    inflowContainer = Color(0xFF153430),
    onInflowContainer = Color(0xFF7CF0E1),
    warning = Color(0xFFE69B2E),
    warningContainer = Color(0xFF2B2110),
    warningBorder = Color(0xFF594119),
    warningBadgeBg = Color(0xFF4D3710),
    onWarningContainer = Color(0xFFFDE68A),
    transfer = Color(0xFF8A9E9A),
    transferContainer = Color(0xFF152220),
    onTransferContainer = Color(0xFFF2F7F6),
    mutedText = Color(0xFF6B7D79),
    divider = Color(0xFF1C2B29),
    cardBorder = Color(0xFF1C2B29),
    navBarSurface = Color(0xFF0B1413),
    totalBalanceCardBg = Color(0xFF122623),
    totalBalanceCardMuted = Color(0xFF8A9E9A),
    infoTintBg = Color(0xFF131E1D),
    infoTintBorder = Color(0xFF1C2B29),
    infoTintText = Color(0xFFF2F7F6),
    categoryPalette = listOf(
        Color(0xFF1FA392),
        Color(0xFF5E7C78),
        Color(0xFF3A4E4B),
        Color(0xFF7FA39E),
        Color(0xFF4E6B66),
        Color(0xFF2B3D3A),
    ),
    uncategorizedChartColor = Color(0xFFE69B2E),
)

val LocalWtrackColors = staticCompositionLocalOf { LightWtrackExtendedColors }

@Immutable
data class ProviderBrandStyle(
    val badgeBg: Color,
    val badgeFg: Color,
    val letter: String,
    val displayName: String,
)

private val fallbackProviderColors = listOf(
    Color(0xFF0B6E63) to Color(0xFFFFFFFF),
    Color(0xFF3B6790) to Color(0xFFFFFFFF),
    Color(0xFF6D59A8) to Color(0xFFFFFFFF),
    Color(0xFF2E7D5B) to Color(0xFFFFFFFF),
)

fun providerBrandStyle(
    labelOrPackage: String,
    accountType: AccountType? = null,
): ProviderBrandStyle {
    val norm = labelOrPackage.trim().lowercase()
    return when {
        "jago" in norm -> ProviderBrandStyle(
            badgeBg = Color(0xFFFFB703),
            badgeFg = Color(0xFF3D2800),
            letter = "J",
            displayName = "Bank Jago",
        )
        "bca" in norm -> ProviderBrandStyle(
            badgeBg = Color(0xFF00529C),
            badgeFg = Color(0xFFFFFFFF),
            letter = "B",
            displayName = "myBCA",
        )
        "dana" in norm -> ProviderBrandStyle(
            badgeBg = Color(0xFF118EEA),
            badgeFg = Color(0xFFFFFFFF),
            letter = "D",
            displayName = "DANA",
        )
        "cash" in norm || accountType == AccountType.CASH -> ProviderBrandStyle(
            badgeBg = Color(0xFF0B6E63),
            badgeFg = Color(0xFFFFFFFF),
            letter = "C",
            displayName = "Cash",
        )
        else -> {
            val clean = labelOrPackage.trim().ifEmpty { "Account" }
            val pair = fallbackProviderColors[kotlin.math.abs(norm.hashCode()) % fallbackProviderColors.size]
            val initial = clean.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "W"
            ProviderBrandStyle(
                badgeBg = pair.first,
                badgeFg = pair.second,
                letter = initial,
                displayName = clean,
            )
        }
    }
}
