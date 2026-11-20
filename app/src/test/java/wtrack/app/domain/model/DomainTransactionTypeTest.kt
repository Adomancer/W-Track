package wtrack.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wtrack.integration.TransactionType

class DomainTransactionTypeTest {
    @Test
    fun domainIncludesAdjustmentWhileIntegrationApiDoesNot() {
        assertThat(DomainTransactionType.entries).contains(DomainTransactionType.ADJUSTMENT)
        assertThat(TransactionType.entries.map { it.name }).doesNotContain("ADJUSTMENT")
    }

    @Test
    fun allParserTransactionTypesMapExhaustivelyToDomainTypes() {
        val mapped = TransactionType.entries.map { it.toDomain() }
        assertThat(mapped).containsExactly(
            DomainTransactionType.PURCHASE,
            DomainTransactionType.TRANSFER,
            DomainTransactionType.FEE,
            DomainTransactionType.REFUND,
            DomainTransactionType.CASH,
            DomainTransactionType.OTHER,
        )
    }
}
