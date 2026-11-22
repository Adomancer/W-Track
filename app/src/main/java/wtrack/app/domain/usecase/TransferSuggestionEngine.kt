package wtrack.app.domain.usecase

import kotlin.math.abs
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.domain.model.TransferPair
import wtrack.app.domain.model.TransferStatus
import wtrack.integration.Direction

data class TransferSuggestion(
    val outgoing: Transaction,
    val incoming: Transaction,
    val estimatedFeeMinor: Long,
    val confidence: Double,
    val timeDeltaMillis: Long,
)

/**
 * Pure-Kotlin detector of likely inter-account transfer pairs among confirmed unlinked transactions.
 */
class TransferSuggestionEngine {

    fun findSuggestions(
        transactions: List<Transaction>,
        existingPairs: List<TransferPair>,
        maxWindowMillis: Long = DEFAULT_MAX_WINDOW_MILLIS,
    ): List<TransferSuggestion> {
        val confirmedLinkedIds = buildSet {
            existingPairs
                .filter { it.status == TransferStatus.CONFIRMED }
                .forEach {
                    add(it.outgoingTransactionId)
                    add(it.incomingTransactionId)
                }
        }
        val rejectedKeys = buildSet {
            existingPairs
                .filter { it.status == TransferStatus.REJECTED }
                .forEach { add(it.outgoingTransactionId to it.incomingTransactionId) }
        }

        val eligible = transactions.filter {
            it.status == TransactionStatus.CONFIRMED && it.id !in confirmedLinkedIds
        }
        val outflows = eligible.filter { it.direction == Direction.OUTFLOW }
        val inflows = eligible.filter { it.direction == Direction.INFLOW }

        val candidates = ArrayList<TransferSuggestion>()
        for (outTx in outflows) {
            for (inTx in inflows) {
                if (outTx.accountId == inTx.accountId) continue
                if (!outTx.currency.equals(inTx.currency, ignoreCase = true)) continue
                if ((outTx.id to inTx.id) in rejectedKeys) continue

                val timeDelta = abs(inTx.occurredAt - outTx.occurredAt)
                if (timeDelta > maxWindowMillis) continue

                if (outTx.amountMinor < inTx.amountMinor) continue
                val feeMinor = outTx.amountMinor - inTx.amountMinor
                val maxFeeMinor = maxAllowedFeeMinor(outTx.amountMinor, outTx.currency)
                if (feeMinor > maxFeeMinor) continue

                val timeFraction = timeDelta.toDouble() / maxWindowMillis.coerceAtLeast(1L).toDouble()
                val baseScore = if (feeMinor == 0L) 0.96 else 0.82
                val feeFraction = if (feeMinor == 0L || outTx.amountMinor <= 0L) {
                    0.0
                } else {
                    feeMinor.toDouble() / outTx.amountMinor.toDouble()
                }
                val confidence = (baseScore - timeFraction * 0.12 - feeFraction * 0.8)
                    .coerceIn(0.50, 0.99)

                candidates += TransferSuggestion(
                    outgoing = outTx,
                    incoming = inTx,
                    estimatedFeeMinor = feeMinor,
                    confidence = confidence,
                    timeDeltaMillis = timeDelta,
                )
            }
        }

        val usedTxIds = HashSet<Long>()
        val selected = ArrayList<TransferSuggestion>()
        candidates
            .sortedWith(
                compareByDescending<TransferSuggestion> { it.confidence }
                    .thenBy { it.timeDeltaMillis }
                    .thenByDescending { it.outgoing.occurredAt },
            )
            .forEach { candidate ->
                if (candidate.outgoing.id !in usedTxIds && candidate.incoming.id !in usedTxIds) {
                    usedTxIds += candidate.outgoing.id
                    usedTxIds += candidate.incoming.id
                    selected += candidate
                }
            }

        return selected
    }

    private fun maxAllowedFeeMinor(outgoingAmountMinor: Long, currency: String): Long {
        val unitScale = if (currency.uppercase() in ZERO_DECIMAL_CURRENCIES) 1L else 100L
        return maxOf(25_000L * unitScale, (outgoingAmountMinor * 0.05).toLong())
    }

    private companion object {
        const val DEFAULT_MAX_WINDOW_MILLIS = 30L * 60_000L
        val ZERO_DECIMAL_CURRENCIES = setOf("JPY", "KRW", "VND")
    }
}
