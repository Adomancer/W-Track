package wtrack.app.feature

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.usecase.AggregateCalculator
import wtrack.app.domain.usecase.CreateManualTransactionUseCase
import wtrack.app.domain.usecase.CreateRuleWithBackfillUseCase
import wtrack.app.domain.usecase.DeleteManualTransactionUseCase
import wtrack.app.domain.usecase.LinkRefundUseCase
import wtrack.app.domain.usecase.LinkTransferUseCase
import wtrack.app.domain.usecase.UnlinkTransferUseCase
import wtrack.app.domain.usecase.UpdateTransactionUseCase
import wtrack.app.domain.usecase.VoidRestoreTransactionUseCase
import wtrack.app.feature.activity.ActivityViewModel
import wtrack.integration.Direction

class ActivityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun createsManualTransactionFiltersAndLinksTransferPairs() = runTest {
        val accRepo = FakeAccountRepository(
            listOf(
                Account(1L, "Bank", "Bank", AccountType.BANK, "IDR", null, null, null, true),
                Account(2L, "Wallet", "Wallet", AccountType.EWALLET, "IDR", null, null, null, true),
            ),
        )
        val txRepo = FakeTransactionRepository()
        val transferRepo = FakeTransferRepository()
        val tagRepo = FakeTagRepository()
        val vm = ActivityViewModel(
            transactionRepository = txRepo,
            accountRepository = accRepo,
            categoryRepository = FakeCategoryRepository(),
            transferRepository = transferRepo,
            tagRepository = tagRepo,
            settingsRepository = FakeSettingsRepository(),
            aggregateCalculator = AggregateCalculator(),
            createManualTransactionUseCase = CreateManualTransactionUseCase(txRepo),
            updateTransactionUseCase = UpdateTransactionUseCase(txRepo, transferRepo),
            voidRestoreTransactionUseCase = VoidRestoreTransactionUseCase(txRepo),
            deleteManualTransactionUseCase = DeleteManualTransactionUseCase(txRepo),
            linkTransferUseCase = LinkTransferUseCase(txRepo, transferRepo),
            unlinkTransferUseCase = UnlinkTransferUseCase(transferRepo),
            linkRefundUseCase = LinkRefundUseCase(txRepo),
            createRuleWithBackfillUseCase = CreateRuleWithBackfillUseCase(FakeRuleRepository(), txRepo),
            clock = FakeClock(),
        )

        vm.uiState.test {
            awaitItem()
            vm.createManualTransaction(1L, "102500", Direction.OUTFLOW, DomainTransactionType.TRANSFER, null, "Topup out", null)
            vm.createManualTransaction(2L, "100000", Direction.INFLOW, DomainTransactionType.TRANSFER, null, "Topup in", null)

            var latest = awaitItem()
            while (latest.items.size < 2) {
                latest = awaitItem()
            }
            val outTx = latest.items.first { it.transaction.accountId == 1L }.transaction
            val inTx = latest.items.first { it.transaction.accountId == 2L }.transaction

            // Add & filter by tag (TX-06)
            vm.addTagToTransaction(outTx.id, "reimburse")
            var taggedState = awaitItem()
            while (taggedState.tags.isEmpty()) {
                taggedState = awaitItem()
            }
            val tagId = taggedState.tags.single().id
            assertThat(taggedState.items.first { it.transaction.id == outTx.id }.tags.map { it.name }).containsExactly("reimburse")

            vm.updateFilter { it.copy(tagId = tagId) }
            val tagFiltered = awaitItem()
            assertThat(tagFiltered.items.map { it.transaction.id }).containsExactly(outTx.id)

            vm.updateFilter { it.copy(tagId = null, query = "reimb") }
            val queryFiltered = awaitItem()
            assertThat(queryFiltered.items.map { it.transaction.id }).containsExactly(outTx.id)

            vm.updateFilter { it.copy(query = "") }
            vm.removeTagFromTransaction(outTx.id, tagId)
            var untagged = awaitItem()
            while (untagged.items.first { it.transaction.id == outTx.id }.tags.isNotEmpty()) {
                untagged = awaitItem()
            }

            vm.linkTransfer(outTx.id, inTx.id)
            latest = awaitItem()
            while (latest.derivedFees.isEmpty()) {
                latest = awaitItem()
            }
            assertThat(latest.derivedFees.single().amountMinor).isEqualTo(250_000L) // Rp2.500

            // Filtering to Account 1 hides Account 2 from `items`, while `allItems` preserves both
            // so TransactionDetailScreen and transfer/refund pickers never lose unfiltered entries.
            vm.updateFilter { it.copy(accountId = 1L) }
            val filteredState = awaitItem()
            assertThat(filteredState.items).hasSize(1)
            assertThat(filteredState.allItems).hasSize(2)

            // Voiding while showVoided=false removes from `items` but keeps in `allItems`.
            vm.voidTransaction(outTx.id)
            var afterVoid = awaitItem()
            while (afterVoid.items.isNotEmpty()) {
                afterVoid = awaitItem()
            }
            assertThat(afterVoid.allItems).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
