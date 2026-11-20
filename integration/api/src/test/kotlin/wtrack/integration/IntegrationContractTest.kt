package wtrack.integration

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class IntegrationContractTest {
    @Test
    fun parserTransactionTypesExcludeAdjustment() {
        val names = TransactionType.entries.map { it.name }
        assertThat(names).containsExactly(
            "PURCHASE",
            "TRANSFER",
            "FEE",
            "REFUND",
            "CASH",
            "OTHER",
        )
    }

    @Test
    fun reviewAndNonTransactionReasonsCoverRequiredStatuses() {
        assertThat(ReviewReason.entries).contains(ReviewReason.PIPELINE_ERROR)
        assertThat(ReviewReason.entries).contains(ReviewReason.AMBIGUOUS_FIELD)
        assertThat(NotTransactionReason.entries).contains(NotTransactionReason.FAILED_ATTEMPT)
        assertThat(NotTransactionReason.entries).contains(NotTransactionReason.SECURITY_NOTICE)
    }

    @Test
    fun partialTransactionSerializesRoundTrip() {
        val draft = PartialTransaction(
            amountMinor = 5_000_000L,
            currency = "IDR",
            direction = Direction.OUTFLOW,
            type = TransactionType.PURCHASE,
            merchantRaw = "SYNTHETIC_MERCHANT",
        )
        val encoded = Json.encodeToString(draft)
        val decoded = Json.decodeFromString<PartialTransaction>(encoded)
        assertThat(decoded).isEqualTo(draft)
    }

    @Test
    fun notificationEventRetainsIdentityAndEnvelopeFields() {
        val event = NotificationEvent(
            sourceId = "com.example.provider",
            packageName = "com.example.provider",
            title = "Title",
            text = "Body",
            bigText = "Expanded Body",
            subText = "Sub",
            channelId = "tx_channel",
            isGroupSummary = false,
            isContentRedacted = false,
            postedAtEpochMillis = 1_700_000_000_000L,
            notificationKey = "0|com.example.provider|101|null|10001",
            notificationId = 101,
            notificationTag = "tx_tag",
            sourceEventReference = "REF-1",
        )
        assertThat(event.postedAtEpochMillis).isEqualTo(1_700_000_000_000L)
        assertThat(event.notificationKey).isEqualTo("0|com.example.provider|101|null|10001")
        assertThat(event.isContentRedacted).isFalse()
    }
}
