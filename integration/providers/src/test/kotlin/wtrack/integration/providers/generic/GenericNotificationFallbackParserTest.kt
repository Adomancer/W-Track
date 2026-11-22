package wtrack.integration.providers.generic

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wtrack.integration.Direction
import wtrack.integration.NotTransactionReason
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionType

class GenericNotificationFallbackParserTest {

    private val parser = GenericNotificationFallbackParser()

    private fun event(
        sourceId: String = "id.co.seabank.id",
        title: String? = "SeaBank",
        text: String = "",
        bigText: String? = null,
        isGroupSummary: Boolean = false,
        isContentRedacted: Boolean = false,
    ) = NotificationEvent(
        sourceId = sourceId,
        packageName = sourceId,
        title = title,
        text = text,
        bigText = bigText,
        subText = null,
        channelId = "default",
        isGroupSummary = isGroupSummary,
        isContentRedacted = isContentRedacted,
        postedAtEpochMillis = 1_700_000_000_000L,
        notificationKey = "0|$sourceId|1|null|1000",
        notificationId = 1,
        notificationTag = null,
        sourceEventReference = null,
    )

    @Test
    fun parsesIndonesianOutflowAndInflowIntoPrefilledNeedsReviewNeverAutoSuccess() = runTest {
        val outRes = parser.parse(
            event(
                text = "Pembayaran Rp25.000 ke Kopi Kenangan berhasil.",
            ),
        )
        assertThat(outRes).isInstanceOf(ParseResult.NeedsReview::class.java)
        val outReview = outRes as ParseResult.NeedsReview
        assertThat(outReview.reason).isEqualTo(ReviewReason.UNKNOWN_FORMAT)
        assertThat(outReview.partial?.amountMinor).isEqualTo(2_500_000L)
        assertThat(outReview.partial?.currency).isEqualTo("IDR")
        assertThat(outReview.partial?.direction).isEqualTo(Direction.OUTFLOW)
        assertThat(outReview.partial?.type).isEqualTo(TransactionType.PURCHASE)
        assertThat(outReview.partial?.merchantRaw).isEqualTo("Kopi Kenangan")

        val inRes = parser.parse(
            event(
                text = "Dana masuk Rp150.000 dari BUDI SANTOSO telah diterima",
            ),
        ) as ParseResult.NeedsReview
        assertThat(inRes.partial?.amountMinor).isEqualTo(15_000_000L)
        assertThat(inRes.partial?.currency).isEqualTo("IDR")
        assertThat(inRes.partial?.direction).isEqualTo(Direction.INFLOW)
        assertThat(inRes.partial?.type).isEqualTo(TransactionType.TRANSFER)
        assertThat(inRes.partial?.merchantRaw).isEqualTo("BUDI SANTOSO")
    }

    @Test
    fun parsesEnglishAndForeignCurrencyFormatsIntoNeedsReview() = runTest {
        val idrComma = parser.parse(
            event(text = "You paid IDR 125,500.00 at Tokopedia."),
        ) as ParseResult.NeedsReview
        assertThat(idrComma.partial?.amountMinor).isEqualTo(12_550_000L)
        assertThat(idrComma.partial?.currency).isEqualTo("IDR")
        assertThat(idrComma.partial?.direction).isEqualTo(Direction.OUTFLOW)
        assertThat(idrComma.partial?.merchantRaw).isEqualTo("Tokopedia")

        val usdRes = parser.parse(
            event(text = "You received USD 12.50 from Acme Corp."),
        ) as ParseResult.NeedsReview
        assertThat(usdRes.partial?.amountMinor).isEqualTo(1_250L)
        assertThat(usdRes.partial?.currency).isEqualTo("USD")
        assertThat(usdRes.partial?.direction).isEqualTo(Direction.INFLOW)
    }

    @Test
    fun filtersGroupSummarySecurityNoticesPromosFailedAndNonMonetaryNoise() = runTest {
        assertThat(parser.parse(event(isGroupSummary = true)))
            .isEqualTo(ParseResult.NotTransaction(NotTransactionReason.OTHER))

        assertThat(parser.parse(event(title = "OTP", text = "Kode OTP Anda 482910. Jangan berikan ke siapa pun.")))
            .isEqualTo(ParseResult.NotTransaction(NotTransactionReason.SECURITY_NOTICE))

        assertThat(parser.parse(event(text = "Promo cashback hingga Rp50.000 khusus hari ini!")))
            .isEqualTo(ParseResult.NotTransaction(NotTransactionReason.PROMOTION))

        assertThat(parser.parse(event(text = "Pembayaran Rp50.000 gagal karena saldo tidak cukup.")))
            .isEqualTo(ParseResult.NotTransaction(NotTransactionReason.FAILED_ATTEMPT))

        assertThat(parser.parse(event(text = "Update aplikasi versi terbaru sudah tersedia sekarang.")))
            .isEqualTo(ParseResult.NotTransaction(NotTransactionReason.OTHER))

        val redacted = parser.parse(event(text = "", isContentRedacted = true)) as ParseResult.NeedsReview
        assertThat(redacted.reason).isEqualTo(ReviewReason.UNKNOWN_FORMAT)
        assertThat(redacted.partial).isNull()
    }
}
