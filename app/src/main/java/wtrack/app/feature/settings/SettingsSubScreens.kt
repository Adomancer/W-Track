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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
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
            title = "Categorization Rules",
            onBack = onBack,
            trailing = {
                Button(
                    onClick = { showAddSheet = true },
                    enabled = state.categories.any { !it.isArchived },
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Add")
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
                            Text("No categorization rules yet.", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Create rules to automatically assign categories based on merchant key, keyword, and direction.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                        }
                    }
                }
            } else {
                items(state.rules, key = { it.id }) { rule ->
                    val catName = state.categories.firstOrNull { it.id == rule.categoryId }?.name ?: "Category"
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
                                        (rule.merchantKey?.let { " · merchant=$it" } ?: "") +
                                        (rule.descriptionFragment?.let { " · desc=$it" } ?: ""),
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
                                        contentDescription = "Delete rule",
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
                Text("Create Categorization Rule", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = merchantKey,
                    onValueChange = { merchantKey = it },
                    label = { Text("Merchant key (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = fragment,
                    onValueChange = { fragment = it },
                    label = { Text("Description keyword (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Direction Filter", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = dir == Direction.OUTFLOW, onClick = { dir = Direction.OUTFLOW }, label = { Text("OUTFLOW") })
                    FilterChip(selected = dir == Direction.INFLOW, onClick = { dir = Direction.INFLOW }, label = { Text("INFLOW") })
                    FilterChip(selected = dir == null, onClick = { dir = null }, label = { Text("ANY") })
                }

                Text("Target Category", style = MaterialTheme.typography.labelLarge)
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
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            showAddSheet = false
                            viewModel.createRule(merchantKey, fragment, catId, dir)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Save Rule") }
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
            title = "Categories",
            onBack = onBack,
            trailing = {
                Button(
                    onClick = { showCreate = true },
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Add")
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
                                StatusPill(label = if (cat.isSystem) "System" else "Custom")
                                if (cat.isArchived) {
                                    StatusPill(
                                        label = "Archived",
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
                                    Text("Rename")
                                }
                                TextButton(onClick = { viewModel.setCategoryArchived(cat.id, !cat.isArchived) }) {
                                    Text(if (cat.isArchived) "Restore" else "Archive")
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
                Text("New Category", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Direction Scope", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = scope == Direction.OUTFLOW, onClick = { scope = Direction.OUTFLOW }, label = { Text("OUTFLOW") })
                    FilterChip(selected = scope == Direction.INFLOW, onClick = { scope = Direction.INFLOW }, label = { Text("INFLOW") })
                    FilterChip(selected = scope == null, onClick = { scope = null }, label = { Text("BOTH") })
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
                    ) { Text("Cancel") }
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            showCreate = false
                            viewModel.createCategory(name, scope)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Create") }
                }
            }
        }
    }

    if (renamingCategoryId != null) {
        val catId = renamingCategoryId!!
        AlertDialog(
            onDismissRequest = { renamingCategoryId = null },
            title = { Text("Rename Category") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Category name") },
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
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renamingCategoryId = null }) { Text("Cancel") } },
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ext = WtrackTheme.colors

    var showAppsSheet by remember { mutableStateOf(false) }
    var showBatterySheet by remember { mutableStateOf(false) }
    var showTechnicalSheet by remember { mutableStateOf(false) }

    var redactTermsInput by remember { mutableStateOf("") }
    var jagoDays by remember { mutableStateOf("14") }
    var jagoA by remember { mutableStateOf("35") }
    var jagoB by remember { mutableStateOf("34") }
    var jagoC by remember { mutableStateOf("33") }
    var jagoD by remember { mutableStateOf("32") }
    var jagoWrong by remember { mutableStateOf("0") }

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
    val statusLabel = if (isActive) "Active" else "Needs attention"
    val heroTitle = if (isActive) "All set" else "Turn on access"
    val appNamesSummary = state.providerCatalog.joinToString(", ") { it.displayName }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { WtrackTopBar(title = "Auto-capture", onBack = onBack) },
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
                    Text("Run quick check", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = { showTechnicalSheet = true }) {
                    Text(
                        text = "Technical details ›",
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

            // Block 1: Hero Status (pure whitespace, plain English)
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
                            "Listening to $appNamesSummary"
                        } else {
                            "Automatically reads bank & e-wallet alerts"
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
                        title = "Notification access",
                        subtitle = if (state.captureSummary.isPermissionGranted) "Allowed" else "Tap to allow",
                        trailingColor = if (state.captureSummary.isPermissionGranted) ext.inflow else ext.warning,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = "Monitored apps",
                        subtitle = "${state.providerCatalog.size} supported apps",
                        onClick = { showAppsSheet = true },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = "Battery & background",
                        subtitle = "Fix if alerts stop arriving",
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
                Text("Monitored apps", style = MaterialTheme.typography.titleLarge)
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
                Text("Battery & background", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "If notifications stop arriving while the app is closed:\n\n1. Open App Info → Battery and allow background power.\n2. On vivo / iQOO OriginOS, turn on Autostart.\n3. On Android 13+, tap ⋮ → Allow restricted settings before enabling Notification Access.",
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
                ) { Text("Open App Info", style = MaterialTheme.typography.titleMedium) }
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
                Text("Technical details", style = MaterialTheme.typography.titleLarge)

                DiagnosticsKvRow("Permission granted", "${state.captureSummary.isPermissionGranted}")
                DiagnosticsKvRow("Listener connected", "${state.captureSummary.isListenerConnected}")
                DiagnosticsKvRow(
                    "Last heartbeat",
                    if (state.captureSummary.lastAliveAt > 0L) {
                        UiDateHelper.formatDateTime(state.captureSummary.lastAliveAt)
                    } else {
                        "Not recorded yet"
                    },
                )

                HorizontalDivider(color = ext.divider)

                Text("M3a Sample Collection (${state.retainedSampleCount} retained)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = redactTermsInput,
                    onValueChange = { redactTermsInput = it },
                    label = { Text("Extra names to redact (optional)") },
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
                    ) { Text("Export JSON") }
                    OutlinedButton(
                        onClick = viewModel::reparseRetainedM3aSamples,
                        enabled = !state.isBusy && state.retainedSampleCount > 0,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("Re-parse") }
                    TextButton(
                        onClick = viewModel::purgeRetainedM3aSamplesNow,
                        enabled = !state.isBusy && state.retainedSampleCount > 0,
                    ) { Text("Purge") }
                }

                HorizontalDivider(color = ext.divider)

                Text("Parser updates", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = viewModel::scanReparseProposals,
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Scan retained events") }
                state.reparseProposals.forEach { prop ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${prop.sourceId} · Tx #${prop.transactionId} (v${prop.oldParserVersion} → v${prop.newParserVersion})",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.applyReparseProposal(prop) },
                                enabled = !state.isBusy,
                                shape = RoundedCornerShape(999.dp),
                            ) { Text("Apply") }
                            TextButton(
                                onClick = { viewModel.dismissReparseProposal(prop.eventId) },
                                enabled = !state.isBusy,
                            ) { Text("Dismiss") }
                        }
                    }
                }

                HorizontalDivider(color = ext.divider)

                Text("Gate G1 Evaluator", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = jagoDays, onValueChange = { jagoDays = it }, label = { Text("Days") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = jagoA, onValueChange = { jagoA = it }, label = { Text("A") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = jagoB, onValueChange = { jagoB = it }, label = { Text("B") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = jagoC, onValueChange = { jagoC = it }, label = { Text("C") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = jagoD, onValueChange = { jagoD = it }, label = { Text("D") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = jagoWrong, onValueChange = { jagoWrong = it }, label = { Text("Wrong") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        viewModel.evaluateGateG1(
                            listOf(
                                ProviderMeasurementInput(
                                    providerName = "Provider Sample",
                                    days = jagoDays.toIntOrNull() ?: 0,
                                    statementTxCountA = jagoA.toIntOrNull() ?: 0,
                                    capturedTxNotificationsB = jagoB.toIntOrNull() ?: 0,
                                    usableNotificationsC = jagoC.toIntOrNull() ?: 0,
                                    matchedStatementTxD = jagoD.toIntOrNull() ?: 0,
                                    unexplainedWrongAmountOrDirection = jagoWrong.toIntOrNull() ?: 0,
                                ),
                            ),
                        )
                    },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Check thresholds") }
                state.gateG1Report?.let { rep ->
                    Text("Verdict: ${rep.verdict.name} — ${rep.summary}", style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider(color = ext.divider)

                Text("Capture gaps (${state.captureGaps.size})", style = MaterialTheme.typography.titleMedium)
                if (state.captureGaps.isEmpty()) {
                    Text("No capture gaps recorded.", style = MaterialTheme.typography.bodyMedium, color = ext.mutedText)
                } else {
                    state.captureGaps.take(10).forEach { gap ->
                        Text(
                            text = "${gap.reason.name} · ${UiDateHelper.formatDateTime(gap.startedAt)}",
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
                    Text("Logs (7d)", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = viewModel::clearDiagnosticLogs) { Text("Clear") }
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
