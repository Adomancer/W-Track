package wtrack.integration.providers.bca

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

class BcaTransactionParserTest {

    private val parser = BcaTransactionParser()

    @Test
    fun parsesCapturedAnonymizedMyBcaM3aSamplesFixture() = runTest {
        val fixtureEvents = loadAnonymizedFixtureSamples("/fixtures/bca/bca_m3a_samples.json")
        assertThat(fixtureEvents).hasSize(3)

        val incoming = parser.parse(fixtureEvents[0])
        assertThat(incoming).isEqualTo(
            ParseResult.Success(
                ParsedTransaction(
                    amountMinor = 5_000_000L,
                    currency = "IDR",
                    direction = Direction.INFLOW,
                    type = TransactionType.TRANSFER,
                    occurredAtEpochMillis = null,
                    merchantRaw = "AL** PR***MA",
                    description = "Received from AL** PR***MA (Account Transfer)",
                    accountHint = null,
                    providerReference = null,
                    reportedBalanceMinor = null,
                ),
            ),
        )

        val outgoing = parser.parse(fixtureEvents[1])
        assertThat(outgoing).isEqualTo(
            ParseResult.Success(
                ParsedTransaction(
                    amountMinor = 5_000_000L,
                    currency = "IDR",
                    direction = Direction.OUTFLOW,
                    type = TransactionType.TRANSFER,
                    occurredAtEpochMillis = null,
                    merchantRaw = "Account Transfer",
                    description = "Spent at Account Transfer",
                    accountHint = null,
                    providerReference = null,
                    reportedBalanceMinor = null,
                ),
            ),
        )

        val groupSummary = parser.parse(fixtureEvents[2])
        assertThat(groupSummary).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.OTHER))
    }

    @Test
    fun handlesEnglishLocaleAmountsCategoriesAndForeignCurrency() = runTest {
        val smallAmount = parser.parse(
            myBcaEvent(text = "You spent IDR 500.00 at Food & Beverage."),
        ) as ParseResult.Success
        assertThat(smallAmount.transaction.amountMinor).isEqualTo(50_000L)
        assertThat(smallAmount.transaction.direction).isEqualTo(Direction.OUTFLOW)
        assertThat(smallAmount.transaction.type).isEqualTo(TransactionType.PURCHASE)
        assertThat(smallAmount.transaction.merchantRaw).isEqualTo("Food & Beverage")

        val largeAmount = parser.parse(
            myBcaEvent(text = "You received IDR 1,250,000.50 from PT MAJU JAYA at Income category."),
        ) as ParseResult.Success
        assertThat(largeAmount.transaction.amountMinor).isEqualTo(125_000_050L)
        assertThat(largeAmount.transaction.direction).isEqualTo(Direction.INFLOW)
        assertThat(largeAmount.transaction.type).isEqualTo(TransactionType.OTHER)
        assertThat(largeAmount.transaction.merchantRaw).isEqualTo("PT MAJU JAYA")

        val fee = parser.parse(
            myBcaEvent(text = "You spent IDR 2,500.00 at Admin Fee."),
        ) as ParseResult.Success
        assertThat(fee.transaction.amountMinor).isEqualTo(250_000L)
        assertThat(fee.transaction.type).isEqualTo(TransactionType.FEE)

        val refund = parser.parse(
            myBcaEvent(text = "You received IDR 25,000.00 from MERCHANT X at Refund category."),
        ) as ParseResult.Success
        assertThat(refund.transaction.amountMinor).isEqualTo(2_500_000L)
        assertThat(refund.transaction.direction).isEqualTo(Direction.INFLOW)
        assertThat(refund.transaction.type).isEqualTo(TransactionType.REFUND)

        val usdSpend = parser.parse(
            myBcaEvent(text = "You spent USD 12.50 at Shopping."),
        ) as ParseResult.Success
        assertThat(usdSpend.transaction.amountMinor).isEqualTo(1_250L)
        assertThat(usdSpend.transaction.currency).isEqualTo("USD")
        assertThat(usdSpend.transaction.type).isEqualTo(TransactionType.PURCHASE)
    }

    @Test
    fun blocksSecurityNoticesPromosFailedAttemptsAndRedactedNotificationsSafely() = runTest {
        assertThat(
            parser.parse(myBcaEvent(title = "OTP", text = "Kode OTP Anda 482910. Jangan berikan kode ini.")),
        ).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.SECURITY_NOTICE))

        assertThat(
            parser.parse(myBcaEvent(text = "Transaksi IDR 50,000.00 gagal karena saldo tidak cukup.")),
        ).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.FAILED_ATTEMPT))

        assertThat(
            parser.parse(myBcaEvent(text = "Promo cashback hingga IDR 25,000.00 hari ini!")),
        ).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.PROMOTION))

        assertThat(
            parser.parse(myBcaEvent(title = null, text = "", isContentRedacted = true)),
        ).isEqualTo(ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, null))
    }

    @Test
    fun routesUnseenFormatToNeedsReviewWithAmountOnlyNeverGuessingDirection() = runTest {
        val unseen = parser.parse(
            myBcaEvent(text = "Autodebit IDR 75,000.00 scheduled."),
        )
        assertThat(unseen).isEqualTo(
            ParseResult.NeedsReview(
                reason = ReviewReason.UNKNOWN_FORMAT,
                partial = PartialTransaction(
                    amountMinor = 7_500_000L,
                    currency = "IDR",
                    direction = null,
                    type = null,
                    description = "Autodebit IDR 75,000.00 scheduled.",
                ),
            ),
        )
    }

    @Test
    fun supportsOnlyMyBcaPackage() {
        assertThat(parser.supports(myBcaEvent(text = "anything"))).isTrue()
        assertThat(parser.supports(myBcaEvent(packageName = "com.bca", text = "anything"))).isFalse()
        assertThat(parser.supports(myBcaEvent(packageName = "id.dana", text = "anything"))).isFalse()
        assertThat(parser.supports(myBcaEvent(packageName = "com.jago.digitalBanking", text = "anything"))).isFalse()
    }

    private fun myBcaEvent(
        packageName: String = BcaTransactionParser.MYBCA_SOURCE_ID,
        title: String? = "Financial Diary",
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
        channelId = "General Notification",
        isGroupSummary = isGroupSummary,
        isContentRedacted = isContentRedacted,
        postedAtEpochMillis = 1_795_265_482_088L,
        notificationKey = "k",
        notificationId = 1,
        notificationTag = null,
        sourceEventReference = null,
    )
}
