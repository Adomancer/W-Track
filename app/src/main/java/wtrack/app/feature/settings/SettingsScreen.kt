package wtrack.app.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.domain.model.ThemeMode
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.CaptureStatusPill
import wtrack.app.feature.common.SegmentedOptionRow
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.common.WtrackTopBar
import wtrack.app.feature.root.canUseDeviceAppLock

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
    onOpenRules: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenCustomParsers: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val ext = WtrackTheme.colors

    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingScheduledFolderUri by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupPassphrase by remember { mutableStateOf("") }
    var backupPassphraseConfirm by remember { mutableStateOf("") }
    var showCsvWarning by remember { mutableStateOf(false) }
    var showWipeStep1 by remember { mutableStateOf(false) }
    var showWipeStep2 by remember { mutableStateOf(false) }
    var wipeConfirmInput by remember { mutableStateOf("") }

    val openBackupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setScheduledBackupConfig(
                folderUri = uri.toString(),
                enabled = true,
                intervalDays = state.settings.scheduledBackupIntervalDays,
            )
        }
    }

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

    Column(modifier = Modifier.fillMaxSize()) {
        WtrackTopBar(title = "Settings", onBack = onBack)

        if (state.isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

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

            if (state.showPostRestorePermissionPrompt) {
                item(key = "post-restore") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Backup restored — re-grant Notification Access so W-Track can resume capturing notifications (ING-02).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                ) { Text("Grant Notification Access") }
                                TextButton(onClick = viewModel::dismissPostRestorePrompt) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }

            // CAPTURE & AUTOMATION
            item(key = "sec-capture") {
                SettingsGroupLabel("CAPTURE & AUTOMATION")
            }
            item(key = "capture-card") {
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenDiagnostics)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(7.dp)
                                        .size(18.dp),
                                )
                            }
                            Column {
                                Text("Capture Diagnostics & Gate G1", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Listener status, gap log, OriginOS tips & logs",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CaptureStatusPill(state.captureSummary.appStatus)
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = ext.mutedText,
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = ext.divider)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenCustomParsers)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text("Notification Parsers", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Custom patterns for new banks & e-wallets",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${state.customParserRuleCount}", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = ext.mutedText,
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = ext.divider)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenRules)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Rule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Text("Categorization Rules", style = MaterialTheme.typography.titleSmall)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${state.rules.size}", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = ext.mutedText,
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = ext.divider)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenCategories)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Text("Categories", style = MaterialTheme.typography.titleSmall)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${state.categories.size}", style = MaterialTheme.typography.labelMedium, color = ext.mutedText)
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = ext.mutedText,
                            )
                        }
                    }
                }
            }

            // APPEARANCE & CURRENCY
            item(key = "sec-appearance") {
                SettingsGroupLabel("APPEARANCE & CURRENCY")
            }
            item(key = "appearance-card") {
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Theme", style = MaterialTheme.typography.titleSmall)
                            val modes = ThemeMode.entries
                            SegmentedOptionRow(
                                options = modes.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                                selectedIndex = modes.indexOf(state.settings.themeMode).coerceAtLeast(0),
                                onSelect = { idx -> viewModel.setThemeMode(modes[idx]) },
                            )
                        }
                        HorizontalDivider(color = ext.divider)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Base Currency (single-currency aggregates)", style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
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
            }

            // SECURITY & PRIVACY
            item(key = "sec-security") {
                SettingsGroupLabel("SECURITY")
            }
            item(key = "security-card") {
                val lockSupported = remember(context) { canUseDeviceAppLock(context) }
                var lockUnavailableHint by remember { mutableStateOf(false) }
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biometric & Device App Lock", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Require fingerprint, face, or device PIN/pattern before viewing ledger data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.mutedText,
                                )
                            }
                            Switch(
                                checked = state.settings.appLockEnabled,
                                onCheckedChange = { enable ->
                                    if (enable && !lockSupported) {
                                        lockUnavailableHint = true
                                    } else {
                                        lockUnavailableHint = false
                                        viewModel.setAppLock(enable, state.settings.appLockTimeoutSeconds)
                                    }
                                },
                            )
                        }
                        if (lockUnavailableHint || (!lockSupported && state.settings.appLockEnabled)) {
                            Text(
                                text = "Configure a screen lock PIN, pattern, or biometric in Android Settings to enable App Lock.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (state.settings.appLockEnabled) {
                            HorizontalDivider(color = ext.divider)
                            Text("Re-lock after leaving app", style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                listOf(
                                    0 to "Immediate",
                                    60 to "1 min",
                                    300 to "5 min",
                                ).forEach { (seconds, label) ->
                                    FilterChip(
                                        selected = state.settings.appLockTimeoutSeconds == seconds,
                                        onClick = { viewModel.setAppLock(true, seconds) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // PRIVACY & MONITORING
            item(key = "sec-retention") {
                SettingsGroupLabel("RETENTION & MONITORING")
            }
            item(key = "retention-card") {
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Verification window for raw notification text", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(0, 7, 14, 30).forEach { days ->
                                FilterChip(
                                    selected = state.settings.verificationWindowDays == days,
                                    onClick = { viewModel.setVerificationWindowDays(days) },
                                    label = { Text(if (days == 0) "0d (Immediate)" else "${days}d") },
                                )
                            }
                        }
                        HorizontalDivider(color = ext.divider)
                        Text("Heartbeat stale gap threshold", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(1, 3, 6, 12).forEach { hours ->
                                FilterChip(
                                    selected = state.settings.heartbeatStaleHours == hours,
                                    onClick = { viewModel.setHeartbeatStaleHours(hours) },
                                    label = { Text("${hours}h") },
                                )
                            }
                        }
                        HorizontalDivider(color = ext.divider)
                        val alertsPermissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission(),
                        ) { granted ->
                            viewModel.setLocalAlertsEnabled(granted)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Local Status Alerts", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Opt-in privacy-safe device alerts for Needs Review, capture pause, and overdue backups (never includes amounts or merchants).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.mutedText,
                                )
                            }
                            Switch(
                                checked = state.settings.localAlertsEnabled,
                                onCheckedChange = { enable ->
                                    if (!enable) {
                                        viewModel.setLocalAlertsEnabled(false)
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        alertsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.setLocalAlertsEnabled(true)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ENCRYPTED BACKUP & DATA SAFETY
            item(key = "sec-backup") {
                SettingsGroupLabel("BACKUP & DATA SAFETY")
            }
            item(key = "backup-card") {
                WtrackCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Encrypted Backups", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Backups are passphrase-encrypted with Argon2id + AES-256-GCM and omit raw notification payloads and unresolved Needs Review drafts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                        )
                        if (state.pendingReviewCount > 0) {
                            Surface(
                                color = ext.warningContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ext.warningBorder),
                            ) {
                                Text(
                                    text = "Pending Needs Review items (${state.pendingReviewCount}) will NOT be included in the backup. Resolve them first if you want them backed up.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.onWarningContainer,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }

                        Text("Backup reminder interval (days)", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(7, 14, 30).forEach { days ->
                                FilterChip(
                                    selected = state.settings.backupReminderDays == days,
                                    onClick = { viewModel.setBackupReminderThresholds(days, state.settings.backupReminderTxCount) },
                                    label = { Text("${days}d") },
                                )
                            }
                        }

                        Text("Backup reminder transaction threshold", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Create Encrypted Backup") }

                        HorizontalDivider(color = ext.divider)

                        Text("Scheduled Backup Folder (SAF • DATA-14)", style = MaterialTheme.typography.labelLarge)
                        val savedFolderUri = state.settings.scheduledBackupFolderUri
                        if (savedFolderUri == null) {
                            Text(
                                text = "No SAF folder selected. Choose a synced or local folder so reminders can target it directly without storing your passphrase.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                            OutlinedButton(
                                onClick = { openBackupFolderLauncher.launch(null) },
                                enabled = !state.isBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Choose SAF Backup Folder") }
                        } else {
                            val folderLabel = Uri.parse(savedFolderUri).lastPathSegment ?: savedFolderUri
                            Text("Saved folder: $folderLabel", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Scheduled reminder enabled", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = state.settings.scheduledBackupEnabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.setScheduledBackupConfig(
                                            savedFolderUri,
                                            enabled,
                                            state.settings.scheduledBackupIntervalDays,
                                        )
                                    },
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(3, 7, 14).forEach { days ->
                                    FilterChip(
                                        selected = state.settings.scheduledBackupIntervalDays == days,
                                        onClick = {
                                            viewModel.setScheduledBackupConfig(
                                                savedFolderUri,
                                                state.settings.scheduledBackupEnabled,
                                                days,
                                            )
                                        },
                                        label = { Text("${days}d") },
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    pendingScheduledFolderUri = savedFolderUri
                                    backupPassphrase = ""
                                    backupPassphraseConfirm = ""
                                },
                                enabled = !state.isBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Back Up to Saved Folder") }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { openBackupFolderLauncher.launch(null) },
                                    enabled = !state.isBusy,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                ) { Text("Change Folder") }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setScheduledBackupConfig(
                                            null,
                                            false,
                                            state.settings.scheduledBackupIntervalDays,
                                        )
                                    },
                                    enabled = !state.isBusy,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                ) { Text("Clear Folder") }
                            }
                        }

                        HorizontalDivider(color = ext.divider)

                        OutlinedButton(
                            onClick = { restoreBackupLauncher.launch(arrayOf("*/*")) },
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Restore Encrypted Backup") }

                        OutlinedButton(
                            onClick = { showCsvWarning = true },
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Export Unencrypted CSV") }
                    }
                }
            }

            // DANGER ZONE
            item(key = "sec-danger") {
                SettingsGroupLabel("DANGER ZONE", color = MaterialTheme.colorScheme.error)
            }
            item(key = "danger-card") {
                WtrackCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("Erase Local Data", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text = "Permanently deletes your SQLCipher database, wrapped keys, logs, and settings from this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                        )
                        OutlinedButton(
                            onClick = { showWipeStep1 = true },
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Erase All Stored Data") }
                    }
                }
            }
        }
    }

    if (pendingExportUri != null || pendingScheduledFolderUri != null) {
        val uri = pendingExportUri
        val folderUriStr = pendingScheduledFolderUri
        AlertDialog(
            onDismissRequest = {
                pendingExportUri = null
                pendingScheduledFolderUri = null
            },
            title = { Text(if (folderUriStr != null) "Encrypt Backup to Saved Folder" else "Encrypt Backup File") },
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
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = backupPassphraseConfirm,
                        onValueChange = { backupPassphraseConfirm = it },
                        label = { Text("Confirm passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
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
                        pendingScheduledFolderUri = null
                        if (uri != null) {
                            viewModel.exportEncryptedBackup(
                                passphrase = pw,
                                streamOpener = { context.contentResolver.openOutputStream(uri) },
                                verifyStreamOpener = { context.contentResolver.openInputStream(uri) },
                            )
                        } else if (folderUriStr != null) {
                            var createdDocUri: Uri? = null
                            viewModel.exportEncryptedBackup(
                                passphrase = pw,
                                streamOpener = {
                                    val treeUri = Uri.parse(folderUriStr)
                                    val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                                        treeUri,
                                        DocumentsContract.getTreeDocumentId(treeUri),
                                    )
                                    val fileName = "wtrack-backup-${System.currentTimeMillis()}.wtrackbackup"
                                    createdDocUri = DocumentsContract.createDocument(
                                        context.contentResolver,
                                        parentDocUri,
                                        "application/octet-stream",
                                        fileName,
                                    )
                                    createdDocUri?.let { context.contentResolver.openOutputStream(it) }
                                },
                                verifyStreamOpener = {
                                    createdDocUri?.let { context.contentResolver.openInputStream(it) }
                                },
                            )
                        }
                    },
                ) { Text("Encrypt & Verify") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingExportUri = null
                    pendingScheduledFolderUri = null
                }) { Text("Cancel") }
            },
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
                    shape = RoundedCornerShape(14.dp),
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
                OutlinedTextField(
                    value = wipeConfirmInput,
                    onValueChange = { wipeConfirmInput = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
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

@Composable
private fun SettingsGroupLabel(
    text: String,
    color: androidx.compose.ui.graphics.Color = WtrackTheme.colors.mutedText,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
