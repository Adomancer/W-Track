package wtrack.app.feature.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.tabularNums
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.AvatarCircle
import wtrack.app.feature.common.CategoryDonutChart
import wtrack.app.feature.common.MoneyAmountTextField
import wtrack.app.feature.common.SectionHeader
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.common.WtrackTopBar
import wtrack.app.feature.common.categorySliceColor
import wtrack.app.feature.common.formatCompactCurrency
import wtrack.app.feature.common.formatCurrency
import wtrack.app.feature.common.isValidMoneyInput

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val ext = WtrackTheme.colors
    var showBudgetSheet by remember { mutableStateOf(false) }

    val positiveCategories = remember(state.summary.categoryBreakdowns) {
        state.summary.categoryBreakdowns.filter { it.netSpendMinor > 0L }
    }
    val totalCategorySpend = remember(positiveCategories) {
        positiveCategories.sumOf { it.netSpendMinor }.coerceAtLeast(1L)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WtrackTopBar(
            title = "Insights & Budgets",
            onBack = onBack,
            trailing = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, ext.cardBorder),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        IconButton(
                            onClick = viewModel::previousMonth,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                contentDescription = "Previous month",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = state.monthLabel,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        IconButton(
                            onClick = viewModel::nextMonth,
                            enabled = state.canAdvanceMonth,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = "Next month",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.actionMessage != null) {
                item(key = "msg") {
                    ActionFeedbackBanner(
                        message = state.actionMessage!!,
                        onDismiss = viewModel::clearMessage,
                    )
                }
            }

            // Spending Breakdown Hero Card (Donut + Top Categories + Period Totals)
            item(key = "spending-breakdown") {
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Spending Breakdown",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Net of refunds + fees · ${state.baseCurrency}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                )
                            }
                            state.summary.expenseDeltaPercent?.let { pct ->
                                val decreased = pct <= 0.0
                                StatusPill(
                                    label = String.format(Locale.US, "%+.1f%% vs prev", pct),
                                    containerColor = if (decreased) ext.inflowContainer else MaterialTheme.colorScheme.errorContainer,
                                    contentColor = if (decreased) ext.onInflowContainer else MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }

                        if (state.summary.categoryBreakdowns.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryDonutChart(
                                    breakdowns = emptyList(),
                                    totalSpendMinor = 0L,
                                    baseCurrency = state.baseCurrency,
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "No expense data in ${state.monthLabel}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "Confirmed expenses, refunds, and transfer fees appear here grouped by category.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ext.mutedText,
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryDonutChart(
                                    breakdowns = state.summary.categoryBreakdowns,
                                    totalSpendMinor = state.summary.totalExpenseMinor,
                                    baseCurrency = state.baseCurrency,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    positiveCategories.take(4).forEachIndexed { idx, cat ->
                                        val share = ((cat.netSpendMinor.toDouble() / totalCategorySpend) * 100.0).roundToInt()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(categorySliceColor(cat.categoryName, idx)),
                                                )
                                                Text(
                                                    text = cat.categoryName,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Text(
                                                text = "$share% · ${formatCompactCurrency(cat.netSpendMinor, state.baseCurrency)}",
                                                style = MaterialTheme.typography.labelSmall.tabularNums(),
                                                color = ext.mutedText,
                                            )
                                        }
                                    }
                                    if (positiveCategories.size > 4) {
                                        Text(
                                            text = "+${positiveCategories.size - 4} more categor${if (positiveCategories.size - 4 == 1) "y" else "ies"} below",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = ext.divider)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Income", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                Text(
                                    text = formatCurrency(state.summary.totalIncomeMinor, state.baseCurrency),
                                    style = MaterialTheme.typography.titleSmall.tabularNums(),
                                    fontWeight = FontWeight.Bold,
                                    color = ext.inflow,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Expenses", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                Text(
                                    text = formatCurrency(state.summary.totalExpenseMinor, state.baseCurrency),
                                    style = MaterialTheme.typography.titleSmall.tabularNums(),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Prev Month", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                Text(
                                    text = formatCurrency(state.summary.previousMonthExpenseMinor ?: 0L, state.baseCurrency),
                                    style = MaterialTheme.typography.titleSmall.tabularNums(),
                                    color = ext.mutedText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Unreconciled adjustments",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText,
                            )
                            Text(
                                text = formatCurrency(state.summary.unreconciledAdjustmentNetMinor, state.baseCurrency, signed = true),
                                style = MaterialTheme.typography.labelMedium.tabularNums(),
                                color = ext.mutedText,
                            )
                        }

                        if (state.summary.excludedNonBaseCurrencyCount > 0) {
                            Text(
                                text = "Excluded ${state.summary.excludedNonBaseCurrencyCount} non-${state.baseCurrency} transaction(s) from totals.",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText,
                            )
                        }
                    }
                }
            }

            // Full Category Breakdown List (including Uncategorized)
            if (state.summary.categoryBreakdowns.isNotEmpty()) {
                item(key = "cat-hdr") {
                    SectionHeader(
                        title = "Category Breakdown",
                        trailingText = "${state.summary.categoryBreakdowns.size} categories",
                    )
                }
                item(key = "cat-card") {
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        state.summary.categoryBreakdowns.forEachIndexed { idx, cat ->
                            val sliceColor = categorySliceColor(cat.categoryName, idx)
                            val shareRatio = if (cat.netSpendMinor > 0L) {
                                (cat.netSpendMinor.toFloat() / totalCategorySpend).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val sharePct = (shareRatio * 100f).roundToInt()

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(sliceColor),
                                        )
                                        Column {
                                            Text(
                                                text = cat.categoryName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "${cat.transactionCount} transaction(s) · $sharePct% of spend",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ext.mutedText,
                                            )
                                        }
                                    }
                                    Text(
                                        text = formatCurrency(cat.netSpendMinor, state.baseCurrency),
                                        style = MaterialTheme.typography.titleSmall.tabularNums(),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { shareRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                    color = sliceColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round,
                                )
                            }
                            if (idx < state.summary.categoryBreakdowns.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = ext.divider,
                                )
                            }
                        }
                    }
                }
            }

            // Monthly Budgets Section
            item(key = "budgets-hdr") {
                SectionHeader(
                    title = "Monthly Budgets",
                    actionLabel = if (state.categories.isNotEmpty()) "+ Add Budget" else null,
                    onAction = { showBudgetSheet = true },
                )
            }

            if (state.budgets.isEmpty()) {
                item(key = "budgets-empty") {
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PieChartOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column {
                                    Text("No budgets configured", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Set monthly category limits to track spending pace.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ext.mutedText,
                                    )
                                }
                            }
                            if (state.categories.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showBudgetSheet = true },
                                    shape = RoundedCornerShape(999.dp),
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(" Set")
                                }
                            }
                        }
                    }
                }
            } else {
                items(state.budgets, key = { it.budget.id }) { bp ->
                    val pct = (bp.progressRatio * 100f).roundToInt()
                    val (barColor, pillBg, pillFg) = when {
                        bp.progressRatio >= 1f -> Triple(
                            MaterialTheme.colorScheme.error,
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer,
                        )
                        bp.progressRatio >= 0.8f -> Triple(
                            ext.warning,
                            ext.warningContainer,
                            ext.onWarningContainer,
                        )
                        else -> Triple(
                            ext.inflow,
                            ext.inflowContainer,
                            ext.onInflowContainer,
                        )
                    }
                    val remainingMinor = bp.budget.limitMinor - bp.spentMinor

                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bp.categoryName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = bp.accountName?.let { "Account: $it" } ?: "All accounts",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ext.mutedText,
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    StatusPill(
                                        label = "$pct%",
                                        containerColor = pillBg,
                                        contentColor = pillFg,
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteBudget(bp.budget.id) },
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete budget",
                                            tint = ext.mutedText,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            LinearProgressIndicator(
                                progress = { bp.progressRatio.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = barColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${formatCurrency(bp.spentMinor, state.baseCurrency)} of ${formatCurrency(bp.budget.limitMinor, state.baseCurrency)}",
                                    style = MaterialTheme.typography.labelMedium.tabularNums(),
                                )
                                Text(
                                    text = if (remainingMinor >= 0L) {
                                        "${formatCurrency(remainingMinor, state.baseCurrency)} left"
                                    } else {
                                        "${formatCurrency(-remainingMinor, state.baseCurrency)} over"
                                    },
                                    style = MaterialTheme.typography.labelSmall.tabularNums(),
                                    color = if (remainingMinor >= 0L) ext.mutedText else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            // Top Merchants & Trends (DASH-08)
            item(key = "merchants-hdr") {
                SectionHeader(
                    title = "Top Merchants & Trends",
                    trailingText = "${state.summary.merchantTrends.size} merchants",
                )
            }
            if (state.summary.merchantTrends.isEmpty()) {
                item(key = "merchants-empty") {
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No merchant spending trends in ${state.monthLabel}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            } else {
                item(key = "merchants-card") {
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        state.summary.merchantTrends.forEachIndexed { idx, trend ->
                            val initial = trend.merchantLabel.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AvatarCircle(
                                        text = initial,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        size = 32.dp,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = trend.merchantLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val deltaColor = when {
                                            trend.deltaMinor > 0L -> MaterialTheme.colorScheme.error
                                            trend.deltaMinor < 0L -> ext.inflow
                                            else -> ext.mutedText
                                        }
                                        val deltaText = if (state.summary.previousMonthExpenseMinor != null || trend.previousSpendMinor != 0L) {
                                            " · vs prev ${formatCurrency(trend.deltaMinor, state.baseCurrency, signed = true)}"
                                        } else {
                                            ""
                                        }
                                        Text(
                                            text = "${trend.transactionCount} tx$deltaText",
                                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                                            color = deltaColor,
                                        )
                                    }
                                }
                                Text(
                                    text = formatCurrency(trend.currentSpendMinor, state.baseCurrency),
                                    style = MaterialTheme.typography.titleSmall.tabularNums(),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (idx < state.summary.merchantTrends.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = ext.divider,
                                )
                            }
                        }
                    }
                }
            }

            // Derived Transfer Fees (Read-Only)
            if (state.summary.derivedFees.isNotEmpty()) {
                item(key = "fees-hdr") {
                    SectionHeader(
                        title = "Derived Transfer Fees",
                        trailingText = "Read-Only",
                    )
                }
                item(key = "fees-card") {
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        state.summary.derivedFees.forEachIndexed { idx, fee ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Pair #${fee.transferPairId} · ${UiDateHelper.formatDate(fee.occurredAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = formatCurrency(fee.amountMinor, fee.currency),
                                    style = MaterialTheme.typography.titleSmall.tabularNums(),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (idx < state.summary.derivedFees.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = ext.divider,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBudgetSheet && state.categories.isNotEmpty()) {
        var selectedCategoryId by remember { mutableLongStateOf(state.categories.first().id) }
        var selectedAccountId by remember { mutableStateOf<Long?>(null) }
        var limitInput by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { showBudgetSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Set Monthly Category Budget", style = MaterialTheme.typography.titleMedium)

                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) },
                        )
                    }
                }

                if (state.accounts.isNotEmpty()) {
                    Text("Account Scope (optional)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = selectedAccountId == null,
                            onClick = { selectedAccountId = null },
                            label = { Text("All Accounts") },
                        )
                        state.accounts.forEach { acc ->
                            FilterChip(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text(acc.name) },
                            )
                        }
                    }
                }

                MoneyAmountTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    currency = state.baseCurrency,
                    label = "Monthly limit (${state.baseCurrency})",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { showBudgetSheet = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Cancel") }
                    Button(
                        enabled = isValidMoneyInput(limitInput, state.baseCurrency),
                        onClick = {
                            showBudgetSheet = false
                            viewModel.upsertBudget(selectedCategoryId, selectedAccountId, limitInput)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Save Budget") }
                }
            }
        }
    }
}
