package wtrack.app.feature.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.formatCurrency
import wtrack.integration.Direction

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onOpenTransaction: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (state.accounts.isNotEmpty()) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add manual transaction")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.actionMessage != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(state.actionMessage!!, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = viewModel::clearMessage) { Text("OK") }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = { q -> viewModel.updateFilter { it.copy(query = q) } },
                    label = { Text("Search merchant, note, category, or account") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityDateFilter.entries.forEach { range ->
                            FilterChip(
                                selected = state.filter.dateFilter == range,
                                onClick = { viewModel.updateFilter { it.copy(dateFilter = range) } },
                                label = { Text(range.label) },
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.filter.uncategorizedOnly,
                            onClick = { viewModel.updateFilter { it.copy(uncategorizedOnly = !it.uncategorizedOnly) } },
                            label = { Text("Uncategorized") },
                        )
                        FilterChip(
                            selected = state.filter.showVoided,
                            onClick = { viewModel.updateFilter { it.copy(showVoided = !it.showVoided) } },
                            label = { Text("Include Voided") },
                        )
                        state.accounts.forEach { acc ->
                            FilterChip(
                                selected = state.filter.accountId == acc.id,
                                onClick = {
                                    viewModel.updateFilter {
                                        it.copy(accountId = if (it.accountId == acc.id) null else acc.id)
                                    }
                                },
                                label = { Text(acc.name) },
                            )
                        }
                    }
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.categories.take(8).forEach { cat ->
                            FilterChip(
                                selected = state.filter.categoryId == cat.id,
                                onClick = {
                                    viewModel.updateFilter {
                                        it.copy(categoryId = if (it.categoryId == cat.id) null else cat.id)
                                    }
                                },
                                label = { Text(cat.name) },
                            )
                        }
                    }
                }
            }

            // Read-only derived transfer fee rows (TRF-04)
            if (state.derivedFees.isNotEmpty()) {
                item {
                    Text("Derived Transfer Fees (Read-Only)", style = MaterialTheme.typography.labelLarge)
                }
                items(state.derivedFees, key = { "fee-${it.transferPairId}" }) { fee ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenTransaction(fee.outgoingTransactionId) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Transfer Fee (Derived)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(UiDateHelper.formatDateTime(fee.occurredAt), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = formatCurrency(-fee.amountMinor, fee.currency, signed = true),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            if (state.items.isEmpty()) {
                item {
                    Text(
                        text = "No matching transactions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.items, key = { it.transaction.id }) { item ->
                    val tx = item.transaction
                    val signed = if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenTransaction(tx.id) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = tx.merchantName ?: tx.description ?: tx.transactionType.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${item.accountName} • ${item.categoryName} • ${UiDateHelper.formatDateTime(tx.occurredAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (tx.status == TransactionStatus.VOIDED) StatusPill("VOIDED")
                                    if (item.transferPair != null) StatusPill("Paired ↔ ${item.counterpartAccountName ?: ""}")
                                    if (tx.currency != state.baseCurrency) StatusPill("Foreign (${tx.currency})")
                                    if (tx.transactionType == DomainTransactionType.ADJUSTMENT) StatusPill("Adjustment")
                                }
                            }
                            Text(
                                text = formatCurrency(signed, tx.currency, signed = true),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var selectedAccountId by remember { mutableLongStateOf(state.accounts.first().id) }
        var amountInput by remember { mutableStateOf("") }
        var direction by remember { mutableStateOf(Direction.OUTFLOW) }
        var type by remember { mutableStateOf(DomainTransactionType.PURCHASE) }
        var categoryId by remember { mutableStateOf<Long?>(null) }
        var description by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Add Manual Transaction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.accounts.forEach { acc ->
                            FilterChip(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text(acc.name) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Amount") },
                        singleLine = true,
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        singleLine = true,
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
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCreateDialog = false
                    viewModel.createManualTransaction(selectedAccountId, amountInput, direction, type, categoryId, description, note)
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
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
    val item = state.allItems.firstOrNull { it.transaction.id == transactionId }
        ?: run {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Transaction Detail") },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                        },
                    )
                },
            ) { inner ->
                Column(modifier = Modifier.padding(inner).padding(20.dp)) {
                    Text("Transaction #$transactionId not found.")
                }
            }
            return
        }

    val tx = item.transaction
    var editAccountId by remember(tx.id, tx.accountId) { mutableLongStateOf(tx.accountId) }
    var editDirection by remember(tx.id, tx.direction) { mutableStateOf(tx.direction) }
    var editDateTime by remember(tx.id, tx.occurredAt) {
        mutableStateOf(UiDateHelper.formatEditableDateTime(tx.occurredAt))
    }
    var editAmount by remember(tx.id, tx.amountMinor) {
        mutableStateOf(MoneyFormatter.formatForCsv(tx.amountMinor, tx.currency))
    }
    var editDescription by remember(tx.id, tx.description) { mutableStateOf(tx.description.orEmpty()) }
    var editNote by remember(tx.id, tx.note) { mutableStateOf(tx.note.orEmpty()) }
    var editType by remember(tx.id, tx.transactionType) { mutableStateOf(tx.transactionType) }
    var editCategoryId by remember(tx.id, tx.categoryId) { mutableStateOf(tx.categoryId) }
    var saveRuleChecked by remember(tx.id) { mutableStateOf(true) }
    var backfillChecked by remember(tx.id) { mutableStateOf(false) }
    var showTransferPicker by remember { mutableStateOf(false) }
    var showRefundPicker by remember { mutableStateOf(false) }
    var confirmPairedVoid by remember { mutableStateOf(false) }
    var confirmPairedDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.actionMessage != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(state.actionMessage!!, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = viewModel::clearMessage) { Text("OK") }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = formatCurrency(
                                if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor,
                                tx.currency,
                                signed = true,
                            ),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${item.accountName} • ${UiDateHelper.formatDateTime(tx.occurredAt)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Type: ${tx.transactionType.name} | Status: ${tx.status.name} | Provenance: parser v${tx.parserVersion ?: "Manual"}", style = MaterialTheme.typography.bodySmall)
                        if (tx.providerReference != null) {
                            Text("Provider ref: ${tx.providerReference}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (item.transferPair != null) {
                            Text(
                                text = "Linked transfer ↔ ${item.counterpartAccountName ?: "Account"} (Derived fee: ${formatCurrency(item.transferPair.feeMinor, tx.currency)})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (tx.retainedPayloadPreview != null) {
                            Text("Verification text: ${tx.retainedPayloadPreview}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (tx.originalSnapshot != null) {
                            val snap = tx.originalSnapshot
                            Text(
                                text = "Original captured: ${formatCurrency(snap.amountMinor, snap.currency)} (${snap.direction.name} ${snap.transactionType.name})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Edit Fields", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                        OutlinedTextField(
                            value = editAmount,
                            onValueChange = { editAmount = it },
                            label = { Text("Amount (${tx.currency})") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = editDateTime,
                            onValueChange = { editDateTime = it },
                            label = { Text("Date & time (yyyy-MM-dd HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Direction.entries.forEach { d ->
                                FilterChip(selected = editDirection == d, onClick = { editDirection = d }, label = { Text(d.name) })
                            }
                        }
                        OutlinedTextField(
                            value = editDescription,
                            onValueChange = { editDescription = it },
                            label = { Text("Description") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = editNote,
                            onValueChange = { editNote = it },
                            label = { Text("Note") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (tx.transactionType != DomainTransactionType.ADJUSTMENT) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DomainTransactionType.entries.filter { it != DomainTransactionType.ADJUSTMENT }.forEach { t ->
                                    FilterChip(selected = editType == t, onClick = { editType = t }, label = { Text(t.name) })
                                }
                            }
                        }
                        Button(
                            onClick = {
                                val parsedDate = UiDateHelper.parseEditableDateTime(editDateTime) ?: tx.occurredAt
                                viewModel.updateTransaction(
                                    tx = tx,
                                    accountId = editAccountId,
                                    amountMajorInput = editAmount,
                                    direction = editDirection,
                                    type = editType,
                                    occurredAtMillis = parsedDate,
                                    categoryId = editCategoryId,
                                    categoryChangedByUser = editCategoryId != tx.categoryId,
                                    description = editDescription,
                                    note = editNote,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Save Changes") }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Category & Auto-Rule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                        OutlinedButton(
                            onClick = {
                                viewModel.setCategoryAndOptionalRule(tx, editCategoryId, saveRuleChecked, backfillChecked)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Apply Category") }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.transferPair == null && tx.status == TransactionStatus.CONFIRMED && tx.transactionType != DomainTransactionType.ADJUSTMENT) {
                        OutlinedButton(onClick = { showTransferPicker = true }, modifier = Modifier.weight(1f)) {
                            Text("Link Transfer Pair")
                        }
                    } else if (item.transferPair != null) {
                        OutlinedButton(onClick = { viewModel.unlinkTransfer(item.transferPair.id) }, modifier = Modifier.weight(1f)) {
                            Text("Unlink Transfer")
                        }
                    }

                    if (tx.transactionType == DomainTransactionType.REFUND && tx.direction == Direction.INFLOW) {
                        OutlinedButton(onClick = { showRefundPicker = true }, modifier = Modifier.weight(1f)) {
                            Text(if (tx.relatedTransactionId == null) "Link Original" else "Change Linked Original")
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tx.status == TransactionStatus.CONFIRMED) {
                        OutlinedButton(
                            onClick = {
                                if (item.transferPair != null) {
                                    confirmPairedVoid = true
                                } else {
                                    viewModel.voidTransaction(tx.id)
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                        ) { Text("Void Transaction") }
                    } else {
                        Button(onClick = { viewModel.restoreTransaction(tx.id) }, modifier = Modifier.weight(1f)) {
                            Text("Restore Transaction")
                        }
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                        ) { Text("Delete Manual") }
                    }
                }
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
                                modifier = Modifier.fillMaxWidth().clickable {
                                    showTransferPicker = false
                                    if (tx.direction == Direction.OUTFLOW) {
                                        viewModel.linkTransfer(tx.id, c.transaction.id)
                                    } else {
                                        viewModel.linkTransfer(c.transaction.id, tx.id)
                                    }
                                }.padding(vertical = 8.dp),
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
                            modifier = Modifier.fillMaxWidth().clickable {
                                showRefundPicker = false
                                viewModel.linkRefund(tx.id, c.transaction.id)
                            }.padding(vertical = 8.dp),
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
