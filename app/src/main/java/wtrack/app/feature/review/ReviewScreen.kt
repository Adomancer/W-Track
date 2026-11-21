package wtrack.app.feature.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.providerBrandStyle
import wtrack.app.core.theme.tabularNums
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.NeedsReviewItem
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.MoneyAmountTextField
import wtrack.app.feature.common.ProviderBadgeAvatar
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.common.WtrackTopBar
import wtrack.app.feature.common.isValidMoneyInput
import wtrack.integration.Direction
import wtrack.integration.ReviewReason

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val ext = WtrackTheme.colors
    var resolvingItem by remember { mutableStateOf<NeedsReviewItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WtrackTopBar(
            title = "Needs Review",
            onBack = onBack,
            trailing = {
                StatusPill(
                    label = if (state.items.isEmpty()) "All clear" else "${state.items.size} pending",
                    containerColor = if (state.items.isEmpty()) ext.inflowContainer else ext.warningContainer,
                    contentColor = if (state.items.isEmpty()) ext.onInflowContainer else ext.onWarningContainer,
                    borderColor = if (state.items.isEmpty()) null else ext.warningBorder,
                )
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

            if (state.items.isEmpty()) {
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
                                color = ext.inflowContainer,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = ext.inflow,
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .size(28.dp),
                                )
                            }
                            Text(
                                text = "Needs Review Queue Empty",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Incomplete, ambiguous, unmapped, or OS-redacted notifications appear here before entering your confirmed ledger.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                        }
                    }
                }
            } else {
                item(key = "reassurance") {
                    Surface(
                        color = ext.warningContainer,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ext.warningBorder),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = ext.warning,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Items here are quarantined before entering your confirmed ledger so your balances never double-count or drift.",
                                style = MaterialTheme.typography.labelMedium,
                                color = ext.onWarningContainer,
                            )
                        }
                    }
                }

                items(state.items, key = { it.id }) { item ->
                    val brand = providerBrandStyle(item.packageName)
                    val isErrorReason = item.reviewReason == ReviewReason.PIPELINE_ERROR ||
                        item.reviewReason == ReviewReason.VALIDATION_FAILED
                    val reasonColor = if (isErrorReason) {
                        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        ext.warningContainer to ext.onWarningContainer
                    }
                    val hasDraftDetails = item.draftAmountMinor != null ||
                        item.draftDirection != null ||
                        item.draftType != null ||
                        !item.draftAccountHint.isNullOrBlank()

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
                                    ProviderBadgeAvatar(labelOrPackage = item.packageName, size = 34.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = brand.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${item.packageName} · ${UiDateHelper.formatDateTime(item.postedAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ext.mutedText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                StatusPill(
                                    label = reviewReasonLabel(item.reviewReason),
                                    containerColor = reasonColor.first,
                                    contentColor = reasonColor.second,
                                    borderColor = if (isErrorReason) null else ext.warningBorder,
                                )
                            }

                            if (!item.payloadText.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "\"${item.payloadText}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    )
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "Notification payload withheld/redacted by OS or retention expiry.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ext.mutedText,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    )
                                }
                            }

                            if (hasDraftDetails) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (item.draftAmountMinor != null && item.draftCurrency != null) {
                                        StatusPill(
                                            label = MoneyFormatter.format(item.draftAmountMinor, item.draftCurrency),
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    item.draftDirection?.let {
                                        StatusPill(label = it.name)
                                    }
                                    item.draftType?.let {
                                        StatusPill(label = it.name)
                                    }
                                    item.draftAccountHint?.takeIf { it.isNotBlank() }?.let { hint ->
                                        StatusPill(label = "Hint: $hint")
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { resolvingItem = item },
                                    enabled = state.accounts.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(if (item.draftAmountMinor != null) "Confirm & Save" else "Fill & Save")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.retry(item.id) },
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(" Retry")
                                }
                                TextButton(onClick = { viewModel.ignore(item.id) }) {
                                    Text("Ignore")
                                }
                            }
                        }
                    }
                }

                item(key = "tip") {
                    WtrackCard(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        elevation = 0.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "When resolving unmapped notifications with a pocket or card hint, check \"Remember hint\" so future alerts match automatically.",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText,
                            )
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
        val initialAmount = item.draftAmountMinor?.let { MoneyFormatter.formatForInput(it, initialCurrency) }.orEmpty()
        var selectedAccountId by remember(item.id) { mutableLongStateOf(initialAccountId) }
        var amountInput by remember(item.id) { mutableStateOf(initialAmount) }
        var currencyInput by remember(item.id) { mutableStateOf(initialCurrency) }
        var direction by remember(item.id) { mutableStateOf(item.draftDirection ?: Direction.OUTFLOW) }
        var type by remember(item.id) { mutableStateOf(item.draftType ?: DomainTransactionType.PURCHASE) }
        var description by remember(item.id) { mutableStateOf(item.draftDescription ?: item.draftMerchantRaw.orEmpty()) }
        var categoryId by remember(item.id) { mutableStateOf<Long?>(null) }
        var rememberHint by remember(item.id) { mutableStateOf(!item.draftAccountHint.isNullOrBlank()) }

        ModalBottomSheet(
            onDismissRequest = { resolvingItem = null },
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
                Text("Confirm Transaction Details", style = MaterialTheme.typography.titleMedium)

                if (!item.payloadText.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = item.payloadText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                Text("Account", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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

                MoneyAmountTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    currency = currencyInput,
                    label = "Amount ($currencyInput)",
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Direction", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Direction.entries.forEach { d ->
                        FilterChip(selected = direction == d, onClick = { direction = d }, label = { Text(d.name) })
                    }
                }

                Text("Transaction Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DomainTransactionType.entries.filter { it != DomainTransactionType.ADJUSTMENT }.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name) })
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Merchant") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
                        Text(
                            text = "Remember hint \"${item.draftAccountHint}\" for this account",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { resolvingItem = null },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Cancel") }
                    Button(
                        enabled = isValidMoneyInput(amountInput, currencyInput),
                        onClick = {
                            resolvingItem = null
                            viewModel.saveResolved(
                                item = item,
                                accountId = selectedAccountId,
                                amountMajorInput = amountInput,
                                currency = currencyInput,
                                direction = direction,
                                type = type,
                                description = description,
                                categoryId = categoryId,
                                rememberAccountHint = rememberHint,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Confirm & Save") }
                }
            }
        }
    }
}

private fun reviewReasonLabel(reason: ReviewReason): String = when (reason) {
    ReviewReason.MISSING_AMOUNT -> "Missing Amount"
    ReviewReason.MISSING_DIRECTION -> "Missing Direction"
    ReviewReason.INVALID_VALUE -> "Invalid Value"
    ReviewReason.AMBIGUOUS_FIELD -> "Ambiguous Field"
    ReviewReason.UNKNOWN_FORMAT -> "Unknown Format"
    ReviewReason.ACCOUNT_UNMAPPED -> "Unmapped Account"
    ReviewReason.ACCOUNT_AMBIGUOUS -> "Ambiguous Account"
    ReviewReason.VALIDATION_FAILED -> "Validation Failed"
    ReviewReason.PIPELINE_ERROR -> "Pipeline Error"
}
