package wtrack.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val md_light_primary = Color(0xFF006A60)
private val md_light_onPrimary = Color(0xFFFFFFFF)
private val md_light_primaryContainer = Color(0xFF74F8E7)
private val md_light_onPrimaryContainer = Color(0xFF00201C)
private val md_light_secondary = Color(0xFF4A6360)
private val md_light_onSecondary = Color(0xFFFFFFFF)
private val md_light_secondaryContainer = Color(0xFFCCE8E4)
private val md_light_onSecondaryContainer = Color(0xFF05201D)
private val md_light_tertiary = Color(0xFF49617B)
private val md_light_onTertiary = Color(0xFFFFFFFF)
private val md_light_tertiaryContainer = Color(0xFFCFE5FF)
private val md_light_onTertiaryContainer = Color(0xFF001E32)
private val md_light_error = Color(0xFFBA1A1A)
private val md_light_onError = Color(0xFFFFFFFF)
private val md_light_errorContainer = Color(0xFFFFDAD6)
private val md_light_onErrorContainer = Color(0xFF410002)
private val md_light_background = Color(0xFFFAFDFB)
private val md_light_onBackground = Color(0xFF191C1B)
private val md_light_surface = Color(0xFFFAFDFB)
private val md_light_onSurface = Color(0xFF191C1B)
private val md_light_surfaceVariant = Color(0xFFDAE5E2)
private val md_light_onSurfaceVariant = Color(0xFF3F4947)
private val md_light_outline = Color(0xFF6F7977)

private val md_dark_primary = Color(0xFF53DBCB)
private val md_dark_onPrimary = Color(0xFF003731)
private val md_dark_primaryContainer = Color(0xFF005048)
private val md_dark_onPrimaryContainer = Color(0xFF74F8E7)
private val md_dark_secondary = Color(0xFFB0CCC8)
private val md_dark_onSecondary = Color(0xFF1B3532)
private val md_dark_secondaryContainer = Color(0xFF324B48)
private val md_dark_onSecondaryContainer = Color(0xFFCCE8E4)
private val md_dark_tertiary = Color(0xFFB1C9E6)
private val md_dark_onTertiary = Color(0xFF1A3449)
private val md_dark_tertiaryContainer = Color(0xFF314A63)
private val md_dark_onTertiaryContainer = Color(0xFFCFE5FF)
private val md_dark_error = Color(0xFFFFB4AB)
private val md_dark_onError = Color(0xFF690005)
private val md_dark_errorContainer = Color(0xFF93000A)
private val md_dark_onErrorContainer = Color(0xFFFFDAD6)
private val md_dark_background = Color(0xFF191C1B)
private val md_dark_onBackground = Color(0xFFE0E3E1)
private val md_dark_surface = Color(0xFF191C1B)
private val md_dark_onSurface = Color(0xFFE0E3E1)
private val md_dark_surfaceVariant = Color(0xFF3F4947)
private val md_dark_onSurfaceVariant = Color(0xFFBEC9C6)
private val md_dark_outline = Color(0xFF889391)

/** Material 3 light color scheme for the W-Track app (PRD section 10.1). */
val LightColors = lightColorScheme(
    primary = md_light_primary,
    onPrimary = md_light_onPrimary,
    primaryContainer = md_light_primaryContainer,
    onPrimaryContainer = md_light_onPrimaryContainer,
    secondary = md_light_secondary,
    onSecondary = md_light_onSecondary,
    secondaryContainer = md_light_secondaryContainer,
    onSecondaryContainer = md_light_onSecondaryContainer,
    tertiary = md_light_tertiary,
    onTertiary = md_light_onTertiary,
    tertiaryContainer = md_light_tertiaryContainer,
    onTertiaryContainer = md_light_onTertiaryContainer,
    error = md_light_error,
    onError = md_light_onError,
    errorContainer = md_light_errorContainer,
    onErrorContainer = md_light_onErrorContainer,
    background = md_light_background,
    onBackground = md_light_onBackground,
    surface = md_light_surface,
    onSurface = md_light_onSurface,
    surfaceVariant = md_light_surfaceVariant,
    onSurfaceVariant = md_light_onSurfaceVariant,
    outline = md_light_outline,
)

/** Material 3 dark color scheme for the W-Track app (PRD section 10.1). */
val DarkColors = darkColorScheme(
    primary = md_dark_primary,
    onPrimary = md_dark_onPrimary,
    primaryContainer = md_dark_primaryContainer,
    onPrimaryContainer = md_dark_onPrimaryContainer,
    secondary = md_dark_secondary,
    onSecondary = md_dark_onSecondary,
    secondaryContainer = md_dark_secondaryContainer,
    onSecondaryContainer = md_dark_onSecondaryContainer,
    tertiary = md_dark_tertiary,
    onTertiary = md_dark_onTertiary,
    tertiaryContainer = md_dark_tertiaryContainer,
    onTertiaryContainer = md_dark_onTertiaryContainer,
    error = md_dark_error,
    onError = md_dark_onError,
    errorContainer = md_dark_errorContainer,
    onErrorContainer = md_dark_onErrorContainer,
    background = md_dark_background,
    onBackground = md_dark_onBackground,
    surface = md_dark_surface,
    onSurface = md_dark_onSurface,
    surfaceVariant = md_dark_surfaceVariant,
    onSurfaceVariant = md_dark_onSurfaceVariant,
    outline = md_dark_outline,
)