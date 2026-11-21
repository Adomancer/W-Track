package wtrack.integration.providers

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wtrack.integration.Direction
import wtrack.integration.NotTransactionReason
import wtrack.integration.NotificationEvent
import wtrack.integration.ParseResult
import wtrack.integration.ParsedTransaction
import wtrack.integration.PartialTransaction
import wtrack.integration.ReviewReason
import wtrack.integration.TransactionParser
import wtrack.integration.TransactionType
import wtrack.integration.providers.common.LocaleNumberParser
import wtrack.integration.providers.common.ParseAmountResult
import wtrack.integration.providers.common.ProviderDateTimeParser
import wtrack.integration.providers.common.ProviderNumberLocale
import wtrack.integration.providers.common.SensitiveContentGuard

/**
 * TEST-ONLY synthetic parser fixtures exercising PAR-01..PAR-12 utilities across multiple Indonesian/English
 * formats without inventing production provider packages or regexes.
 */
class SyntheticBankParser(
    override val sourceId: String = "synthetic.bank.id",
    override val version: Int = 1,
) : TransactionParser {

    private val debitRegex = Regex("(?:Debit|Pembayaran|Bayar)\\s+(Rp\\s*[0-9.,]+)\\s+di\\s+(.+?)(?:\\s+Ref[:\\s]+([A-Za-z0-9-]+))?$", RegexOption.IGNORE_CASE)
    private val transferOutRegex = Regex("Transfer\\s+(Rp\\s*[0-9.,]+)\\s+ke\\s+(.+?)(?:\\s+\\((?:Kantong|Acc)\\s*([A-Za-z0-9]{2,8})\\))?(?:\\s+Ref[:\\s]+([A-Za-z0-9-]+))?$", RegexOption.IGNORE_CASE)
    private val inflowRegex = Regex("(?:Uang\\s+masuk|Masuk)\\s+(Rp\\s*[0-9.,]+)\\s+dari\\s+(.+?)(?:\\s+Ref[:\\s]+([A-Za-z0-9-]+))?$", RegexOption.IGNORE_CASE)
    private val feeRegex = Regex("Biaya\\s+admin\\s+(Rp\\s*[0-9.,]+)", RegexOption.IGNORE_CASE)
    private val atmRegex = Regex("Tarik\\s+tunai\\s+(Rp\\s*[0-9.,]+)", RegexOption.IGNORE_CASE)
    private val timestampRegex = Regex("\\b(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}(?:\\s+(?:WIB|WITA|WIT))?)\\b", RegexOption.IGNORE_CASE)

    override fun supports(event: NotificationEvent): Boolean = event.packageName == sourceId

    override suspend fun parse(event: NotificationEvent): ParseResult {
        SensitiveContentGuard.inspectPreconditions(event)?.let { return it }
        SensitiveContentGuard.inspectNonTransactionNotice(event)?.let { return it }
        if (event.isGroupSummary) return ParseResult.NotTransaction(NotTransactionReason.OTHER)

        val body = (event.bigText?.takeIf { it.isNotBlank() } ?: event.text).trim()
        val occurredAt = timestampRegex.find(body)?.groupValues?.get(1)?.let {
            ProviderDateTimeParser.parseToUtcEpochMillis(it)
        }
        val cleanBody = body.replace(timestampRegex, "").replace("  ", " ").trim()

        debitRegex.find(cleanBody)?.let { match ->
            return when (val amt = LocaleNumberParser.parse(match.groupValues[1], "IDR", ProviderNumberLocale.INDONESIAN)) {
                is ParseAmountResult.Parsed -> ParseResult.Success(
                    ParsedTransaction(
                        amountMinor = amt.amountMinor,
                        currency = "IDR",
                        direction = Direction.OUTFLOW,
                        type = TransactionType.PURCHASE,
                        occurredAtEpochMillis = occurredAt,
                        merchantRaw = match.groupValues[2].trim(),
                        description = event.title,
                        accountHint = null,
                        providerReference = match.groupValues[3].takeIf { it.isNotBlank() },
                        reportedBalanceMinor = null,
                    ),
                )
                is ParseAmountResult.Ambiguous -> ParseResult.NeedsReview(ReviewReason.AMBIGUOUS_FIELD, PartialTransaction(currency = "IDR"))
                is ParseAmountResult.Invalid -> ParseResult.NeedsReview(ReviewReason.INVALID_VALUE, PartialTransaction(currency = "IDR"))
            }
        }

        transferOutRegex.find(cleanBody)?.let { match ->
            val amt = LocaleNumberParser.parse(match.groupValues[1], "IDR", ProviderNumberLocale.INDONESIAN)
            return when (amt) {
                is ParseAmountResult.Parsed -> ParseResult.Success(
                    ParsedTransaction(
                        amountMinor = amt.amountMinor,
                        currency = "IDR",
                        direction = Direction.OUTFLOW,
                        type = TransactionType.TRANSFER,
                        occurredAtEpochMillis = occurredAt,
                        merchantRaw = match.groupValues[2].trim(),
                        description = "Transfer",
                        accountHint = match.groupValues[3].takeIf { it.isNotBlank() },
                        providerReference = match.groupValues[4].takeIf { it.isNotBlank() },
                        reportedBalanceMinor = null,
                    ),
                )
                is ParseAmountResult.Ambiguous -> ParseResult.NeedsReview(ReviewReason.AMBIGUOUS_FIELD, PartialTransaction(currency = "IDR"))
                is ParseAmountResult.Invalid -> ParseResult.NeedsReview(ReviewReason.INVALID_VALUE, PartialTransaction(currency = "IDR"))
            }
        }

        inflowRegex.find(cleanBody)?.let { match ->
            val amt = LocaleNumberParser.parse(match.groupValues[1], "IDR", ProviderNumberLocale.INDONESIAN)
            return when (amt) {
                is ParseAmountResult.Parsed -> ParseResult.Success(
                    ParsedTransaction(
                        amountMinor = amt.amountMinor,
                        currency = "IDR",
                        direction = Direction.INFLOW,
                        type = if (body.contains("refund", ignoreCase = true)) TransactionType.REFUND else TransactionType.TRANSFER,
                        occurredAtEpochMillis = occurredAt,
                        merchantRaw = match.groupValues[2].trim(),
                        description = event.title,
                        accountHint = null,
                        providerReference = match.groupValues[3].takeIf { it.isNotBlank() },
                        reportedBalanceMinor = null,
                    ),
                )
                is ParseAmountResult.Ambiguous -> ParseResult.NeedsReview(ReviewReason.AMBIGUOUS_FIELD, PartialTransaction(currency = "IDR"))
                is ParseAmountResult.Invalid -> ParseResult.NeedsReview(ReviewReason.INVALID_VALUE, PartialTransaction(currency = "IDR"))
            }
        }

        feeRegex.find(cleanBody)?.let { match ->
            val amt = LocaleNumberParser.parse(match.groupValues[1], "IDR", ProviderNumberLocale.INDONESIAN)
            if (amt is ParseAmountResult.Parsed) {
                return ParseResult.Success(
                    ParsedTransaction(amt.amountMinor, "IDR", Direction.OUTFLOW, TransactionType.FEE, occurredAt, "Bank Admin", "Admin Fee", null, null, null),
                )
            }
        }

        atmRegex.find(cleanBody)?.let { match ->
            val amt = LocaleNumberParser.parse(match.groupValues[1], "IDR", ProviderNumberLocale.INDONESIAN)
            if (amt is ParseAmountResult.Parsed) {
                return ParseResult.Success(
                    ParsedTransaction(amt.amountMinor, "IDR", Direction.OUTFLOW, TransactionType.CASH, occurredAt, "ATM Withdrawal", "ATM Cash", null, null, null),
                )
            }
        }

        return ParseResult.NeedsReview(ReviewReason.UNKNOWN_FORMAT, null)
    }
}

