package wtrack.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.tabularNums
import wtrack.app.feature.common.SectionHeader
import wtrack.app.feature.common.V2StatusDot
import wtrack.app.feature.common.V2TransactionRow
import wtrack.app.feature.common.WtrackBrandMark
import wtrack.app.feature.common.formatCompactCurrency
import wtrack.app.feature.common.formatCurrency
import wtrack.app.feature.common.v2CategoryIcon
import wtrack.integration.Direction

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenReview: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ext = WtrackTheme.colors
    val visibleRecent = state.recentTransactions.take(5)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        // Top Bar: Brand mark left, Bell (with amber dot only if reviews pending) + quiet Settings right
        item(key = "top-bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WtrackBrandMark(size = 28.dp, cornerRadius = 8.dp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenReview),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Needs Review",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                        if (state.pendingReviewCount > 0) {
                            V2StatusDot(
                                color = ext.warning,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Block 1: Hero Cash Flow (pure whitespace, no boxed card)
        item(key = "hero-cash-flow") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Net cash flow · ${state.shortMonthLabel.ifBlank { state.monthLabel }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
                        onClick = viewModel::previousMonth,
                        modifier = Modifier.size(22.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = "Previous month",
                            tint = ext.mutedText,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (state.canAdvanceMonth) {
                        IconButton(
                            onClick = viewModel::nextMonth,
                            modifier = Modifier.size(22.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = "Next month",
                                tint = ext.mutedText,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Text(
                    text = formatCurrency(state.summary.netCashFlowMinor, state.baseCurrency, signed = true),
                    style = MaterialTheme.typography.displaySmall.tabularNums(),
                    color = MaterialTheme.colorScheme.onBackground,
                )

                val incomeStr = formatCompactCurrency(state.summary.totalIncomeMinor, state.baseCurrency)
                val spentStr = formatCompactCurrency(state.summary.totalExpenseMinor, state.baseCurrency)
                Text(
                    text = "Income $incomeStr · Spent $spentStr",
                    style = MaterialTheme.typography.bodyMedium.tabularNums(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Block 2: Quiet Needs Review row (replaces boxed amber banner)
        if (state.pendingReviewCount > 0) {
            item(key = "needs-review-row") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = ext.divider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable(onClick = onOpenReview),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            V2StatusDot(color = ext.warning)
                            Text(
                                text = "${state.pendingReviewCount} need review",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    HorizontalDivider(color = ext.divider)
                }
            }
        } else if (state.showBackupReminder) {
            item(key = "backup-reminder-row") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = ext.divider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Back up your ledger",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Row {
                            TextButton(onClick = viewModel::dismissBackupReminder) {
                                Text("Later", color = ext.mutedText)
                            }
                            TextButton(onClick = onOpenSettings) {
                                Text("Back up", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    HorizontalDivider(color = ext.divider)
                }
            }
        }

        // Block 3: Recent Activity (5 clean rows + See all)
        item(key = "recent-activity") {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(
                    title = "Recent activity",
                    actionLabel = "See all",
                    onAction = onOpenActivity,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (visibleRecent.isEmpty()) {
                    Text(
                        text = "No activity yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.mutedText,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    visibleRecent.forEachIndexed { idx, tx ->
                        val isPaired = tx.id in state.pairedTxIds
                        val isInflow = !isPaired && tx.direction == Direction.INFLOW
                        val title = tx.merchantName ?: tx.description ?: tx.transactionType.name
                        val categoryName = state.categoryNames[tx.categoryId]
                        val isUncategorized = !isPaired && categoryName.isNullOrBlank()
                        val displayCategory = when {
                            isPaired -> "Transfer"
                            isUncategorized -> "Needs category"
                            else -> categoryName!!
                        }
                        val amountText = if (isPaired) {
                            formatCurrency(abs(tx.amountMinor), tx.currency)
                        } else {
                            val signed = if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor
                            formatCurrency(signed, tx.currency, signed = true)
                        }

                        V2TransactionRow(
                            merchant = title,
                            category = displayCategory,
                            amountText = amountText,
                            isInflow = isInflow,
                            isUncategorized = isUncategorized,
                            icon = v2CategoryIcon(
                                categoryName = displayCategory,
                                isIncome = isInflow,
                                isTransfer = isPaired,
                                isUncategorized = isUncategorized,
                            ),
                            onClick = { onOpenTransaction(tx.id) },
                        )
                        if (idx < visibleRecent.lastIndex) {
                            HorizontalDivider(color = ext.divider)
                        }
                    }
                }
            }
        }
    }
}
