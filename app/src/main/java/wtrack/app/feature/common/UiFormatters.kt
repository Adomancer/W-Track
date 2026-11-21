package wtrack.app.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import wtrack.app.domain.model.CaptureStatus
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
