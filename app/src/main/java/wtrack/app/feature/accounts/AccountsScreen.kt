package wtrack.app.feature.accounts

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.feature.common.CaptureStatusPill
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.formatCurrency

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onOpenDiagnostics: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var correctingAccount by remember { mutableStateOf<Account?>(null) }
    var linkingAccount by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add account")
            }
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

            if (state.activeAccounts.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No accounts configured", style = MaterialTheme.typography.titleMedium)
                            Text("Create your bank, e-wallet, or cash accounts, then link allowlisted notification packages.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(state.activeAccounts, key = { it.account.id }) { item ->
                    val acc = item.account
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(acc.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${acc.provider} • ${acc.accountType.name}", style = MaterialTheme.typography.bodySmall)
                                }
                                CaptureStatusPill(acc.captureStatus)
                            }

                            Text(
                                text = "Estimated balance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatCurrency(acc.estimatedBalanceMinor, acc.currency),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (acc.lastReconciledAt != null) {
                                Text(
                                    text = "Last reconciled: ${UiDateHelper.formatDate(acc.lastReconciledAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (acc.hasPossibleGap) {
                                    StatusPill(
                                        label = "Possible capture gap",
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                                if (acc.unparsedLast30DaysCount > 0) {
                                    StatusPill(label = "${acc.unparsedLast30DaysCount} unparsed (30d)")
                                }
                            }

                            if (acc.captureStatus == CaptureStatus.UNSUPPORTED) {
                                Text(
                                    text = "This allowlisted app has no parser yet (manual entry or optional 14-day M3a sample collection below).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (item.checkpoint != null) {
                                val cp = item.checkpoint
                                Text(
                                    text = "Last reported balance: ${formatCurrency(cp.reportedBalanceMinor, acc.currency)} (drift ${formatCurrency(cp.driftMinor, acc.currency, signed = true)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (cp.driftMinor != 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }

                            if (item.linkedSources.isNotEmpty()) {
                                HorizontalDivider()
                                Text("Linked Sources", style = MaterialTheme.typography.labelLarge)
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
                                                text = ls.link.sourceId + (ls.link.accountHint?.let { " (hint: $it)" } ?: ""),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            if (src != null) {
                                                Text(
                                                    text = if (src.isAllowlisted) "Allowlisted" else "Paused",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (src != null && src.parserVersion == null) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("14d Samples", style = MaterialTheme.typography.labelSmall)
                                                    Switch(
                                                        checked = samplingActive,
                                                        onCheckedChange = { enable -> viewModel.toggleSampleCollection(src.id, enable) },
                                                    )
                                                }
                                            }
                                            TextButton(onClick = { viewModel.unlinkSource(ls.link.id) }) { Text("Unlink") }
                                        }
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { correctingAccount = acc }, modifier = Modifier.weight(1f)) {
                                    Text("Correct Balance")
                                }
                                OutlinedButton(onClick = { linkingAccount = acc }, modifier = Modifier.weight(1f)) {
                                    Text("Link App")
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { editingAccount = acc }) { Text("Edit") }
                                TextButton(onClick = onOpenDiagnostics) { Text("Diagnostics") }
                                TextButton(onClick = { viewModel.archiveAccount(acc.id) }) { Text("Archive") }
                            }
                        }
                    }
                }
            }

            if (state.archivedAccounts.isNotEmpty()) {
                item {
                    Text("Archived Accounts", style = MaterialTheme.typography.titleMedium)
                }
                items(state.archivedAccounts, key = { "arch-${it.account.id}" }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(item.account.name, style = MaterialTheme.typography.bodyLarge)
                                Text(formatCurrency(item.account.estimatedBalanceMinor, item.account.currency), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { viewModel.restoreAccount(item.account.id) }) { Text("Restore") }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AccountEditorDialog(
            title = "New Account",
            initial = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, provider, type, cur, opening ->
                showCreateDialog = false
                viewModel.createAccount(name, provider, type, cur, opening)
            },
        )
    }

    if (editingAccount != null) {
        val acc = editingAccount!!
        AccountEditorDialog(
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
        val acc = correctingAccount!!
        var actualInput by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { correctingAccount = null },
            title = { Text("Correct Balance — ${acc.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Current estimate: ${formatCurrency(acc.estimatedBalanceMinor, acc.currency)}.\n" +
                            "An adjustment updates this account's balance without changing income or spending totals (ACC-08).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = actualInput,
                        onValueChange = { actualInput = it },
                        label = { Text("Actual balance (${acc.currency})") },
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
                }
            },
            confirmButton = {
                Button(onClick = {
                    correctingAccount = null
                    viewModel.correctBalance(acc.id, acc.currency, actualInput, note)
                }) { Text("Save Adjustment") }
            },
            dismissButton = { TextButton(onClick = { correctingAccount = null }) { Text("Cancel") } },
        )
    }

    if (linkingAccount != null) {
        val acc = linkingAccount!!
        var pkg by remember { mutableStateOf("") }
        var label by remember { mutableStateOf("") }
        var hint by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { linkingAccount = null },
            title = { Text("Allowlist & Link Source") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Candidate packages (tap to prefill, verify actual installed ID on device):", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    OutlinedTextField(
                        value = pkg,
                        onValueChange = { pkg = it },
                        label = { Text("Android package name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = hint,
                        onValueChange = { hint = it },
                        label = { Text("Masked account hint (optional, e.g. last 4 or pocket)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    linkingAccount = null
                    viewModel.linkSource(acc.id, pkg, label, hint.ifBlank { null })
                }) { Text("Allowlist & Link") }
            },
            dismissButton = { TextButton(onClick = { linkingAccount = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountEditorDialog(
    title: String,
    initial: Account?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, AccountType, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var provider by remember { mutableStateOf(initial?.provider.orEmpty()) }
    var type by remember { mutableStateOf(initial?.accountType ?: AccountType.BANK) }
    var currency by remember { mutableStateOf(initial?.currency ?: "IDR") }
    var opening by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = provider, onValueChange = { provider = it }, label = { Text("Provider") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name) })
                    }
                }
                OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("ISO Currency (e.g., IDR)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (initial == null) {
                    OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Opening balance (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name, provider, type, currency, opening) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
