package wtrack.app.feature

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.ThemeMode
import wtrack.app.domain.repository.ReparseProposal
import wtrack.app.domain.usecase.GateG1Evaluator
import wtrack.app.domain.usecase.GateVerdict
import wtrack.app.domain.usecase.ProviderMeasurementInput
import wtrack.app.feature.settings.SettingsViewModel
import wtrack.integration.Direction

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
            customParserRuleRepository = FakeCustomParserRuleRepository(),
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

            vm.setScheduledBackupConfig("content://tree/backups", enabled = true, intervalDays = 14)
            val withFolder = awaitItem()
            assertThat(withFolder.settings.scheduledBackupFolderUri).isEqualTo("content://tree/backups")
            assertThat(withFolder.settings.scheduledBackupEnabled).isTrue()
            assertThat(withFolder.settings.scheduledBackupIntervalDays).isEqualTo(14)

            vm.setAppLock(enabled = true, timeoutSeconds = 300)
            val withAppLock = awaitItem()
            assertThat(withAppLock.settings.appLockEnabled).isTrue()
            assertThat(withAppLock.settings.appLockTimeoutSeconds).isEqualTo(300)

            vm.setLocalAlertsEnabled(true)
            val withAlerts = awaitItem()
            assertThat(withAlerts.settings.localAlertsEnabled).isTrue()

            val proposal = ReparseProposal(
                eventId = 10L,
                transactionId = 20L,
                sourceId = "com.jago.digitalBanking",
                oldParserVersion = 1,
                newParserVersion = 2,
                currency = "IDR",
                currentAmountMinor = 5_000_000L,
                proposedAmountMinor = 5_500_000L,
                currentDirection = Direction.OUTFLOW,
                proposedDirection = Direction.OUTFLOW,
                currentType = DomainTransactionType.PURCHASE,
                proposedType = DomainTransactionType.PURCHASE,
                currentDescription = "Old",
                proposedDescription = "New",
            )
            backupPort.proposals.add(proposal)
            vm.scanReparseProposals()
            var withProposal = awaitItem()
            while (withProposal.reparseProposals.isEmpty()) {
                withProposal = awaitItem()
            }
            assertThat(withProposal.reparseProposals).containsExactly(proposal)
            vm.applyReparseProposal(proposal)
            var afterApply = awaitItem()
            while (afterApply.reparseProposals.isNotEmpty()) {
                afterApply = awaitItem()
            }
            assertThat(backupPort.appliedProposalIds).containsExactly(10L)

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
