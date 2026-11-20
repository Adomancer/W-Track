package wtrack.app.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val mdLightPrimary = Color(0xFF006A60)
private val mdLightOnPrimary = Color(0xFFFFFFFF)
private val mdLightPrimaryContainer = Color(0xFF74F8E7)
private val mdLightOnPrimaryContainer = Color(0xFF00201C)
private val mdLightSecondary = Color(0xFF4A6360)
private val mdLightOnSecondary = Color(0xFFFFFFFF)
private val mdLightSecondaryContainer = Color(0xFFCCE8E4)
private val mdLightOnSecondaryContainer = Color(0xFF05201D)
private val mdLightTertiary = Color(0xFF49617B)
private val mdLightOnTertiary = Color(0xFFFFFFFF)
private val mdLightTertiaryContainer = Color(0xFFCFE5FF)
private val mdLightOnTertiaryContainer = Color(0xFF001E32)
private val mdLightError = Color(0xFFBA1A1A)
private val mdLightOnError = Color(0xFFFFFFFF)
private val mdLightErrorContainer = Color(0xFFFFDAD6)
private val mdLightOnErrorContainer = Color(0xFF410002)
private val mdLightBackground = Color(0xFFFAFDFB)
private val mdLightOnBackground = Color(0xFF191C1B)
private val mdLightSurface = Color(0xFFFAFDFB)
private val mdLightOnSurface = Color(0xFF191C1B)
private val mdLightSurfaceVariant = Color(0xFFDAE5E2)
private val mdLightOnSurfaceVariant = Color(0xFF3F4947)
private val mdLightOutline = Color(0xFF6F7977)

private val mdDarkPrimary = Color(0xFF53DBCB)
private val mdDarkOnPrimary = Color(0xFF003731)
private val mdDarkPrimaryContainer = Color(0xFF005048)
private val mdDarkOnPrimaryContainer = Color(0xFF74F8E7)
private val mdDarkSecondary = Color(0xFFB0CCC8)
private val mdDarkOnSecondary = Color(0xFF1B3532)
private val mdDarkSecondaryContainer = Color(0xFF324B48)
private val mdDarkOnSecondaryContainer = Color(0xFFCCE8E4)
private val mdDarkTertiary = Color(0xFFB1C9E6)
private val mdDarkOnTertiary = Color(0xFF1A3449)
private val mdDarkTertiaryContainer = Color(0xFF314A63)
private val mdDarkOnTertiaryContainer = Color(0xFFCFE5FF)
private val mdDarkError = Color(0xFFFFB4AB)
private val mdDarkOnError = Color(0xFF690005)
private val mdDarkErrorContainer = Color(0xFF93000A)
private val mdDarkOnErrorContainer = Color(0xFFFFDAD6)
private val mdDarkBackground = Color(0xFF191C1B)
private val mdDarkOnBackground = Color(0xFFE0E3E1)
private val mdDarkSurface = Color(0xFF191C1B)
private val mdDarkOnSurface = Color(0xFFE0E3E1)
private val mdDarkSurfaceVariant = Color(0xFF3F4947)
private val mdDarkOnSurfaceVariant = Color(0xFFBEC9C6)
private val mdDarkOutline = Color(0xFF889391)

/** Material 3 light color scheme (PRD section 10.1). */
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
)

/** Material 3 dark color scheme (PRD section 10.1). */
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
)
