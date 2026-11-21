package wtrack.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GateG1EvaluatorTest {

    private val evaluator = GateG1Evaluator()

    @Test
    fun emitsGoWhenAtLeastTwoProvidersMeetAllThresholds() {
        val report = evaluator.evaluate(
            listOf(
                ProviderMeasurementInput("Bank Jago", days = 14, statementTxCountA = 40, capturedTxNotificationsB = 38, usableNotificationsC = 37, matchedStatementTxD = 37, unexplainedWrongAmountOrDirection = 0),
                ProviderMeasurementInput("DANA", days = 14, statementTxCountA = 35, capturedTxNotificationsB = 34, usableNotificationsC = 33, matchedStatementTxD = 32, unexplainedWrongAmountOrDirection = 0),
                ProviderMeasurementInput("BCA", days = 7, statementTxCountA = 12, capturedTxNotificationsB = 10, usableNotificationsC = 10, matchedStatementTxD = 10, unexplainedWrongAmountOrDirection = 0),
            ),
        )

        assertThat(report.passingProviderCount).isEqualTo(2)
        assertThat(report.verdict).isEqualTo(GateVerdict.GO)
        assertThat(report.providerResults[0].coverage).isWithin(0.001).of(0.925)
        assertThat(report.providerResults[0].completeness).isWithin(0.001).of(37.0 / 38.0)
        assertThat(report.providerResults[2].passed).isFalse()
    }

    @Test
    fun emitsReScopeWhenOnlyOneProviderPasses() {
        val report = evaluator.evaluate(
            listOf(
                ProviderMeasurementInput("Bank Jago", days = 10, statementTxCountA = 32, capturedTxNotificationsB = 30, usableNotificationsC = 30, matchedStatementTxD = 30, unexplainedWrongAmountOrDirection = 0),
                ProviderMeasurementInput("DANA", days = 10, statementTxCountA = 32, capturedTxNotificationsB = 30, usableNotificationsC = 20, matchedStatementTxD = 20, unexplainedWrongAmountOrDirection = 0),
            ),
        )
        assertThat(report.passingProviderCount).isEqualTo(1)
        assertThat(report.verdict).isEqualTo(GateVerdict.RE_SCOPE)
    }

    @Test
    fun emitsNoGoWheneverAnyProviderHasUnexplainedWrongAmountOrDirection() {
        val report = evaluator.evaluate(
            listOf(
                ProviderMeasurementInput("Bank Jago", days = 14, statementTxCountA = 50, capturedTxNotificationsB = 50, usableNotificationsC = 50, matchedStatementTxD = 50, unexplainedWrongAmountOrDirection = 1),
                ProviderMeasurementInput("DANA", days = 14, statementTxCountA = 50, capturedTxNotificationsB = 50, usableNotificationsC = 50, matchedStatementTxD = 50, unexplainedWrongAmountOrDirection = 0),
                ProviderMeasurementInput("BCA", days = 14, statementTxCountA = 50, capturedTxNotificationsB = 50, usableNotificationsC = 50, matchedStatementTxD = 50, unexplainedWrongAmountOrDirection = 0),
            ),
        )
        assertThat(report.verdict).isEqualTo(GateVerdict.NO_GO)
    }
}
