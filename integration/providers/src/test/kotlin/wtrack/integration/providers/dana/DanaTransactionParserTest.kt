package wtrack.integration.providers.dana

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wtrack.integration.Direction
import wtrack.integration.NotTransactionReason
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionType
import wtrack.integration.providers.loadAnonymizedFixtureSamples

class DanaTransactionParserTest {

    private val parser = DanaTransactionParser()

    @Test
    fun parsesCapturedAnonymizedDanaM3aSamplesFixture() = runTest {
        val fixtureEvents = loadAnonymizedFixtureSamples("/fixtures/dana/dana_m3a_samples.json")
        assertThat(fixtureEvents).hasSize(7)

        // Samples 0, 1, 4, 5, 6 are quest/mission pushes on the same channel -> PROMOTION
        listOf(0, 1, 4, 5, 6).forEach { idx ->
            assertThat(parser.parse(fixtureEvents[idx]))
                .isEqualTo(ParseResult.NotTransaction(NotTransactionReason.PROMOTION))
        }

        // Sample 2 is Android 16 auto-group summary -> OTHER
        assertThat(parser.parse(fixtureEvents[2]))
            .isEqualTo(ParseResult.NotTransaction(NotTransactionReason.OTHER))

        // Sample 3 is incoming transfer with trailing emoji stripped cleanly
        assertThat(parser.parse(fixtureEvents[3])).isEqualTo(
            ParseResult.Success(
                ParsedTransaction(
                    amountMinor = 200_000L,
                    currency = "IDR",
                    direction = Direction.INFLOW,
                    type = TransactionType.TRANSFER,
                    occurredAtEpochMillis = null,
                    merchantRaw = "ALEX PRATAMA",
                    description = "Diterima dari ALEX PRATAMA",
                    accountHint = null,
                    providerReference = null,
                    reportedBalanceMinor = null,
                ),
            ),
        )
    }

    @Test
    fun parsesLargeIndonesianAmountAndStripsTrailingSymbols() = runTest {
        val result = parser.parse(
            danaEvent(text = "Rp1.250.000,50 telah diterima dari BUDI SANTOSO! 🎉💰"),
        ) as ParseResult.Success
        assertThat(result.transaction.amountMinor).isEqualTo(125_000_050L)
        assertThat(result.transaction.direction).isEqualTo(Direction.INFLOW)
        assertThat(result.transaction.type).isEqualTo(TransactionType.TRANSFER)
        assertThat(result.transaction.merchantRaw).isEqualTo("BUDI SANTOSO")
    }

    @Test
    fun blocksSecurityNoticesPromosFailedAttemptsAndRedactedNotificationsSafely() = runTest {
        assertThat(
            parser.parse(danaEvent(title = "OTP", text = "Kode OTP Anda 482910. Jangan berikan kode ini.")),
        ).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.SECURITY_NOTICE))

        assertThat(
            parser.parse(danaEvent(text = "Pembayaran Rp50.000 gagal karena saldo tidak cukup.")),
        ).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.FAILED_ATTEMPT))

        assertThat(
            parser.parse(danaEvent(text = "Promo voucher cashback hingga Rp25.000 hari ini!")),
        ).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.PROMOTION))

        assertThat(
            parser.parse(danaEvent(title = null, text = "", isContentRedacted = true)),
        ).isEqualTo(ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, null))
    }

    @Test
    fun routesUnseenOutgoingFormatToNeedsReviewWithAmountOnlyNeverGuessingDirection() = runTest {
        val unseen = parser.parse(
            danaEvent(text = "Pembayaran Rp25.000 di Kopi Kenangan berhasil"),
        )
        assertThat(unseen).isEqualTo(
            ParseResult.NeedsReview(
                reason = ReviewReason.UNKNOWN_FORMAT,
                partial = PartialTransaction(
                    amountMinor = 2_500_000L,
                    currency = "IDR",
                    direction = null,
                    type = null,
                    description = "Pembayaran Rp25.000 di Kopi Kenangan berhasil",
                ),
            ),
        )
    }

    @Test
    fun supportsOnlyDanaPackage() {
        assertThat(parser.supports(danaEvent(text = "anything"))).isTrue()
        assertThat(parser.supports(danaEvent(packageName = "com.jago.digitalBanking", text = "anything"))).isFalse()
        assertThat(parser.supports(danaEvent(packageName = "com.bca.mybca.omni.android", text = "anything"))).isFalse()
    }

    private fun danaEvent(
        packageName: String = DanaTransactionParser.SOURCE_ID,
        title: String? = "DANA",
        text: String,
        isGroupSummary: Boolean = false,
        isContentRedacted: Boolean = false,
    ): NotificationEvent = NotificationEvent(
        sourceId = packageName,
        packageName = packageName,
        title = title,
        text = text,
        bigText = text.takeIf { it.isNotBlank() },
        subText = null,
        channelId = "fcm_default_channel_sound_2131951648",
        isGroupSummary = isGroupSummary,
        isContentRedacted = isContentRedacted,
        postedAtEpochMillis = 1_795_266_394_676L,
        notificationKey = "k",
        notificationId = 21006,
        notificationTag = null,
        sourceEventReference = null,
    )
}
