package wtrack.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferOrigin
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.model.TransferStatus
import wtrack.integration.Direction

class TransferSuggestionEngineTest {

    private val engine = TransferSuggestionEngine()

    private fun tx(
        id: Long,
        accountId: Long,
        amountMinor: Long,
        direction: Direction,
        occurredAt: Long,
        currency: String = "IDR",
        status: TransactionStatus = TransactionStatus.CONFIRMED,
    ) = Transaction(
        id = id,
        accountId = accountId,
        sourceId = "src",
        eventId = null,
        amountMinor = amountMinor,
        currency = currency,
        direction = direction,
        transactionType = DomainTransactionType.TRANSFER,
        merchantId = null,
        description = "tx-$id",
        occurredAt = occurredAt,
        categoryId = null,
        categorySource = null,
        appliedRuleId = null,
        status = status,
        fingerprint = null,
        parserVersion = null,
        originalSnapshot = null,
        providerReference = null,
        relatedTransactionId = null,
        reportedBalanceMinor = null,
        createdAt = occurredAt,
        updatedAt = occurredAt,
    )

    @Test
    fun suggestsExactAndFeeAdjustedInterAccountTransfersWithinWindow() {
        val base = 1_700_000_000_000L
        val txs = listOf(
            tx(1L, 10L, 10_000_000L, Direction.OUTFLOW, base),
            tx(2L, 20L, 10_000_000L, Direction.INFLOW, base + 60_000L),
            tx(3L, 10L, 5_250_000L, Direction.OUTFLOW, base + 3_600_000L),
            tx(4L, 30L, 5_000_000L, Direction.INFLOW, base + 3_720_000L), // fee Rp2,500
        )

        val suggestions = engine.findSuggestions(txs, emptyList())
        assertThat(suggestions).hasSize(2)
        assertThat(suggestions[0].outgoing.id).isEqualTo(1L)
        assertThat(suggestions[0].incoming.id).isEqualTo(2L)
        assertThat(suggestions[0].estimatedFeeMinor).isEqualTo(0L)
        assertThat(suggestions[0].confidence).isGreaterThan(suggestions[1].confidence)

        assertThat(suggestions[1].outgoing.id).isEqualTo(3L)
        assertThat(suggestions[1].incoming.id).isEqualTo(4L)
        assertThat(suggestions[1].estimatedFeeMinor).isEqualTo(250_000L)
    }

    @Test
    fun excludesSameAccountCurrencyMismatchVoidedOutOfWindowConfirmedAndRejectedPairs() {
        val base = 1_700_000_000_000L
        val txs = listOf(
            // Same account
            tx(1L, 10L, 1_000_000L, Direction.OUTFLOW, base),
            tx(2L, 10L, 1_000_000L, Direction.INFLOW, base + 10_000L),
            // Currency mismatch
            tx(3L, 10L, 1_000_000L, Direction.OUTFLOW, base, currency = "USD"),
            // Outside 30 min window (35 min)
            tx(4L, 20L, 1_000_000L, Direction.INFLOW, base + 35L * 60_000L),
            // Voided
            tx(5L, 20L, 1_000_000L, Direction.INFLOW, base + 10_000L, status = TransactionStatus.VOIDED),
            // Rejected pair
            tx(6L, 10L, 2_000_000L, Direction.OUTFLOW, base),
            tx(7L, 20L, 2_000_000L, Direction.INFLOW, base + 15_000L),
            // Already confirmed pair
            tx(8L, 10L, 3_000_000L, Direction.OUTFLOW, base),
            tx(9L, 20L, 3_000_000L, Direction.INFLOW, base + 15_000L),
        )
        val existingPairs = listOf(
            TransferPair(1L, 6L, 7L, TransferOrigin.AUTO, null, TransferStatus.REJECTED, 0L, base),
            TransferPair(2L, 8L, 9L, TransferOrigin.MANUAL, null, TransferStatus.CONFIRMED, 0L, base),
        )

        val suggestions = engine.findSuggestions(txs, existingPairs)
        assertThat(suggestions).isEmpty()
    }

    @Test
    fun selectsGreedyNonOverlappingPairsWithHighestConfidence() {
        val base = 1_700_000_000_000L
        val txs = listOf(
            tx(1L, 10L, 10_000_000L, Direction.OUTFLOW, base),
            tx(2L, 20L, 10_000_000L, Direction.INFLOW, base + 5_000L), // closest & exact match
            tx(3L, 30L, 10_000_000L, Direction.INFLOW, base + 20_000L),
        )

        val suggestions = engine.findSuggestions(txs, emptyList())
        assertThat(suggestions).hasSize(1)
        assertThat(suggestions.single().incoming.id).isEqualTo(2L)
    }
}
