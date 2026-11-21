package wtrack.app.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import wtrack.app.domain.model.AccountType

private val mdLightPrimary = Color(0xFF0B6E63)
private val mdLightOnPrimary = Color(0xFFFFFFFF)
private val mdLightPrimaryContainer = Color(0xFFD8F3EE)
private val mdLightOnPrimaryContainer = Color(0xFF06403A)
private val mdLightSecondary = Color(0xFF4E615D)
private val mdLightOnSecondary = Color(0xFFFFFFFF)
private val mdLightSecondaryContainer = Color(0xFFEAF2F0)
private val mdLightOnSecondaryContainer = Color(0xFF101C1A)
private val mdLightTertiary = Color(0xFF3B6790)
private val mdLightOnTertiary = Color(0xFFFFFFFF)
private val mdLightTertiaryContainer = Color(0xFFE6F0FA)
private val mdLightOnTertiaryContainer = Color(0xFF1E4263)
private val mdLightError = Color(0xFFD94848)
private val mdLightOnError = Color(0xFFFFFFFF)
private val mdLightErrorContainer = Color(0xFFFDECEC)
private val mdLightOnErrorContainer = Color(0xFF8C1D1D)
private val mdLightBackground = Color(0xFFF4F7F6)
private val mdLightOnBackground = Color(0xFF101C1A)
private val mdLightSurface = Color(0xFFFFFFFF)
private val mdLightOnSurface = Color(0xFF101C1A)
private val mdLightSurfaceVariant = Color(0xFFEAF2F0)
private val mdLightOnSurfaceVariant = Color(0xFF4E615D)
private val mdLightOutline = Color(0xFFCBD7D4)
private val mdLightOutlineVariant = Color(0xFFE3EAE8)

private val mdDarkPrimary = Color(0xFF3ED6C3)
private val mdDarkOnPrimary = Color(0xFF042B26)
private val mdDarkPrimaryContainer = Color(0xFF154740)
private val mdDarkOnPrimaryContainer = Color(0xFF7CF0E1)
private val mdDarkSecondary = Color(0xFF9EB2AE)
private val mdDarkOnSecondary = Color(0xFF0D1413)
private val mdDarkSecondaryContainer = Color(0xFF1E2C2A)
private val mdDarkOnSecondaryContainer = Color(0xFFEFF5F3)
private val mdDarkTertiary = Color(0xFF7CC4FA)
private val mdDarkOnTertiary = Color(0xFF082238)
private val mdDarkTertiaryContainer = Color(0xFF142B42)
private val mdDarkOnTertiaryContainer = Color(0xFFCBE5FF)
private val mdDarkError = Color(0xFFF87171)
private val mdDarkOnError = Color(0xFF450A0A)
private val mdDarkErrorContainer = Color(0xFF381616)
private val mdDarkOnErrorContainer = Color(0xFFFECACA)
private val mdDarkBackground = Color(0xFF0D1413)
private val mdDarkOnBackground = Color(0xFFEFF5F3)
private val mdDarkSurface = Color(0xFF16201F)
private val mdDarkOnSurface = Color(0xFFEFF5F3)
private val mdDarkSurfaceVariant = Color(0xFF1E2C2A)
private val mdDarkOnSurfaceVariant = Color(0xFF9EB2AE)
private val mdDarkOutline = Color(0xFF2D3E3C)
private val mdDarkOutlineVariant = Color(0xFF243331)

/** Material 3 light color scheme aligned with the FigCraft Calm Precision Fintech spec. */
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
    surfaceContainerLow = Color(0xFFF4F7F6),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEAF2F0),
    surfaceContainerHighest = Color(0xFFE3EAE8),
)

