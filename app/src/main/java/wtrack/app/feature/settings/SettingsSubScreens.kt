package wtrack.app.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wtrack.app.R
import wtrack.app.core.i18n.AppLocaleHelper
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.tabularNums
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.usecase.ProviderMeasurementInput
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.SectionHeader
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.V2SettingRow
import wtrack.app.feature.common.V2StatusDot
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.common.WtrackTopBar
import wtrack.app.feature.common.labelRes
import wtrack.integration.Direction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val ext = WtrackTheme.colors
    var showAddSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        WtrackTopBar(
            title = stringResource(R.string.rules_title),
            onBack = onBack,
            trailing = {
                Button(
                    onClick = { showAddSheet = true },
                    enabled = state.categories.any { !it.isArchived },
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" " + stringResource(R.string.action_add))
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
            if (state.rules.isEmpty()) {
                item(key = "empty") {
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(stringResource(R.string.rules_empty_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.rules_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                        }
                    }
                }
            } else {
                items(state.rules, key = { it.id }) { rule ->
                    val catName = state.categories.firstOrNull { it.id == rule.categoryId }?.name
                        ?: stringResource(R.string.label_category_fallback)
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(rule.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "→ $catName" +
                                        (rule.merchantKey?.let { stringResource(R.string.rules_summary_merchant, it) } ?: "") +
                                        (rule.descriptionFragment?.let { stringResource(R.string.rules_summary_desc, it) } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { viewModel.setRuleEnabled(rule.id, it) },
                                )
                                IconButton(
                                    onClick = { viewModel.deleteRule(rule.id) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.rules_cd_delete),
                                        tint = ext.mutedText,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet && state.categories.isNotEmpty()) {
        var merchantKey by remember { mutableStateOf("") }
        var fragment by remember { mutableStateOf("") }
        var catId by remember { mutableLongStateOf(state.categories.first().id) }
        var dir by remember { mutableStateOf<Direction?>(Direction.OUTFLOW) }

        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
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
                Text(stringResource(R.string.rules_create_title), style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = merchantKey,
                    onValueChange = { merchantKey = it },
                    label = { Text(stringResource(R.string.rules_merchant_key_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = fragment,
                    onValueChange = { fragment = it },
                    label = { Text(stringResource(R.string.rules_desc_keyword_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.rules_direction_filter), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dir == Direction.OUTFLOW,
                        onClick = { dir = Direction.OUTFLOW },
                        label = { Text(stringResource(Direction.OUTFLOW.labelRes())) },
                    )
                    FilterChip(
                        selected = dir == Direction.INFLOW,
                        onClick = { dir = Direction.INFLOW },
                        label = { Text(stringResource(Direction.INFLOW.labelRes())) },
                    )
                    FilterChip(
                        selected = dir == null,
                        onClick = { dir = null },
                        label = { Text(stringResource(R.string.label_any)) },
                    )
                }

                Text(stringResource(R.string.rules_target_category), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.categories.filter { !it.isArchived }.forEach { cat ->
                        FilterChip(selected = catId == cat.id, onClick = { catId = cat.id }, label = { Text(cat.name) })
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { showAddSheet = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = {
                            showAddSheet = false
                            viewModel.createRule(merchantKey, fragment, catId, dir)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(stringResource(R.string.rules_save)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val ext = WtrackTheme.colors
    var showCreate by remember { mutableStateOf(false) }
    var renamingCategoryId by remember { mutableStateOf<Long?>(null) }
    var renameInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        WtrackTopBar(
            title = stringResource(R.string.categories_title),
            onBack = onBack,
            trailing = {
                Button(
                    onClick = { showCreate = true },
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" " + stringResource(R.string.action_add))
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.categories, key = { it.id }) { cat ->
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(cat.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(label = stringResource(if (cat.isSystem) R.string.label_system else R.string.label_custom))
                                if (cat.isArchived) {
                                    StatusPill(
                                        label = stringResource(R.string.label_archived),
                                        containerColor = ext.warningContainer,
                                        contentColor = ext.onWarningContainer,
                                    )
                                }
                            }
                        }
                        if (!cat.isSystem) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(onClick = {
                                    renamingCategoryId = cat.id
                                    renameInput = cat.name
                                }) {
                                    Text(stringResource(R.string.categories_rename))
                                }
                                TextButton(onClick = { viewModel.setCategoryArchived(cat.id, !cat.isArchived) }) {
                                    Text(stringResource(if (cat.isArchived) R.string.action_restore else R.string.categories_archive))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var scope by remember { mutableStateOf<Direction?>(Direction.OUTFLOW) }
        ModalBottomSheet(
            onDismissRequest = { showCreate = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.categories_new_title), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.categories_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.categories_direction_scope), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scope == Direction.OUTFLOW,
                        onClick = { scope = Direction.OUTFLOW },
                        label = { Text(stringResource(Direction.OUTFLOW.labelRes())) },
                    )
                    FilterChip(
                        selected = scope == Direction.INFLOW,
                        onClick = { scope = Direction.INFLOW },
                        label = { Text(stringResource(Direction.INFLOW.labelRes())) },
                    )
                    FilterChip(
                        selected = scope == null,
                        onClick = { scope = null },
                        label = { Text(stringResource(R.string.label_both)) },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { showCreate = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            showCreate = false
                            viewModel.createCategory(name, scope)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(stringResource(R.string.categories_create)) }
                }
            }
        }
    }

    if (renamingCategoryId != null) {
        val catId = renamingCategoryId!!
        AlertDialog(
            onDismissRequest = { renamingCategoryId = null },
            title = { Text(stringResource(R.string.categories_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text(stringResource(R.string.categories_rename_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = renameInput.isNotBlank(),
                    onClick = {
                        renamingCategoryId = null
                        viewModel.renameCategory(catId, renameInput)
                    },
                ) { Text(stringResource(R.string.categories_rename)) }
            },
            dismissButton = { TextButton(onClick = { renamingCategoryId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activeLocale = AppLocaleHelper.activeLocale(context)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ext = WtrackTheme.colors

    var showAppsSheet by remember { mutableStateOf(false) }
    var showBatterySheet by remember { mutableStateOf(false) }
    var showTechnicalSheet by remember { mutableStateOf(false) }

    var redactTermsInput by remember { mutableStateOf("") }
    var gateProviderName by remember { mutableStateOf("") }
    var jagoDays by remember { mutableStateOf("") }
    var jagoA by remember { mutableStateOf("") }
    var jagoB by remember { mutableStateOf("") }
    var jagoC by remember { mutableStateOf("") }
    var jagoD by remember { mutableStateOf("") }
    var jagoWrong by remember { mutableStateOf("") }

    val exportSamplesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportAnonymizedM3aSamples(redactTermsInput) {
                context.contentResolver.openOutputStream(uri)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDiagnostics()
    }

    val isActive = state.captureSummary.appStatus == CaptureStatus.ACTIVE && state.captureSummary.isPermissionGranted
    val statusDotColor = if (isActive) ext.inflow else ext.warning
    val statusLabel = stringResource(if (isActive) R.string.diagnostics_status_active else R.string.diagnostics_status_attention)
    val heroTitle = stringResource(if (isActive) R.string.diagnostics_hero_active else R.string.diagnostics_hero_attention)
    val appNamesSummary = state.providerCatalog.joinToString(", ") { it.displayName }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { WtrackTopBar(title = stringResource(R.string.settings_auto_capture), onBack = onBack) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = { viewModel.refreshDiagnostics() },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(stringResource(R.string.onboarding_run_quick_check), style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = { showTechnicalSheet = true }) {
                    Text(
                        text = stringResource(R.string.diagnostics_technical_details),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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

            // Block 1: Hero Status (pure whitespace)
            item(key = "hero-status") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        V2StatusDot(color = statusDotColor)
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = statusDotColor,
                        )
                    }
                    Text(
                        text = heroTitle,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (appNamesSummary.isNotBlank()) {
                            stringResource(R.string.diagnostics_listening_to, appNamesSummary)
                        } else {
                            stringResource(R.string.diagnostics_listening_fallback)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Block 2: 3 plain health rows
            item(key = "health-rows") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    V2SettingRow(
                        title = stringResource(R.string.onboarding_notification_access),
                        subtitle = stringResource(
                            if (state.captureSummary.isPermissionGranted) R.string.label_allowed else R.string.diagnostics_tap_to_allow,
                        ),
                        trailingColor = if (state.captureSummary.isPermissionGranted) ext.inflow else ext.warning,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = stringResource(R.string.diagnostics_monitored_apps),
                        subtitle = pluralStringResource(
                            R.plurals.diagnostics_supported_apps_count,
                            state.providerCatalog.size,
                            state.providerCatalog.size,
                        ),
                        onClick = { showAppsSheet = true },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = stringResource(R.string.diagnostics_battery_background),
                        subtitle = stringResource(R.string.diagnostics_battery_subtitle),
                        onClick = { showBatterySheet = true },
                    )
                }
            }
        }
    }

    if (showAppsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppsSheet = false },
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
                Text(stringResource(R.string.diagnostics_monitored_apps), style = MaterialTheme.typography.titleLarge)
                state.providerCatalog.forEachIndexed { idx, p ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(p.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = p.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (idx < state.providerCatalog.lastIndex) {
                        HorizontalDivider(color = ext.divider)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showBatterySheet) {
        ModalBottomSheet(
            onDismissRequest = { showBatterySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(R.string.diagnostics_battery_background), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.diagnostics_battery_guide),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        showBatterySheet = false
                        val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(appInfoIntent)
                    },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text(stringResource(R.string.action_open_app_info), style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // One tap deeper: Gate G1, M3a sample collection, PAR-11, Capture Gaps & Logs
    if (showTechnicalSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTechnicalSheet = false },
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
                Text(stringResource(R.string.diagnostics_technical_title), style = MaterialTheme.typography.titleLarge)

                DiagnosticsKvRow(stringResource(R.string.diagnostics_permission_granted), "${state.captureSummary.isPermissionGranted}")
                DiagnosticsKvRow(stringResource(R.string.diagnostics_listener_connected), "${state.captureSummary.isListenerConnected}")
                DiagnosticsKvRow(
                    stringResource(R.string.diagnostics_last_heartbeat),
                    if (state.captureSummary.lastAliveAt > 0L) {
                        UiDateHelper.formatDateTime(state.captureSummary.lastAliveAt, locale = activeLocale)
                    } else {
                        stringResource(R.string.diagnostics_not_recorded)
                    },
                )

                HorizontalDivider(color = ext.divider)

                Text(
                    stringResource(R.string.diagnostics_m3a_title, state.retainedSampleCount),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = redactTermsInput,
                    onValueChange = { redactTermsInput = it },
                    label = { Text(stringResource(R.string.diagnostics_extra_redact)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportSamplesLauncher.launch("wtrack-m3a-samples-anonymized.json") },
                        enabled = !state.isBusy && state.retainedSampleCount > 0,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.diagnostics_export_json)) }
                    OutlinedButton(
                        onClick = viewModel::reparseRetainedM3aSamples,
                        enabled = !state.isBusy && state.retainedSampleCount > 0,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.diagnostics_reparse)) }
                    TextButton(
                        onClick = viewModel::purgeRetainedM3aSamplesNow,
                        enabled = !state.isBusy && state.retainedSampleCount > 0,
                    ) { Text(stringResource(R.string.diagnostics_purge)) }
                }

                HorizontalDivider(color = ext.divider)

                Text(stringResource(R.string.diagnostics_parser_updates), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = viewModel::scanReparseProposals,
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.diagnostics_scan_retained)) }
                state.reparseProposals.forEach { prop ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(
                                R.string.diagnostics_proposal_item,
                                prop.sourceId,
                                prop.transactionId,
                                prop.oldParserVersion,
                                prop.newParserVersion,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.applyReparseProposal(prop) },
                                enabled = !state.isBusy,
                                shape = RoundedCornerShape(999.dp),
                            ) { Text(stringResource(R.string.action_apply)) }
                            TextButton(
                                onClick = { viewModel.dismissReparseProposal(prop.eventId) },
                                enabled = !state.isBusy,
                            ) { Text(stringResource(R.string.action_dismiss)) }
                        }
                    }
                }

                HorizontalDivider(color = ext.divider)

                Text(stringResource(R.string.diagnostics_gate_g1_title), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = gateProviderName,
                    onValueChange = { gateProviderName = it },
                    label = { Text(stringResource(R.string.diagnostics_gate_provider_label)) },
                    placeholder = { Text(stringResource(R.string.diagnostics_gate_provider_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = jagoDays,
                        onValueChange = { jagoDays = it },
                        label = { Text(stringResource(R.string.diagnostics_gate_days)) },
                        placeholder = { Text(stringResource(R.string.diagnostics_gate_days_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = jagoA,
                        onValueChange = { jagoA = it },
                        label = { Text(stringResource(R.string.diagnostics_gate_a)) },
                        placeholder = { Text(stringResource(R.string.diagnostics_gate_a_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = jagoB,
                        onValueChange = { jagoB = it },
                        label = { Text(stringResource(R.string.diagnostics_gate_b)) },
                        placeholder = { Text(stringResource(R.string.diagnostics_gate_b_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = jagoC,
                        onValueChange = { jagoC = it },
                        label = { Text(stringResource(R.string.diagnostics_gate_c)) },
                        placeholder = { Text(stringResource(R.string.diagnostics_gate_c_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = jagoD,
                        onValueChange = { jagoD = it },
                        label = { Text(stringResource(R.string.diagnostics_gate_d)) },
                        placeholder = { Text(stringResource(R.string.diagnostics_gate_d_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = jagoWrong,
                        onValueChange = { jagoWrong = it },
                        label = { Text(stringResource(R.string.diagnostics_gate_wrong)) },
                        placeholder = { Text(stringResource(R.string.diagnostics_gate_wrong_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                val parsedGateInput = remember(gateProviderName, jagoDays, jagoA, jagoB, jagoC, jagoD, jagoWrong) {
                    parseGateG1FormInput(
                        providerName = gateProviderName,
                        days = jagoDays,
                        statementTxCountA = jagoA,
                        capturedTxNotificationsB = jagoB,
                        usableNotificationsC = jagoC,
                        matchedStatementTxD = jagoD,
                        unexplainedWrong = jagoWrong,
                    )
                }
                OutlinedButton(
                    enabled = parsedGateInput != null,
                    onClick = {
                        parsedGateInput?.let { input ->
                            viewModel.evaluateGateG1(listOf(input))
                        }
                    },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.diagnostics_check_thresholds)) }
                state.gateG1Report?.let { rep ->
                    Text(
                        stringResource(R.string.diagnostics_verdict, rep.verdict.name, rep.summary),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                HorizontalDivider(color = ext.divider)

                Text(
                    stringResource(R.string.diagnostics_capture_gaps, state.captureGaps.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.captureGaps.isEmpty()) {
                    Text(stringResource(R.string.diagnostics_no_gaps), style = MaterialTheme.typography.bodyMedium, color = ext.mutedText)
                } else {
                    state.captureGaps.take(10).forEach { gap ->
                        Text(
                            text = "${gap.reason.name} · ${UiDateHelper.formatDateTime(gap.startedAt, locale = activeLocale)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.diagnostics_logs_7d), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = viewModel::clearDiagnosticLogs) { Text(stringResource(R.string.action_clear)) }
                }
                state.diagnosticLogs.take(20).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DiagnosticsKvRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = WtrackTheme.colors.mutedText)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

internal fun parseGateG1FormInput(
    providerName: String,
    days: String,
    statementTxCountA: String,
    capturedTxNotificationsB: String,
    usableNotificationsC: String,
    matchedStatementTxD: String,
    unexplainedWrong: String,
): ProviderMeasurementInput? {
    val cleanName = providerName.trim()
    if (cleanName.isEmpty()) return null
    val parsedDays = days.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val parsedA = statementTxCountA.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val parsedB = capturedTxNotificationsB.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val parsedC = usableNotificationsC.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val parsedD = matchedStatementTxD.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val parsedWrong = unexplainedWrong.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
    return ProviderMeasurementInput(
        providerName = cleanName,
        days = parsedDays,
        statementTxCountA = parsedA,
        capturedTxNotificationsB = parsedB,
        usableNotificationsC = parsedC,
        matchedStatementTxD = parsedD,
        unexplainedWrongAmountOrDirection = parsedWrong,
    )
}
