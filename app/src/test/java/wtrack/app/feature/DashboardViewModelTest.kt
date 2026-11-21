package wtrack.app.feature

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.AppSettings
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.usecase.AggregateCalculator
import wtrack.app.feature.dashboard.DashboardViewModel
import wtrack.integration.Direction

class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun emitsMonthlyAggregatesPendingCountAndDismissesBackupReminder() = runTest {
        val clock = FakeClock(now = 1_795_100_000_000L)
        val accRepo = FakeAccountRepository(
            listOf(Account(1L, "Main", "Bank", AccountType.BANK, "IDR", 100_000_00L, 1_795_000_000_000L, null, true, estimatedBalanceMinor = 50_000_00L)),
        )
        val txRepo = FakeTransactionRepository(
            listOf(
                Transaction(
                    id = 1L,
                    accountId = 1L,
                    sourceId = null,
                    eventId = null,
                    amountMinor = 50_000_00L,
                    currency = "IDR",
                    direction = Direction.OUTFLOW,
                    transactionType = DomainTransactionType.PURCHASE,
                    merchantId = null,
                    description = "Coffee",
                    occurredAt = 1_795_050_000_000L,
                    categoryId = 4L,
                    categorySource = null,
                    appliedRuleId = null,
                    status = TransactionStatus.CONFIRMED,
                    fingerprint = null,
                    parserVersion = null,
                    originalSnapshot = null,
                    providerReference = null,
                    relatedTransactionId = null,
                    reportedBalanceMinor = null,
                    createdAt = 1_795_050_000_000L,
                    updatedAt = 1_795_050_000_000L,
                ),
            ),
        )
        val settingsRepo = FakeSettingsRepository(
            AppSettings(
                onboardingCompleted = true,
                txCountSinceLastBackup = 120,
                backupReminderTxCount = 100,
            ),
        )
        val vm = DashboardViewModel(
            accountRepository = accRepo,
            transactionRepository = txRepo,
            transferRepository = FakeTransferRepository(),
            categoryRepository = FakeCategoryRepository(),
            needsReviewRepository = FakeNeedsReviewRepository(),
            captureStatusRepository = FakeCaptureStatusRepository(),
            settingsRepository = settingsRepo,
            aggregateCalculator = AggregateCalculator(),
            clock = clock,
        )

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.summary.totalExpenseMinor).isEqualTo(50_000_00L)
            assertThat(state.showBackupReminder).isTrue()

            vm.dismissBackupReminder()
            val afterDismiss = awaitItem()
            assertThat(afterDismiss.showBackupReminder).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
