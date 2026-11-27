package wtrack.app.feature

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import wtrack.app.R
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.domain.model.LockedReason
import wtrack.app.domain.model.ThemeMode
import wtrack.app.feature.root.AppLockOverlay
import wtrack.app.feature.root.DatabaseLockedScreen
import wtrack.app.feature.root.RootUiState

/**
 * Isolated Compose UI verification on physical hardware using `createComposeRule()` so
 * `MainActivity` and the live `wtrack.db` singleton are never touched.
 */
class ComposeThemeAndLockedUiInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersSystemAndLightThemesCleanly() {
        composeRule.setContent {
            WtrackTheme(themeMode = ThemeMode.SYSTEM) {
                Text("System Theme Probe", color = MaterialTheme.colorScheme.onBackground)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("System Theme Probe").assertIsDisplayed()
    }

    @Test
    fun rendersLightThemeCleanly() {
        composeRule.setContent {
            WtrackTheme(themeMode = ThemeMode.LIGHT) {
                Text("Light Theme Probe", color = MaterialTheme.colorScheme.onBackground)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Light Theme Probe").assertIsDisplayed()
    }

    @Test
    fun rendersDarkThemeDatabaseLockedScreenWithNonDestructiveRecoveryControls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            WtrackTheme(themeMode = ThemeMode.DARK) {
                DatabaseLockedScreen(
                    state = RootUiState.Locked(
                        reason = LockedReason.KEY_MISSING,
                        detail = "Wrapped database key file missing.",
                    ),
                    onRestoreBackup = { _, _ -> },
                    onWipeAllData = {},
                    onClearMessage = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.db_locked_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.locked_reason_key_missing)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.db_locked_restore_button)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.db_locked_erase_button)).assertIsDisplayed()
    }

    @Test
    fun rendersAppLockOverlayCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            WtrackTheme(themeMode = ThemeMode.DARK) {
                AppLockOverlay(onUnlocked = {})
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.app_lock_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.app_lock_unlock_button)).assertIsDisplayed()
    }
}
