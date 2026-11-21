package wtrack.integration.providers

import wtrack.integration.TransactionParser
import wtrack.integration.providers.bca.BcaTransactionParser
import wtrack.integration.providers.dana.DanaTransactionParser
import wtrack.integration.providers.jago.JagoTransactionParser

/**
 * Pure-Kotlin provider parser registry entry point for `:integration:providers`.
 *
 * All three initial PoC v1 parsers (Bank Jago, DANA, and myBCA) are active from verified
 * `iQOO Z10R` M3a notification captures.
 */
object ProviderParsers {
    fun registered(): Set<TransactionParser> = setOf(
        JagoTransactionParser(),
        DanaTransactionParser(),
        BcaTransactionParser(),
    )
}
