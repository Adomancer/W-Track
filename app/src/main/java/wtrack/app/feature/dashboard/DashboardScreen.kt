package wtrack.app.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.feature.common.CaptureStatusPill
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.formatCurrency
import wtrack.integration.Direction

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenReview: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenSettingsBackup: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::previousMonth) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = state.monthLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::nextMonth, enabled = state.canAdvanceMonth) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month")
                    }
                    IconButton(onClick = onOpenInsights) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "Insights")
                    }
                }
            }
        }

        // Capture status indicator banner (ING-07 / section 9.1)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDiagnostics),
                colors = CardDefaults.cardColors(
                    containerColor = when (state.captureSummary.appStatus) {
                        CaptureStatus.ACTIVE -> MaterialTheme.colorScheme.surfaceVariant
                        CaptureStatus.DISCONNECTED -> MaterialTheme.colorScheme.tertiaryContainer
                        CaptureStatus.ATTENTION, CaptureStatus.UNSUPPORTED -> MaterialTheme.colorScheme.errorContainer
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Capture Health", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = when (state.captureSummary.appStatus) {
                                CaptureStatus.ACTIVE -> "Listening automatically in background"
                                CaptureStatus.DISCONNECTED -> "Permission granted; awaiting OS listener connection"
                                CaptureStatus.ATTENTION -> "Notification Access needed or no allowlisted sources"
                                CaptureStatus.UNSUPPORTED -> "Allowlisted source has no registered parser"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    CaptureStatusPill(state.captureSummary.appStatus)
                }
            }
        }

        // Needs Review CTA (DASH-04)
        if (state.pendingReviewCount > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenReview),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "${state.pendingReviewCount} item(s) need review",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text("Confirm or ignore unparsed / ambiguous notifications", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = onOpenReview) { Text("Review") }
                    }
                }
            }
        }

        // Backup reminder (DATA-12) without exposing financial details
        if (state.showBackupReminder) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Backup Reminder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Create an encrypted local backup to protect against device loss.", style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            TextButton(onClick = viewModel::dismissBackupReminder) { Text("Later") }
                            TextButton(onClick = onOpenSettingsBackup) { Text("Back up") }
                        }
                    }
                }
            }
        }

        // Monthly Income, Expense & Net Cash Flow (DASH-01, DASH-06)
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStatCard(
                    title = "Income",
                    value = formatCurrency(state.summary.totalIncomeMinor, state.baseCurrency),
                    modifier = Modifier.weight(1f),
                )
                SummaryStatCard(
                    title = "Expenses",
                    value = formatCurrency(state.summary.totalExpenseMinor, state.baseCurrency),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Net Cash Flow (${state.baseCurrency})", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = formatCurrency(state.summary.netCashFlowMinor, state.baseCurrency, signed = true),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        val deltaMinor = state.summary.expenseDeltaVsPreviousMonthMinor
                        if (deltaMinor != null) {
                            val pctText = state.summary.expenseDeltaPercent?.let {
                                String.format(Locale.US, " (%+.1f%%)", it)
                            }.orEmpty()
                            StatusPill(
                                label = "vs prev mo: ${formatCurrency(deltaMinor, state.baseCurrency, signed = true)}$pctText",
                            )
                        }
                    }
                    if (state.summary.unreconciledAdjustmentNetMinor != 0L) {
                        Text(
                            text = "Unreconciled adjustments: ${formatCurrency(state.summary.unreconciledAdjustmentNetMinor, state.baseCurrency, signed = true)} (excluded from cash flow)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.summary.excludedNonBaseCurrencyCount > 0) {
                        Text(
                            text = "${state.summary.excludedNonBaseCurrencyCount} non-${state.baseCurrency} transaction(s) excluded from totals",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }

        // Accounts strip (DASH-03)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onOpenAccounts) { Text("Manage") }
            }
            if (state.activeAccounts.isEmpty()) {
                OutlinedButton(onClick = onOpenAccounts, modifier = Modifier.fillMaxWidth()) {
                    Text("Add an account to start tracking")
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.activeAccounts, key = { it.id }) { account ->
                        Card(
                            modifier = Modifier.clickable(onClick = onOpenAccounts),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(account.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Est. balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = formatCurrency(account.estimatedBalanceMinor, account.currency),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                CaptureStatusPill(account.captureStatus)
                            }
                        }
                    }
                }
            }
        }

        // Category preview (DASH-05)
        if (state.summary.categoryBreakdowns.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Spending by Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onOpenInsights) { Text("Insights") }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.summary.categoryBreakdowns.take(5).forEach { cat ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${cat.categoryName} (${cat.transactionCount})", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = formatCurrency(cat.netSpendMinor, state.baseCurrency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent transactions (DASH-02)
        item {
            Text("Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (state.recentTransactions.isEmpty()) {
            item {
                Text(
                    text = "No transactions captured in this ledger yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.recentTransactions, key = { it.id }) { tx ->
                val signedAmount = if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTransaction(tx.id) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = tx.merchantName ?: tx.description ?: tx.transactionType.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${state.accountNames[tx.accountId] ?: "Account"} • ${state.categoryNames[tx.categoryId] ?: "Uncategorized"} • ${UiDateHelper.formatDateTime(tx.occurredAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = formatCurrency(signedAmount, tx.currency, signed = true),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (tx.direction == Direction.INFLOW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
