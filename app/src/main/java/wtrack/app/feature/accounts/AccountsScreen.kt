package wtrack.app.feature.accounts

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.tabularNums
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.CaptureStatusPill
import wtrack.app.feature.common.MoneyAmountTextField
import wtrack.app.feature.common.PossibleGapPill
import wtrack.app.feature.common.ProviderBadgeAvatar
import wtrack.app.feature.common.SectionHeader
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.common.WtrackTopBar
import wtrack.app.feature.common.formatCurrency
import wtrack.app.feature.common.isValidMoneyInput

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onOpenDiagnostics: () -> Unit,
    onOpenCustomParsers: (String?) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val ext = WtrackTheme.colors
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var correctingAccount by remember { mutableStateOf<AccountCardUiModel?>(null) }
    var linkingAccount by remember { mutableStateOf<Account?>(null) }

    val primaryCurrency = state.activeAccounts.firstOrNull()?.account?.currency ?: "IDR"
    val primaryCurrencyTotalMinor = state.activeAccounts
        .filter { it.account.currency.equals(primaryCurrency, ignoreCase = true) }
        .sumOf { it.account.estimatedBalanceMinor }
    val otherCurrencyTotals = state.activeAccounts
        .filter { !it.account.currency.equals(primaryCurrency, ignoreCase = true) }
        .groupBy { it.account.currency.uppercase() }
        .mapValues { (_, items) -> items.sumOf { it.account.estimatedBalanceMinor } }
    val attentionCount = state.activeAccounts.count {
        it.account.captureStatus == CaptureStatus.ATTENTION ||
            it.account.hasPossibleGap ||
            (it.checkpoint?.driftMinor ?: 0L) != 0L
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WtrackTopBar(
            title = "Accounts",
            trailing = {
                IconButton(onClick = onOpenDiagnostics, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = "Capture Diagnostics",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { showCreateSheet = true },
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = " Add",
                        style = MaterialTheme.typography.labelMedium,
                    )
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

            if (state.activeAccounts.isNotEmpty()) {
                item(key = "total-card") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(ext.totalBalanceCardBg)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Total Estimated Balance ($primaryCurrency)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ext.totalBalanceCardMuted,
                                )
                                Surface(
                                    color = Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(999.dp),
                                ) {
                                    Text(
                                        text = "${state.activeAccounts.size} active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Text(
                                text = formatCurrency(primaryCurrencyTotalMinor, primaryCurrency),
                                style = MaterialTheme.typography.headlineMedium.tabularNums(),
                                color = Color.White,
                            )
                            if (otherCurrencyTotals.isNotEmpty()) {
                                val formattedOthers = otherCurrencyTotals.entries.joinToString(" · ") { (cur, amt) ->
                                    formatCurrency(amt, cur)
                                }
                                Text(
                                    text = "Other currencies: $formattedOthers",
                                    style = MaterialTheme.typography.labelSmall.tabularNums(),
                                    color = ext.totalBalanceCardMuted,
                                )
                            }
                            if (attentionCount > 0) {
                                Text(
                                    text = "$attentionCount account(s) have a capture gap or balance drift",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.warningBadgeBg,
                                )
                            }
                        }
                    }
                }
            }

            if (state.activeAccounts.isEmpty()) {
                item(key = "empty") {
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .size(28.dp),
                                )
                            }
                            Text(
                                text = "No accounts configured",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Add your bank, e-wallet, or cash accounts, then allowlist notification apps to auto-capture balances and transactions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                            Button(onClick = { showCreateSheet = true }, shape = RoundedCornerShape(999.dp)) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" Add First Account")
                            }
                        }
                    }
                }
            } else {
                items(state.activeAccounts, key = { it.account.id }) { item ->
                    val acc = item.account
                    var menuExpanded by remember(acc.id) { mutableStateOf(false) }
                    val needsReconcileEmphasis = acc.hasPossibleGap || (item.checkpoint?.driftMinor ?: 0L) != 0L

                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ProviderBadgeAvatar(
                                        labelOrPackage = acc.provider.ifBlank { acc.name },
                                        size = 38.dp,
                                        accountType = acc.accountType,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = acc.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${acc.provider.ifBlank { "Manual" }} · ${acc.accountType.name} · ${acc.currency}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ext.mutedText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    CaptureStatusPill(acc.captureStatus)
                                    Box {
                                        IconButton(
                                            onClick = { menuExpanded = true },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.MoreVert,
                                                contentDescription = "Account actions",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit account") },
                                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    editingAccount = acc
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Link notification app") },
                                                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    linkingAccount = acc
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Parser rules") },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Rule, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    onOpenCustomParsers(item.linkedSources.firstOrNull()?.link?.sourceId)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Capture diagnostics") },
                                                leadingIcon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    onOpenDiagnostics()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Archive account") },
                                                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    viewModel.archiveAccount(acc.id)
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Estimated Balance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                )
                                Text(
                                    text = formatCurrency(acc.estimatedBalanceMinor, acc.currency),
                                    style = MaterialTheme.typography.headlineSmall.tabularNums(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (acc.lastReconciledAt != null) {
                                        "Last reconciled ${UiDateHelper.formatDate(acc.lastReconciledAt)}"
                                    } else {
                                        "Not reconciled yet"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                )
                            }

                            if (acc.hasPossibleGap || acc.unparsedLast30DaysCount > 0) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (acc.hasPossibleGap) {
                                        PossibleGapPill(label = "Possible capture gap")
                                    }
                                    if (acc.unparsedLast30DaysCount > 0) {
                                        StatusPill(
                                            label = "${acc.unparsedLast30DaysCount} unparsed (30d)",
                                            containerColor = ext.warningContainer,
                                            contentColor = ext.onWarningContainer,
                                            borderColor = ext.warningBorder,
                                        )
                                    }
                                }
                            }

                            if (acc.captureStatus == CaptureStatus.UNSUPPORTED) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Auto-detect fallback active: alerts go to Needs Review pre-filled. Teach a pattern once to auto-parse future alerts.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            onClick = { onOpenCustomParsers(item.linkedSources.firstOrNull()?.link?.sourceId) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("Teach Pattern", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }

                            if (item.checkpoint != null) {
                                val cp = item.checkpoint
                                val hasDrift = cp.driftMinor != 0L
                                Surface(
                                    color = if (hasDrift) ext.warningContainer else ext.inflowContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (hasDrift) ext.warningBorder else ext.inflowContainer,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Reported ${formatCurrency(cp.reportedBalanceMinor, acc.currency)}",
                                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                                            color = if (hasDrift) ext.onWarningContainer else ext.onInflowContainer,
                                        )
                                        Text(
                                            text = if (hasDrift) {
                                                "Drift ${formatCurrency(cp.driftMinor, acc.currency, signed = true)}"
                                            } else {
                                                "Matches estimate"
                                            },
                                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasDrift) ext.onWarningContainer else ext.onInflowContainer,
                                        )
                                    }
                                }
                            }

                            if (item.linkedSources.isNotEmpty()) {
                                HorizontalDivider(color = ext.divider)
                                Text(
                                    text = "Linked Notification Sources",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                item.linkedSources.forEach { ls ->
                                    val src = ls.source
                                    val samplingActive = src?.sampleCollectionUntil != null && src.sampleCollectionUntil > state.nowEpochMillis
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ls.link.sourceId + (ls.link.accountHint?.let { " · hint: $it" } ?: ""),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (src != null) {
                                                val customCount = state.customRuleCountsBySource[src.id] ?: 0
                                                val statusText = buildString {
                                                    append(if (src.isAllowlisted) "Allowlisted" else "Paused")
                                                    if (customCount > 0) {
                                                        append(" · $customCount custom pattern(s)")
                                                    }
                                                }
                                                Text(
                                                    text = statusText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ext.mutedText,
                                                )
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            if (src != null && src.parserVersion == null) {
                                                Text("14d", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                                Switch(
                                                    checked = samplingActive,
                                                    onCheckedChange = { enable -> viewModel.toggleSampleCollection(src.id, enable) },
                                                )
                                            }
                                            TextButton(
                                                onClick = { viewModel.unlinkSource(ls.link.id) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            ) {
                                                Text("Unlink", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (needsReconcileEmphasis) {
                                    Button(
                                        onClick = { correctingAccount = item },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ext.warning,
                                            contentColor = Color.White,
                                        ),
                                    ) {
                                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(" Reconcile Now")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { correctingAccount = item },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(" Reconcile")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { linkingAccount = acc },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(" Link App")
                                }
                            }
                        }
                    }
                }
            }

            if (state.archivedAccounts.isNotEmpty()) {
                item(key = "archived-hdr") {
                    SectionHeader(
                        title = "Archived Accounts",
                        trailingText = "${state.archivedAccounts.size}",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(state.archivedAccounts, key = { "arch-${it.account.id}" }) { item ->
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ProviderBadgeAvatar(
                                    labelOrPackage = item.account.provider.ifBlank { item.account.name },
                                    size = 32.dp,
                                    accountType = item.account.accountType,
                                )
                                Column {
                                    Text(item.account.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = formatCurrency(item.account.estimatedBalanceMinor, item.account.currency),
                                        style = MaterialTheme.typography.labelSmall.tabularNums(),
                                        color = ext.mutedText,
                                    )
                                }
                            }
                            TextButton(onClick = { viewModel.restoreAccount(item.account.id) }) {
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        AccountEditorBottomSheet(
            title = "New Account",
            initial = null,
            onDismiss = { showCreateSheet = false },
            onConfirm = { name, provider, type, cur, opening ->
                showCreateSheet = false
                viewModel.createAccount(name, provider, type, cur, opening)
            },
        )
    }

    if (editingAccount != null) {
        val acc = editingAccount!!
        AccountEditorBottomSheet(
            title = "Edit Account",
            initial = acc,
            onDismiss = { editingAccount = null },
            onConfirm = { name, provider, type, cur, _ ->
                editingAccount = null
                viewModel.updateAccount(acc.id, name, provider, type, cur)
            },
        )
    }

    if (correctingAccount != null) {
        val card = correctingAccount!!
        val acc = card.account
        var actualInput by remember(acc.id) { mutableStateOf("") }
        var note by remember(acc.id) { mutableStateOf("") }
        val parsedActualMinor = remember(actualInput, acc.currency) {
            if (actualInput.isBlank()) null else MoneyFormatter.parseMajorInput(actualInput, acc.currency)
        }
        val deltaMinor = parsedActualMinor?.minus(acc.estimatedBalanceMinor)

        ModalBottomSheet(
            onDismissRequest = { correctingAccount = null },
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderBadgeAvatar(
                        labelOrPackage = acc.provider.ifBlank { acc.name },
                        size = 36.dp,
                        accountType = acc.accountType,
                    )
                    Column {
                        Text("Reconcile Balance", style = MaterialTheme.typography.titleMedium)
                        Text("${acc.name} · ${acc.currency}", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                    }
                }

                WtrackCard(
                    cornerRadius = 14.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    elevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Estimated Balance", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                            Text(
                                text = formatCurrency(acc.estimatedBalanceMinor, acc.currency),
                                style = MaterialTheme.typography.titleSmall.tabularNums(),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (card.checkpoint != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Last Reported Checkpoint", style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
                                Text(
                                    text = formatCurrency(card.checkpoint.reportedBalanceMinor, acc.currency),
                                    style = MaterialTheme.typography.labelMedium.tabularNums(),
                                    color = ext.mutedText,
                                )
                            }
                        }
                    }
                }

                MoneyAmountTextField(
                    value = actualInput,
                    onValueChange = { actualInput = it },
                    currency = acc.currency,
                    label = "Actual balance (${acc.currency})",
                    allowZero = true,
                    allowNegative = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (deltaMinor != null) {
                    Surface(
                        color = if (deltaMinor == 0L) ext.inflowContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ext.infoTintBorder),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Adjustment Delta", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = if (deltaMinor == 0L) {
                                        "No adjustment needed"
                                    } else {
                                        formatCurrency(deltaMinor, acc.currency, signed = true)
                                    },
                                    style = MaterialTheme.typography.titleSmall.tabularNums(),
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        deltaMinor > 0L -> ext.inflow
                                        deltaMinor < 0L -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                )
                            }
                            Text(
                                text = "Updates this account balance without changing Income or Spending totals.",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Balance adjustments update only this account's balance without changing Income or Spending totals.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.mutedText,
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. Monthly statement check") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { correctingAccount = null },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Cancel") }
                    Button(
                        enabled = isValidMoneyInput(actualInput, acc.currency, allowZero = true, allowNegative = true),
                        onClick = {
                            correctingAccount = null
                            viewModel.correctBalance(acc.id, acc.currency, actualInput, note)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Save Adjustment") }
                }
            }
        }
    }

    if (linkingAccount != null) {
        val acc = linkingAccount!!
        var pkg by remember(acc.id) { mutableStateOf("") }
        var label by remember(acc.id) { mutableStateOf(acc.provider) }
        var hint by remember(acc.id) { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { linkingAccount = null },
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
                Text("Allowlist & Link App", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Candidate packages (tap to prefill, verify actual installed ID on device):",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.mutedText,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.providerCatalog.forEach { target ->
                        target.candidatePackageIds.forEach { candidatePkg ->
                            FilterChip(
                                selected = pkg == candidatePkg,
                                onClick = {
                                    pkg = candidatePkg
                                    label = target.displayName
                                },
                                label = { Text("${target.displayName}: $candidatePkg") },
                            )
                        }
                    }
                }
                Text(
                    text = "Other common Indonesian banks & e-wallets (auto-detect fallback ready):",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.mutedText,
                )
                val otherIndonesianApps = remember {
                    listOf(
                        "GoPay / Gojek" to "com.gojek.app",
                        "OVO" to "ovo.id",
                        "Shopee" to "com.shopee.id",
                        "SeaBank" to "id.co.seabank.id",
                        "blu" to "com.bcadigital.blu",
                        "Livin'" to "id.bmri.livin",
                        "BRImo" to "id.co.bri.brimo",
                        "wondr BNI" to "id.co.bni.wondr",
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    otherIndonesianApps.forEach { (appLabel, appPkg) ->
                        FilterChip(
                            selected = pkg == appPkg,
                            onClick = {
                                pkg = appPkg
                                label = appLabel
                            },
                            label = { Text(appLabel) },
                        )
                    }
                }
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text("Android package name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { Text("Masked account hint (optional, e.g. last 4 or pocket)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { linkingAccount = null },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Cancel") }
                    Button(
                        enabled = pkg.isNotBlank(),
                        onClick = {
                            linkingAccount = null
                            viewModel.linkSource(acc.id, pkg, label, hint.ifBlank { null })
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Allowlist & Link") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AccountEditorBottomSheet(
    title: String,
    initial: Account?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, AccountType, String, String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var provider by remember(initial) { mutableStateOf(initial?.provider.orEmpty()) }
    var type by remember(initial) { mutableStateOf(initial?.accountType ?: AccountType.BANK) }
    var currency by remember(initial) { mutableStateOf(initial?.currency ?: "IDR") }
    var opening by remember(initial) { mutableStateOf("") }

    val quickProviders = remember {
        listOf(
            "Jago" to AccountType.BANK,
            "myBCA" to AccountType.BANK,
            "Mandiri" to AccountType.BANK,
            "DANA" to AccountType.EWALLET,
            "OVO" to AccountType.EWALLET,
            "GoPay" to AccountType.EWALLET,
            "Cash" to AccountType.CASH,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Text(title, style = MaterialTheme.typography.titleMedium)

            if (initial == null) {
                Text("Quick Provider Preset", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    quickProviders.forEach { (presetProvider, presetType) ->
                        FilterChip(
                            selected = provider.equals(presetProvider, ignoreCase = true),
                            onClick = {
                                provider = presetProvider
                                type = presetType
                                if (name.isBlank()) name = "$presetProvider Main"
                            },
                            label = { Text(presetProvider) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Account Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("Provider") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Account Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AccountType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(t.name) },
                    )
                }
            }

            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.uppercase() },
                label = { Text("ISO Currency (e.g., IDR)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (initial == null) {
                MoneyAmountTextField(
                    value = opening,
                    onValueChange = { opening = it },
                    currency = currency,
                    label = "Opening balance (optional)",
                    allowZero = true,
                    allowNegative = true,
                    isOptional = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Cancel") }
                Button(
                    enabled = name.isNotBlank() &&
                        (initial != null || isValidMoneyInput(opening, currency, allowZero = true, allowNegative = true, isOptional = true)),
                    onClick = { onConfirm(name, provider, type, currency, opening) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Save Account") }
            }
        }
    }
}
