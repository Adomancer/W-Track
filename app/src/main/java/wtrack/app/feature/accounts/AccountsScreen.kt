package wtrack.app.feature.accounts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wtrack.app.R
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.tabularNums
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.CurrencyRegistry
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.MoneyAmountTextField
import wtrack.app.feature.common.ProviderPresets
import wtrack.app.feature.common.V2CircleIconButton
import wtrack.app.feature.common.V2SettingRow
import wtrack.app.feature.common.V2StatusDot
import wtrack.app.feature.common.formatCurrency
import wtrack.app.feature.common.isValidMoneyInput
import wtrack.app.feature.common.labelRes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onOpenDiagnostics: () -> Unit,
    onOpenCustomParsers: (String?) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ext = WtrackTheme.colors
    var showCreateSheet by remember { mutableStateOf(false) }
    var selectedAccountForActions by remember { mutableStateOf<AccountCardUiModel?>(null) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var correctingAccount by remember { mutableStateOf<AccountCardUiModel?>(null) }
    var linkingAccount by remember { mutableStateOf<Account?>(null) }
    var showArchivedSheet by remember { mutableStateOf(false) }

    val (primaryCurrency, primaryCurrencyTotalMinor) = resolvePrimaryBalanceSummary(
        activeAccounts = state.activeAccounts,
        baseCurrency = state.baseCurrency,
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        if (state.actionMessage != null) {
            item(key = "msg") {
                ActionFeedbackBanner(
                    message = state.actionMessage!!,
                    onDismiss = viewModel::clearMessage,
                )
            }
        }

        // Top Bar
        item(key = "top-bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.accounts_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                V2CircleIconButton(
                    icon = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.accounts_add),
                    onClick = { showCreateSheet = true },
                )
            }
        }

        // Block 1: ONLY dark elevated card in the app
        item(key = "total-balance-card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ext.totalBalanceCardBg)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.accounts_total_balance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.totalBalanceCardMuted,
                    )
                    Text(
                        text = formatCurrency(primaryCurrencyTotalMinor, primaryCurrency),
                        style = MaterialTheme.typography.displaySmall.tabularNums(),
                        color = Color.White,
                    )
                }
            }
        }

        // Block 2: Plain list of name + balance with 6px status dot
        item(key = "accounts-list") {
            if (state.activeAccounts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.accounts_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.accounts_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { showCreateSheet = true },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(stringResource(R.string.accounts_add))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    state.activeAccounts.forEachIndexed { idx, item ->
                        val acc = item.account
                        val needsCheck = acc.hasPossibleGap ||
                            acc.captureStatus == CaptureStatus.ATTENTION ||
                            (item.checkpoint?.driftMinor ?: 0L) != 0L
                        val dotColor = if (needsCheck) ext.warning else ext.inflow

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { selectedAccountForActions = item },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                V2StatusDot(color = dotColor)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = acc.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (needsCheck) {
                                        Text(
                                            text = stringResource(R.string.accounts_needs_check),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = ext.warning,
                                        )
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatCurrency(acc.estimatedBalanceMinor, acc.currency),
                                    style = MaterialTheme.typography.titleMedium.tabularNums(),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = ext.mutedText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (idx < state.activeAccounts.lastIndex) {
                            HorizontalDivider(color = ext.divider)
                        }
                    }

                    if (state.archivedAccounts.isNotEmpty()) {
                        HorizontalDivider(color = ext.divider)
                        V2SettingRow(
                            title = stringResource(R.string.accounts_archived_title),
                            trailingText = "${state.archivedAccounts.size}",
                            onClick = { showArchivedSheet = true },
                        )
                    }
                }
            }
        }
    }

    // Account Detail / Actions Sheet (one tap deeper)
    if (selectedAccountForActions != null) {
        val item = state.activeAccounts.firstOrNull { it.account.id == selectedAccountForActions!!.account.id }
            ?: selectedAccountForActions!!
        val acc = item.account
        ModalBottomSheet(
            onDismissRequest = { selectedAccountForActions = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(acc.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = formatCurrency(acc.estimatedBalanceMinor, acc.currency),
                        style = MaterialTheme.typography.displaySmall.tabularNums(),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    val providerLabel = acc.provider.ifBlank { stringResource(R.string.label_manual) }
                    val accountTypeLabel = stringResource(acc.accountType.labelRes())
                    Text(
                        text = "$providerLabel · $accountTypeLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = {
                        selectedAccountForActions = null
                        correctingAccount = item
                    },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text(stringResource(R.string.accounts_update_balance), style = MaterialTheme.typography.titleMedium) }

                Column(modifier = Modifier.fillMaxWidth()) {
                    V2SettingRow(
                        title = stringResource(R.string.accounts_edit_account),
                        onClick = {
                            selectedAccountForActions = null
                            editingAccount = acc
                        },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = stringResource(R.string.accounts_link_notification_app),
                        subtitle = if (item.linkedSources.isEmpty()) {
                            stringResource(R.string.accounts_not_linked)
                        } else {
                            stringResource(R.string.accounts_linked_count, item.linkedSources.size)
                        },
                        onClick = {
                            selectedAccountForActions = null
                            linkingAccount = acc
                        },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = stringResource(R.string.accounts_parser_rules),
                        onClick = {
                            selectedAccountForActions = null
                            onOpenCustomParsers(item.linkedSources.firstOrNull()?.link?.sourceId)
                        },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = stringResource(R.string.accounts_auto_capture_health),
                        onClick = {
                            selectedAccountForActions = null
                            onOpenDiagnostics()
                        },
                    )
                }

                if (item.linkedSources.isNotEmpty()) {
                    HorizontalDivider(color = ext.divider)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.accounts_linked_apps),
                            style = MaterialTheme.typography.labelLarge,
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
                                    Text(ls.link.sourceId, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (src != null) {
                                        Text(
                                            text = stringResource(if (src.isAllowlisted) R.string.label_active else R.string.label_paused),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (src != null && src.parserVersion == null) {
                                        Switch(
                                            checked = samplingActive,
                                            onCheckedChange = { enable -> viewModel.toggleSampleCollection(src.id, enable) },
                                        )
                                    }
                                    TextButton(onClick = { viewModel.unlinkSource(ls.link.id) }) {
                                        Text(stringResource(R.string.action_unlink))
                                    }
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        selectedAccountForActions = null
                        viewModel.archiveAccount(acc.id)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.accounts_archive_account), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showArchivedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showArchivedSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.accounts_archived_title), style = MaterialTheme.typography.titleLarge)
                state.archivedAccounts.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.account.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = formatCurrency(item.account.estimatedBalanceMinor, item.account.currency),
                                style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { viewModel.restoreAccount(item.account.id) }) {
                            Text(stringResource(R.string.action_restore))
                        }
                    }
                    if (idx < state.archivedAccounts.lastIndex) {
                        HorizontalDivider(color = ext.divider)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showCreateSheet) {
        AccountEditorBottomSheet(
            title = stringResource(R.string.accounts_new_account),
            initial = null,
            defaultCurrency = state.baseCurrency,
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
            title = stringResource(R.string.accounts_edit_account),
            initial = acc,
            defaultCurrency = state.baseCurrency,
            onDismiss = { editingAccount = null },
            onConfirm = { name, provider, type, cur, _ ->
                editingAccount = null
                viewModel.updateAccount(acc.id, name, provider, type, cur)
            },
        )
    }

    // V2 Minimal Screen 09: Update / Reconcile Balance
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
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.accounts_update_balance), style = MaterialTheme.typography.titleLarge)
                    val currentText = stringResource(
                        R.string.accounts_current_balance,
                        formatCurrency(acc.estimatedBalanceMinor, acc.currency),
                    )
                    Text(
                        text = "${acc.name} · $currentText",
                        style = MaterialTheme.typography.bodyMedium.tabularNums(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                MoneyAmountTextField(
                    value = actualInput,
                    onValueChange = { actualInput = it },
                    currency = acc.currency,
                    label = stringResource(R.string.accounts_actual_balance_label, acc.currency),
                    allowZero = true,
                    allowNegative = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (deltaMinor != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.accounts_adjustment),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (deltaMinor == 0L) {
                                    stringResource(R.string.accounts_no_change)
                                } else {
                                    formatCurrency(deltaMinor, acc.currency, signed = true)
                                },
                                style = MaterialTheme.typography.titleMedium.tabularNums(),
                                color = if (deltaMinor == 0L) ext.inflow else ext.warning,
                            )
                        }
                        Text(
                            text = stringResource(R.string.accounts_adjustment_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = ext.divider)
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.label_note_optional)) },
                    placeholder = { Text(stringResource(R.string.accounts_adjustment_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        enabled = isValidMoneyInput(actualInput, acc.currency, allowZero = true, allowNegative = true),
                        onClick = {
                            correctingAccount = null
                            viewModel.correctBalance(acc.id, acc.currency, actualInput, note)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(999.dp),
                    ) { Text(stringResource(R.string.accounts_save_balance), style = MaterialTheme.typography.titleMedium) }
                    TextButton(onClick = { correctingAccount = null }) {
                        Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.accounts_link_notification_app), style = MaterialTheme.typography.titleLarge)
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ProviderPresets.commonWalletPackages.forEach { preset ->
                        FilterChip(
                            selected = pkg == preset.packageName,
                            onClick = {
                                pkg = preset.packageName
                                label = preset.displayName
                            },
                            label = { Text(preset.displayName) },
                        )
                    }
                }
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text(stringResource(R.string.accounts_android_package_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.accounts_display_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { Text(stringResource(R.string.accounts_hint_optional)) },
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
                        shape = RoundedCornerShape(999.dp),
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        enabled = pkg.isNotBlank(),
                        onClick = {
                            linkingAccount = null
                            viewModel.linkSource(acc.id, pkg, label, hint.ifBlank { null })
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                    ) { Text(stringResource(R.string.accounts_link_app_button)) }
                }
            }
        }
    }
}

