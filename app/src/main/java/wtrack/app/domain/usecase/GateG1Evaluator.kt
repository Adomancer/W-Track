package wtrack.app.domain.usecase

/**
 * Pure-Kotlin evaluator for Gate G1 ingestion quality metrics (PRD section 17 & Appendix C, ADR-003).
 */
data class GateG1Thresholds(
    val minCoverage: Double = 0.90,
    val minCompleteness: Double = 0.95,
    val maxUnexplainedWrong: Int = 0,
    val minDays: Int = 7,
    val preferredDays: Int = 14,
    val minStatementTxPerProvider: Int = 30,
    val minPassingProviders: Int = 2,
)

data class ProviderMeasurementInput(
    val providerName: String,
    val days: Int,
    /** A: transactions in the provider's official statement. */
    val statementTxCountA: Int,
    /** B: transaction notifications captured by W-Track. */
    val capturedTxNotificationsB: Int,
    /** C: captured notifications with usable amount, direction, and time. */
    val usableNotificationsC: Int,
    /** D: statement transactions backed by a usable notification. */
    val matchedStatementTxD: Int,
    val unexplainedWrongAmountOrDirection: Int,
    val duplicateOrUpdateNotes: String = "",
)

data class ProviderGateResult(
    val input: ProviderMeasurementInput,
    /** Coverage = D / A (Appendix C). */
    val coverage: Double,
    /** Completeness = C / B (Appendix C). */
    val completeness: Double,
    val meetsCoverage: Boolean,
    val meetsCompleteness: Boolean,
    val meetsZeroWrongErrors: Boolean,
    val meetsDuration: Boolean,
    val meetsPreferredDuration: Boolean,
    val meetsSampleVolume: Boolean,
    val passed: Boolean,
    val blockingReasons: List<String>,
)

enum class GateVerdict {
    GO,
    RE_SCOPE,
    NO_GO,
}

data class GateG1Report(
    val thresholds: GateG1Thresholds,
    val providerResults: List<ProviderGateResult>,
    val passingProviderCount: Int,
    val verdict: GateVerdict,
    val summary: String,
)

class GateG1Evaluator {
    fun evaluate(
        inputs: List<ProviderMeasurementInput>,
        thresholds: GateG1Thresholds = GateG1Thresholds(),
    ): GateG1Report {
        val results = inputs.map { input -> evaluateSingle(input, thresholds) }
        val passingCount = results.count { it.passed }
        val anyZeroErrorViolation = results.any { !it.meetsZeroWrongErrors }

        val verdict = when {
            results.isEmpty() -> GateVerdict.NO_GO
            anyZeroErrorViolation -> GateVerdict.NO_GO
            passingCount >= thresholds.minPassingProviders -> GateVerdict.GO
            passingCount >= 1 -> GateVerdict.RE_SCOPE
            else -> GateVerdict.NO_GO
        }

        val summary = when (verdict) {
            GateVerdict.GO ->
                "GO: $passingCount/${results.size} providers met all Gate G1 thresholds."
            GateVerdict.RE_SCOPE ->
                "RE_SCOPE: $passingCount/${results.size} providers passed (need ${thresholds.minPassingProviders}); evaluate single-provider scope or notification constraints."
            GateVerdict.NO_GO ->
                "NO_GO: Critical Gate G1 criteria failed ($passingCount/${results.size} passing, wrong-field violations=$anyZeroErrorViolation)."
        }

        return GateG1Report(
            thresholds = thresholds,
            providerResults = results,
            passingProviderCount = passingCount,
            verdict = verdict,
            summary = summary,
        )
    }

    private fun evaluateSingle(
        input: ProviderMeasurementInput,
        t: GateG1Thresholds,
    ): ProviderGateResult {
        val coverage = if (input.statementTxCountA > 0) {
            input.matchedStatementTxD.toDouble() / input.statementTxCountA.toDouble()
        } else {
            0.0
        }
        val completeness = if (input.capturedTxNotificationsB > 0) {
            input.usableNotificationsC.toDouble() / input.capturedTxNotificationsB.toDouble()
        } else {
            0.0
        }

        val meetsCoverage = coverage >= t.minCoverage
        val meetsCompleteness = completeness >= t.minCompleteness
        val meetsZeroWrongErrors = input.unexplainedWrongAmountOrDirection <= t.maxUnexplainedWrong
        val meetsDuration = input.days >= t.minDays
        val meetsPreferredDuration = input.days >= t.preferredDays
        val meetsSampleVolume = input.statementTxCountA >= t.minStatementTxPerProvider

        val reasons = buildList {
            if (!meetsCoverage) add("Coverage ${(coverage * 100).toInt()}% < ${(t.minCoverage * 100).toInt()}%")
            if (!meetsCompleteness) add("Completeness ${(completeness * 100).toInt()}% < ${(t.minCompleteness * 100).toInt()}%")
            if (!meetsZeroWrongErrors) add("Wrong amount/direction = ${input.unexplainedWrongAmountOrDirection} > ${t.maxUnexplainedWrong}")
            if (!meetsDuration) add("Duration ${input.days}d < ${t.minDays}d")
            if (!meetsSampleVolume) add("Statement count ${input.statementTxCountA} < ${t.minStatementTxPerProvider}")
        }

        val passed = meetsCoverage &&
            meetsCompleteness &&
            meetsZeroWrongErrors &&
            meetsDuration &&
            meetsSampleVolume

        return ProviderGateResult(
            input = input,
            coverage = coverage,
            completeness = completeness,
            meetsCoverage = meetsCoverage,
            meetsCompleteness = meetsCompleteness,
            meetsZeroWrongErrors = meetsZeroWrongErrors,
            meetsDuration = meetsDuration,
            meetsPreferredDuration = meetsPreferredDuration,
            meetsSampleVolume = meetsSampleVolume,
            passed = passed,
            blockingReasons = reasons,
        )
    }
}