class SyntheticParserIntegrationTest {
    @Test
    fun parsesIndonesianBankNotificationVariantsIntoCanonicalContracts() = runTest {
        val parser = SyntheticBankParser()

        val debit = parser.parse(env("Pembayaran Rp 50.000 di Kopi Kenangan Ref TXN100"))
        assertThat(debit).isEqualTo(
            ParseResult.Success(
                ParsedTransaction(
                    amountMinor = 5_000_000L,
                    currency = "IDR",
                    direction = Direction.OUTFLOW,
                    type = TransactionType.PURCHASE,
                    occurredAtEpochMillis = null,
                    merchantRaw = "Kopi Kenangan",
                    description = "Alert",
                    accountHint = null,
                    providerReference = "TXN100",
                    reportedBalanceMinor = null,
                ),
            ),
        )

        val fee = parser.parse(env("Biaya admin Rp 6.500 20/11/2026 14:30 WIB"))
        assertThat(fee).isInstanceOf(ParseResult.Success::class.java)
        val feeTx = (fee as ParseResult.Success).transaction
        assertThat(feeTx.type).isEqualTo(TransactionType.FEE)
        assertThat(feeTx.amountMinor).isEqualTo(650_000L)
        assertThat(feeTx.occurredAtEpochMillis).isEqualTo(Instant.parse("2026-11-20T07:30:00Z").toEpochMilli())
    }

    private fun env(text: String): NotificationEvent = NotificationEvent(
        sourceId = "synthetic.bank.id",
        packageName = "synthetic.bank.id",
        title = "Alert",
        text = text,
        bigText = null,
        subText = null,
        channelId = "tx",
        isGroupSummary = false,
        isContentRedacted = false,
        postedAtEpochMillis = 1_795_165_000_000L,
        notificationKey = "k",
        notificationId = 1,
        notificationTag = null,
        sourceEventReference = null,
    )
}
