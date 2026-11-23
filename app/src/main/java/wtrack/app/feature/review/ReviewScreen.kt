package wtrack.app.feature.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.providerBrandStyle
import wtrack.app.core.theme.tabularNums
import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.NeedsReviewItem
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.MoneyAmountTextField
import wtrack.app.feature.common.UiDateHelper
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ext = WtrackTheme.colors
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var resolvingItem by remember { mutableStateOf<NeedsReviewItem?>(null) }

    val safeIndex = if (state.items.isNotEmpty()) currentIndex.coerceIn(0, state.items.lastIndex) else 0
    val currentItem = state.items.getOrNull(safeIndex)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WtrackTopBar(
                title = "Needs review",
                onBack = onBack,
                trailing = {
                    if (state.items.isNotEmpty()) {
                        Text(
                            text = "${safeIndex + 1} of ${state.items.size}",
                            style = MaterialTheme.typography.bodyMedium.tabularNums(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (currentItem != null) {
                val canQuickConfirm = currentItem.draftAmountMinor != null && state.accounts.isNotEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        enabled = state.accounts.isNotEmpty(),
                        onClick = {
                            if (canQuickConfirm) {
                                val targetAccountId = currentItem.draftAccountId ?: state.accounts.first().id
                                val currency = currentItem.draftCurrency
                                    ?: state.accounts.firstOrNull { it.id == targetAccountId }?.currency
                                    ?: state.defaultCurrency
                                val amountInput = MoneyFormatter.formatForInput(currentItem.draftAmountMinor!!, currency)
                                viewModel.saveResolved(
                                    item = currentItem,
                                    accountId = targetAccountId,
                                    amountMajorInput = amountInput,
                                    currency = currency,
                                    direction = currentItem.draftDirection ?: Direction.OUTFLOW,
                                    type = currentItem.draftType ?: DomainTransactionType.PURCHASE,
                                    description = currentItem.draftDescription ?: currentItem.draftMerchantRaw.orEmpty(),
                                    categoryId = null,
                                    rememberAccountHint = !currentItem.draftAccountHint.isNullOrBlank(),
                                    rememberPattern = false,
                                    customTemplatePattern = null,
                                    customNumberLocale = CustomNumberLocale.INDONESIAN,
                                )
                            } else {
                                resolvingItem = currentItem
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(
                            text = if (canQuickConfirm) "Confirm" else "Review details",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    TextButton(
                        onClick = {
                            if (state.items.size > 1) {
                                currentIndex = (safeIndex + 1) % state.items.size
                            } else {
                                viewModel.ignore(currentItem.id)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                    ) {
                        Text(
                            text = if (state.items.size > 1) "Skip" else "Dismiss",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state.actionMessage != null) {
                ActionFeedbackBanner(
                    message = state.actionMessage!!,
                    onDismiss = viewModel::clearMessage,
                )
            }

            if (currentItem == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircleOutline,
                            contentDescription = null,
                            tint = ext.inflow,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = "All caught up",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "No notifications waiting for review.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val brand = providerBrandStyle(currentItem.packageName)
                val currency = currentItem.draftCurrency ?: state.defaultCurrency
                val amountLabel = currentItem.draftAmountMinor?.let { amt ->
                    val signed = if (currentItem.draftDirection == Direction.INFLOW) amt else -amt
                    MoneyFormatter.format(signed, currency, signed = true)
                }

                // Single Item Focus Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, ext.divider),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(
                            text = "${brand.displayName} · ${UiDateHelper.formatDateTime(currentItem.postedAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text = amountLabel ?: "Amount needed",
                            style = if (amountLabel != null) {
                                MaterialTheme.typography.displaySmall.tabularNums()
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                            color = if (currentItem.draftDirection == Direction.INFLOW) {
                                ext.inflow
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        )

                        Text(
                            text = if (!currentItem.payloadText.isNullOrBlank()) {
                                "“${currentItem.payloadText}”"
                            } else {
                                "Notification text hidden by Android. Tap below to enter the amount."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )

                        HorizontalDivider(color = ext.divider)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { resolvingItem = currentItem },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Details & category",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = (currentItem.draftDescription ?: currentItem.draftMerchantRaw ?: "Edit") + " ›",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
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
        val patternSuggestion = remember(item.id, currencyInput) {
            viewModel.generatePatternSuggestion(item, item.draftMerchantRaw ?: description, currencyInput)
        }
        var rememberPattern by remember(item.id) {
            mutableStateOf(item.reviewReason == ReviewReason.UNKNOWN_FORMAT && patternSuggestion != null)
        }
        var patternInput by remember(item.id) {
            mutableStateOf(patternSuggestion?.templatePattern.orEmpty())
        }
        var patternLocale by remember(item.id) {
            mutableStateOf(patternSuggestion?.numberLocale ?: CustomNumberLocale.INDONESIAN)
        }

        ModalBottomSheet(
            onDismissRequest = { resolvingItem = null },
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
                Text("Review details", style = MaterialTheme.typography.titleLarge)

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
                        FilterChip(
                            selected = direction == d,
                            onClick = {
                                direction = d
                                if (d == Direction.OUTFLOW && type == DomainTransactionType.REFUND) {
                                    type = DomainTransactionType.OTHER
                                }
                            },
                            label = { Text(d.name) },
                        )
                    }
                }

                Text("Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DomainTransactionType.entries
                        .filter { it != DomainTransactionType.ADJUSTMENT }
                        .filter { it != DomainTransactionType.REFUND || direction == Direction.INFLOW }
                        .forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name) })
                        }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Merchant / description") },
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
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (item.payloadBody != null || item.payloadText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberPattern,
                            onCheckedChange = { checked ->
                                rememberPattern = checked
                                if (checked && patternInput.isBlank() && patternSuggestion != null) {
                                    patternInput = patternSuggestion.templatePattern
                                    patternLocale = patternSuggestion.numberLocale
                                }
                            },
                        )
                        Text(
                            text = "Remember notification format",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (rememberPattern) {
                        OutlinedTextField(
                            value = patternInput,
                            onValueChange = { patternInput = it },
                            label = { Text("Pattern template") },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = patternLocale == CustomNumberLocale.INDONESIAN,
                                onClick = { patternLocale = CustomNumberLocale.INDONESIAN },
                                label = { Text("ID (50.000)") },
                            )
                            FilterChip(
                                selected = patternLocale == CustomNumberLocale.ENGLISH,
                                onClick = { patternLocale = CustomNumberLocale.ENGLISH },
                                label = { Text("EN (50,000.00)") },
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        enabled = isValidMoneyInput(amountInput, currencyInput) &&
                            (!rememberPattern || patternInput.isNotBlank()),
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
                                rememberPattern = rememberPattern,
                                customTemplatePattern = patternInput.takeIf { rememberPattern },
                                customNumberLocale = patternLocale,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(999.dp),
                    ) { Text("Confirm", style = MaterialTheme.typography.titleMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = {
                            resolvingItem = null
                            viewModel.retry(item.id)
                        }) { Text("Retry") }
                        TextButton(onClick = {
                            resolvingItem = null
                            viewModel.ignore(item.id)
                        }) { Text("Ignore", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
