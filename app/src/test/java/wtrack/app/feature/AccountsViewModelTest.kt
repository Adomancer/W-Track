package wtrack.app.feature

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wtrack.app.domain.model.AccountType
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
        val clock = FakeClock()

        val vm = AccountsViewModel(
            accountRepository = accRepo,
            sourceRepository = sourceRepo,
            transactionRepository = txRepo,
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
            cancelAndIgnoreRemainingEvents()
        }
    }
}
