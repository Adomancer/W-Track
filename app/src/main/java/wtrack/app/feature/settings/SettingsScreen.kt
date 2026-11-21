package wtrack.app.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import wtrack.app.domain.model.ThemeMode
import wtrack.app.feature.common.CaptureStatusPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenRules: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupPassphrase by remember { mutableStateOf("") }
    var backupPassphraseConfirm by remember { mutableStateOf("") }
    var showCsvWarning by remember { mutableStateOf(false) }
    var showWipeStep1 by remember { mutableStateOf(false) }
    var showWipeStep2 by remember { mutableStateOf(false) }
    var wipeConfirmInput by remember { mutableStateOf("") }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            pendingExportUri = uri
            backupPassphrase = ""
            backupPassphraseConfirm = ""
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            backupPassphrase = ""
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsv { context.contentResolver.openOutputStream(uri) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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

        if (state.showPostRestorePermissionPrompt) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Backup restored — re-grant Notification Access so W-Track can resume capturing notifications (ING-02).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }) { Text("Grant Notification Access") }
                            TextButton(onClick = viewModel::dismissPostRestorePrompt) { Text("Dismiss") }
                        }
                    }
                }
            }
        }

        if (state.isBusy) {
            item { CircularProgressIndicator() }
        }

        // Capture & Tools
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDiagnostics),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Capture Diagnostics & Gate G1", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Listener status, gap log, OriginOS tips & logs", style = MaterialTheme.typography.bodySmall)
                    }
                    CaptureStatusPill(state.captureSummary.appStatus)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenRules, modifier = Modifier.weight(1f)) {
                    Text("Rules (${state.rules.size})")
                }
                OutlinedButton(onClick = onOpenCategories, modifier = Modifier.weight(1f)) {
                    Text("Categories (${state.categories.size})")
                }
            }
        }

        // Appearance & Currency
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance & Currency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.name) },
                            )
                        }
                    }
                    Text("Base Currency (single-currency aggregates)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("IDR", "USD", "SGD", "MYR", "EUR", "JPY").forEach { cur ->
                            FilterChip(
                                selected = state.settings.baseCurrency == cur,
                                onClick = { viewModel.setBaseCurrency(cur) },
                                label = { Text(cur) },
                            )
                        }
                    }
                }
            }
        }

        // Retention & Heartbeat
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Retention & Monitoring", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Verification window for raw notification text", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 7, 14, 30).forEach { days ->
                            FilterChip(
                                selected = state.settings.verificationWindowDays == days,
                                onClick = { viewModel.setVerificationWindowDays(days) },
                                label = { Text(if (days == 0) "0d (Immediate purge)" else "${days}d") },
                            )
                        }
                    }
                    Text("Heartbeat stale gap threshold", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 6, 12).forEach { hours ->
                            FilterChip(
                                selected = state.settings.heartbeatStaleHours == hours,
                                onClick = { viewModel.setHeartbeatStaleHours(hours) },
                                label = { Text("${hours}h") },
                            )
                        }
                    }
                }
            }
        }

        // Encrypted Backup, CSV Export & Wipe
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backup & Data Safety", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Backups are passphrase-encrypted with Argon2id + AES-256-GCM and omit raw notification payloads and unresolved Needs Review drafts.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.pendingReviewCount > 0) {
                        Text(
                            text = "Pending Needs Review items (${state.pendingReviewCount}) will NOT be included in the backup. Resolve them first if you want them backed up.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text("Backup reminder interval (days)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 14, 30).forEach { days ->
                            FilterChip(
                                selected = state.settings.backupReminderDays == days,
                                onClick = { viewModel.setBackupReminderThresholds(days, state.settings.backupReminderTxCount) },
                                label = { Text("${days}d") },
                            )
                        }
                    }
                    Text("Backup reminder transaction threshold", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(50, 100, 250).forEach { txCount ->
                            FilterChip(
                                selected = state.settings.backupReminderTxCount == txCount,
                                onClick = { viewModel.setBackupReminderThresholds(state.settings.backupReminderDays, txCount) },
                                label = { Text("$txCount tx") },
                            )
                        }
                    }
                    Button(
                        onClick = { exportBackupLauncher.launch("wtrack-backup.wtrackbackup") },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Create Encrypted Backup") }

                    OutlinedButton(
                        onClick = { restoreBackupLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Restore Encrypted Backup") }

                    OutlinedButton(
                        onClick = { showCsvWarning = true },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Export Unencrypted CSV") }

                    OutlinedButton(
                        onClick = { showWipeStep1 = true },
                        enabled = !state.isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Erase All Stored Data") }
                }
            }
        }
    }

    if (pendingExportUri != null) {
        val uri = pendingExportUri!!
        AlertDialog(
            onDismissRequest = { pendingExportUri = null },
            title = { Text("Encrypt Backup File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = buildString {
                            append("Lost passphrases cannot be recovered. Raw notification payloads and Needs Review items are excluded by design.")
                            if (state.pendingReviewCount > 0) {
                                append(" You currently have ${state.pendingReviewCount} pending Needs Review item(s) that will be skipped.")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Passphrase (min 8 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = backupPassphraseConfirm,
                        onValueChange = { backupPassphraseConfirm = it },
                        label = { Text("Confirm passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = backupPassphrase.length >= 8 && backupPassphrase == backupPassphraseConfirm,
                    onClick = {
                        val pw = backupPassphrase
                        pendingExportUri = null
                        viewModel.exportEncryptedBackup(
                            passphrase = pw,
                            streamOpener = { context.contentResolver.openOutputStream(uri) },
                            verifyStreamOpener = { context.contentResolver.openInputStream(uri) },
                        )
                    },
                ) { Text("Encrypt & Verify") }
            },
            dismissButton = { TextButton(onClick = { pendingExportUri = null }) { Text("Cancel") } },
        )
    }

    if (pendingRestoreUri != null) {
        val uri = pendingRestoreUri!!
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore Backup") },
            text = {
                OutlinedTextField(
                    value = backupPassphrase,
                    onValueChange = { backupPassphrase = it },
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    val pw = backupPassphrase
                    pendingRestoreUri = null
                    viewModel.restoreEncryptedBackup(pw) { context.contentResolver.openInputStream(uri) }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") } },
        )
    }

    if (showCsvWarning) {
        AlertDialog(
            onDismissRequest = { showCsvWarning = false },
            title = { Text("Unencrypted CSV Warning") },
            text = {
                Text("CSV export writes plaintext financial transactions without encryption (DATA-11). Use encrypted backup for secure storage or device migration.")
            },
            confirmButton = {
                Button(onClick = {
                    showCsvWarning = false
                    exportCsvLauncher.launch("wtrack-transactions.csv")
                }) { Text("Continue to CSV Export") }
            },
            dismissButton = { TextButton(onClick = { showCsvWarning = false }) { Text("Cancel") } },
        )
    }

    if (showWipeStep1) {
        AlertDialog(
            onDismissRequest = { showWipeStep1 = false },
            title = { Text("Erase All Local Data?") },
            text = { Text("This deletes the SQLCipher database, wrapped keys, logs, and preferences from this device.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showWipeStep1 = false
                        wipeConfirmInput = ""
                        showWipeStep2 = true
                    },
                ) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showWipeStep1 = false }) { Text("Cancel") } },
        )
    }

    if (showWipeStep2) {
        AlertDialog(
            onDismissRequest = { showWipeStep2 = false },
            title = { Text("Type WIPE to Confirm") },
            text = {
                OutlinedTextField(value = wipeConfirmInput, onValueChange = { wipeConfirmInput = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(
                    enabled = wipeConfirmInput.trim() == "WIPE",
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showWipeStep2 = false
                        viewModel.wipeAllData()
                    },
                ) { Text("Erase Everything") }
            },
            dismissButton = { TextButton(onClick = { showWipeStep2 = false }) { Text("Cancel") } },
        )
    }
}
