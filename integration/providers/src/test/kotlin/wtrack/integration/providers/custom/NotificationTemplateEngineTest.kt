package wtrack.integration.providers.custom

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wtrack.integration.Direction
import wtrack.integration.NotTransactionReason
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionType
import wtrack.integration.providers.common.ProviderNumberLocale
import wtrack.integration.providers.dana.DanaTransactionParser
import wtrack.integration.providers.generic.GenericNotificationFallbackParser

class NotificationTemplateEngineTest {

    private fun event(
        sourceId: String = "com.gojek.app",
        title: String? = "GoPay",
        text: String,
    ) = NotificationEvent(
        sourceId = sourceId,
        packageName = sourceId,
        title = title,
        text = text,
        bigText = null,
        subText = null,
        channelId = "tx",
        isGroupSummary = false,
        isContentRedacted = false,
        postedAtEpochMillis = 1_700_000_000_000L,
        notificationKey = "0|$sourceId|1|null|1000",
        notificationId = 1,
        notificationTag = null,
        sourceEventReference = null,
    )

    @Test
    fun synthesizesAndMatchesIndonesianWalletAndBankSamples() {
        val samples = listOf(
            Triple(
                "Pembayaran Rp25.000 ke Kopi Kenangan berhasil",
                "Kopi Kenangan",
                2_500_000L,
            ),
            Triple(
                "Transfer masuk sebesar Rp150.000 dari BUDI SANTOSO. Saldo Rp500.000",
                "BUDI SANTOSO",
                15_000_000L,
            ),
            Triple(
                "Transaksi QRIS IDR 42,500.00 di Alfamart sukses pada 22/11/2026 14:30 WIB",
                "Alfamart",
                4_250_000L,
            ),
        )

        samples.forEachIndexed { idx, (sampleText, expectedMerchant, expectedAmount) ->
            val generated = NotificationTemplateEngine.generateTemplateFromSample(
                sampleBody = sampleText,
                merchantOrDescHint = expectedMerchant,
                defaultCurrency = "IDR",
            )
            assertThat(generated).isNotNull()
            assertThat(generated!!.detectedAmountMinor).isEqualTo(expectedAmount)

            val rule = CustomParserRuleSpec(
                id = idx + 1L,
                sourceId = "pkg.test",
                name = "Rule $idx",
                titleContains = null,
                templatePattern = generated.templatePattern,
                direction = Direction.OUTFLOW,
                transactionType = TransactionType.PURCHASE,
                currency = generated.currency,
                numberLocale = generated.numberLocale,
                updatedAt = 1_000L,
            )

            val parsed = NotificationTemplateEngine.parseWithRules(
                event = event(sourceId = "pkg.test", text = sampleText),
                rules = listOf(rule),
            )
            assertThat(parsed).isInstanceOf(ParseResult.Success::class.java)
            val tx = (parsed as ParseResult.Success).transaction
            assertThat(tx.amountMinor).isEqualTo(expectedAmount)
            assertThat(tx.merchantRaw).isEqualTo(expectedMerchant)
        }
    }

    @Test
    fun rejectsTooBroadOrOtpTemplatesAndEscapesRegexMetacharacters() {
        assertThat(NotificationTemplateEngine.validateTemplate("{AMOUNT}")).isNotNull()
        assertThat(NotificationTemplateEngine.validateTemplate("   {AMOUNT}   ")).isNotNull()
        assertThat(NotificationTemplateEngine.validateTemplate("Kode OTP anda {AMOUNT}")).isNotNull()
        assertThat(NotificationTemplateEngine.validateTemplate("No amount placeholder here")).isNotNull()
        assertThat(NotificationTemplateEngine.validateTemplate("Bayar {AMOUNT} dan {AMOUNT}")).isNotNull()

        // Regex metacharacters in literal text (., +, *, ?, [, ], (, )) must be treated literally.
        val rule = CustomParserRuleSpec(
            id = 1L,
            sourceId = "pkg.literal",
            name = "Literal",
            titleContains = null,
            templatePattern = "Bayar [QRIS] {AMOUNT} ke {MERCHANT} (berhasil).",
            direction = Direction.OUTFLOW,
            transactionType = TransactionType.PURCHASE,
            currency = "IDR",
            numberLocale = ProviderNumberLocale.INDONESIAN,
        )
        val matched = NotificationTemplateEngine.parseWithRules(
            event("pkg.literal", text = "Bayar [QRIS] Rp15.000 ke Warung Mba (berhasil)."),
            listOf(rule),
        )
        assertThat(matched).isInstanceOf(ParseResult.Success::class.java)
        assertThat((matched as ParseResult.Success).transaction.amountMinor).isEqualTo(1_500_000L)
        assertThat(matched.transaction.merchantRaw).isEqualTo("Warung Mba")

        val notMatched = NotificationTemplateEngine.parseWithRules(
            event("pkg.literal", text = "Bayar QRIS Rp15.000 ke Warung Mba berhasil."),
            listOf(rule),
        )
        assertThat(notMatched).isNull()
    }

