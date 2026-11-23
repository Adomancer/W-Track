package wtrack.app.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.tabularNums
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.CategoryDonutChart
import wtrack.app.feature.common.MoneyAmountTextField
import wtrack.app.feature.common.SectionHeader
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.V2StatusDot
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ext = WtrackTheme.colors
    var showBudgetSheet by remember { mutableStateOf(false) }
    var showMoreInsightsSheet by remember { mutableStateOf(false) }

    val positiveCategories = remember(state.summary.categoryBreakdowns) {
        state.summary.categoryBreakdowns.filter { it.netSpendMinor > 0L }
    }
    val totalCategorySpend = remember(positiveCategories) {
        positiveCategories.sumOf { it.netSpendMinor }.coerceAtLeast(1L)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        if (state.actionMessage != null) {
            item(key = "msg") {
                ActionFeedbackBanner(
                    message = state.actionMessage!!,
                    onDismiss = viewModel::clearMessage,
                )
            }
        }

        // Top bar: Title + small month selector
        item(key = "top-bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                contentDescription = "Back",
                            )
                        }
                    }
                    Text(
                        text = "Insights",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(onClick = viewModel::previousMonth, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = "Previous month",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = state.monthLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
                        onClick = viewModel::nextMonth,
                        enabled = state.canAdvanceMonth,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "Next month",
                            tint = if (state.canAdvanceMonth) MaterialTheme.colorScheme.onSurfaceVariant else ext.mutedText.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // Block 1: Hero Total Spending
        item(key = "hero-spending") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Total spending",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatCurrency(state.summary.totalExpenseMinor, state.baseCurrency),
                    style = MaterialTheme.typography.displaySmall.tabularNums(),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // Block 2: One thin donut + top 3 categories plain list
        item(key = "donut-and-top3") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryDonutChart(
                    breakdowns = state.summary.categoryBreakdowns,
                    totalSpendMinor = state.summary.totalExpenseMinor,
                    baseCurrency = state.baseCurrency,
                )

                if (positiveCategories.isEmpty()) {
                    Text(
                        text = "No spending recorded in ${state.monthLabel}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.mutedText,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        positiveCategories.take(3).forEachIndexed { idx, cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showMoreInsightsSheet = true },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    V2StatusDot(color = categorySliceColor(cat.categoryName, idx))
                                    Text(
                                        text = cat.categoryName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = formatCompactCurrency(cat.netSpendMinor, state.baseCurrency),
                                    style = MaterialTheme.typography.labelLarge.tabularNums(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Block 3: Budgets as 4px progress lines (teal, amber only above 85%)
        item(key = "budgets-section") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Budgets",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (state.categories.isNotEmpty()) {
                            Text(
                                text = "+ Add",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { showBudgetSheet = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                        if (state.summary.merchantTrends.isNotEmpty() || state.summary.categoryBreakdowns.size > 3) {
                            Text(
                                text = "See all",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { showMoreInsightsSheet = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                if (state.budgets.isEmpty()) {
                    Text(
                        text = "No budgets set yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.mutedText,
                    )
                } else {
                    state.budgets.forEach { bp ->
                        val over85 = bp.progressRatio > 0.85f
                        val barColor = if (over85) ext.warning else ext.inflow
                        val spentCompact = formatCompactCurrency(bp.spentMinor, state.baseCurrency)
                        val limitCompact = formatCompactCurrency(bp.budget.limitMinor, state.baseCurrency)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = bp.categoryName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "$spentCompact / $limitCompact",
                                        style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                        color = if (over85) ext.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteBudget(bp.budget.id) },
                                        modifier = Modifier.size(22.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete budget",
                                            tint = ext.mutedText,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            LinearProgressIndicator(
                                progress = { bp.progressRatio.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = barColor,
                                trackColor = ext.divider,
                                strokeCap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
        }
    }

    // One tap deeper: Top Merchants & Full Category Breakdown
    if (showMoreInsightsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreInsightsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text("More insights · ${state.monthLabel}", style = MaterialTheme.typography.titleLarge)

                if (state.summary.categoryBreakdowns.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionHeader(title = "All categories", trailingText = "${state.summary.categoryBreakdowns.size}")
                        state.summary.categoryBreakdowns.forEachIndexed { idx, cat ->
                            val sharePct = if (cat.netSpendMinor > 0L) {
                                ((cat.netSpendMinor.toDouble() / totalCategorySpend) * 100.0).roundToInt()
                            } else 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    V2StatusDot(color = categorySliceColor(cat.categoryName, idx))
                                    Column {
                                        Text(cat.categoryName, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "${cat.transactionCount} transactions · $sharePct%",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text = formatCurrency(cat.netSpendMinor, state.baseCurrency),
                                    style = MaterialTheme.typography.titleMedium.tabularNums(),
                                )
                            }
                            if (idx < state.summary.categoryBreakdowns.lastIndex) {
                                HorizontalDivider(color = ext.divider)
                            }
                        }
                    }
                }

                if (state.summary.merchantTrends.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionHeader(title = "Top merchants", trailingText = "${state.summary.merchantTrends.size}")
                        state.summary.merchantTrends.forEachIndexed { idx, trend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trend.merchantLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${trend.transactionCount} transactions",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = formatCurrency(trend.currentSpendMinor, state.baseCurrency),
                                    style = MaterialTheme.typography.titleMedium.tabularNums(),
                                )
                            }
                            if (idx < state.summary.merchantTrends.lastIndex) {
                                HorizontalDivider(color = ext.divider)
                            }
                        }
                    }
                }

                if (state.summary.derivedFees.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionHeader(title = "Transfer fees")
                        state.summary.derivedFees.forEach { fee ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = UiDateHelper.formatDate(fee.occurredAt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = formatCurrency(fee.amountMinor, fee.currency),
                                    style = MaterialTheme.typography.titleMedium.tabularNums(),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
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
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Set monthly budget", style = MaterialTheme.typography.titleLarge)

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
                    Text("Account scope", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = selectedAccountId == null,
                            onClick = { selectedAccountId = null },
                            label = { Text("All accounts") },
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
                        shape = RoundedCornerShape(999.dp),
                    ) { Text("Cancel") }
                    Button(
                        enabled = isValidMoneyInput(limitInput, state.baseCurrency),
                        onClick = {
                            showBudgetSheet = false
                            viewModel.upsertBudget(selectedCategoryId, selectedAccountId, limitInput)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                    ) { Text("Save budget") }
                }
            }
        }
    }
}
