package wtrack.app.domain.parser

import wtrack.integration.NotificationEvent
import wtrack.integration.TransactionParser

/**
 * Resolves a compatible [TransactionParser] for an allowlisted notification event (PAR-01, PAR-08).
 * Returns null when no parser supports the source, triggering the ING-13 unsupported-source rule.
 */
interface ParserRegistry {
    fun resolve(event: NotificationEvent): TransactionParser?
    fun parserVersionForSource(sourceId: String): Int? = null
    fun registeredCount(): Int = 0
}

class DefaultParserRegistry(
    private val parsers: Set<TransactionParser>,
) : ParserRegistry {
    override fun resolve(event: NotificationEvent): TransactionParser? =
        parsers
            .asSequence()
            .filter { it.sourceId == event.sourceId && it.supports(event) }
            .maxByOrNull { it.version }

    override fun parserVersionForSource(sourceId: String): Int? =
        parsers
            .asSequence()
            .filter { it.sourceId == sourceId }
            .maxOfOrNull { it.version }

    override fun registeredCount(): Int = parsers.size
}
