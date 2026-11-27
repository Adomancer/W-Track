package wtrack.app.feature

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wtrack.app.R
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.NeedsReviewItem
import wtrack.app.domain.usecase.ResolveNeedsReviewUseCase
import wtrack.app.feature.common.UiText
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
            payloadTitle = "Bank",
            payloadBody = "Paid Rp 75.000",
        )
        val reviewRepo = FakeNeedsReviewRepository(listOf(reviewItem))
        val accRepo = FakeAccountRepository(
            listOf(Account(1L, "Main", "Bank", AccountType.BANK, "IDR", null, null, null, true)),
        )
        val customParserRepo = FakeCustomParserRuleRepository().apply {
            autoParsedReturnCount = 2
        }
        val vm = ReviewViewModel(
            needsReviewRepository = reviewRepo,
            accountRepository = accRepo,
            categoryRepository = FakeCategoryRepository(),
            settingsRepository = FakeSettingsRepository(),
            customParserRuleRepository = customParserRepo,
            resolveNeedsReviewUseCase = ResolveNeedsReviewUseCase(reviewRepo, accRepo),
            clock = FakeClock(),
        )

        vm.uiState.test {
            val initial = awaitItem()
            assertThat(initial.items).hasSize(1)

            vm.retry(7L)
            assertThat(reviewRepo.retriedIds).containsExactly(7L)
            assertThat(awaitItem().actionMessage).isEqualTo(UiText.Resource(R.string.msg_review_retry_done))

            // Malformed or non-positive amounts must be rejected cleanly
            vm.saveResolved(
                item = reviewItem,
                accountId = 1L,
                amountMajorInput = "0.500",
                currency = "IDR",
                direction = Direction.OUTFLOW,
                type = DomainTransactionType.PURCHASE,
                description = "Store purchase",
                categoryId = 4L,
                rememberAccountHint = true,
            )
            val rejected = awaitItem()
            assertThat(rejected.items).hasSize(1)
            assertThat(rejected.actionMessage).isEqualTo(
                UiText.Resource(R.string.msg_enter_positive_amount, "IDR")
            )

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
                rememberPattern = true,
                customTemplatePattern = "Paid {AMOUNT} at {MERCHANT}",
                customNumberLocale = CustomNumberLocale.INDONESIAN,
            )
            var afterSave = awaitItem()
            while (afterSave.items.isNotEmpty()) {
                afterSave = awaitItem()
            }
            assertThat(afterSave.items).isEmpty()
            assertThat(reviewRepo.savedCalls.single().second.amountMinor).isEqualTo(7_500_000L)
            assertThat(reviewRepo.savedCalls.single().second.rememberAccountHint).isTrue()
            assertThat(customParserRepo.rulesFlow.value).hasSize(1)
            assertThat(customParserRepo.rulesFlow.value.single().templatePattern).isEqualTo("Paid {AMOUNT} at {MERCHANT}")
            assertThat(customParserRepo.retriedSources).containsExactly("pkg.bank")
            assertThat(afterSave.actionMessage).isEqualTo(
                UiText.PluralResource(R.plurals.msg_review_saved_with_pattern_and_autoparsed, 2, 2)
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
