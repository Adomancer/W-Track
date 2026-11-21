package wtrack.app.feature

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wtrack.app.domain.model.ThemeMode
import wtrack.app.domain.usecase.GateG1Evaluator
import wtrack.app.domain.usecase.GateVerdict
import wtrack.app.domain.usecase.ProviderMeasurementInput
import wtrack.app.feature.settings.SettingsViewModel

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updatesSettingsEvaluatesGateG1AndWipesLocalData() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val backupPort = FakeBackupAndDiagnosticsPort()
        val vm = SettingsViewModel(
            settingsRepository = settingsRepo,
            categoryRepository = FakeCategoryRepository(),
            ruleRepository = FakeRuleRepository(),
            captureStatusRepository = FakeCaptureStatusRepository(),
            needsReviewRepository = FakeNeedsReviewRepository(),
            backupPort = backupPort,
            gateG1Evaluator = GateG1Evaluator(),
            clock = FakeClock(),
            dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.dispatcher),
        )

        vm.uiState.test {
            awaitItem()
            vm.setThemeMode(ThemeMode.DARK)
            assertThat(awaitItem().settings.themeMode).isEqualTo(ThemeMode.DARK)

            vm.setBackupReminderThresholds(30, 250)
            val withThresholds = awaitItem()
            assertThat(withThresholds.settings.backupReminderDays).isEqualTo(30)
            assertThat(withThresholds.settings.backupReminderTxCount).isEqualTo(250)

            vm.evaluateGateG1(
                listOf(
                    ProviderMeasurementInput("Bank Jago", 14, 35, 35, 34, 34, 0),
                    ProviderMeasurementInput("DANA", 14, 32, 31, 30, 30, 0),
                ),
            )
            val withGate = awaitItem()
            assertThat(withGate.gateG1Report?.verdict).isEqualTo(GateVerdict.GO)

            vm.wipeAllData()
            assertThat(backupPort.wiped).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
