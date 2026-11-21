package wtrack.app.feature.review

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import wtrack.app.domain.model.NeedsReviewItem
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.UiDateHelper
import wtrack.integration.Direction

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(viewModel: ReviewViewModel) {
    val state by viewModel.uiState.collectAsState()
    var resolvingItem by remember { mutableStateOf<NeedsReviewItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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

        if (state.items.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Needs Review Queue Empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Incomplete, ambiguous, unmapped, or OS-redacted notifications appear here before entering your confirmed ledger.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(state.items, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.packageName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            StatusPill(item.reviewReason.name)
                        }
                        Text(UiDateHelper.formatDateTime(item.postedAt), style = MaterialTheme.typography.labelSmall)
                        if (!item.payloadText.isNullOrBlank()) {
                            Text(item.payloadText, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("Notification payload withheld/redacted by OS or retention expiry.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (item.draftAmountMinor != null && item.draftCurrency != null) {
                            Text(
                                text = "Draft: ${MoneyFormatter.format(item.draftAmountMinor, item.draftCurrency)} • ${item.draftDirection?.name ?: "?"} • ${item.draftType?.name ?: "?"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { resolvingItem = item },
                                enabled = state.accounts.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text("Resolve & Save") }
                            OutlinedButton(onClick = { viewModel.retry(item.id) }) { Text("Retry") }
                            TextButton(onClick = { viewModel.ignore(item.id) }) { Text("Ignore") }
                        }
                    }
                }
            }
        }
    }

    if (resolvingItem != null && state.accounts.isNotEmpty()) {
        val item = resolvingItem!!
        val initialAccountId = item.draftAccountId ?: state.accounts.first().id
        val initialCurrency = item.draftCurrency
            ?: state.accounts.firstOrNull { it.id == initialAccountId }?.currency
            ?: state.defaultCurrency
        val initialAmount = item.draftAmountMinor?.let { MoneyFormatter.formatForCsv(it, initialCurrency) }.orEmpty()
        var selectedAccountId by remember(item.id) { mutableLongStateOf(initialAccountId) }
        var amountInput by remember(item.id) { mutableStateOf(initialAmount) }
        var currencyInput by remember(item.id) { mutableStateOf(initialCurrency) }
        var direction by remember(item.id) { mutableStateOf(item.draftDirection ?: Direction.OUTFLOW) }
        var type by remember(item.id) { mutableStateOf(item.draftType ?: DomainTransactionType.PURCHASE) }
        var description by remember(item.id) { mutableStateOf(item.draftDescription ?: item.draftMerchantRaw.orEmpty()) }
        var categoryId by remember(item.id) { mutableStateOf<Long?>(null) }
        var rememberHint by remember(item.id) { mutableStateOf(!item.draftAccountHint.isNullOrBlank()) }

        AlertDialog(
            onDismissRequest = { resolvingItem = null },
            title = { Text("Confirm Transaction Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.accounts.forEach { acc ->
                            FilterChip(
                                selected = selectedAccountId == acc.id,
                                onClick = {
                                    selectedAccountId = acc.id
                                    currencyInput = acc.currency
                                },
                                label = { Text(acc.name) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Amount ($currencyInput)") },
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
                        label = { Text("Description") },
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
                    if (!item.draftAccountHint.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = rememberHint, onCheckedChange = { rememberHint = it })
                            Text("Remember hint \"${item.draftAccountHint}\" for this account", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    resolvingItem = null
                    viewModel.saveResolved(item, selectedAccountId, amountInput, currencyInput, direction, type, description, categoryId, rememberHint)
                }) { Text("Confirm & Save") }
            },
            dismissButton = { TextButton(onClick = { resolvingItem = null }) { Text("Cancel") } },
        )
    }
}
