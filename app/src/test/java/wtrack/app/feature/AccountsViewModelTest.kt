package wtrack.app.feature

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.usecase.BalanceCheckpointEvaluator
import wtrack.app.domain.usecase.CorrectBalanceUseCase
import wtrack.app.feature.accounts.AccountsViewModel
import wtrack.integration.Direction

class AccountsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun createsAccountRecordsBalanceAdjustmentAndTogglesSampleCollection() = runTest {
        val accRepo = FakeAccountRepository()
        val txRepo = FakeTransactionRepository()
        val sourceRepo = FakeSourceAndEventRepository()
        val customParserRepo = FakeCustomParserRuleRepository()
        val settingsRepo = FakeSettingsRepository()
        val clock = FakeClock()

        val vm = AccountsViewModel(
            accountRepository = accRepo,
            sourceRepository = sourceRepo,
            transactionRepository = txRepo,
            customParserRuleRepository = customParserRepo,
            settingsRepository = settingsRepo,
            backupPort = FakeBackupAndDiagnosticsPort(),
            checkpointEvaluator = BalanceCheckpointEvaluator(),
            correctBalanceUseCase = CorrectBalanceUseCase(accRepo, txRepo, clock),
            clock = clock,
        )

        vm.uiState.test {
            awaitItem()
            vm.createAccount("Jago Main", "Bank Jago", AccountType.BANK, "IDR", "500000")
            var latest = awaitItem()
            while (latest.activeAccounts.isEmpty()) {
                latest = awaitItem()
            }
            val accId = latest.activeAccounts.single().account.id

            vm.linkSource(accId, "com.example.jago", "Jago", "1234")
            latest = awaitItem()
            while (latest.sources.isEmpty()) {
                latest = awaitItem()
            }
            vm.toggleSampleCollection("com.example.jago", enable14Days = true)
            latest = awaitItem()
            while (latest.sources.single().sampleCollectionUntil == null) {
                latest = awaitItem()
            }
            assertThat(latest.sources.single().sampleCollectionUntil).isEqualTo(clock.now + 14L * 86_400_000L)

            vm.correctBalance(accId, "IDR", "450000", "Cash count diff")
            latest = awaitItem()
            assertThat(txRepo.txsFlow.value.single().transactionType).isEqualTo(DomainTransactionType.ADJUSTMENT)
            assertThat(txRepo.txsFlow.value.single().direction).isEqualTo(Direction.OUTFLOW)
            assertThat(txRepo.txsFlow.value.single().amountMinor).isEqualTo(5_000_000L)

            // Also verify grouped Indonesian ("50.000") and English ("100,000") balance inputs work
            vm.correctBalance(accId, "IDR", "50.000", "Grouped dot")
            latest = awaitItem()
            assertThat(txRepo.txsFlow.value.last().transactionType).isEqualTo(DomainTransactionType.ADJUSTMENT)
            assertThat(txRepo.txsFlow.value.last().direction).isEqualTo(Direction.OUTFLOW)
            assertThat(txRepo.txsFlow.value.last().amountMinor).isEqualTo(40_000_000L)

            vm.correctBalance(accId, "IDR", "100,000", "Grouped comma")
            latest = awaitItem()
            assertThat(txRepo.txsFlow.value.last().transactionType).isEqualTo(DomainTransactionType.ADJUSTMENT)
            assertThat(txRepo.txsFlow.value.last().direction).isEqualTo(Direction.INFLOW)
            assertThat(txRepo.txsFlow.value.last().amountMinor).isEqualTo(5_000_000L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tracksBaseCurrencyFromSettingsAndUsesItAsCreateFallback() = runTest {
        val accRepo = FakeAccountRepository()
        val txRepo = FakeTransactionRepository()
        val sourceRepo = FakeSourceAndEventRepository()
        val customParserRepo = FakeCustomParserRuleRepository()
        val settingsRepo = FakeSettingsRepository(AppSettings(baseCurrency = "USD", onboardingCompleted = true))
        val clock = FakeClock()

        val vm = AccountsViewModel(
            accountRepository = accRepo,
            sourceRepository = sourceRepo,
            transactionRepository = txRepo,
            customParserRuleRepository = customParserRepo,
            settingsRepository = settingsRepo,
            backupPort = FakeBackupAndDiagnosticsPort(),
            checkpointEvaluator = BalanceCheckpointEvaluator(),
            correctBalanceUseCase = CorrectBalanceUseCase(accRepo, txRepo, clock),
            clock = clock,
        )

        vm.uiState.test {
            var latest = awaitItem()
            while (latest.baseCurrency != "USD") {
                latest = awaitItem()
            }
            assertThat(latest.baseCurrency).isEqualTo("USD")

            // Blank currency falls back to settings.baseCurrency ("USD"), not hardcoded "IDR"
            vm.createAccount("Dollar Checking", "Bank", AccountType.BANK, "  ", "100.50")
            latest = awaitItem()
            while (latest.activeAccounts.isEmpty()) {
                latest = awaitItem()
            }
            val usdAccount = latest.activeAccounts.single().account
            assertThat(usdAccount.currency).isEqualTo("USD")
            assertThat(usdAccount.estimatedBalanceMinor).isEqualTo(10_050L)

            // Updating baseCurrency in settings updates AccountsUiState reactively and normalizes lowercase input
            settingsRepo.setBaseCurrency("SGD")
            latest = awaitItem()
            while (latest.baseCurrency != "SGD") {
                latest = awaitItem()
            }
            vm.createAccount("Singapore Cash", "Cash", AccountType.CASH, " sgd ", "25.00")
            latest = awaitItem()
            while (latest.activeAccounts.size < 2) {
                latest = awaitItem()
            }
            assertThat(latest.activeAccounts.last().account.currency).isEqualTo("SGD")
            assertThat(latest.activeAccounts.last().account.estimatedBalanceMinor).isEqualTo(2_500L)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
