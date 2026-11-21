package wtrack.app.feature.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.tabularNums
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

    fun formatTimeOnly(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(formatter)
    }

    fun formatDayGroupHeader(
        epochMillis: Long,
        nowEpochMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val txDate = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        val dayMonth = txDate.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
        return when (txDate) {
            today -> "TODAY · $dayMonth"
            today.minusDays(1) -> "YESTERDAY · $dayMonth"
            else -> if (txDate.year == today.year) {
                dayMonth
            } else {
                txDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
            }
        }
    }

    fun localDateOf(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

    fun formatDaysAgo(epochMillis: Long, nowEpochMillis: Long): String {
        if (epochMillis <= 0L) return "Never"
        val diffDays = ((nowEpochMillis - epochMillis) / 86_400_000L).coerceAtLeast(0L)
        return when {
            diffDays == 0L -> "Today"
            diffDays == 1L -> "1d ago"
            else -> "${diffDays}d ago"
        }
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
        val shortMonthYearFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
        val currentMonth = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        val targetYm = YearMonth.from(currentMonth).plusMonths(monthOffset.toLong())
        val prevYm = targetYm.minusMonths(1)
        val start = targetYm.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = targetYm.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val prevStart = prevYm.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val firstDay = targetYm.atDay(1)
        return MonthWindow(
            label = firstDay.format(monthYearFormatter),
            shortLabel = firstDay.format(shortMonthYearFormatter),
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
    val shortLabel: String = label,
)

@Composable
fun CaptureStatusPill(status: CaptureStatus, modifier: Modifier = Modifier) {
    val ext = WtrackTheme.colors
    val (label, containerColor, contentColor, dotColor) = when (status) {
        CaptureStatus.ACTIVE -> Quad(
            "Active",
            ext.inflowContainer,
            ext.onInflowContainer,
            ext.inflow,
        )
        CaptureStatus.DISCONNECTED -> Quad(
            "Disconnected",
            ext.transferContainer,
            ext.onTransferContainer,
            ext.transfer,
        )
        CaptureStatus.ATTENTION -> Quad(
            "Attention",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error,
        )
        CaptureStatus.UNSUPPORTED -> Quad(
            "Unsupported",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            ext.mutedText,
        )
    }
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
    }
}

@Composable
fun PossibleGapPill(modifier: Modifier = Modifier, label: String = "Possible Gap") {
    val ext = WtrackTheme.colors
    StatusPill(
        label = label,
        containerColor = ext.warningContainer,
        contentColor = ext.onWarningContainer,
        borderColor = ext.warningBorder,
        dotColor = ext.warning,
        modifier = modifier,
    )
}

@Composable
fun StatusPill(
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    borderColor: Color? = null,
    dotColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
        border = borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(5.5.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
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

fun formatCompactCurrency(amountMinor: Long, currency: String): String {
    val spec = CurrencyRegistry.specFor(currency)
    val factor = CurrencyRegistry.scaleFactor(spec.code).toDouble().coerceAtLeast(1.0)
    val major = kotlin.math.abs(amountMinor) / factor
    val sign = if (amountMinor < 0L) "-" else ""
    return if (spec.code == "IDR") {
        val idLocale = Locale.forLanguageTag("id-ID")
        when {
            major >= 1_000_000_000.0 -> sign + String.format(idLocale, "%.2fM", major / 1_000_000_000.0)
            major >= 1_000_000.0 -> sign + String.format(idLocale, "%.2fjt", major / 1_000_000.0)
            major >= 1_000.0 -> sign + String.format(idLocale, "%.0frb", major / 1_000.0)
            else -> MoneyFormatter.format(amountMinor, currency)
        }
    } else {
        when {
            major >= 1_000_000.0 -> sign + String.format(Locale.US, "%.1fM", major / 1_000_000.0)
            major >= 1_000.0 -> sign + String.format(Locale.US, "%.1fK", major / 1_000.0)
            else -> MoneyFormatter.format(amountMinor, currency)
        }
    }
}

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
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.titleMedium.tabularNums(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
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
                    style = MaterialTheme.typography.labelMedium.tabularNums(),
                )
            }
        },
        modifier = modifier,
    )
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
