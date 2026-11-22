package wtrack.app.data.ingestion

import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.CustomParserRule
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.parser.ParserRegistry
import wtrack.integration.NotificationEvent
import wtrack.integration.TransactionParser
import wtrack.integration.TransactionType
import wtrack.integration.providers.common.ProviderNumberLocale
import wtrack.integration.providers.custom.CompositeSourceParser
import wtrack.integration.providers.custom.CustomParserRuleSpec
import wtrack.integration.providers.generic.GenericNotificationFallbackParser

/**
 * Resolves parsers with three-tier precedence:
 * 1. Built-in provider parser (highest version matching `sourceId`)
 * 2. User-taught custom parser rules for `sourceId` (also rescues `UNKNOWN_FORMAT` on built-in apps)
 * 3. [GenericNotificationFallbackParser] (when [enableGenericFallback] is true), routing newly
 *    added bank/wallet notifications to pre-filled `NeedsReview` instead of dropping as `NO_PARSER`.
 */
class SmartParserRegistry(
    private val builtInParsers: Set<TransactionParser>,
    private val customRulesProvider: (String) -> List<CustomParserRule>,
    private val customSourceIdsProvider: () -> Set<String> = { emptySet() },
    private val enableGenericFallback: Boolean = true,
) : ParserRegistry {

    private val fallbackParser = GenericNotificationFallbackParser()

    override fun resolve(event: NotificationEvent): TransactionParser? {
        val builtIn = builtInParsers
            .asSequence()
            .filter { it.sourceId == event.sourceId && it.supports(event) }
            .maxByOrNull { it.version }

        val customSpecs = customRulesProvider(event.sourceId).map { it.toSpec() }

        return when {
            builtIn != null && customSpecs.isEmpty() -> builtIn
            builtIn != null || customSpecs.isNotEmpty() -> CompositeSourceParser(
                sourceId = event.sourceId,
                builtInParser = builtIn,
                customRules = customSpecs,
                fallbackParser = if (enableGenericFallback) fallbackParser else null,
            )
            enableGenericFallback -> fallbackParser
            else -> null
        }
    }

    override fun parserVersionForSource(sourceId: String): Int? {
        val builtInVersion = builtInParsers
            .asSequence()
            .filter { it.sourceId == sourceId }
            .maxOfOrNull { it.version }
        if (builtInVersion != null) return builtInVersion
        return if (customRulesProvider(sourceId).isNotEmpty()) 1 else null
    }

    override fun registeredCount(): Int {
        val builtInSources = builtInParsers.map { it.sourceId }.toSet()
        val extraCustomSources = customSourceIdsProvider().count { it !in builtInSources }
        return builtInParsers.size + extraCustomSources
    }
}

internal fun CustomParserRule.toSpec(): CustomParserRuleSpec = CustomParserRuleSpec(
    id = id,
    sourceId = sourceId,
    name = name,
    titleContains = titleContains,
    templatePattern = templatePattern,
    direction = direction,
    transactionType = transactionType.toIntegrationType(),
    currency = currency,
    numberLocale = numberLocale.toProviderLocale(),
    accountHint = accountHint,
    updatedAt = updatedAt,
)

internal fun DomainTransactionType.toIntegrationType(): TransactionType = when (this) {
    DomainTransactionType.PURCHASE -> TransactionType.PURCHASE
    DomainTransactionType.TRANSFER -> TransactionType.TRANSFER
    DomainTransactionType.FEE -> TransactionType.FEE
    DomainTransactionType.REFUND -> TransactionType.REFUND
    DomainTransactionType.CASH -> TransactionType.CASH
    DomainTransactionType.OTHER,
    DomainTransactionType.ADJUSTMENT -> TransactionType.OTHER
}

internal fun CustomNumberLocale.toProviderLocale(): ProviderNumberLocale = when (this) {
    CustomNumberLocale.INDONESIAN -> ProviderNumberLocale.INDONESIAN
    CustomNumberLocale.ENGLISH -> ProviderNumberLocale.ENGLISH
}

internal fun ProviderNumberLocale.toDomainLocale(): CustomNumberLocale = when (this) {
    ProviderNumberLocale.ENGLISH -> CustomNumberLocale.ENGLISH
    ProviderNumberLocale.INDONESIAN,
    ProviderNumberLocale.UNKNOWN -> CustomNumberLocale.INDONESIAN
}
