package wtrack.app.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.tabularNums
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.feature.common.AvatarCircle
import wtrack.app.feature.common.CaptureStatusPill
import wtrack.app.feature.common.CategoryStackedBar
import wtrack.app.feature.common.ProviderBadgeAvatar
import wtrack.app.feature.common.SectionHeader
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.WtrackBrandMark
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.common.categorySliceColor
import wtrack.app.feature.common.formatCurrency
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
    val state by viewModel.uiState.collectAsState()
    val ext = WtrackTheme.colors

    val totalBaseBalanceMinor = state.activeAccounts
        .filter { it.currency.equals(state.baseCurrency, ignoreCase = true) }
        .sumOf { it.estimatedBalanceMinor }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top Header: W-Track brand mark + Month selector + Capture Health + Review Inbox + Settings
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WtrackBrandMark(size = 32.dp)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            IconButton(
                                onClick = viewModel::previousMonth,
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                    contentDescription = "Previous month",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = state.shortMonthLabel.ifBlank { state.monthLabel },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            IconButton(
                                onClick = viewModel::nextMonth,
                                enabled = state.canAdvanceMonth,
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = "Next month",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CaptureStatusPill(
                        status = state.captureSummary.appStatus,
                        modifier = Modifier.clickable(onClick = onOpenDiagnostics),
                    )

                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, ext.cardBorder),
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onOpenReview),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Needs Review",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (state.pendingReviewCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 3.dp, y = (-2).dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(ext.warning),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (state.pendingReviewCount > 9) "9+" else state.pendingReviewCount.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, ext.cardBorder),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenSettings),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Hero Cash Flow Card (DASH-01, DASH-06, DASH-09)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(ext.heroGradientStart, ext.heroGradientEnd)))
                    .border(1.dp, ext.heroBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Net Cash Flow · ${state.baseCurrency}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ext.heroLabel,
                        )
                        state.summary.expenseDeltaPercent?.let { pct ->
                            Surface(
                                color = ext.heroPillContainer,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%+.1f%% spend vs prev", pct),
                                    style = MaterialTheme.typography.labelSmall.tabularNums(),
                                    color = ext.heroPillText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = formatCurrency(state.summary.netCashFlowMinor, state.baseCurrency, signed = true),
                        style = MaterialTheme.typography.headlineMedium.tabularNums(),
                        color = Color.White,
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Income", style = MaterialTheme.typography.labelSmall, color = ext.heroLabel)
                            Text(
                                text = formatCurrency(state.summary.totalIncomeMinor, state.baseCurrency, signed = true),
                                style = MaterialTheme.typography.titleMedium.tabularNums(),
                                color = ext.heroIncomeText,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Expenses (net)", style = MaterialTheme.typography.labelSmall, color = ext.heroLabel)
                            val expStr = if (state.summary.totalExpenseMinor > 0L) {
                                "-" + formatCurrency(state.summary.totalExpenseMinor, state.baseCurrency)
                            } else {
                                formatCurrency(state.summary.totalExpenseMinor, state.baseCurrency)
                            }
                            Text(
                                text = expStr,
                                style = MaterialTheme.typography.titleMedium.tabularNums(),
                                color = ext.heroExpenseText,
                            )
                        }
                    }

                    Text(
                        text = "Est. Total Balance: ${formatCurrency(totalBaseBalanceMinor, state.baseCurrency)} · ${state.activeAccounts.size} Active Account(s)",
                        style = MaterialTheme.typography.labelSmall.tabularNums(),
                        color = ext.heroMutedText,
                    )

                    if (state.summary.unreconciledAdjustmentNetMinor != 0L) {
                        Text(
                            text = "Unreconciled adjustments: ${formatCurrency(state.summary.unreconciledAdjustmentNetMinor, state.baseCurrency, signed = true)} (excluded from cash flow)",
                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                            color = ext.heroMutedText,
                        )
                    }
                    if (state.summary.excludedNonBaseCurrencyCount > 0) {
                        Text(
                            text = "${state.summary.excludedNonBaseCurrencyCount} non-${state.baseCurrency} transaction(s) excluded from totals",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.heroPillText,
                        )
                    }
                }
            }
        }

        // Needs Review CTA Banner (DASH-04)
        if (state.pendingReviewCount > 0) {
            item {
                WtrackCard(
                    cornerRadius = 16.dp,
                    containerColor = ext.warningContainer,
                    borderColor = ext.warningBorder,
                    onClick = onOpenReview,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AvatarCircle(
                                text = "!",
                                containerColor = ext.warningBadgeBg,
                                contentColor = ext.onWarningContainer,
                                size = 28.dp,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = "${state.pendingReviewCount} notification(s) need review",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ext.onWarningContainer,
                                )
                                Text(
                                    text = "Confirm or ignore unparsed alerts before ledger",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Normal,
                                    color = ext.onWarningContainer.copy(alpha = 0.85f),
                                )
                            }
                        }
                        Surface(
                            color = ext.warning,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text(
                                text = "Review ›",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        // Backup reminder (DATA-12) without exposing financial details
        if (state.showBackupReminder) {
            item {
                WtrackCard(containerColor = ext.infoTintBg, borderColor = ext.infoTintBorder) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(
                                    text = "Backup Reminder",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ext.infoTintText,
                                )
                                Text(
                                    text = "Create an encrypted local backup to protect against device loss.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.infoTintText.copy(alpha = 0.85f),
                                )
                            }
                        }
                        Row {
                            TextButton(onClick = viewModel::dismissBackupReminder) { Text("Later") }
                            TextButton(onClick = onOpenSettings) { Text("Back up") }
                        }
                    }
                }
            }
        }

        // Accounts Strip (DASH-03)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "Accounts",
                    actionLabel = "Manage ›",
                    onAction = onOpenAccounts,
                )
                if (state.activeAccounts.isEmpty()) {
                    OutlinedButton(
                        onClick = onOpenAccounts,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add an account to start tracking")
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.activeAccounts, key = { it.id }) { account ->
                            val dotColor = when {
                                account.hasPossibleGap -> ext.warning
                                account.captureStatus == CaptureStatus.ACTIVE -> ext.inflow
                                account.captureStatus == CaptureStatus.DISCONNECTED -> ext.transfer
                                account.captureStatus == CaptureStatus.ATTENTION -> MaterialTheme.colorScheme.error
                                else -> ext.mutedText
                            }
                            WtrackCard(
                                modifier = Modifier.width(150.dp),
                                onClick = onOpenAccounts,
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ProviderBadgeAvatar(
                                            labelOrPackage = account.provider.ifBlank { account.name },
                                            size = 26.dp,
                                            accountType = account.accountType,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(dotColor),
                                        )
                                    }
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (account.hasPossibleGap) "Possible gap" else "Est. balance",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (account.hasPossibleGap) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (account.hasPossibleGap) ext.warning else ext.mutedText,
                                    )
                                    Text(
                                        text = formatCurrency(account.estimatedBalanceMinor, account.currency),
                                        style = MaterialTheme.typography.titleSmall.tabularNums(),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category preview (DASH-05)
        val positiveCategories = state.summary.categoryBreakdowns.filter { it.netSpendMinor > 0L }
        if (positiveCategories.isNotEmpty()) {
            item {
                val totalPositive = positiveCategories.sumOf { it.netSpendMinor }.toDouble().coerceAtLeast(1.0)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = "Spending by Category",
                        actionLabel = "Insights ›",
                        onAction = onOpenInsights,
                    )
                    WtrackCard {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CategoryStackedBar(positiveCategories)
                            val topFour = positiveCategories.take(4)
                            topFour.chunked(2).forEachIndexed { rowIdx, pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    pair.forEachIndexed { colIdx, cat ->
                                        val globalIdx = rowIdx * 2 + colIdx
                                        val pct = ((cat.netSpendMinor / totalPositive) * 100.0).roundToInt()
                                        val isUncat = cat.categoryName.equals("Uncategorized", ignoreCase = true)
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(categorySliceColor(cat.categoryName, globalIdx)),
                                            )
                                            Text(
                                                text = "${cat.categoryName} · $pct%",
                                                style = MaterialTheme.typography.labelMedium.tabularNums(),
                                                color = if (isUncat) ext.onWarningContainer else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent transactions (DASH-02)
        item {
            SectionHeader(
                title = "Recent Activity",
                actionLabel = "See all ›",
                onAction = onOpenActivity,
            )
        }
        if (state.recentTransactions.isEmpty()) {
            item {
                WtrackCard {
                    Text(
                        text = "No transactions captured in this ledger yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            item {
                val visibleRecent = state.recentTransactions.take(5)
                WtrackCard {
                    visibleRecent.forEachIndexed { idx, tx ->
                        val isPaired = tx.id in state.pairedTxIds
                        val title = tx.merchantName ?: tx.description ?: tx.transactionType.name
                        val (avatarText, avatarBg, avatarFg) = when {
                            isPaired -> Triple("↔", ext.transferContainer, ext.onTransferContainer)
                            tx.direction == Direction.INFLOW -> Triple("↓", ext.inflowContainer, ext.inflow)
                            else -> Triple(
                                title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "W",
                                ext.warningContainer,
                                ext.warning,
                            )
                        }
                        val amountText = if (isPaired) {
                            formatCurrency(abs(tx.amountMinor), tx.currency)
                        } else {
                            val signed = if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor
                            formatCurrency(signed, tx.currency, signed = true)
                        }
                        val amountColor = when {
                            isPaired -> ext.transfer
                            tx.direction == Direction.INFLOW -> ext.inflow
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val subtitle = buildString {
                            append(state.accountNames[tx.accountId] ?: "Account")
                            append(" · ")
                            append(if (isPaired) "Paired ↔" else state.categoryNames[tx.categoryId] ?: "Uncategorized")
                            append(" · ")
                            append(UiDateHelper.formatTimeOnly(tx.occurredAt))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenTransaction(tx.id) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AvatarCircle(
                                text = avatarText,
                                containerColor = avatarBg,
                                contentColor = avatarFg,
                                size = 34.dp,
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.mutedText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = amountText,
                                style = MaterialTheme.typography.titleSmall.tabularNums(),
                                fontWeight = FontWeight.Bold,
                                color = amountColor,
                            )
                        }
                        if (idx < visibleRecent.lastIndex) {
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
