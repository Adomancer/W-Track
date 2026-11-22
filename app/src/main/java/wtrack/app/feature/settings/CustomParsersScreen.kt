package wtrack.app.feature.settings

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.CustomParserRule
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.repository.CustomParserTestOutcome
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.common.WtrackTopBar
import wtrack.integration.Direction

private val commonWalletQuickPackages = listOf(
    "GoPay / Gojek" to "com.gojek.app",
    "OVO" to "ovo.id",
    "Shopee" to "com.shopee.id",
    "SeaBank" to "id.co.seabank.id",
    "blu" to "com.bcadigital.blu",
    "Livin'" to "id.bmri.livin",
    "BRImo" to "id.co.bri.brimo",
    "wondr BNI" to "id.co.bni.wondr",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomParsersScreen(
    viewModel: CustomParsersViewModel,
    initialSourceId: String? = null,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val ext = WtrackTheme.colors
    var editingRule by remember { mutableStateOf<CustomParserRule?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var handledInitialSource by remember(initialSourceId) { mutableStateOf(false) }

    LaunchedEffect(initialSourceId, state.rules) {
        if (!handledInitialSource && !initialSourceId.isNullOrBlank()) {
            handledInitialSource = true
            if (state.rules.none { it.sourceId == initialSourceId }) {
                editingRule = null
                showEditor = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WtrackTopBar(
            title = "Notification Parsers",
            onBack = onBack,
            trailing = {
                Button(
                    onClick = {
                        editingRule = null
                        showEditor = true
                    },
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Add Pattern")
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.actionMessage != null) {
                item(key = "msg") {
                    ActionFeedbackBanner(
                        message = state.actionMessage!!,
                        onDismiss = viewModel::clearMessage,
                    )
                }
            }

            item(key = "intro") {
                Surface(
                    color = ext.infoTintBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ext.infoTintBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Teach W-Track Any Bank or E-Wallet",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Paste a sample notification here (or check \"Remember this pattern\" when resolving in Needs Review). Matching patterns auto-parse future alerts locally with zero JSON exports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.rules.isEmpty()) {
                item(key = "empty") {
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("No custom parser patterns yet.", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Newly linked apps already auto-detect amounts into Needs Review. Add a pattern here anytime by pasting an example notification.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                            Button(
                                onClick = {
                                    editingRule = null
                                    showEditor = true
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" Paste Sample & Create Pattern")
                            }
                        }
                    }
                }
            } else {
                items(state.rules, key = { it.id }) { rule ->
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = rule.sourceId + (rule.titleContains?.let { " · title=\"$it\"" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ext.mutedText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { viewModel.setEnabled(rule.id, it) },
                                    )
                                    IconButton(
                                        onClick = {
                                            editingRule = rule
                                            showEditor = true
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Edit pattern",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteRule(rule.id) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete pattern",
                                            tint = ext.mutedText,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(
                                    label = rule.direction.name,
                                    containerColor = if (rule.direction == Direction.INFLOW) ext.inflowContainer else MaterialTheme.colorScheme.errorContainer,
                                    contentColor = if (rule.direction == Direction.INFLOW) ext.onInflowContainer else MaterialTheme.colorScheme.onErrorContainer,
                                )
                                StatusPill(label = rule.transactionType.name)
                                StatusPill(label = "${rule.currency} · ${if (rule.numberLocale == CustomNumberLocale.INDONESIAN) "ID" else "EN"}")
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = rule.templatePattern,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val existing = editingRule
        var sourceId by remember(existing) { mutableStateOf(existing?.sourceId ?: initialSourceId.orEmpty()) }
        var ruleName by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
        var titleContains by remember(existing) { mutableStateOf(existing?.titleContains.orEmpty()) }
        var sampleTitle by remember(existing) { mutableStateOf(existing?.titleContains.orEmpty()) }
        var sampleBody by remember(existing) { mutableStateOf("") }
        var templatePattern by remember(existing) { mutableStateOf(existing?.templatePattern.orEmpty()) }
        var direction by remember(existing) { mutableStateOf(existing?.direction ?: Direction.OUTFLOW) }
        var txType by remember(existing) { mutableStateOf(existing?.transactionType ?: DomainTransactionType.PURCHASE) }
        var currency by remember(existing) { mutableStateOf(existing?.currency ?: state.defaultCurrency) }
        var locale by remember(existing) { mutableStateOf(existing?.numberLocale ?: CustomNumberLocale.INDONESIAN) }
        var accountHint by remember(existing) { mutableStateOf(existing?.accountHint.orEmpty()) }

        val testOutcome = remember(
            templatePattern,
            titleContains,
            direction,
            txType,
            currency,
            locale,
            sampleTitle,
            sampleBody,
        ) {
            if (templatePattern.isBlank() && sampleBody.isBlank()) {
                null
            } else {
                viewModel.testRule(
                    templatePattern = templatePattern,
                    titleContains = titleContains.ifBlank { null },
                    direction = direction,
                    transactionType = txType,
                    currency = currency,
                    numberLocale = locale,
                    sampleTitle = sampleTitle.ifBlank { null },
                    sampleBody = sampleBody,
                )
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
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
                Text(
                    text = if (existing == null) "Teach Notification Pattern" else "Edit Notification Pattern",
                    style = MaterialTheme.typography.titleMedium,
                )

                if (state.sources.isNotEmpty() || existing == null) {
                    Text("Source App Package", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.sources.forEach { src ->
                            FilterChip(
                                selected = sourceId == src.packageName,
                                onClick = { sourceId = src.packageName },
                                label = { Text(src.displayName) },
                            )
                        }
                        commonWalletQuickPackages
                            .filter { (_, pkg) -> state.sources.none { it.packageName == pkg } }
                            .take(6)
                            .forEach { (label, pkg) ->
                                FilterChip(
                                    selected = sourceId == pkg,
                                    onClick = { sourceId = pkg },
                                    label = { Text(label) },
                                )
                            }
                    }
                }

                OutlinedTextField(
                    value = sourceId,
                    onValueChange = { sourceId = it },
                    label = { Text("Android package ID (e.g. com.gojek.app)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Paste Sample Notification (Optional)", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = sampleTitle,
                            onValueChange = { sampleTitle = it },
                            label = { Text("Sample title (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = sampleBody,
                            onValueChange = { sampleBody = it },
                            label = { Text("Sample notification text") },
                            placeholder = { Text("e.g. Pembayaran Rp25.000 ke Kopi Kenangan berhasil") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            enabled = sampleBody.isNotBlank(),
                            onClick = {
                                val generated = viewModel.generateFromSample(
                                    sampleBody = sampleBody,
                                    currency = currency,
                                )
                                if (generated != null) {
                                    templatePattern = generated.templatePattern
                                    currency = generated.currency
                                    locale = generated.numberLocale
                                    if (ruleName.isBlank()) {
                                        val pkgShort = sourceId.substringAfterLast('.').ifBlank { "Custom" }
                                        ruleName = "$pkgShort ${direction.name.lowercase()}"
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Auto-Generate Pattern from Sample")
                        }
                    }
                }

                OutlinedTextField(
                    value = templatePattern,
                    onValueChange = { templatePattern = it },
                    label = { Text("Pattern template ({AMOUNT} required)") },
                    placeholder = { Text("Pembayaran {AMOUNT} ke {MERCHANT} berhasil") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Placeholders: {AMOUNT} (required), {MERCHANT} (counterparty), {REF}, {IGNORE}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.mutedText,
                )

                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Pattern name (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = titleContains,
                    onValueChange = { titleContains = it },
                    label = { Text("Title filter (optional, e.g. QRIS)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Direction", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Direction.entries.forEach { d ->
                        FilterChip(
                            selected = direction == d,
                            onClick = {
                                direction = d
                                if (d == Direction.OUTFLOW && txType == DomainTransactionType.REFUND) {
                                    txType = DomainTransactionType.OTHER
                                }
                            },
                            label = { Text(d.name) },
                        )
                    }
                }

                Text("Transaction Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DomainTransactionType.entries
                        .filter { it != DomainTransactionType.ADJUSTMENT }
                        .filter { it != DomainTransactionType.REFUND || direction == Direction.INFLOW }
                        .forEach { t ->
                            FilterChip(selected = txType == t, onClick = { txType = t }, label = { Text(t.name) })
                        }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase() },
                        label = { Text("Currency") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = accountHint,
                        onValueChange = { accountHint = it },
                        label = { Text("Account hint (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                }

                Text("Number Format", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = locale == CustomNumberLocale.INDONESIAN,
                        onClick = { locale = CustomNumberLocale.INDONESIAN },
                        label = { Text("ID (50.000)") },
                    )
                    FilterChip(
                        selected = locale == CustomNumberLocale.ENGLISH,
                        onClick = { locale = CustomNumberLocale.ENGLISH },
                        label = { Text("EN (50,000.00)") },
                    )
                }

                if (testOutcome != null) {
                    val (bgColor, fgColor, previewText) = when (testOutcome) {
                        is CustomParserTestOutcome.Matched -> Triple(
                            ext.inflowContainer,
                            ext.onInflowContainer,
                            "Matched: ${MoneyFormatter.format(testOutcome.amountMinor, testOutcome.currency)} · " +
                                "${testOutcome.direction.name} · ${testOutcome.transactionType.name}" +
                                (testOutcome.merchantRaw?.let { " · Merchant: $it" } ?: ""),
                        )
                        is CustomParserTestOutcome.Rejected -> Triple(
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer,
                            testOutcome.reason,
                        )
                        is CustomParserTestOutcome.NotMatched -> Triple(
                            ext.warningContainer,
                            ext.onWarningContainer,
                            testOutcome.detail,
                        )
                    }
                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = fgColor,
                            modifier = Modifier.padding(10.dp),
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
                        onClick = { showEditor = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Cancel") }
                    Button(
                        enabled = sourceId.isNotBlank() && templatePattern.isNotBlank(),
                        onClick = {
                            showEditor = false
                            viewModel.saveRule(
                                id = existing?.id,
                                sourceId = sourceId,
                                name = ruleName,
                                titleContains = titleContains,
                                templatePattern = templatePattern,
                                direction = direction,
                                transactionType = txType,
                                currency = currency,
                                numberLocale = locale,
                                accountHint = accountHint,
                                enabled = existing?.enabled ?: true,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Save Pattern") }
                }
            }
        }
    }
}
