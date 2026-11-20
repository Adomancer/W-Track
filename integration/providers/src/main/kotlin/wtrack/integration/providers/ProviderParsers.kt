package wtrack.integration.providers

import wtrack.integration.TransactionParser

/**
 * Pure-Kotlin provider parser registry entry point for `:integration:providers`.
 *
 * Provider parsers (Bank Jago, DANA, BCA) will be registered here incrementally after M0–M2
 * ingestion foundations and real-device notification inspection on `iQOO Z10R` are completed.
 */
object ProviderParsers {
    fun registered(): Set<TransactionParser> = emptySet()
}
