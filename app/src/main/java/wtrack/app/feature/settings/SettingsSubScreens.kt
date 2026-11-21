package wtrack.app.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import wtrack.app.domain.usecase.ProviderMeasurementInput
import wtrack.app.feature.common.CaptureStatusPill
import wtrack.app.feature.common.UiDateHelper
import wtrack.integration.Direction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorization Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = { showAddDialog = true }) { Text("Add") }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.rules.isEmpty()) {
                item { Text("No categorization rules yet.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(state.rules, key = { it.id }) { rule ->
                    val catName = state.categories.firstOrNull { it.id == rule.categoryId }?.name ?: "Category"
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "→ $catName" +
                                        (rule.merchantKey?.let { " • merchant=$it" } ?: "") +
                                        (rule.descriptionFragment?.let { " • desc=$it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(checked = rule.enabled, onCheckedChange = { viewModel.setRuleEnabled(rule.id, it) })
                            TextButton(onClick = { viewModel.deleteRule(rule.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog && state.categories.isNotEmpty()) {
        var merchantKey by remember { mutableStateOf("") }
        var fragment by remember { mutableStateOf("") }
        var catId by remember { mutableLongStateOf(state.categories.first().id) }
        var dir by remember { mutableStateOf<Direction?>(Direction.OUTFLOW) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Create Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = merchantKey, onValueChange = { merchantKey = it }, label = { Text("Merchant key (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = fragment, onValueChange = { fragment = it }, label = { Text("Description keyword (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.categories.filter { !it.isArchived }.forEach { cat ->
                            FilterChip(selected = catId == cat.id, onClick = { catId = cat.id }, label = { Text(cat.name) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showAddDialog = false
                    viewModel.createRule(merchantKey, fragment, catId, dir)
                }) { Text("Save Rule") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var renamingCategoryId by remember { mutableStateOf<Long?>(null) }
    var renameInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = { TextButton(onClick = { showCreate = true }) { Text("Add") } },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.categories, key = { it.id }) { cat ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(cat.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                text = (if (cat.isSystem) "System" else "Custom") + (if (cat.isArchived) " • Archived" else ""),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (!cat.isSystem) {
                            Row {
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
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = scope == Direction.OUTFLOW, onClick = { scope = Direction.OUTFLOW }, label = { Text("OUTFLOW") })
                        FilterChip(selected = scope == Direction.INFLOW, onClick = { scope = Direction.INFLOW }, label = { Text("INFLOW") })
                        FilterChip(selected = scope == null, onClick = { scope = null }, label = { Text("BOTH") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCreate = false
                    viewModel.createCategory(name, scope)
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
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
    val state by viewModel.uiState.collectAsState()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture Diagnostics & Gate G1") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
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

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Listener & Permission Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            CaptureStatusPill(state.captureSummary.appStatus)
                        }
                        Text("Permission granted: ${state.captureSummary.isPermissionGranted}", style = MaterialTheme.typography.bodySmall)
                        Text("Listener connected: ${state.captureSummary.isListenerConnected}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "Last alive heartbeat: " + if (state.captureSummary.lastAliveAt > 0L) {
                                UiDateHelper.formatDateTime(state.captureSummary.lastAliveAt)
                            } else {
                                "Not recorded yet"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Open Android Notification Access") }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("vivo / iQOO OriginOS Guidance (ING-10)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "• Test default OEM battery behavior first before Gate G1 exempting.\n" +
                                "• If OriginOS kills the listener in Deep Doze: Settings → Battery → High background power consumption → Allow W-Track, and App Info → Allow autostart.\n" +
                                "• Sideloaded Android 13–16 builds: App Info → ⋮ → Allow restricted settings before granting Notification Access.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Initial PoC Provider Targets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        state.providerCatalog.forEach { p ->
                            val pkgInfo = if (p.verifiedPackageId != null) {
                                val extra = if (p.candidatePackageIds.size > 1) " • all: ${p.candidatePackageIds.joinToString()}" else ""
                                "verified: ${p.verifiedPackageId}$extra"
                            } else {
                                "ref: ${p.candidatePackageIds.joinToString()}"
                            }
                            Text(
                                text = "${p.priority}. ${p.displayName} (${p.verificationStatus}) • $pkgInfo",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = p.notes,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("M3a Sample Collection (Anonymized Export)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Retained encrypted samples: ${state.retainedSampleCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "OTPs, PINs, and security notices are never retained. Exporting masks names, emails, phone numbers, and account/card numbers before writing JSON.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = redactTermsInput,
                            onValueChange = { redactTermsInput = it },
                            label = { Text("Extra names/aliases to redact (comma-separated, optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { exportSamplesLauncher.launch("wtrack-m3a-samples-anonymized.json") },
                            enabled = !state.isBusy && state.retainedSampleCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Export Anonymized Samples (JSON)") }
                        OutlinedButton(
                            onClick = viewModel::reparseRetainedM3aSamples,
                            enabled = !state.isBusy && state.retainedSampleCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Re-parse Retained Samples") }
                        OutlinedButton(
                            onClick = viewModel::purgeRetainedM3aSamplesNow,
                            enabled = !state.isBusy && state.retainedSampleCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Purge Retained Samples Now") }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gate G1 Evaluator (Appendix C)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = jagoDays, onValueChange = { jagoDays = it }, label = { Text("Days") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = jagoA, onValueChange = { jagoA = it }, label = { Text("A (Stmt)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = jagoB, onValueChange = { jagoB = it }, label = { Text("B (Capt)") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = jagoC, onValueChange = { jagoC = it }, label = { Text("C (Usable)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = jagoD, onValueChange = { jagoD = it }, label = { Text("D (Matched)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = jagoWrong, onValueChange = { jagoWrong = it }, label = { Text("Wrong") }, singleLine = true, modifier = Modifier.weight(1f))
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
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Check Thresholds") }

                        state.gateG1Report?.let { rep ->
                            Text("Verdict: ${rep.verdict.name} — ${rep.summary}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            rep.providerResults.forEach { res ->
                                val cov = String.format(Locale.US, "%.1f%%", res.coverage * 100)
                                val comp = String.format(Locale.US, "%.1f%%", res.completeness * 100)
                                Text("Coverage=$cov, Completeness=$comp, Blocking=${res.blockingReasons}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                Text("Capture Gaps (${state.captureGaps.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(state.captureGaps.take(10), key = { it.id }) { gap ->
                Text(
                    text = "${gap.reason.name}: ${UiDateHelper.formatDateTime(gap.startedAt)} → ${gap.endedAt?.let { UiDateHelper.formatDateTime(it) } ?: "OPEN"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Non-Sensitive Logs (7d)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row {
                        TextButton(onClick = viewModel::refreshDiagnostics) { Text("Refresh") }
                        TextButton(onClick = viewModel::clearDiagnosticLogs) { Text("Clear") }
                    }
                }
            }
            items(state.diagnosticLogs) { line ->
                Text(line, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
