package wtrack.app.feature.settings

import android.content.Intent
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
import java.util.Locale
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.core.theme.tabularNums
import wtrack.app.domain.usecase.ProviderMeasurementInput
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.CaptureStatusPill
import wtrack.app.feature.common.SectionHeader
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.UiDateHelper
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

@Composable
fun DiagnosticsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val ext = WtrackTheme.colors

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

    Column(modifier = Modifier.fillMaxSize()) {
        WtrackTopBar(title = "Capture Diagnostics & Gate G1", onBack = onBack)

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

            item(key = "listener-status") {
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Listener & Permission Status", style = MaterialTheme.typography.titleMedium)
                            CaptureStatusPill(state.captureSummary.appStatus)
                        }
                        DiagnosticsKvRow("Permission granted", "${state.captureSummary.isPermissionGranted}")
                        DiagnosticsKvRow("Listener connected", "${state.captureSummary.isListenerConnected}")
                        DiagnosticsKvRow(
                            "Last alive heartbeat",
                            if (state.captureSummary.lastAliveAt > 0L) {
                                UiDateHelper.formatDateTime(state.captureSummary.lastAliveAt)
                            } else {
                                "Not recorded yet"
                            },
                        )
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Open Android Notification Access") }
                    }
                }
            }

            item(key = "originos") {
                Surface(
                    color = ext.infoTintBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ext.infoTintBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("vivo / iQOO OriginOS Guidance (ING-10)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "• Test default OEM battery behavior first before Gate G1 exempting.\n" +
                                "• If OriginOS kills the listener in Deep Doze: Settings → Battery → High background power consumption → Allow W-Track, and App Info → Allow autostart.\n" +
                                "• Sideloaded Android 13–16 builds: App Info → ⋮ → Allow restricted settings before granting Notification Access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "providers") {
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Initial PoC Provider Targets", style = MaterialTheme.typography.titleMedium)
                        state.providerCatalog.forEachIndexed { idx, p ->
                            val pkgInfo = if (p.verifiedPackageId != null) {
                                val extra = if (p.candidatePackageIds.size > 1) " • all: ${p.candidatePackageIds.joinToString()}" else ""
                                "verified: ${p.verifiedPackageId}$extra"
                            } else {
                                "ref: ${p.candidatePackageIds.joinToString()}"
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${p.priority}. ${p.displayName} (${p.verificationStatus}) • $pkgInfo",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = p.notes,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                )
                            }
                            if (idx < state.providerCatalog.lastIndex) {
                                HorizontalDivider(color = ext.divider)
                            }
                        }
                    }
                }
            }

            item(key = "m3a") {
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("M3a Sample Collection", style = MaterialTheme.typography.titleMedium)
                            StatusPill(
                                label = "${state.retainedSampleCount} retained",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            text = "OTPs, PINs, and security notices are never retained. Exporting masks names, emails, phone numbers, and account/card numbers before writing JSON.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                        )
                        OutlinedTextField(
                            value = redactTermsInput,
                            onValueChange = { redactTermsInput = it },
                            label = { Text("Extra names/aliases to redact (comma-separated, optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { exportSamplesLauncher.launch("wtrack-m3a-samples-anonymized.json") },
                            enabled = !state.isBusy && state.retainedSampleCount > 0,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Export Anonymized Samples (JSON)") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = viewModel::reparseRetainedM3aSamples,
                                enabled = !state.isBusy && state.retainedSampleCount > 0,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text("Re-parse") }
                            OutlinedButton(
                                onClick = viewModel::purgeRetainedM3aSamplesNow,
                                enabled = !state.isBusy && state.retainedSampleCount > 0,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text("Purge Now") }
                        }
                    }
                }
            }

            item(key = "par11") {
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Parser Update Re-Parse Proposals (PAR-11)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Checks retained notifications (within verification window) against newer parser versions without touching user-edited transactions or user-set categories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                        )
                        OutlinedButton(
                            onClick = viewModel::scanReparseProposals,
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Scan Retained Events for Parser Updates") }
                        state.reparseProposals.forEach { prop ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "${prop.sourceId} • Tx #${prop.transactionId} (v${prop.oldParserVersion} → v${prop.newParserVersion})",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = "Amount: ${prop.currentAmountMinor} → ${prop.proposedAmountMinor} ${prop.currency}\n" +
                                            "Dir/Type: ${prop.currentDirection}/${prop.currentType} → ${prop.proposedDirection}/${prop.proposedType}\n" +
                                            "Desc: ${prop.currentDescription ?: "-"} → ${prop.proposedDescription ?: "-"}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.applyReparseProposal(prop) },
                                            enabled = !state.isBusy,
                                            shape = RoundedCornerShape(10.dp),
                                        ) { Text("Apply Update") }
                                        TextButton(
                                            onClick = { viewModel.dismissReparseProposal(prop.eventId) },
                                            enabled = !state.isBusy,
                                        ) { Text("Dismiss") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "g1") {
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Gate G1 Evaluator (Appendix C)", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = jagoDays,
                                onValueChange = { jagoDays = it },
                                label = { Text("Days") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = jagoA,
                                onValueChange = { jagoA = it },
                                label = { Text("A (Stmt)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = jagoB,
                                onValueChange = { jagoB = it },
                                label = { Text("B (Capt)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = jagoC,
                                onValueChange = { jagoC = it },
                                label = { Text("C (Usable)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = jagoD,
                                onValueChange = { jagoD = it },
                                label = { Text("D (Matched)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = jagoWrong,
                                onValueChange = { jagoWrong = it },
                                label = { Text("Wrong") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Button(
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Check Thresholds") }

                        state.gateG1Report?.let { rep ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "Verdict: ${rep.verdict.name} — ${rep.summary}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    rep.providerResults.forEach { res ->
                                        val cov = String.format(Locale.US, "%.1f%%", res.coverage * 100)
                                        val comp = String.format(Locale.US, "%.1f%%", res.completeness * 100)
                                        Text(
                                            text = "Coverage=$cov, Completeness=$comp, Blocking=${res.blockingReasons}",
                                            style = MaterialTheme.typography.bodySmall.tabularNums(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "gaps-hdr") {
                SectionHeader(
                    title = "Capture Gaps",
                    trailingText = "${state.captureGaps.size}",
                )
            }
            if (state.captureGaps.isEmpty()) {
                item(key = "gaps-empty") {
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No open or recent capture gaps recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            } else {
                items(state.captureGaps.take(10), key = { it.id }) { gap ->
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(gap.reason.name, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "${UiDateHelper.formatDateTime(gap.startedAt)} → ${gap.endedAt?.let { UiDateHelper.formatDateTime(it) } ?: "OPEN"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                )
                            }
                            if (gap.endedAt == null) {
                                StatusPill(
                                    label = "OPEN",
                                    containerColor = ext.warningContainer,
                                    contentColor = ext.onWarningContainer,
                                    borderColor = ext.warningBorder,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "logs-hdr") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Non-Sensitive Logs (7d)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = viewModel::refreshDiagnostics) { Text("Refresh") }
                        TextButton(onClick = viewModel::clearDiagnosticLogs) { Text("Clear") }
                    }
                }
            }
            if (state.diagnosticLogs.isNotEmpty()) {
                item(key = "logs-box") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ext.cardBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.diagnosticLogs.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
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
        Text(label, style = MaterialTheme.typography.bodySmall, color = WtrackTheme.colors.mutedText)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}
