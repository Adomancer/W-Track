package wtrack.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wtrack.app.domain.exception.InvalidTransferPairException
import wtrack.app.domain.exception.ValidationException
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferOrigin
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.model.TransferStatus
import wtrack.app.domain.repository.CreateManualTransactionRequest
import wtrack.app.domain.repository.LinkTransferRequest
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.TransferRepository
import wtrack.app.domain.repository.UpdateTransactionRequest
import wtrack.integration.Direction

class TransferAndEditInvariantTest {

    @Test
    fun linkTransferValidatesPairRulesAndDerivesPositiveFee() = runTest {
        val txRepo = FakeTxRepo(
            tx(1L, acc = 10L, amount = 502_500_00L, dir = Direction.OUTFLOW, cur = "IDR"),
            tx(2L, acc = 20L, amount = 500_000_00L, dir = Direction.INFLOW, cur = "IDR"),
            tx(3L, acc = 10L, amount = 500_000_00L, dir = Direction.INFLOW, cur = "IDR"), // same account
            tx(4L, acc = 20L, amount = 600_000_00L, dir = Direction.INFLOW, cur = "IDR"), // > out
            tx(5L, acc = 20L, amount = 100_00L, dir = Direction.INFLOW, cur = "USD"), // currency mismatch
            tx(6L, acc = 30L, amount = 100_000_00L, dir = Direction.INFLOW, cur = "IDR", status = TransactionStatus.VOIDED),
        )
        val transferRepo = FakeTransferRepo()
        val linkUseCase = LinkTransferUseCase(txRepo, transferRepo)

        // Same transaction ID
        assertThat(runCatching { linkUseCase(1L, 1L) }.exceptionOrNull()).isInstanceOf(InvalidTransferPairException::class.java)
        // Same account
        assertThat(runCatching { linkUseCase(1L, 3L) }.exceptionOrNull()).isInstanceOf(InvalidTransferPairException::class.java)
        // Incoming > Outgoing
        assertThat(runCatching { linkUseCase(1L, 4L) }.exceptionOrNull()).isInstanceOf(InvalidTransferPairException::class.java)
        // Currency mismatch
        assertThat(runCatching { linkUseCase(1L, 5L) }.exceptionOrNull()).isInstanceOf(InvalidTransferPairException::class.java)
        // Voided leg
        assertThat(runCatching { linkUseCase(1L, 6L) }.exceptionOrNull()).isInstanceOf(InvalidTransferPairException::class.java)

        // Valid pair -> computes derived feeMinor = 2.500 IDR = 250_000 minor
        val pairId = linkUseCase(1L, 2L)
        assertThat(transferRepo.items.single { it.id == pairId }.feeMinor).isEqualTo(250_000L)

        // Double-linking a transaction already in a confirmed pair must fail
        txRepo.items += tx(7L, acc = 30L, amount = 400_000_00L, dir = Direction.INFLOW, cur = "IDR")
        assertThat(runCatching { linkUseCase(1L, 7L) }.exceptionOrNull()).isInstanceOf(InvalidTransferPairException::class.java)

        // Adjustments and refunds cannot be linked as transfer pairs
        txRepo.items += tx(8L, acc = 10L, amount = 100_000_00L, dir = Direction.OUTFLOW, cur = "IDR", type = DomainTransactionType.ADJUSTMENT)
        txRepo.items += tx(9L, acc = 30L, amount = 100_000_00L, dir = Direction.INFLOW, cur = "IDR", type = DomainTransactionType.REFUND)
        assertThat(runCatching { linkUseCase(8L, 7L) }.exceptionOrNull()).isInstanceOf(InvalidTransferPairException::class.java)
        assertThat(runCatching { linkUseCase(1L, 9L) }.exceptionOrNull()).isInstanceOf(InvalidTransferPairException::class.java)
    }

