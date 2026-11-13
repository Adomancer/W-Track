package wtrack.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** User-selectable theme mode (PRD section 10.1: System / Light / Dark). */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Root Material 3 theme for the W-Track app.
 *
 * Follows the Android system theme by default; an explicit [themeMode] can force light or dark.
 * On Android 12+ dynamic color (Material You) is used when [dynamicColor] is true, otherwise the
 * fixed [LightColors]/[DarkColors] schemes apply (PRD section 10.1).
 */
@Composable
fun WtrackTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}