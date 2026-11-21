package wtrack.integration.providers.jago

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

class JagoTransactionParserTest {

    private val parser = JagoTransactionParser()

    @Test
    fun parsesCapturedAnonymizedM3aSamplesFixture() = runTest {
        val fixtureEvents = loadAnonymizedFixtureSamples("/fixtures/jago/jago_m3a_samples.json")
        assertThat(fixtureEvents).hasSize(7)

        val incoming = parser.parse(fixtureEvents[0])
        assertThat(incoming).isEqualTo(
            ParseResult.Success(
                ParsedTransaction(
                    amountMinor = 5_000_000L,
                    currency = "IDR",
                    direction = Direction.INFLOW,
                    type = TransactionType.TRANSFER,
                    occurredAtEpochMillis = null,
                    merchantRaw = "ALEX PRATAMA",
                    description = "Transfer from ALEX PRATAMA",
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
                    merchantRaw = "BUDI SANTOSO",
                    description = "Transfer to BUDI SANTOSO",
                    accountHint = null,
                    providerReference = null,
                    reportedBalanceMinor = null,
                ),
            ),
        )

        val groupSummary = parser.parse(fixtureEvents[2])
        assertThat(groupSummary).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.OTHER))

        val subPocketToMain = parser.parse(fixtureEvents[3])
        assertThat(subPocketToMain).isInstanceOf(ParseResult.Success::class.java)
        val subToMainTx = (subPocketToMain as ParseResult.Success).transaction
        assertThat(subToMainTx.direction).isEqualTo(Direction.INFLOW)
        assertThat(subToMainTx.amountMinor).isEqualTo(250_000_00L)
        assertThat(subToMainTx.accountHint).isEqualTo(JagoTransactionParser.MAIN_POCKET_HINT)
        assertThat(subToMainTx.merchantRaw).isEqualTo("Laptop Savings")
        assertThat(subToMainTx.description).isEqualTo("Moved from Laptop Savings to Main Pocket")

        val mainToSubPocket = parser.parse(fixtureEvents[4])
        assertThat(mainToSubPocket).isInstanceOf(ParseResult.Success::class.java)
        val mainToSubTx = (mainToSubPocket as ParseResult.Success).transaction
        assertThat(mainToSubTx.direction).isEqualTo(Direction.OUTFLOW)
        assertThat(mainToSubTx.amountMinor).isEqualTo(250_000_00L)
        assertThat(mainToSubTx.accountHint).isEqualTo(JagoTransactionParser.MAIN_POCKET_HINT)
        assertThat(mainToSubTx.merchantRaw).isEqualTo("Laptop Savings")
        assertThat(mainToSubTx.description).isEqualTo("Moved from Main Pocket to Laptop Savings")

        val subAToSubB = parser.parse(fixtureEvents[5])
        assertThat(subAToSubB).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.OTHER))

        val subBToSubA = parser.parse(fixtureEvents[6])
        assertThat(subBToSubA).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.OTHER))
    }

    @Test
    fun handlesLargeIndonesianAmountsCurlyApostropheAndHelplineExclusion() = runTest {
        val largeTransfer = parser.parse(
            jagoEvent(
                text = "You’ve transferred Rp1.250.000,50 to PT. MAJU JAYA. Need help? Contact Tanya Jago at 1500 746.",
            ),
        )
        assertThat(largeTransfer).isEqualTo(
            ParseResult.Success(
                ParsedTransaction(
                    amountMinor = 125_000_050L,
                    currency = "IDR",
                    direction = Direction.OUTFLOW,
                    type = TransactionType.TRANSFER,
                    occurredAtEpochMillis = null,
                    merchantRaw = "PT. MAJU JAYA",
                    description = "Transfer to PT. MAJU JAYA",
                    accountHint = null,
                    providerReference = null,
                    reportedBalanceMinor = null,
                ),
            ),
        )

        val incomingWithoutSpace = parser.parse(
            jagoEvent(
                text = "SITI RAHMA has sent Rp2.500.000 to you. Need help? Contact Tanya Jago at 1500 746.",
            ),
        )
        val success = incomingWithoutSpace as ParseResult.Success
        assertThat(success.transaction.amountMinor).isEqualTo(250_000_000L)
        assertThat(success.transaction.direction).isEqualTo(Direction.INFLOW)
        assertThat(success.transaction.merchantRaw).isEqualTo("SITI RAHMA")
        assertThat(success.transaction.description).doesNotContain("1500 746")
    }

    @Test
    fun blocksSecurityNoticesPromosFailedAttemptsAndRedactedNotificationsSafely() = runTest {
        assertThat(
            parser.parse(jagoEvent(title = "OTP", text = "Kode OTP Anda 482910. Jangan berikan kode ini.")),
        ).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.SECURITY_NOTICE))

        assertThat(
            parser.parse(jagoEvent(text = "Transaksi Rp50.000 gagal karena saldo tidak cukup.")),
        ).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.FAILED_ATTEMPT))

        assertThat(
            parser.parse(jagoEvent(text = "Promo cashback hingga Rp25.000 hari ini!")),
        ).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.PROMOTION))

        assertThat(
            parser.parse(jagoEvent(title = null, text = "", isContentRedacted = true)),
        ).isEqualTo(ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, null))
    }

    @Test
    fun routesUnseenFormatToNeedsReviewWithAmountOnlyNeverGuessingDirection() = runTest {
        val unseen = parser.parse(
            jagoEvent(
                text = "You paid Rp25.000 at Kopi Kenangan. Need help? Contact Tanya Jago at 1500 746.",
            ),
        )
        assertThat(unseen).isEqualTo(
            ParseResult.NeedsReview(
                reason = ReviewReason.UNKNOWN_FORMAT,
                partial = PartialTransaction(
                    amountMinor = 2_500_000L,
                    currency = "IDR",
                    direction = null,
                    type = null,
                    description = "You paid Rp25.000 at Kopi Kenangan.",
                ),
            ),
        )
    }

    @Test
    fun parsesActivePocketMoveIntoMainPocketAndKantongUtama() = runTest {
        val activeIntoMain = parser.parse(
            jagoEvent(
                text = "You've moved Rp125.000 from Daily Savings Pocket to your Main Pocket Pocket. Click here to see how to add money to Jago. Need help? Contact Tanya Jago at 1500 746.",
            ),
        )
        assertThat(activeIntoMain).isInstanceOf(ParseResult.Success::class.java)
        val activeTx = (activeIntoMain as ParseResult.Success).transaction
        assertThat(activeTx.direction).isEqualTo(Direction.INFLOW)
        assertThat(activeTx.amountMinor).isEqualTo(125_000_00L)
        assertThat(activeTx.accountHint).isEqualTo("Main Pocket")
        assertThat(activeTx.merchantRaw).isEqualTo("Daily Savings")

        val outOfMainPocket = parser.parse(
            jagoEvent(
                text = "You've moved Rp75.000 out of your Main Pocket Pocket. Need help? Contact Tanya Jago at 1500 746.",
            ),
        )
        assertThat(outOfMainPocket).isInstanceOf(ParseResult.Success::class.java)
        val outOfMainTx = (outOfMainPocket as ParseResult.Success).transaction
        assertThat(outOfMainTx.direction).isEqualTo(Direction.OUTFLOW)
        assertThat(outOfMainTx.amountMinor).isEqualTo(75_000_00L)
        assertThat(outOfMainTx.accountHint).isEqualTo("Main Pocket")

        val indonesianMainPocket = parser.parse(
            jagoEvent(
                text = "Rp300.000 has been moved from your Kantong Utama Pocket to your Emergency Pocket.",
            ),
        )
        assertThat(indonesianMainPocket).isInstanceOf(ParseResult.Success::class.java)
        val idTx = (indonesianMainPocket as ParseResult.Success).transaction
        assertThat(idTx.direction).isEqualTo(Direction.OUTFLOW)
        assertThat(idTx.amountMinor).isEqualTo(300_000_00L)
        assertThat(idTx.merchantRaw).isEqualTo("Emergency")
        assertThat(idTx.accountHint).isEqualTo("Main Pocket")
    }

    @Test
    fun parserVersionIsTwo() {
        assertThat(parser.version).isEqualTo(2)
    }

    @Test
    fun supportsOnlyBankJagoPackage() {
        assertThat(parser.supports(jagoEvent(text = "anything"))).isTrue()
        assertThat(parser.supports(jagoEvent(packageName = "id.dana", text = "anything"))).isFalse()
    }

    private fun jagoEvent(
        packageName: String = JagoTransactionParser.SOURCE_ID,
        title: String? = "Jago",
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
        channelId = "foreground",
        isGroupSummary = isGroupSummary,
        isContentRedacted = isContentRedacted,
        postedAtEpochMillis = 1_795_264_548_346L,
        notificationKey = "k",
        notificationId = 1,
        notificationTag = null,
        sourceEventReference = null,
    )
}
