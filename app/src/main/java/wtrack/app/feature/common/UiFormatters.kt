package wtrack.app.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.CurrencyRegistry
import wtrack.app.domain.model.MoneyFormatter

object UiDateHelper {
    fun formatDateTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.getDefault())
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(formatter)
    }

    fun formatDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(formatter)
    }

    fun formatEditableDateTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(formatter)
    }

    fun parseEditableDateTime(input: String, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
        return runCatching {
            java.time.LocalDateTime.parse(trimmed, formatter).atZone(zoneId).toInstant().toEpochMilli()
        }.getOrNull()
    }

    fun monthWindow(nowEpochMillis: Long, monthOffset: Int, zoneId: ZoneId = ZoneId.systemDefault()): MonthWindow {
        val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        val currentMonth = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        val targetYm = YearMonth.from(currentMonth).plusMonths(monthOffset.toLong())
        val prevYm = targetYm.minusMonths(1)
        val start = targetYm.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = targetYm.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val prevStart = prevYm.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return MonthWindow(
            label = targetYm.atDay(1).format(monthYearFormatter),
            startMillis = start,
            endMillis = end,
            previousMonthRange = prevStart to start,
        )
    }
}

data class MonthWindow(
    val label: String,
    val startMillis: Long,
    val endMillis: Long,
    val previousMonthRange: Pair<Long, Long>,
)

@Composable
fun CaptureStatusPill(status: CaptureStatus, modifier: Modifier = Modifier) {
    val (label, containerColor, contentColor) = when (status) {
        CaptureStatus.ACTIVE -> Triple(
            "Active",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        CaptureStatus.DISCONNECTED -> Triple(
            "Disconnected",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        CaptureStatus.ATTENTION -> Triple(
            "Attention Needed",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        CaptureStatus.UNSUPPORTED -> Triple(
            "Unsupported",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

fun formatCurrency(amountMinor: Long, currency: String, signed: Boolean = false): String =
    MoneyFormatter.format(amountMinor, currency, signed)

fun isValidMoneyInput(
    value: String,
    currency: String,
    allowZero: Boolean = false,
    allowNegative: Boolean = false,
    isOptional: Boolean = false,
): Boolean {
    if (value.isBlank()) return isOptional
    val parsed = MoneyFormatter.parseMajorInput(value, currency) ?: return false
    if (!allowZero && parsed == 0L) return false
    if (!allowNegative && parsed < 0L) return false
    return true
}

@Composable
fun MoneyAmountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: String,
    label: String,
    modifier: Modifier = Modifier,
    allowZero: Boolean = false,
    allowNegative: Boolean = false,
    isOptional: Boolean = false,
) {
    val spec = remember(currency) { CurrencyRegistry.specFor(currency) }
    val parsedMinor = remember(value, currency) {
        if (value.isBlank()) null else MoneyFormatter.parseMajorInput(value, currency)
    }
    val isInvalid = value.isNotBlank() &&
        !isValidMoneyInput(value, currency, allowZero = allowZero, allowNegative = allowNegative, isOptional = isOptional)
    val placeholderText = when {
        spec.code == "IDR" -> "e.g. 50000 or 50.000"
        spec.minorUnitDigits == 0 -> "e.g. 1500"
        else -> "e.g. 50000 or 50.25"
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholderText) },
        singleLine = true,
        isError = isInvalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = {
            when {
                value.isBlank() -> Text(
                    text = when {
                        spec.code == "IDR" -> "Type 50000, 50.000, or 50,000 (comma for sen: 50.000,50)"
                        spec.minorUnitDigits == 0 -> "Type 1500 or 1,500"
                        else -> "Type 50000, 50,000, or 50.25"
                    },
                )
                isInvalid -> Text("Invalid $currency amount (examples: 50000, 50.000, 50,000)")
                parsedMinor != null -> Text(
                    text = "Will save as: ${MoneyFormatter.format(parsedMinor, currency)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        modifier = modifier,
    )
}
