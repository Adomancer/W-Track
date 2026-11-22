package wtrack.integration.providers.custom

import wtrack.integration.NotTransactionReason
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionParser
import wtrack.integration.providers.common.SensitiveContentGuard

/**
 * Combines parsers for a single notification source with strict precedence:
 * 1. Built-in provider parser (e.g., Bank Jago, DANA, myBCA)
 * 2. User-taught custom parser rules (also rescues [ReviewReason.UNKNOWN_FORMAT] on built-in apps)
 * 3. Generic fallback parser (routes unseen monetary formats to `NeedsReview` with pre-filled hints)
 */
class CompositeSourceParser(
    override val sourceId: String,
    private val builtInParser: TransactionParser?,
    private val customRules: List<CustomParserRuleSpec>,
    private val fallbackParser: TransactionParser?,
) : TransactionParser {

    override val version: Int = builtInParser?.version ?: if (customRules.isNotEmpty()) 1 else 0

    override fun supports(event: NotificationEvent): Boolean =
        event.sourceId == sourceId || event.packageName == sourceId

    override suspend fun parse(event: NotificationEvent): ParseResult {
        if (builtInParser != null && builtInParser.supports(event)) {
            val builtInResult = builtInParser.parse(event)
            if (builtInResult is ParseResult.NeedsReview &&
                builtInResult.reason == ReviewReason.UNKNOWN_FORMAT &&
                !event.isContentRedacted &&
                customRules.isNotEmpty()
            ) {
                SensitiveContentGuard.inspectPreconditions(event)?.let { return it }
                SensitiveContentGuard.inspectNonTransactionNotice(event)?.let { return it }
                NotificationTemplateEngine.parseWithRules(event, customRules)?.let { return it }
            }
            return builtInResult
        }

        if (event.isGroupSummary) {
            return ParseResult.NotTransaction(NotTransactionReason.OTHER)
        }
        SensitiveContentGuard.inspectPreconditions(event)?.let { return it }
        SensitiveContentGuard.inspectNonTransactionNotice(event)?.let { return it }

        if (customRules.isNotEmpty()) {
            NotificationTemplateEngine.parseWithRules(event, customRules)?.let { return it }
        }

        return fallbackParser?.parse(event)
            ?: ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, null)
    }
}
