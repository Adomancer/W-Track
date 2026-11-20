package wtrack.app.domain.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wtrack.integration.NotificationEvent
import wtrack.integration.NotTransactionReason
import wtrack.integration.ParseResult
import wtrack.integration.TransactionParser

class ParserRegistryTest {
    private fun eventFor(sourceId: String) = NotificationEvent(
        sourceId = sourceId,
        packageName = sourceId,
        title = null,
        text = "Sample",
        bigText = null,
        subText = null,
        channelId = null,
        isGroupSummary = false,
        isContentRedacted = false,
        postedAtEpochMillis = 1_700_000_000_000L,
        notificationKey = null,
        notificationId = 1,
        notificationTag = null,
        sourceEventReference = null,
    )

    private fun fakeParser(
        sourceId: String,
        version: Int,
        supported: Boolean,
    ): TransactionParser = object : TransactionParser {
        override val sourceId: String = sourceId
        override val version: Int = version
        override fun supports(event: NotificationEvent): Boolean = supported
        override suspend fun parse(event: NotificationEvent): ParseResult =
            ParseResult.NotTransaction(NotTransactionReason.OTHER)
    }

    @Test
    fun returnsNullWhenNoParserMatchesSource() {
        val registry = DefaultParserRegistry(emptySet())
        assertThat(registry.resolve(eventFor("com.example.unsupported"))).isNull()
    }

    @Test
    fun selectsHighestVersionSupportedParserForSource() {
        val v1 = fakeParser(sourceId = "com.example.bank", version = 1, supported = true)
        val v2 = fakeParser(sourceId = "com.example.bank", version = 2, supported = true)
        val v3Unsupported = fakeParser(sourceId = "com.example.bank", version = 3, supported = false)
        val registry = DefaultParserRegistry(setOf(v1, v2, v3Unsupported))

        val resolved = registry.resolve(eventFor("com.example.bank"))
        assertThat(resolved).isSameInstanceAs(v2)
    }
}
