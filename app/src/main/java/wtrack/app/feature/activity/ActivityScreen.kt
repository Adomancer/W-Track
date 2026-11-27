package wtrack.app.feature.activity

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import wtrack.app.R
import wtrack.app.core.i18n.AppLocaleHelper
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.tabularNums
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.TransactionStatus
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.MoneyAmountTextField
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.V2CircleIconButton
import wtrack.app.feature.common.V2StatusDot
import wtrack.app.feature.common.V2TransactionRow
import wtrack.app.feature.common.WtrackTopBar
import wtrack.app.feature.common.formatCurrency
import wtrack.app.feature.common.isValidMoneyInput
import wtrack.app.feature.common.labelRes
import wtrack.app.feature.common.v2CategoryIcon
import wtrack.integration.Direction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onOpenTransaction: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ext = WtrackTheme.colors
    var showCreateSheet by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }

    val hasActiveFilter = state.filter.dateFilter != ActivityDateFilter.ALL ||
        state.filter.accountId != null ||
        state.filter.uncategorizedOnly ||
        state.filter.showVoided ||
        state.filter.type != null ||
        state.filter.categoryId != null ||
        state.filter.tagId != null ||
        state.filter.direction != null

    val groupedByDay = remember(state.items) {
        state.items.groupBy { UiDateHelper.localDateOf(it.transaction.occurredAt) }
            .entries
            .toList()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (state.actionMessage != null) {
            item(key = "msg") {
                ActionFeedbackBanner(
                    message = state.actionMessage!!,
                    onDismiss = viewModel::clearMessage,
                )
            }
        }

        // Top Bar: Title + circular "+" button
        item(key = "top-bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.activity_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (state.accounts.isNotEmpty()) {
                    V2CircleIconButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.activity_cd_add_tx),
                        onClick = { showCreateSheet = true },
                    )
                }
            }
        }

        // Pill search field + circular filter icon
        item(key = "search-filter") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (state.filter.query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.activity_search_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = ext.mutedText,
                            )
                        }
                        BasicTextField(
                            value = state.filter.query,
                            onValueChange = { q -> viewModel.updateFilter { it.copy(query = q) } },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.filter.query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.updateFilter { it.copy(query = "") } },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.activity_cd_clear_search),
                                tint = ext.mutedText,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Box {
                    V2CircleIconButton(
                        icon = Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.activity_cd_filters),
                        onClick = { showFiltersSheet = true },
                        size = 44.dp,
                    )
                    if (hasActiveFilter) {
                        V2StatusDot(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-6).dp, y = 6.dp),
                        )
                    }
                }
            }
        }

        // Day-grouped transaction rows (no tags or chips on rows)
        if (state.items.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.activity_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.activity_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            groupedByDay.forEach { (date, dayItems) ->
                val firstEpoch = dayItems.first().transaction.occurredAt
                val dayNet = dayItems
                    .asSequence()
                    .filter {
                        it.transaction.status == TransactionStatus.CONFIRMED &&
                            it.transaction.currency.equals(state.baseCurrency, ignoreCase = true) &&
                            it.transferPair == null &&
                            it.transaction.transactionType != DomainTransactionType.ADJUSTMENT
                    }
                    .sumOf {
                        if (it.transaction.direction == Direction.INFLOW) it.transaction.amountMinor else -it.transaction.amountMinor
                    }

                item(key = "day-$date") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = UiDateHelper.formatDayGroupHeader(firstEpoch),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (dayNet != 0L) {
                                Text(
                                    text = formatCurrency(dayNet, state.baseCurrency, signed = true),
                                    style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        dayItems.forEachIndexed { idx, item ->
                            val tx = item.transaction
                            val isPaired = item.transferPair != null
                            val isInflow = !isPaired && tx.direction == Direction.INFLOW
                            val title = tx.merchantName ?: tx.description ?: stringResource(tx.transactionType.labelRes())
                            val isUncategorized = !isPaired &&
                                (item.categoryName.isBlank() || item.categoryName.equals("Uncategorized", ignoreCase = true))
                            val displayCategory = when {
                                isPaired -> stringResource(R.string.label_transfer)
                                isUncategorized -> stringResource(R.string.label_needs_category)
                                else -> item.categoryName
                            }
                            val amountText = if (isPaired) {
                                formatCurrency(abs(tx.amountMinor), tx.currency)
                            } else {
                                val signed = if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor
                                formatCurrency(signed, tx.currency, signed = true)
                            }

                            V2TransactionRow(
                                merchant = title,
                                category = displayCategory,
                                amountText = amountText,
                                isInflow = isInflow,
                                isUncategorized = isUncategorized,
                                isVoided = tx.status == TransactionStatus.VOIDED,
                                icon = v2CategoryIcon(
                                    categoryName = displayCategory,
                                    isIncome = isInflow,
                                    isTransfer = isPaired,
                                    isUncategorized = isUncategorized,
                                ),
                                onClick = { onOpenTransaction(tx.id) },
                            )
                            if (idx < dayItems.lastIndex) {
                                HorizontalDivider(color = ext.divider)
                            }
                        }
                    }
                }
            }
        }
    }

    // All filters live neatly one tap deeper in the Filters sheet
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
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.activity_filters_title), style = MaterialTheme.typography.titleLarge)
                    TextButton(
                        onClick = {
                            viewModel.updateFilter {
                                it.copy(
                                    dateFilter = ActivityDateFilter.ALL,
                                    accountId = null,
                                    uncategorizedOnly = false,
                                    showVoided = false,
                                    type = null,
                                    categoryId = null,
                                    tagId = null,
                                    direction = null,
                                )
                            }
                        },
                    ) { Text(stringResource(R.string.action_reset)) }
                }

                Text(stringResource(R.string.label_date), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityDateFilter.entries.forEach { range ->
                        FilterChip(
                            selected = state.filter.dateFilter == range,
                            onClick = { viewModel.updateFilter { it.copy(dateFilter = range) } },
                            label = { Text(stringResource(range.labelRes)) },
                        )
                    }
                }

                if (state.accounts.isNotEmpty()) {
                    Text(stringResource(R.string.label_account), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.filter.accountId == null,
                            onClick = { viewModel.updateFilter { it.copy(accountId = null) } },
                            label = { Text(stringResource(R.string.label_all)) },
                        )
                        state.accounts.forEach { acc ->
                            FilterChip(
                                selected = state.filter.accountId == acc.id,
                                onClick = {
                                    viewModel.updateFilter { it.copy(accountId = if (it.accountId == acc.id) null else acc.id) }
                                },
                                label = { Text(acc.name) },
                            )
                        }
                    }
                }

                Text(stringResource(R.string.label_status), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.filter.uncategorizedOnly,
                        onClick = { viewModel.updateFilter { it.copy(uncategorizedOnly = !it.uncategorizedOnly) } },
                        label = { Text(stringResource(R.string.activity_filter_needs_category)) },
                    )
                    FilterChip(
                        selected = state.filter.showVoided,
                        onClick = { viewModel.updateFilter { it.copy(showVoided = !it.showVoided) } },
                        label = { Text(stringResource(R.string.activity_filter_include_voided)) },
                    )
                }

                Text(stringResource(R.string.label_direction), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Direction.entries.forEach { d ->
                        FilterChip(
                            selected = state.filter.direction == d,
                            onClick = {
                                viewModel.updateFilter { it.copy(direction = if (it.direction == d) null else d) }
                            },
                            label = { Text(stringResource(d.labelRes())) },
                        )
                    }
                }

                Text(stringResource(R.string.label_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DomainTransactionType.entries.forEach { t ->
                        FilterChip(
                            selected = state.filter.type == t,
                            onClick = {
                                viewModel.updateFilter { it.copy(type = if (it.type == t) null else t) }
                            },
                            label = { Text(stringResource(t.labelRes())) },
                        )
                    }
                }

                if (state.categories.isNotEmpty()) {
                    Text(stringResource(R.string.label_category), style = MaterialTheme.typography.labelLarge)
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
                }

                if (state.tags.isNotEmpty()) {
                    Text(stringResource(R.string.label_tags), style = MaterialTheme.typography.labelLarge)
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
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text(stringResource(R.string.action_done), style = MaterialTheme.typography.titleMedium) }
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
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.activity_add_tx_title), style = MaterialTheme.typography.titleLarge)
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
                    label = stringResource(R.string.activity_amount_label, selectedCurrency),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Direction.entries.forEach { d ->
                        FilterChip(
                            selected = direction == d,
                            onClick = { direction = d },
                            label = { Text(stringResource(d.labelRes())) },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DomainTransactionType.entries.filter { it != DomainTransactionType.ADJUSTMENT }.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(stringResource(t.labelRes())) },
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.activity_merchant_desc_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.label_note_optional)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
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
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    viewModel: ActivityViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ext = WtrackTheme.colors
    val activeLocale = AppLocaleHelper.activeLocale(LocalContext.current)
    val item = state.allItems.firstOrNull { it.transaction.id == transactionId }
        ?: run {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { WtrackTopBar(title = "", onBack = onBack) },
            ) { inner ->
                Column(modifier = Modifier.padding(inner).padding(24.dp)) {
                    Text(stringResource(R.string.tx_detail_not_found), style = MaterialTheme.typography.bodyLarge)
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
    var showMoreDetails by remember { mutableStateOf(false) }

    val displayTitle = tx.merchantName ?: tx.description ?: stringResource(tx.transactionType.labelRes())
    val displayAccountName = item.accountName.ifBlank {
        stringResource(R.string.label_account_fallback, tx.accountId)
    }
    val isPaired = item.transferPair != null
    val isInflow = !isPaired && tx.direction == Direction.INFLOW
    val signed = if (tx.direction == Direction.INFLOW) tx.amountMinor else -tx.amountMinor
    val amountText = if (isPaired) {
        formatCurrency(abs(tx.amountMinor), tx.currency)
    } else {
        formatCurrency(signed, tx.currency, signed = true)
    }
    val heroAmountColor = when {
        tx.status == TransactionStatus.VOIDED -> ext.mutedText
        isInflow -> ext.inflow
        else -> MaterialTheme.colorScheme.onBackground
    }
    val isUncategorized = item.categoryName.isBlank() || item.categoryName.equals("Uncategorized", ignoreCase = true)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WtrackTopBar(
                title = "",
                onBack = onBack,
                trailing = {
                    TextButton(onClick = { showEditSheet = true }) {
                        Text(
                            text = stringResource(R.string.action_edit),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (tx.status == TransactionStatus.CONFIRMED) {
                    TextButton(
                        onClick = {
                            if (item.transferPair != null) {
                                confirmPairedVoid = true
                            } else {
                                viewModel.voidTransaction(tx.id)
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.tx_detail_void_action),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    TextButton(onClick = { viewModel.restoreTransaction(tx.id) }) {
                        Text(
                            text = stringResource(R.string.tx_detail_restore_action),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
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

            // Centered Hero: 48dp neutral icon circle, 40sp amount, 16sp merchant
            item(key = "hero") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = v2CategoryIcon(
                                categoryName = item.categoryName,
                                isIncome = isInflow,
                                isTransfer = isPaired,
                                isUncategorized = isUncategorized,
                            ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.displaySmall.tabularNums(),
                        color = heroAmountColor,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // 4 core rows + "More details ›"
            item(key = "details") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DetailRow(label = stringResource(R.string.label_account)) {
                        Text(
                            text = displayAccountName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    HorizontalDivider(color = ext.divider)
                    DetailRow(label = stringResource(R.string.label_date)) {
                        Text(
                            text = UiDateHelper.formatDateTime(tx.occurredAt, locale = activeLocale),
                            style = MaterialTheme.typography.bodyLarge.tabularNums(),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    HorizontalDivider(color = ext.divider)
                    DetailRow(
                        label = stringResource(R.string.label_category),
                        onClick = { showCategorySheet = true },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = if (isUncategorized) stringResource(R.string.label_needs_category) else item.categoryName,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isUncategorized) ext.mutedText else MaterialTheme.colorScheme.onBackground,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = ext.mutedText,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = ext.divider)
                    DetailRow(
                        label = stringResource(R.string.label_note),
                        onClick = { showEditSheet = true },
                    ) {
                        Text(
                            text = tx.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.tx_detail_add_note),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (tx.note.isNullOrBlank()) ext.mutedText else MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(color = ext.divider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { showMoreDetails = !showMoreDetails },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.tx_detail_more_details),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = if (showMoreDetails) {
                                Icons.Outlined.KeyboardArrowDown
                            } else {
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = ext.mutedText,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    // One tap deeper: Evidence, auto-rule, tags, transfer linking, manual delete
                    AnimatedVisibility(visible = showMoreDetails) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (tx.retainedPayloadPreview != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stringResource(R.string.tx_detail_captured_notif),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "\"${tx.retainedPayloadPreview}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                            tx.originalSnapshot?.let { snap ->
                                val unchanged = snap.amountMinor == tx.amountMinor &&
                                    snap.direction == tx.direction &&
                                    snap.transactionType == tx.transactionType &&
                                    snap.accountId == tx.accountId
                                Text(
                                    text = stringResource(
                                        if (unchanged) R.string.tx_detail_original_unchanged else R.string.tx_detail_edited_after_capture,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (unchanged) ext.inflow else ext.warning,
                                )
                            }
                            if (tx.parserVersion != null) {
                                Text(
                                    text = tx.appliedRuleId?.let { ruleId ->
                                        stringResource(R.string.tx_detail_parser_version_with_rule, tx.parserVersion, ruleId)
                                    } ?: stringResource(R.string.tx_detail_parser_version, tx.parserVersion),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ext.mutedText,
                                )
                            }
                            if (item.transferPair != null) {
                                Text(
                                    text = stringResource(
                                        R.string.tx_detail_paired_transfer,
                                        item.counterpartAccountName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.label_account),
                                        formatCurrency(item.transferPair.feeMinor, tx.currency),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.tabularNums(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                item.tags.forEach { tag ->
                                    StatusPill(
                                        label = "#${tag.name} ×",
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable { viewModel.removeTagFromTransaction(tx.id, tag.id) },
                                    )
                                }
                                StatusPill(
                                    label = stringResource(R.string.tx_detail_add_tag_pill),
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { showTagSheet = true },
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (item.transferPair == null && tx.status == TransactionStatus.CONFIRMED && tx.transactionType != DomainTransactionType.ADJUSTMENT) {
                                    OutlinedButton(
                                        onClick = { showTransferPicker = true },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.weight(1f),
                                    ) { Text(stringResource(R.string.tx_detail_link_transfer)) }
                                } else if (item.transferPair != null) {
                                    OutlinedButton(
                                        onClick = { viewModel.unlinkTransfer(item.transferPair.id) },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.weight(1f),
                                    ) { Text(stringResource(R.string.tx_detail_unlink_transfer)) }
                                }

                                if (tx.transactionType == DomainTransactionType.REFUND && tx.direction == Direction.INFLOW) {
                                    OutlinedButton(
                                        onClick = { showRefundPicker = true },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            stringResource(
                                                if (tx.relatedTransactionId == null) {
                                                    R.string.tx_detail_link_original
                                                } else {
                                                    R.string.tx_detail_change_original
                                                },
                                            ),
                                        )
                                    }
                                }
                            }

                            if (tx.eventId == null) {
                                TextButton(
                                    onClick = {
                                        if (item.transferPair != null) {
                                            confirmPairedDelete = true
                                        } else {
                                            viewModel.deleteManual(tx.id)
                                            onBack()
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.tx_detail_delete_manual), color = MaterialTheme.colorScheme.error)
                                }
                            }
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
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.tx_detail_edit_title), style = MaterialTheme.typography.titleLarge)
                if (state.accounts.isNotEmpty()) {
                    Text(stringResource(R.string.label_account), style = MaterialTheme.typography.labelLarge)
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
                    label = stringResource(R.string.activity_amount_label, tx.currency),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editDateTime,
                    onValueChange = { editDateTime = it },
                    label = { Text(stringResource(R.string.tx_detail_datetime_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Direction.entries.forEach { d ->
                        FilterChip(
                            selected = editDirection == d,
                            onClick = { editDirection = d },
                            label = { Text(stringResource(d.labelRes())) },
                        )
                    }
                }
                if (tx.transactionType != DomainTransactionType.ADJUSTMENT) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DomainTransactionType.entries.filter { it != DomainTransactionType.ADJUSTMENT }.forEach { t ->
                            FilterChip(
                                selected = editType == t,
                                onClick = { editType = t },
                                label = { Text(stringResource(t.labelRes())) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    label = { Text(stringResource(R.string.activity_merchant_desc_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editNote,
                    onValueChange = { editNote = it },
                    label = { Text(stringResource(R.string.label_note)) },
                    shape = RoundedCornerShape(14.dp),
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
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text(stringResource(R.string.tx_detail_save_changes), style = MaterialTheme.typography.titleMedium) }
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
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.label_category), style = MaterialTheme.typography.titleLarge)
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
                    Text(stringResource(R.string.tx_detail_remember_rule), style = MaterialTheme.typography.bodyMedium)
                }
                if (saveRuleChecked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = backfillChecked, onCheckedChange = { backfillChecked = it })
                        Text(stringResource(R.string.tx_detail_apply_backfill), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(
                    onClick = {
                        showCategorySheet = false
                        viewModel.setCategoryAndOptionalRule(tx, editCategoryId, saveRuleChecked, backfillChecked)
                    },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium) }
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
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.label_tags), style = MaterialTheme.typography.titleLarge)
                if (availableSuggestions.isNotEmpty()) {
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
                        label = { Text(stringResource(R.string.tx_detail_new_tag)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
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
                        shape = RoundedCornerShape(999.dp),
                    ) { Text(stringResource(R.string.action_add)) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (confirmPairedVoid) {
        AlertDialog(
            onDismissRequest = { confirmPairedVoid = false },
            title = { Text(stringResource(R.string.tx_detail_unlink_void_title)) },
            text = { Text(stringResource(R.string.tx_detail_unlink_void_body)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        confirmPairedVoid = false
                        viewModel.voidTransaction(tx.id)
                    },
                ) { Text(stringResource(R.string.tx_detail_unlink_void_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmPairedVoid = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (confirmPairedDelete) {
        AlertDialog(
            onDismissRequest = { confirmPairedDelete = false },
            title = { Text(stringResource(R.string.tx_detail_unlink_delete_title)) },
            text = { Text(stringResource(R.string.tx_detail_unlink_delete_body)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        confirmPairedDelete = false
                        viewModel.deleteManual(tx.id)
                        onBack()
                    },
                ) { Text(stringResource(R.string.tx_detail_unlink_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmPairedDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
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
            title = { Text(stringResource(R.string.tx_detail_link_transfer_title)) },
            text = {
                if (candidates.isEmpty()) {
                    Text(stringResource(R.string.tx_detail_no_transfer_candidates, tx.currency))
                } else {
                    LazyColumn {
                        items(candidates, key = { it.transaction.id }) { c ->
                            val candidateAccName = c.accountName.ifBlank {
                                stringResource(R.string.label_account_fallback, c.transaction.accountId)
                            }
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
                                Text(
                                    "$candidateAccName · ${UiDateHelper.formatDate(c.transaction.occurredAt, locale = activeLocale)}",
                                    modifier = Modifier.weight(1f),
                                )
                                Text(formatCurrency(c.transaction.amountMinor, c.transaction.currency), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTransferPicker = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    if (showRefundPicker) {
        val originals = state.allItems.filter { c ->
            val o = c.transaction
            o.id != tx.id && o.direction == Direction.OUTFLOW && o.status == TransactionStatus.CONFIRMED && o.currency.equals(tx.currency, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showRefundPicker = false },
            title = { Text(stringResource(R.string.tx_detail_link_refund_title)) },
            text = {
                LazyColumn {
                    item {
                        TextButton(onClick = {
                            showRefundPicker = false
                            viewModel.linkRefund(tx.id, null)
                        }) { Text(stringResource(R.string.tx_detail_clear_link)) }
                    }
                    items(originals, key = { it.transaction.id }) { c ->
                        val fallbackName = c.accountName.ifBlank {
                            stringResource(R.string.label_account_fallback, c.transaction.accountId)
                        }
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
                            Text(c.transaction.merchantName ?: c.transaction.description ?: fallbackName, modifier = Modifier.weight(1f))
                            Text(formatCurrency(c.transaction.amountMinor, c.transaction.currency), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showRefundPicker = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Box(contentAlignment = Alignment.CenterEnd) {
            content()
        }
    }
}