/** Material 3 dark color scheme aligned with the FigCraft Dark Mode showcase. */
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
    surfaceContainerLowest = Color(0xFF0D1413),
    surfaceContainerLow = Color(0xFF141D1C),
    surfaceContainer = Color(0xFF16201F),
    surfaceContainerHigh = Color(0xFF1E2C2A),
    surfaceContainerHighest = Color(0xFF243331),
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
    heroGradientEnd = Color(0xFF064740),
    heroBorder = Color(0xFF0B6E63),
    heroLabel = Color(0xFFB9E8E1),
    heroMutedText = Color(0xFF9AD6CE),
    heroIncomeText = Color(0xFF74F8E7),
    heroExpenseText = Color(0xFFFFD5D0),
    heroPillContainer = Color(0x22FFFFFF),
    heroPillText = Color(0xFF74F8E7),
    inflow = Color(0xFF0E8A5F),
    inflowContainer = Color(0xFFE2F7EE),
    onInflowContainer = Color(0xFF065238),
    warning = Color(0xFFC87A0A),
    warningContainer = Color(0xFFFEF3DC),
    warningBorder = Color(0xFFF3C97A),
    warningBadgeBg = Color(0xFFF9DDA2),
    onWarningContainer = Color(0xFF7A4600),
    transfer = Color(0xFF3B6790),
    transferContainer = Color(0xFFE6F0FA),
    onTransferContainer = Color(0xFF1E4263),
    mutedText = Color(0xFF7A8D89),
    divider = Color(0xFFEDF2F0),
    cardBorder = Color(0xFFE3EAE8),
    navBarSurface = Color(0xFFFFFFFF),
    totalBalanceCardBg = Color(0xFF142624),
    totalBalanceCardMuted = Color(0xFFA8C2BE),
    infoTintBg = Color(0xFFEBF7F4),
    infoTintBorder = Color(0xFFB8E3D9),
    infoTintText = Color(0xFF06403A),
    categoryPalette = listOf(
        Color(0xFF0B6E63),
        Color(0xFF4C6B94),
        Color(0xFF2F9CC9),
        Color(0xFF7B61B8),
        Color(0xFF0E8A5F),
        Color(0xFFC25975),
    ),
    uncategorizedChartColor = Color(0xFFE69500),
)

val DarkWtrackExtendedColors = WtrackExtendedColors(
    heroGradientStart = Color(0xFF0C3B35),
    heroGradientEnd = Color(0xFF072622),
    heroBorder = Color(0xFF1B5E54),
    heroLabel = Color(0xFF9AD6CE),
    heroMutedText = Color(0xFF8FB5AF),
    heroIncomeText = Color(0xFF34D399),
    heroExpenseText = Color(0xFFFCA5A5),
    heroPillContainer = Color(0x18FFFFFF),
    heroPillText = Color(0xFF3ED6C3),
    inflow = Color(0xFF34D399),
    inflowContainer = Color(0xFF0E3328),
    onInflowContainer = Color(0xFF6EE7B7),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF33250B),
    warningBorder = Color(0xFF664912),
    warningBadgeBg = Color(0xFF4D3710),
    onWarningContainer = Color(0xFFFDE68A),
    transfer = Color(0xFF7CC4FA),
    transferContainer = Color(0xFF142B42),
    onTransferContainer = Color(0xFF93C5FD),
    mutedText = Color(0xFF6C807C),
    divider = Color(0xFF243331),
    cardBorder = Color(0xFF243331),
    navBarSurface = Color(0xFF141D1C),
    totalBalanceCardBg = Color(0xFF0B2522),
    totalBalanceCardMuted = Color(0xFF8FB5AF),
    infoTintBg = Color(0xFF112D29),
    infoTintBorder = Color(0xFF1F544C),
    infoTintText = Color(0xFF9AD6CE),
    categoryPalette = listOf(
        Color(0xFF3ED6C3),
        Color(0xFF60A5FA),
        Color(0xFF38BDF8),
        Color(0xFFA78BFA),
        Color(0xFF34D399),
        Color(0xFFF472B6),
    ),
    uncategorizedChartColor = Color(0xFFFBBF24),
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
