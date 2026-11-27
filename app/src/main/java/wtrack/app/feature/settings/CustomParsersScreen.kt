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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wtrack.app.R
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.CustomParserRule
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.repository.CustomParserTestOutcome
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.ProviderPresets
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.common.WtrackTopBar
import wtrack.app.feature.common.exampleLabelRes
import wtrack.app.feature.common.labelRes
import wtrack.app.feature.common.shortLabelRes
import wtrack.integration.Direction

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
            title = stringResource(R.string.custom_parsers_title),
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
                    Text(" " + stringResource(R.string.custom_parsers_add_pattern))
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
                            text = stringResource(R.string.custom_parsers_intro_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.custom_parsers_intro_body),
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
                            Text(stringResource(R.string.custom_parsers_empty_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.custom_parsers_empty_body),
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
                                Text(" " + stringResource(R.string.custom_parsers_paste_sample_cta))
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
                                            contentDescription = stringResource(R.string.custom_parsers_cd_edit),
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
                                            contentDescription = stringResource(R.string.custom_parsers_cd_delete),
                                            tint = ext.mutedText,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(
                                    label = stringResource(rule.direction.labelRes()),
                                    containerColor = if (rule.direction == Direction.INFLOW) ext.inflowContainer else MaterialTheme.colorScheme.errorContainer,
                                    contentColor = if (rule.direction == Direction.INFLOW) ext.onInflowContainer else MaterialTheme.colorScheme.onErrorContainer,
                                )
                                StatusPill(label = stringResource(rule.transactionType.labelRes()))
                                StatusPill(label = "${rule.currency} · ${stringResource(rule.numberLocale.shortLabelRes())}")
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
                    text = stringResource(if (existing == null) R.string.custom_parsers_teach_title else R.string.custom_parsers_edit_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                if (state.sources.isNotEmpty() || existing == null) {
                    Text(stringResource(R.string.custom_parsers_source_package), style = MaterialTheme.typography.labelLarge)
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
                        ProviderPresets.commonWalletPackages
                            .filter { preset -> state.sources.none { it.packageName == preset.packageName } }
                            .take(6)
                            .forEach { preset ->
                                FilterChip(
                                    selected = sourceId == preset.packageName,
                                    onClick = { sourceId = preset.packageName },
                                    label = { Text(preset.displayName) },
                                )
                            }
                    }
                }

                OutlinedTextField(
                    value = sourceId,
                    onValueChange = { sourceId = it },
                    label = { Text(stringResource(R.string.custom_parsers_package_id_label)) },
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
                        Text(stringResource(R.string.custom_parsers_paste_section), style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = sampleTitle,
                            onValueChange = { sampleTitle = it },
                            label = { Text(stringResource(R.string.custom_parsers_sample_title_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = sampleBody,
                            onValueChange = { sampleBody = it },
                            label = { Text(stringResource(R.string.custom_parsers_sample_body_label)) },
                            placeholder = { Text(stringResource(R.string.custom_parsers_sample_placeholder)) },
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
                            Text(" " + stringResource(R.string.custom_parsers_autogenerate))
                        }
                    }
                }

                OutlinedTextField(
                    value = templatePattern,
                    onValueChange = { templatePattern = it },
                    label = { Text(stringResource(R.string.custom_parsers_template_label)) },
                    placeholder = { Text(stringResource(R.string.custom_parsers_template_placeholder)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.custom_parsers_placeholders_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.mutedText,
                )

                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text(stringResource(R.string.custom_parsers_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = titleContains,
                    onValueChange = { titleContains = it },
                    label = { Text(stringResource(R.string.custom_parsers_title_filter_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.label_direction), style = MaterialTheme.typography.labelLarge)
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
                            label = { Text(stringResource(d.labelRes())) },
                        )
                    }
                }

                Text(stringResource(R.string.custom_parsers_tx_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DomainTransactionType.entries
                        .filter { it != DomainTransactionType.ADJUSTMENT }
                        .filter { it != DomainTransactionType.REFUND || direction == Direction.INFLOW }
                        .forEach { t ->
                            FilterChip(
                                selected = txType == t,
                                onClick = { txType = t },
                                label = { Text(stringResource(t.labelRes())) },
                            )
                        }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase() },
                        label = { Text(stringResource(R.string.label_currency)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = accountHint,
                        onValueChange = { accountHint = it },
                        label = { Text(stringResource(R.string.accounts_hint_optional)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(stringResource(R.string.custom_parsers_number_format), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = locale == CustomNumberLocale.INDONESIAN,
                        onClick = { locale = CustomNumberLocale.INDONESIAN },
                        label = { Text(stringResource(CustomNumberLocale.INDONESIAN.exampleLabelRes())) },
                    )
                    FilterChip(
                        selected = locale == CustomNumberLocale.ENGLISH,
                        onClick = { locale = CustomNumberLocale.ENGLISH },
                        label = { Text(stringResource(CustomNumberLocale.ENGLISH.exampleLabelRes())) },
                    )
                }

                if (testOutcome != null) {
                    val (bgColor, fgColor, previewText) = when (testOutcome) {
                        is CustomParserTestOutcome.Matched -> Triple(
                            ext.inflowContainer,
                            ext.onInflowContainer,
                            stringResource(
                                R.string.custom_parsers_matched_preview,
                                MoneyFormatter.format(testOutcome.amountMinor, testOutcome.currency),
                                stringResource(testOutcome.direction.labelRes()),
                                stringResource(testOutcome.transactionType.labelRes()),
                            ) + (testOutcome.merchantRaw?.let {
                                stringResource(R.string.custom_parsers_matched_merchant_suffix, it)
                            } ?: ""),
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
                    ) { Text(stringResource(R.string.action_cancel)) }
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
                    ) { Text(stringResource(R.string.custom_parsers_save)) }
                }
            }
        }
    }
}