    @Test
    fun compositeSourceParserRespectsBuiltInCustomAndFallbackPriority() = runTest {
        val danaOutRule = CustomParserRuleSpec(
            id = 10L,
            sourceId = DanaTransactionParser.SOURCE_ID,
            name = "DANA QRIS Out",
            titleContains = null,
            templatePattern = "Pembayaran QRIS {AMOUNT} di {MERCHANT} berhasil",
            direction = Direction.OUTFLOW,
            transactionType = TransactionType.PURCHASE,
            currency = "IDR",
            numberLocale = ProviderNumberLocale.INDONESIAN,
        )
        val composite = CompositeSourceParser(
            sourceId = DanaTransactionParser.SOURCE_ID,
            builtInParser = DanaTransactionParser(),
            customRules = listOf(danaOutRule),
            fallbackParser = GenericNotificationFallbackParser(),
        )

        // 1. Built-in DANA incoming still handled by built-in parser
        val builtInIn = composite.parse(
            event(sourceId = DanaTransactionParser.SOURCE_ID, title = "DANA", text = "Rp20.000 telah diterima dari ANDI 💰"),
        )
        assertThat(builtInIn).isInstanceOf(ParseResult.Success::class.java)
        assertThat((builtInIn as ParseResult.Success).transaction.direction).isEqualTo(Direction.INFLOW)

        // 2. Built-in DANA quest promo still filtered as PROMOTION
        val quest = composite.parse(
            event(sourceId = DanaTransactionParser.SOURCE_ID, title = "DANA", text = "Yay, kamu berhasil selesaikan quest!"),
        )
        assertThat(quest).isEqualTo(ParseResult.NotTransaction(NotTransactionReason.PROMOTION))

        // 3. Unseen DANA outgoing QRIS (which built-in returns UNKNOWN_FORMAT for) is rescued by custom rule!
        val customOut = composite.parse(
            event(sourceId = DanaTransactionParser.SOURCE_ID, title = "DANA", text = "Pembayaran QRIS Rp35.000 di Indomaret berhasil"),
        )
        assertThat(customOut).isInstanceOf(ParseResult.Success::class.java)
        val tx = (customOut as ParseResult.Success).transaction
        assertThat(tx.amountMinor).isEqualTo(3_500_000L)
        assertThat(tx.direction).isEqualTo(Direction.OUTFLOW)
        assertThat(tx.merchantRaw).isEqualTo("Indomaret")

        // 4. Completely unseen format on new source falls through to Generic fallback -> NeedsReview
        val unseenSource = CompositeSourceParser(
            sourceId = "id.co.seabank.id",
            builtInParser = null,
            customRules = emptyList(),
            fallbackParser = GenericNotificationFallbackParser(),
        )
        val fallbackRes = unseenSource.parse(
            event(sourceId = "id.co.seabank.id", title = "SeaBank", text = "Transaksi Rp99.000 di Shopee berhasil"),
        )
        assertThat(fallbackRes).isInstanceOf(ParseResult.NeedsReview::class.java)
        assertThat((fallbackRes as ParseResult.NeedsReview).partial?.amountMinor).isEqualTo(9_900_000L)
    }
}