internal fun resolvePrimaryBalanceSummary(
    activeAccounts: List<AccountCardUiModel>,
    baseCurrency: String,
): Pair<String, Long> {
    val normalizedBase = baseCurrency.trim().uppercase().ifEmpty { CurrencyRegistry.DEFAULT_CURRENCY }
    if (activeAccounts.isEmpty()) return normalizedBase to 0L
    val primaryCurrency = if (activeAccounts.any { it.account.currency.equals(normalizedBase, ignoreCase = true) }) {
        normalizedBase
    } else {
        activeAccounts.first().account.currency
    }
    val totalMinor = activeAccounts
        .filter { it.account.currency.equals(primaryCurrency, ignoreCase = true) }
        .sumOf { it.account.estimatedBalanceMinor }
    return primaryCurrency to totalMinor
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AccountEditorBottomSheet(
    title: String,
    initial: Account?,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, AccountType, String, String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var provider by remember(initial) { mutableStateOf(initial?.provider.orEmpty()) }
    var type by remember(initial) { mutableStateOf(initial?.accountType ?: AccountType.BANK) }
    var currency by remember(initial, defaultCurrency) { mutableStateOf(initial?.currency ?: defaultCurrency) }
    var opening by remember(initial) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Text(title, style = MaterialTheme.typography.titleLarge)

            if (initial == null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ProviderPresets.quickAccountProviders.forEach { preset ->
                        FilterChip(
                            selected = provider.equals(preset.displayName, ignoreCase = true),
                            onClick = {
                                provider = preset.displayName
                                type = preset.accountType
                                if (name.isBlank()) name = preset.displayName
                            },
                            label = { Text(preset.displayName) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.onboarding_account_name)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text(stringResource(R.string.accounts_provider_label)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AccountType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(stringResource(t.labelRes())) },
                    )
                }
            }

            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.uppercase() },
                label = { Text(stringResource(R.string.accounts_currency_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (initial == null) {
                MoneyAmountTextField(
                    value = opening,
                    onValueChange = { opening = it },
                    currency = currency,
                    label = stringResource(R.string.onboarding_opening_balance),
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
                    shape = RoundedCornerShape(999.dp),
                ) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    enabled = name.isNotBlank() &&
                        (initial != null || isValidMoneyInput(opening, currency, allowZero = true, allowNegative = true, isOptional = true)),
                    onClick = { onConfirm(name, provider, type, currency, opening) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                ) { Text(stringResource(R.string.onboarding_save_account)) }
            }
        }
    }
}