    @Test
    fun linkRefundValidatesDirectionCurrencyAndVoidStatus() = runTest {
        val txRepo = FakeTxRepo(
            tx(10L, acc = 1L, amount = 50_000_00L, dir = Direction.OUTFLOW, cur = "IDR"),
            tx(11L, acc = 1L, amount = 50_000_00L, dir = Direction.INFLOW, cur = "IDR", type = DomainTransactionType.REFUND),
            tx(12L, acc = 1L, amount = 50_000_00L, dir = Direction.INFLOW, cur = "IDR"),
            tx(13L, acc = 1L, amount = 5_000L, dir = Direction.OUTFLOW, cur = "USD"),
        )
        val linkRefund = LinkRefundUseCase(txRepo)

        assertThat(runCatching { linkRefund(11L, 11L) }.exceptionOrNull()).isInstanceOf(ValidationException::class.java)
        assertThat(runCatching { linkRefund(11L, 12L) }.exceptionOrNull()).isInstanceOf(ValidationException::class.java)
        assertThat(runCatching { linkRefund(11L, 13L) }.exceptionOrNull()).isInstanceOf(ValidationException::class.java)

        linkRefund(11L, 10L)
        assertThat(txRepo.transaction(11L)?.relatedTransactionId).isEqualTo(10L)
    }

    private fun tx(
        id: Long,
        acc: Long,
        amount: Long,
        dir: Direction,
        cur: String,
        type: DomainTransactionType = DomainTransactionType.TRANSFER,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
    ) = Transaction(
        id = id, accountId = acc, sourceId = null, eventId = null, amountMinor = amount,
        currency = cur, direction = dir, transactionType = type, merchantId = null,
        description = null, occurredAt = 1000L, categoryId = null, categorySource = null,
        appliedRuleId = null, status = status, fingerprint = null, parserVersion = null,
        originalSnapshot = null, providerReference = null, relatedTransactionId = null,
        reportedBalanceMinor = null, createdAt = 1000L, updatedAt = 1000L,
    )

    private class FakeTxRepo(vararg initial: Transaction) : TransactionRepository {
        val items = initial.toMutableList()
        override val transactions: Flow<List<Transaction>> = MutableStateFlow(items)
        override suspend fun transaction(id: Long): Transaction? = items.firstOrNull { it.id == id }
        override suspend fun getAllOnce(): List<Transaction> = items.toList()
        override suspend fun insertManual(request: CreateManualTransactionRequest): Long = 0L
        override suspend fun insertAdjustment(accountId: Long, amountMinor: Long, currency: String, direction: Direction, occurredAt: Long, description: String?): Long = 0L
        override suspend fun update(request: UpdateTransactionRequest) {}
        override suspend fun setCategory(transactionId: Long, categoryId: Long?, byUser: Boolean, ruleId: Long?) {}
        override suspend fun voidTransaction(id: Long) {}
        override suspend fun restoreTransaction(id: Long) {}
        override suspend fun deleteManual(id: Long) {}
        override suspend fun linkRefund(refundTransactionId: Long, originalTransactionId: Long?) {
            val idx = items.indexOfFirst { it.id == refundTransactionId }
            if (idx >= 0) items[idx] = items[idx].copy(relatedTransactionId = originalTransactionId)
        }
        override suspend fun findByProviderReference(sourceId: String, providerReference: String): Transaction? = null
        override suspend fun countUncategorizedForMerchant(merchantId: Long): Int = 0
        override suspend fun applyCategoryToUncategorizedMerchant(merchantId: Long, categoryId: Long, ruleId: Long): Int = 0
    }

    private class FakeTransferRepo : TransferRepository {
        val items = mutableListOf<TransferPair>()
        private var nextId = 1L
        override val pairs: Flow<List<TransferPair>> = MutableStateFlow(items)
        override suspend fun getAllOnce(): List<TransferPair> = items.toList()
        override suspend fun findConfirmedForTransaction(transactionId: Long): TransferPair? =
            items.firstOrNull { it.status == TransferStatus.CONFIRMED && (it.outgoingTransactionId == transactionId || it.incomingTransactionId == transactionId) }
        override suspend fun link(request: LinkTransferRequest, feeMinor: Long): Long {
            val id = nextId++
            items += TransferPair(id, request.outgoingTransactionId, request.incomingTransactionId, TransferOrigin.MANUAL, null, TransferStatus.CONFIRMED, feeMinor, 1000L)
            return id
        }
        override suspend fun updateFee(pairId: Long, feeMinor: Long) {}
        override suspend fun unlink(pairId: Long) { items.removeAll { it.id == pairId } }
    }
}
