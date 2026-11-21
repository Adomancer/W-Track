package wtrack.app.feature.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.providerBrandStyle
import wtrack.app.core.theme.tabularNums
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.usecase.CategorySpendBreakdown

@Composable
fun WtrackCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = WtrackTheme.colors.cardBorder,
    elevation: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val cardModifier = if (onClick != null) {
        modifier.clip(shape).clickable(onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = cardModifier,
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = elevation,
    ) {
        Column(content = content)
    }
}

@Composable
fun WtrackBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    cornerRadius: Dp = 9.dp,
) {
    val ext = WtrackTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(listOf(ext.heroGradientStart, ext.heroGradientEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.6f)) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(0f, 0.14f * h)
                lineTo(0.18f * w, 0.14f * h)
                lineTo(0.30f * w, 0.62f * h)
                lineTo(0.43f * w, 0.14f * h)
                lineTo(0.57f * w, 0.14f * h)
                lineTo(0.70f * w, 0.62f * h)
                lineTo(0.82f * w, 0.14f * h)
                lineTo(w, 0.14f * h)
                lineTo(0.79f * w, 0.86f * h)
                lineTo(0.62f * w, 0.86f * h)
                lineTo(0.50f * w, 0.41f * h)
                lineTo(0.38f * w, 0.86f * h)
                lineTo(0.21f * w, 0.86f * h)
                close()
            }
            drawPath(path = path, color = Color.White)
        }
    }
}

@Composable
fun ProviderBadgeAvatar(
    labelOrPackage: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    accountType: AccountType? = null,
) {
    val style = providerBrandStyle(labelOrPackage, accountType)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(style.badgeBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = style.letter,
            color = style.badgeFg,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

@Composable
fun AvatarCircle(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.39f).sp,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailingText: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        when {
            actionLabel != null && onAction != null -> Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            trailingText != null -> Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = WtrackTheme.colors.mutedText,
            )
        }
    }
}

@Composable
fun WtrackTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (onBack != null) 6.dp else 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    content = trailing,
                )
            }
            HorizontalDivider(color = WtrackTheme.colors.divider)
        }
    }
}

@Composable
fun ActionFeedbackBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, WtrackTheme.colors.infoTintBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TextButton(onClick = onDismiss) {
                Text("OK", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun categorySliceColor(categoryName: String, index: Int): Color {
    val ext = WtrackTheme.colors
    return if (categoryName.equals("Uncategorized", ignoreCase = true)) {
        ext.uncategorizedChartColor
    } else {
        ext.categoryPalette[index % ext.categoryPalette.size]
    }
}

@Composable
fun CategoryStackedBar(
    breakdowns: List<CategorySpendBreakdown>,
    modifier: Modifier = Modifier,
) {
    val positive = breakdowns.filter { it.netSpendMinor > 0L }.take(5)
    val total = positive.sumOf { it.netSpendMinor }.toFloat()
    if (positive.isEmpty() || total <= 0f) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(9.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        positive.forEachIndexed { idx, cat ->
            val fraction = (cat.netSpendMinor / total).coerceAtLeast(0.04f)
            Box(
                modifier = Modifier
                    .weight(fraction)
                    .height(9.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(categorySliceColor(cat.categoryName, idx)),
            )
        }
    }
}

@Composable
fun CategoryDonutChart(
    breakdowns: List<CategorySpendBreakdown>,
    totalSpendMinor: Long,
    baseCurrency: String,
    modifier: Modifier = Modifier,
) {
    val positive = breakdowns.filter { it.netSpendMinor > 0L }
    val sumPositive = positive.sumOf { it.netSpendMinor }.toFloat()
    val colors = positive.mapIndexed { idx, item -> categorySliceColor(item.categoryName, idx) }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier.size(114.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(114.dp)) {
            val strokePx = 18.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            if (positive.isEmpty() || sumPositive <= 0f) {
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx),
                )
            } else {
                val gapDegrees = if (positive.size > 1) 2.5f else 0f
                var currentStart = -90f
                positive.forEachIndexed { idx, cat ->
                    val rawSweep = (cat.netSpendMinor / sumPositive) * 360f
                    val sweep = (rawSweep - gapDegrees).coerceAtLeast(3f)
                    drawArc(
                        color = colors[idx],
                        startAngle = currentStart,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                    )
                    currentStart += rawSweep
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${positive.size} Cats",
                style = MaterialTheme.typography.labelSmall,
                color = WtrackTheme.colors.mutedText,
                textAlign = TextAlign.Center,
            )
            Text(
                text = formatCompactCurrency(abs(totalSpendMinor), baseCurrency),
                style = MaterialTheme.typography.titleSmall.tabularNums(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SegmentedOptionRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(idx) },
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}
