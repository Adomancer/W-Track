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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wtrack.app.R
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.providerBrandStyle
import wtrack.app.core.theme.tabularNums
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.usecase.CategorySpendBreakdown

@Composable
fun WtrackCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = WtrackTheme.colors.cardBorder,
    elevation: Dp = 0.dp,
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
        Icon(
            painter = painterResource(id = R.drawable.ic_wtrack_mark_mono),
            contentDescription = stringResource(R.string.app_name),
            tint = Color.White,
            modifier = Modifier.size(size * 0.72f),
        )
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        when {
            actionLabel != null && onAction != null -> Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            trailingText != null -> Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) 12.dp else 24.dp,
                end = 24.dp,
                top = 12.dp,
                bottom = 8.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = trailing,
        )
    }
}

@Composable
fun ActionFeedbackBanner(
    message: UiText,
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
                text = message.asString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok), fontWeight = FontWeight.SemiBold)
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
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val strokePx = 8.dp.toPx()
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
                val gapDegrees = if (positive.size > 1) 4.5f else 0f
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
    }
}

@Composable
fun V2StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

fun v2CategoryIcon(
    categoryName: String,
    isIncome: Boolean = false,
    isTransfer: Boolean = false,
    isUncategorized: Boolean = false,
): ImageVector {
    val norm = categoryName.trim().lowercase()
    return when {
        isTransfer || containsAnyKeyword(norm, "transfer", "pindah", "mutasi") -> Icons.Outlined.SwapHoriz
        isIncome || containsAnyKeyword(norm, "salary", "income", "gaji", "pemasukan", "pendapatan", "bonus") -> Icons.Outlined.SouthWest
        isUncategorized || containsAnyKeyword(norm, "needs category", "uncategorized", "belum dikategorikan", "tanpa kategori") -> Icons.Outlined.LocalOffer
        containsAnyKeyword(norm, "food", "dining", "coffee", "makan", "minum", "kopi", "kuliner", "jajan") -> Icons.Outlined.LocalCafe
        containsAnyKeyword(norm, "transport", "fuel", "ride", "bensin", "bbm", "parkir", "tol", "ojek", "grab", "gojek") -> Icons.Outlined.DirectionsCar
        containsAnyKeyword(norm, "bill", "util", "electric", "tagihan", "listrik", "pln", "air", "pdam", "internet", "pulsa", "wifi") -> Icons.Outlined.Bolt
        containsAnyKeyword(norm, "shop", "belanja", "mart", "grocery", "groceries", "kebutuhan") -> Icons.Outlined.ShoppingBag
        containsAnyKeyword(norm, "health", "kesehatan", "dokter", "obat", "apotek") -> Icons.Outlined.FavoriteBorder
        containsAnyKeyword(norm, "entertain", "hiburan", "nonton", "game", "musik") -> Icons.Outlined.Movie
        else -> Icons.AutoMirrored.Outlined.ReceiptLong
    }
}

private fun containsAnyKeyword(text: String, vararg keywords: String): Boolean =
    keywords.any { it in text }

@Composable
fun V2TransactionRow(
    merchant: String,
    category: String,
    amountText: String,
    isInflow: Boolean,
    isUncategorized: Boolean,
    isVoided: Boolean = false,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = WtrackTheme.colors
    val amountColor = when {
        isVoided -> ext.mutedText
        isInflow -> ext.inflow
        else -> MaterialTheme.colorScheme.onBackground
    }
    val categoryColor = if (isUncategorized) ext.mutedText else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = merchant,
                style = MaterialTheme.typography.titleMedium,
                color = if (isVoided) ext.mutedText else MaterialTheme.colorScheme.onBackground,
                textDecoration = if (isVoided) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isVoided) stringResource(R.string.label_voided_category, category) else category,
                style = MaterialTheme.typography.bodyMedium,
                color = categoryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleMedium.tabularNums(),
            color = amountColor,
            textDecoration = if (isVoided) TextDecoration.LineThrough else null,
            maxLines = 1,
        )
    }
}

@Composable
fun V2SettingRow(
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    trailingColor: Color? = null,
    leadingDotColor: Color? = null,
    showChevron: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = WtrackTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingDotColor != null) {
                V2StatusDot(color = leadingDotColor)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!trailingText.isNullOrBlank()) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (trailingColor != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = trailingColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ext.mutedText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun V2CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(18.dp),
        )
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
