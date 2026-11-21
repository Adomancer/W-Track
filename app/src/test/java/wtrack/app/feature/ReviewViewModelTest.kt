package wtrack.app.feature

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.NeedsReviewItem
import wtrack.app.domain.usecase.ResolveNeedsReviewUseCase
import wtrack.app.feature.review.ReviewViewModel
import wtrack.integration.Direction
import wtrack.integration.ReviewReason

class ReviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun resolvesIgnoresAndRetriesNeedsReviewItems() = runTest {
        val reviewItem = NeedsReviewItem(
            id = 7L,
            sourceId = "pkg.bank",
            packageName = "pkg.bank",
            postedAt = 1_795_000_000_000L,
            reviewReason = ReviewReason.ACCOUNT_UNMAPPED,
            payloadText = "Paid Rp 75.000",
            draftAmountMinor = 7_500_000L,
            draftCurrency = "IDR",
            draftDirection = Direction.OUTFLOW,
            draftType = null,
            draftOccurredAt = null,
            draftMerchantRaw = "Store",
            draftDescription = "Store purchase",
            draftAccountHint = "9901",
            draftAccountId = null,
            draftProviderReference = null,
            draftReportedBalanceMinor = null,
        )
        val reviewRepo = FakeNeedsReviewRepository(listOf(reviewItem))
        val accRepo = FakeAccountRepository(
            listOf(Account(1L, "Main", "Bank", AccountType.BANK, "IDR", null, null, null, true)),
        )
        val vm = ReviewViewModel(
            needsReviewRepository = reviewRepo,
            accountRepository = accRepo,
            categoryRepository = FakeCategoryRepository(),
            settingsRepository = FakeSettingsRepository(),
            resolveNeedsReviewUseCase = ResolveNeedsReviewUseCase(reviewRepo, accRepo),
            clock = FakeClock(),
        )

        vm.uiState.test {
            val initial = awaitItem()
            assertThat(initial.items).hasSize(1)

            vm.retry(7L)
            assertThat(reviewRepo.retriedIds).containsExactly(7L)
            assertThat(awaitItem().actionMessage).isEqualTo("Re-run completed.")

            // Ambiguous 3-digit grouping input ("75.000") must be rejected rather than guessed
            vm.saveResolved(
                item = reviewItem,
                accountId = 1L,
                amountMajorInput = "75.000",
                currency = "IDR",
                direction = Direction.OUTFLOW,
                type = DomainTransactionType.PURCHASE,
                description = "Store purchase",
                categoryId = 4L,
                rememberAccountHint = true,
            )
            val rejected = awaitItem()
            assertThat(rejected.items).hasSize(1)
            assertThat(rejected.actionMessage).contains("valid positive amount")

            vm.saveResolved(
                item = reviewItem,
                accountId = 1L,
                amountMajorInput = "75000",
                currency = "IDR",
                direction = Direction.OUTFLOW,
                type = DomainTransactionType.PURCHASE,
                description = "Store purchase",
                categoryId = 4L,
                rememberAccountHint = true,
            )
            var afterSave = awaitItem()
            while (afterSave.items.isNotEmpty()) {
                afterSave = awaitItem()
            }
            assertThat(afterSave.items).isEmpty()
            assertThat(reviewRepo.savedCalls.single().second.amountMinor).isEqualTo(7_500_000L)
            assertThat(reviewRepo.savedCalls.single().second.rememberAccountHint).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
