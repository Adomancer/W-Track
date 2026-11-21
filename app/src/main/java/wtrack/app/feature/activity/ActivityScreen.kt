package wtrack.app.feature.activity

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.tabularNums
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.AvatarCircle
import wtrack.app.feature.common.MoneyAmountTextField
import wtrack.app.feature.common.ProviderBadgeAvatar
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.common.WtrackTopBar
import wtrack.app.feature.common.formatCurrency
import wtrack.app.feature.common.isValidMoneyInput
import wtrack.integration.Direction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onOpenTransaction: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val ext = WtrackTheme.colors
    var showCreateSheet by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }

    val advancedFilterCount = listOfNotNull(
        state.filter.type,
        state.filter.categoryId,
        state.filter.tagId,
        state.filter.direction,
    ).size
    val uncategorizedId = remember(state.categories) {
        state.categories.firstOrNull { it.systemKey == "UNCATEGORIZED" }?.id
    }
    val uncategorizedCount = remember(state.allItems, uncategorizedId) {
        state.allItems.count {
            it.transaction.status == TransactionStatus.CONFIRMED &&
                (it.transaction.categoryId == null || it.transaction.categoryId == uncategorizedId)
        }
    }

    val groupedByDay = remember(state.items) {
        state.items.groupBy { UiDateHelper.localDateOf(it.transaction.occurredAt) }
            .entries
            .toList()
    }

    val netOutflowMinor = remember(state.items, state.baseCurrency) {
        state.items
            .asSequence()
            .filter {
                it.transaction.status == TransactionStatus.CONFIRMED &&
                    it.transaction.currency.equals(state.baseCurrency, ignoreCase = true) &&
                    it.transferPair == null &&
                    it.transaction.transactionType != DomainTransactionType.ADJUSTMENT &&
                    it.transaction.direction == Direction.OUTFLOW
            }
            .sumOf { it.transaction.amountMinor }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.accounts.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showCreateSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add manual transaction")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.actionMessage != null) {
                item {
                    ActionFeedbackBanner(
                        message = state.actionMessage!!,
                        onDismiss = viewModel::clearMessage,
                    )
                }
            }

            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Surface(
                        color = if (advancedFilterCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(
                            1.dp,
                            if (advancedFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { showFiltersSheet = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (advancedFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (advancedFilterCount > 0) "Filters ($advancedFilterCount)" else "Filters",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (advancedFilterCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // Rounded tonal search field
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = state.filter.query,
                        onValueChange = { q -> viewModel.updateFilter { it.copy(query = q) } },
                        placeholder = {
                            Text(
                                text = "Search merchant, account, #tag...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ext.mutedText,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            if (state.filter.query.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.updateFilter { it.copy(query = "") } },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Single smart filter chip row (decluttered vs 5 stacked rows)
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Box {
                            val activeDate = state.filter.dateFilter != ActivityDateFilter.ALL
                            FilterChip(
                                selected = activeDate,
                                onClick = { showDateMenu = true },
                                label = { Text("${state.filter.dateFilter.label} ▾") },
                                shape = RoundedCornerShape(999.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                            DropdownMenu(expanded = showDateMenu, onDismissRequest = { showDateMenu = false }) {
                                ActivityDateFilter.entries.forEach { range ->
                                    DropdownMenuItem(
                                        text = { Text(range.label) },
                                        onClick = {
                                            showDateMenu = false
                                            viewModel.updateFilter { it.copy(dateFilter = range) }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Box {
                            val selectedAccName = state.accounts.firstOrNull { it.id == state.filter.accountId }?.name
                            FilterChip(
                                selected = selectedAccName != null,
                                onClick = { showAccountMenu = true },
                                label = { Text((selectedAccName ?: "All Accounts") + " ▾") },
                                shape = RoundedCornerShape(999.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                            DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("All Accounts") },
                                    onClick = {
                                        showAccountMenu = false
                                        viewModel.updateFilter { it.copy(accountId = null) }
                                    },
                                )
                                state.accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name) },
                                        onClick = {
                                            showAccountMenu = false
                                            viewModel.updateFilter { it.copy(accountId = acc.id) }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        val uncatLabel = if (uncategorizedCount > 0) "Uncategorized ($uncategorizedCount)" else "Uncategorized"
                        FilterChip(
                            selected = state.filter.uncategorizedOnly,
                            onClick = { viewModel.updateFilter { it.copy(uncategorizedOnly = !it.uncategorizedOnly) } },
                            label = { Text(uncatLabel) },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = if (uncategorizedCount > 0) ext.warningContainer else MaterialTheme.colorScheme.surface,
                                labelColor = if (uncategorizedCount > 0) ext.onWarningContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = ext.warning,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }

                    item {
                        FilterChip(
                            selected = state.filter.showVoided,
                            onClick = { viewModel.updateFilter { it.copy(showVoided = !it.showVoided) } },
                            label = { Text("Voided") },
                            shape = RoundedCornerShape(999.dp),
                        )
                    }
                }
            }

            // Meta strip
            item {
                Text(
                    text = "${state.items.size} transaction(s) · Outflow ${formatCurrency(netOutflowMinor, state.baseCurrency)}",
                    style = MaterialTheme.typography.labelMedium.tabularNums(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Read-only derived transfer fee rows (TRF-04)
            if (state.derivedFees.isNotEmpty()) {
                item {
                    WtrackCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Derived Transfer Fees (Read-Only)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.derivedFees.forEach { fee ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenTransaction(fee.outgoingTransactionId) },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text("Transfer Fee (Derived)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = UiDateHelper.formatDateTime(fee.occurredAt),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ext.mutedText,
                                        )
                                    }
                                    Text(
                                        text = formatCurrency(-fee.amountMinor, fee.currency, signed = true),
                                        style = MaterialTheme.typography.titleSmall.tabularNums(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.items.isEmpty()) {
                item {
                    WtrackCard {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("No matching transactions", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Try clearing filters or tap + to add a manual cash entry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                        }
                    }
                }
            } else {
                groupedByDay.forEach { (_, dayItems) ->
                    val firstEpoch = dayItems.first().transaction.occurredAt
                    val dayOutflow = dayItems
                        .asSequence()
                        .filter {
                            it.transaction.status == TransactionStatus.CONFIRMED &&
                                it.transaction.currency.equals(state.baseCurrency, ignoreCase = true) &&
                                it.transferPair == null &&
                                it.transaction.transactionType != DomainTransactionType.ADJUSTMENT &&
                                it.transaction.direction == Direction.OUTFLOW
                        }
                        .sumOf { it.transaction.amountMinor }

                    item(key = "hdr-${UiDateHelper.localDateOf(firstEpoch)}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = UiDateHelper.formatDayGroupHeader(firstEpoch),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (dayOutflow > 0L) {
                                Text(
                                    text = formatCurrency(-dayOutflow, state.baseCurrency, signed = true),
                                    style = MaterialTheme.typography.labelMedium.tabularNums(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item(key = "grp-${UiDateHelper.localDateOf(firstEpoch)}") {
                        WtrackCard {
                            dayItems.forEachIndexed { idx, item ->
                                ActivityTransactionRow(
                                    item = item,
                                    baseCurrency = state.baseCurrency,
                                    onClick = { onOpenTransaction(item.transaction.id) },
                                )
                                if (idx < dayItems.lastIndex) {
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
    }

    if (showFiltersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFiltersSheet = false },
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Filter Transactions", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = {
                            viewModel.updateFilter {
                                it.copy(type = null, categoryId = null, tagId = null, direction = null)
                            }
                        },
                    ) { Text("Reset") }
                }

                Text("Direction", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Direction.entries.forEach { d ->
                        FilterChip(
                            selected = state.filter.direction == d,
                            onClick = {
                                viewModel.updateFilter { it.copy(direction = if (it.direction == d) null else d) }
                            },
                            label = { Text(d.name) },
                        )
                    }
                }

                Text("Transaction Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DomainTransactionType.entries.forEach { t ->
                        FilterChip(
                            selected = state.filter.type == t,
                            onClick = {
                                viewModel.updateFilter { it.copy(type = if (it.type == t) null else t) }
                            },
                            label = { Text(t.name) },
                        )
                    }
                }

                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.categories.forEach { cat ->
                        FilterChip(
                            selected = state.filter.categoryId == cat.id,
                            onClick = {
                                viewModel.updateFilter { it.copy(categoryId = if (it.categoryId == cat.id) null else cat.id) }
                            },
                            label = { Text(cat.name) },
                        )
                    }
                }

                if (state.tags.isNotEmpty()) {
                    Text("Tags", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.tags.forEach { tag ->
                            FilterChip(
                                selected = state.filter.tagId == tag.id,
                                onClick = {
                                    viewModel.updateFilter { it.copy(tagId = if (it.tagId == tag.id) null else tag.id) }
                                },
                                label = { Text("#${tag.name}") },
                            )
                        }
                    }
                }

                Button(
                    onClick = { showFiltersSheet = false },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Apply Filters") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showCreateSheet && state.accounts.isNotEmpty()) {
        var selectedAccountId by remember { mutableLongStateOf(state.accounts.first().id) }
        val selectedCurrency = state.accounts.firstOrNull { it.id == selectedAccountId }?.currency ?: state.baseCurrency
        var amountInput by remember { mutableStateOf("") }
        var direction by remember { mutableStateOf(Direction.OUTFLOW) }
        var type by remember { mutableStateOf(DomainTransactionType.PURCHASE) }
        var categoryId by remember { mutableStateOf<Long?>(null) }
        var description by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Add Manual Transaction", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.accounts.forEach { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.id,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text(acc.name) },
                        )
                    }
                }
                MoneyAmountTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    currency = selectedCurrency,
                    label = "Amount ($selectedCurrency)",
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Direction.entries.forEach { d ->
                        FilterChip(selected = direction == d, onClick = { direction = d }, label = { Text(d.name) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DomainTransactionType.entries.filter { it != DomainTransactionType.ADJUSTMENT }.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name) })
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Merchant") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.categories.take(8).forEach { cat ->
                        FilterChip(
                            selected = categoryId == cat.id,
                            onClick = { categoryId = if (categoryId == cat.id) null else cat.id },
                            label = { Text(cat.name) },
                        )
                    }
                }
                Button(
                    enabled = isValidMoneyInput(amountInput, selectedCurrency),
                    onClick = {
                        showCreateSheet = false
                        viewModel.createManualTransaction(selectedAccountId, amountInput, direction, type, categoryId, description, note)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Save Transaction") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityTransactionRow(
    item: ActivityTransactionItem,
    baseCurrency: String,
    onClick: () -> Unit,
) {
    val ext = WtrackTheme.colors
    val tx = item.transaction
    val isPaired = item.transferPair != null
    val title = tx.merchantName ?: tx.description ?: tx.transactionType.name
    val (avatarText, avatarBg, avatarFg) = when {
        tx.status == TransactionStatus.VOIDED -> Triple("×", MaterialTheme.colorScheme.surfaceVariant, ext.mutedText)
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
        tx.status == TransactionStatus.VOIDED -> ext.mutedText
        isPaired -> ext.transfer
        tx.direction == Direction.INFLOW -> ext.inflow
        else -> MaterialTheme.colorScheme.onSurface
    }

    val isUncategorized = item.categoryName.equals("Uncategorized", ignoreCase = true)
    val subtitle = buildString {
        if (isPaired && item.counterpartAccountName != null) {
            val arrow = if (tx.direction == Direction.OUTFLOW) "→" else "←"
            append("${item.accountName} $arrow ${item.counterpartAccountName}")
        } else {
            append(item.accountName)
            if (!isUncategorized) append(" · ${item.categoryName}")
        }
        append(" · ${UiDateHelper.formatTimeOnly(tx.occurredAt)}")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (tx.status == TransactionStatus.VOIDED) ext.mutedText else MaterialTheme.colorScheme.onSurface,
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
            val hasBadges = tx.status == TransactionStatus.VOIDED ||
                isPaired ||
                isUncategorized ||
                !tx.currency.equals(baseCurrency, ignoreCase = true) ||
                tx.transactionType == DomainTransactionType.ADJUSTMENT ||
                item.tags.isNotEmpty()
            if (hasBadges) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (tx.status == TransactionStatus.VOIDED) {
                        StatusPill("VOIDED", MaterialTheme.colorScheme.surfaceVariant, ext.mutedText)
                    }
                    if (isPaired) {
                        StatusPill("Paired ↔", ext.transferContainer, ext.onTransferContainer)
                    }
                    if (isUncategorized && !isPaired && tx.transactionType != DomainTransactionType.ADJUSTMENT) {
                        StatusPill("Uncategorized", ext.warningContainer, ext.onWarningContainer)
                    }
                    if (!tx.currency.equals(baseCurrency, ignoreCase = true)) {
                        StatusPill("Foreign (${tx.currency})")
                    }
                    if (tx.transactionType == DomainTransactionType.ADJUSTMENT) {
                        StatusPill("Adjustment")
                    }
                    item.tags.forEach { tag ->
                        StatusPill(
                            label = "#${tag.name}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleSmall.tabularNums(),
            fontWeight = FontWeight.Bold,
            color = amountColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    viewModel: ActivityViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val ext = WtrackTheme.colors
    val item = state.allItems.firstOrNull { it.transaction.id == transactionId }
        ?: run {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { WtrackTopBar(title = "Transaction Detail", onBack = onBack) },
            ) { inner ->
                Column(modifier = Modifier.padding(inner).padding(20.dp)) {
                    Text("Transaction #$transactionId not found.")
                }
            }
            return
        }

    val tx = item.transaction
    var showEditSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }
    var showTransferPicker by remember { mutableStateOf(false) }
    var showRefundPicker by remember { mutableStateOf(false) }
    var confirmPairedVoid by remember { mutableStateOf(false) }
    var confirmPairedDelete by remember { mutableStateOf(false) }

    val displayTitle = tx.merchantName ?: tx.description ?: tx.transactionType.name
    val isPaired = item.transferPair != null
    val signed = if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor
    val heroAmountColor = when {
        tx.status == TransactionStatus.VOIDED -> ext.mutedText
        isPaired -> ext.transfer
        tx.direction == Direction.INFLOW -> ext.inflow
        else -> MaterialTheme.colorScheme.onSurface
    }
    val heroAvatarBg = when {
        isPaired -> ext.transferContainer
        tx.direction == Direction.INFLOW -> ext.inflowContainer
        else -> ext.warningContainer
    }
    val heroAvatarFg = when {
        isPaired -> ext.onTransferContainer
        tx.direction == Direction.INFLOW -> ext.inflow
        else -> ext.warning
    }
    val heroAvatarText = when {
        isPaired -> "↔"
        tx.direction == Direction.INFLOW -> "↓"
        else -> displayTitle.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "W"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WtrackTopBar(
                title = "Transaction Detail",
                onBack = onBack,
                trailing = {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { showEditSheet = true },
                    ) {
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.actionMessage != null) {
                item {
                    ActionFeedbackBanner(
                        message = state.actionMessage!!,
                        onDismiss = viewModel::clearMessage,
                    )
                }
            }

            // Hero Receipt Card
            item {
                WtrackCard(cornerRadius = 20.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AvatarCircle(
                            text = heroAvatarText,
                            containerColor = heroAvatarBg,
                            contentColor = heroAvatarFg,
                            size = 44.dp,
                        )
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = formatCurrency(signed, tx.currency, signed = true),
                            style = MaterialTheme.typography.headlineMedium.tabularNums(),
                            color = heroAmountColor,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            StatusPill(
                                label = if (tx.status == TransactionStatus.CONFIRMED) "Confirmed" else "VOIDED",
                                containerColor = if (tx.status == TransactionStatus.CONFIRMED) ext.inflowContainer else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (tx.status == TransactionStatus.CONFIRMED) ext.onInflowContainer else MaterialTheme.colorScheme.onErrorContainer,
                            )
                            StatusPill(
                                label = if (tx.parserVersion != null) "Auto · v${tx.parserVersion}" else "Manual",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            StatusPill(
                                label = tx.transactionType.name,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Structured Details Card
            item {
                WtrackCard {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        DetailRow(label = "Account") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ProviderBadgeAvatar(labelOrPackage = item.accountName, size = 18.dp)
                                Text(item.accountName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider(color = ext.divider)
                        DetailRow(label = "Date & Time") {
                            Text(
                                text = UiDateHelper.formatDateTime(tx.occurredAt),
                                style = MaterialTheme.typography.bodyMedium.tabularNums(),
                            )
                        }
                        HorizontalDivider(color = ext.divider)
                        DetailRow(label = "Category") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showCategorySheet = true }
                                    .padding(vertical = 2.dp),
                            ) {
                                Text(item.categoryName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Change ›",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        HorizontalDivider(color = ext.divider)
                        DetailRow(label = "Tags") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                item.tags.forEach { tag ->
                                    StatusPill(
                                        label = "#${tag.name} ×",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable { viewModel.removeTagFromTransaction(tx.id, tag.id) },
                                    )
                                }
                                StatusPill(
                                    label = "+ Tag",
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    borderColor = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { showTagSheet = true },
                                )
                            }
                        }
                        HorizontalDivider(color = ext.divider)
                        DetailRow(label = "Note") {
                            Text(
                                text = tx.note?.takeIf { it.isNotBlank() } ?: "No note (tap Edit)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (tx.note.isNullOrBlank()) ext.mutedText else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (item.transferPair != null) {
                            HorizontalDivider(color = ext.divider)
                            DetailRow(label = "Linked Pair") {
                                Text(
                                    text = "↔ ${item.counterpartAccountName ?: "Account"} (Fee: ${formatCurrency(item.transferPair.feeMinor, tx.currency)})",
                                    style = MaterialTheme.typography.bodySmall.tabularNums(),
                                    fontWeight = FontWeight.SemiBold,
                                    color = ext.transfer,
                                )
                            }
                        }
                        if (tx.providerReference != null) {
                            HorizontalDivider(color = ext.divider)
                            DetailRow(label = "Provider Ref") {
                                Text(tx.providerReference, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Auto-Rule Shortcut Card
            if (tx.transactionType != DomainTransactionType.ADJUSTMENT) {
                item {
                    WtrackCard(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        onClick = { showCategorySheet = true },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (tx.appliedRuleId != null) "Auto-rule applied" else "Category & Auto-Rule",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = "Assign \"$displayTitle\" → ${item.categoryName} for future captures",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "Configure ›",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Captured Evidence Card (TX-03, TX-05)
            if (tx.retainedPayloadPreview != null || tx.originalSnapshot != null) {
                item {
                    WtrackCard(borderColor = ext.infoTintBorder) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text("Captured Evidence", style = MaterialTheme.typography.labelLarge)
                                }
                                StatusPill(
                                    label = if (tx.parserVersion != null) "Parser v${tx.parserVersion}" else "Captured",
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (tx.retainedPayloadPreview != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "\"${tx.retainedPayloadPreview}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                            }
                            tx.originalSnapshot?.let { snap ->
                                val unchanged = snap.amountMinor == tx.amountMinor &&
                                    snap.direction == tx.direction &&
                                    snap.transactionType == tx.transactionType &&
                                    snap.accountId == tx.accountId
                                val snapSigned = if (snap.direction == Direction.INFLOW) snap.amountMinor else -snap.amountMinor
                                Text(
                                    text = "Original captured: ${formatCurrency(snapSigned, snap.currency, signed = true)} · ${snap.direction.name}" +
                                        if (unchanged) " · Unchanged ✓" else " · Edited",
                                    style = MaterialTheme.typography.labelSmall.tabularNums(),
                                    color = if (unchanged) ext.inflow else ext.warning,
                                )
                            }
                        }
                    }
                }
            }

            // Transfer / Refund & Void Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (item.transferPair == null && tx.status == TransactionStatus.CONFIRMED && tx.transactionType != DomainTransactionType.ADJUSTMENT) {
                            OutlinedButton(
                                onClick = { showTransferPicker = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text("Link Transfer Pair") }
                        } else if (item.transferPair != null) {
                            OutlinedButton(
                                onClick = { viewModel.unlinkTransfer(item.transferPair.id) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text("Unlink Transfer") }
                        }

                        if (tx.transactionType == DomainTransactionType.REFUND && tx.direction == Direction.INFLOW) {
                            OutlinedButton(
                                onClick = { showRefundPicker = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text(if (tx.relatedTransactionId == null) "Link Original" else "Change Original") }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (tx.status == TransactionStatus.CONFIRMED) {
                            OutlinedButton(
                                onClick = {
                                    if (item.transferPair != null) {
                                        confirmPairedVoid = true
                                    } else {
                                        viewModel.voidTransaction(tx.id)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                            ) { Text("Void Transaction") }
                        } else {
                            Button(
                                onClick = { viewModel.restoreTransaction(tx.id) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text("Restore Transaction") }
                        }
                        if (tx.eventId == null) {
                            OutlinedButton(
                                onClick = {
                                    if (item.transferPair != null) {
                                        confirmPairedDelete = true
                                    } else {
                                        viewModel.deleteManual(tx.id)
                                        onBack()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                            ) { Text("Delete Manual") }
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet) {
        var editAccountId by remember(tx.id, tx.accountId) { mutableLongStateOf(tx.accountId) }
        var editDirection by remember(tx.id, tx.direction) { mutableStateOf(tx.direction) }
        var editDateTime by remember(tx.id, tx.occurredAt) { mutableStateOf(UiDateHelper.formatEditableDateTime(tx.occurredAt)) }
        var editAmount by remember(tx.id, tx.amountMinor) { mutableStateOf(MoneyFormatter.formatForInput(tx.amountMinor, tx.currency)) }
        var editDescription by remember(tx.id, tx.description) { mutableStateOf(tx.description.orEmpty()) }
        var editNote by remember(tx.id, tx.note) { mutableStateOf(tx.note.orEmpty()) }
        var editType by remember(tx.id, tx.transactionType) { mutableStateOf(tx.transactionType) }

        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Edit Transaction", style = MaterialTheme.typography.titleMedium)
                if (state.accounts.isNotEmpty()) {
                    Text("Account", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.accounts.forEach { acc ->
                            FilterChip(
                                selected = editAccountId == acc.id,
                                onClick = { editAccountId = acc.id },
                                label = { Text(acc.name) },
                            )
                        }
                    }
                }
                MoneyAmountTextField(
                    value = editAmount,
                    onValueChange = { editAmount = it },
                    currency = tx.currency,
                    label = "Amount (${tx.currency})",
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editDateTime,
                    onValueChange = { editDateTime = it },
                    label = { Text("Date & time (yyyy-MM-dd HH:mm)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Direction.entries.forEach { d ->
                        FilterChip(selected = editDirection == d, onClick = { editDirection = d }, label = { Text(d.name) })
                    }
                }
                if (tx.transactionType != DomainTransactionType.ADJUSTMENT) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DomainTransactionType.entries.filter { it != DomainTransactionType.ADJUSTMENT }.forEach { t ->
                            FilterChip(selected = editType == t, onClick = { editType = t }, label = { Text(t.name) })
                        }
                    }
                }
                OutlinedTextField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    label = { Text("Description") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editNote,
                    onValueChange = { editNote = it },
                    label = { Text("Note") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = isValidMoneyInput(editAmount, tx.currency),
                    onClick = {
                        val parsedDate = UiDateHelper.parseEditableDateTime(editDateTime) ?: tx.occurredAt
                        showEditSheet = false
                        viewModel.updateTransaction(
                            tx = tx,
                            accountId = editAccountId,
                            amountMajorInput = editAmount,
                            direction = editDirection,
                            type = editType,
                            occurredAtMillis = parsedDate,
                            categoryId = tx.categoryId,
                            categoryChangedByUser = false,
                            description = editDescription,
                            note = editNote,
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Save Changes") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showCategorySheet) {
        var editCategoryId by remember(tx.id, tx.categoryId) { mutableStateOf(tx.categoryId) }
        var saveRuleChecked by remember(tx.id) { mutableStateOf(true) }
        var backfillChecked by remember(tx.id) { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Category & Auto-Rule", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.categories.forEach { cat ->
                        FilterChip(
                            selected = editCategoryId == cat.id,
                            onClick = { editCategoryId = cat.id },
                            label = { Text(cat.name) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveRuleChecked, onCheckedChange = { saveRuleChecked = it })
                    Text("Remember as rule for future matching transactions", style = MaterialTheme.typography.bodySmall)
                }
                if (saveRuleChecked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = backfillChecked, onCheckedChange = { backfillChecked = it })
                        Text("Backfill uncategorized transactions for this merchant", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(
                    onClick = {
                        showCategorySheet = false
                        viewModel.setCategoryAndOptionalRule(tx, editCategoryId, saveRuleChecked, backfillChecked)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Apply Category") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showTagSheet) {
        var newTagInput by remember { mutableStateOf("") }
        val availableSuggestions = state.tags.filter { existing -> item.tags.none { it.id == existing.id } }

        ModalBottomSheet(
            onDismissRequest = { showTagSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Manage Tags (TX-06)", style = MaterialTheme.typography.titleMedium)
                if (availableSuggestions.isNotEmpty()) {
                    Text("Existing tags", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableSuggestions.forEach { suggestion ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    viewModel.addTagToTransaction(tx.id, suggestion.name)
                                    showTagSheet = false
                                },
                                label = { Text("+ #${suggestion.name}") },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        label = { Text("New tag (e.g., work, travel)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = newTagInput.isNotBlank(),
                        onClick = {
                            val tag = newTagInput
                            newTagInput = ""
                            showTagSheet = false
                            viewModel.addTagToTransaction(tx.id, tag)
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Add") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (confirmPairedVoid) {
        AlertDialog(
            onDismissRequest = { confirmPairedVoid = false },
            title = { Text("Unlink Transfer & Void?") },
            text = { Text("This transaction belongs to a confirmed transfer pair. Voiding it will unlink the transfer pair (PRD section 4.5).") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        confirmPairedVoid = false
                        viewModel.voidTransaction(tx.id)
                    },
                ) { Text("Unlink & Void") }
            },
            dismissButton = { TextButton(onClick = { confirmPairedVoid = false }) { Text("Cancel") } },
        )
    }

    if (confirmPairedDelete) {
        AlertDialog(
            onDismissRequest = { confirmPairedDelete = false },
            title = { Text("Unlink Transfer & Delete?") },
            text = { Text("This manual transaction belongs to a confirmed transfer pair. Deleting it will unlink the transfer pair.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        confirmPairedDelete = false
                        viewModel.deleteManual(tx.id)
                        onBack()
                    },
                ) { Text("Unlink & Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmPairedDelete = false }) { Text("Cancel") } },
        )
    }

    if (showTransferPicker) {
        val candidates = state.allItems.filter { candidate ->
            val other = candidate.transaction
            other.id != tx.id &&
                other.accountId != tx.accountId &&
                other.direction != tx.direction &&
                other.status == TransactionStatus.CONFIRMED &&
                other.transactionType != DomainTransactionType.ADJUSTMENT &&
                other.transactionType != DomainTransactionType.REFUND &&
                candidate.transferPair == null &&
                other.currency.equals(tx.currency, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showTransferPicker = false },
            title = { Text("Select Matching Transfer Leg") },
            text = {
                if (candidates.isEmpty()) {
                    Text("No unlinked opposite-direction transactions in ${tx.currency} on another account.")
                } else {
                    LazyColumn {
                        items(candidates, key = { it.transaction.id }) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showTransferPicker = false
                                        if (tx.direction == Direction.OUTFLOW) {
                                            viewModel.linkTransfer(tx.id, c.transaction.id)
                                        } else {
                                            viewModel.linkTransfer(c.transaction.id, tx.id)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${c.accountName} • ${UiDateHelper.formatDate(c.transaction.occurredAt)}", modifier = Modifier.weight(1f))
                                Text(formatCurrency(c.transaction.amountMinor, c.transaction.currency), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTransferPicker = false }) { Text("Close") } },
        )
    }

    if (showRefundPicker) {
        val originals = state.allItems.filter { c ->
            val o = c.transaction
            o.id != tx.id && o.direction == Direction.OUTFLOW && o.status == TransactionStatus.CONFIRMED && o.currency.equals(tx.currency, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showRefundPicker = false },
            title = { Text("Link Refund to Original Purchase") },
            text = {
                LazyColumn {
                    item {
                        TextButton(onClick = {
                            showRefundPicker = false
                            viewModel.linkRefund(tx.id, null)
                        }) { Text("Clear link") }
                    }
                    items(originals, key = { it.transaction.id }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showRefundPicker = false
                                    viewModel.linkRefund(tx.id, c.transaction.id)
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(c.transaction.merchantName ?: c.transaction.description ?: c.accountName, modifier = Modifier.weight(1f))
                            Text(formatCurrency(c.transaction.amountMinor, c.transaction.currency), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showRefundPicker = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = WtrackTheme.colors.mutedText,
            modifier = Modifier.padding(end = 12.dp),
        )
        Box(contentAlignment = Alignment.CenterEnd) {
            content()
        }
    }
}
