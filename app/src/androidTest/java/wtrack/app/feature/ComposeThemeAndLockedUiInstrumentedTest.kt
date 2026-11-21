package wtrack.app.feature

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.domain.model.LockedReason
import wtrack.app.domain.model.ThemeMode
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
        composeRule.onNodeWithText("Encrypted Database Locked").assertIsDisplayed()
        composeRule.onNodeWithText("Wrapped Key Missing").assertIsDisplayed()
        composeRule.onNodeWithText("Restore from Encrypted Backup (.wtrackbackup)").assertIsDisplayed()
        composeRule.onNodeWithText("Erase Local Data & Start Fresh").assertIsDisplayed()
    }
}
