package wtrack.app.data.backup

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.Transaction

/**
 * Unencrypted RFC-4180 CSV exporter for user portability (`DATA-11`) with spreadsheet formula
 * injection neutralization (`=`, `+`, `-`, `@`, tab, CR).
 */
class CsvExporter {
    fun writeTransactionsCsv(
        outputStream: OutputStream,
        transactions: List<Transaction>,
        accounts: List<Account>,
        categories: List<Category>,
    ): Int {
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }

        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(
                listOf(
                    "id",
                    "occurred_at_utc",
                    "account_name",
                    "account_currency",
                    "amount",
                    "currency",
                    "direction",
                    "transaction_type",
                    "status",
                    "category",
                    "merchant",
                    "description",
                    "note",
                    "provider_reference",
                ).joinToString(",") + "\r\n",
            )

            transactions.forEach { tx ->
                val account = accountMap[tx.accountId]
                val categoryName = categoryMap[tx.categoryId]?.name ?: "Uncategorized"
                val amountDecimal = MoneyFormatter.formatForCsv(tx.amountMinor, tx.currency)
                val row = listOf(
                    tx.id.toString(),
                    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(tx.occurredAt).atOffset(ZoneOffset.UTC)),
                    escapeCsvCell(neutralizeFormula(account?.name ?: "Account #${tx.accountId}")),
                    escapeCsvCell(account?.currency ?: tx.currency),
                    amountDecimal,
                    tx.currency,
                    tx.direction.name,
                    tx.transactionType.name,
                    tx.status.name,
                    escapeCsvCell(neutralizeFormula(categoryName)),
                    escapeCsvCell(neutralizeFormula(tx.merchantName.orEmpty())),
                    escapeCsvCell(neutralizeFormula(tx.description.orEmpty())),
                    escapeCsvCell(neutralizeFormula(tx.note.orEmpty())),
                    escapeCsvCell(neutralizeFormula(tx.providerReference.orEmpty())),
                ).joinToString(",")
                writer.write("$row\r\n")
            }
            writer.flush()
        }
        return transactions.size
    }

    companion object {
        fun neutralizeFormula(raw: String): String {
            if (raw.isEmpty()) return ""
            val first = raw.first()
            return if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                "'$raw"
            } else {
                raw
            }
        }

        fun escapeCsvCell(value: String): String {
            val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
            if (!needsQuotes) return value
            return "\"${value.replace("\"", "\"\"")}\""
        }
    }
}
