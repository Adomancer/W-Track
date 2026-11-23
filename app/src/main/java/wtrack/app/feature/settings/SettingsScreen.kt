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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.ThemeMode
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.V2SettingRow
import wtrack.app.feature.common.WtrackTopBar
import wtrack.app.feature.root.canUseDeviceAppLock

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
    onOpenRules: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenCustomParsers: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ext = WtrackTheme.colors

    var showBackupSheet by remember { mutableStateOf(false) }
    var showCategoriesSheet by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showCurrencySecuritySheet by remember { mutableStateOf(false) }

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

    val alertsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setLocalAlertsEnabled(granted)
    }

    val captureHealthy = state.captureSummary.appStatus == CaptureStatus.ACTIVE
    val captureSubtitle = when (state.captureSummary.appStatus) {
        CaptureStatus.ACTIVE -> "Working smoothly"
        CaptureStatus.DISCONNECTED -> "Paused — tap to check"
        CaptureStatus.ATTENTION -> "Needs attention"
        CaptureStatus.UNSUPPORTED -> "Manual mode"
    }
    val lastBackupLabel = state.settings.lastBackupAt?.let { ts ->
        "Last backed up ${UiDateHelper.formatDaysAgo(ts, System.currentTimeMillis()).lowercase()}"
    } ?: "Not backed up yet"
    val themeLabel = state.settings.themeMode.name.lowercase().replaceFirstChar(Char::uppercase)
    val appVersion = remember(context) { getAppVersionName(context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                WtrackTopBar(title = "Settings", onBack = onBack)
                if (state.isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(
                    onClick = { showWipeStep1 = true },
                    enabled = !state.isBusy,
                ) {
                    Text(
                        text = "Erase all data",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = "W-Track $appVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.mutedText,
                )
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

            if (state.showPostRestorePermissionPrompt) {
                item(key = "post-restore") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Backup restored — allow Notification Access to resume auto-capture.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                                shape = RoundedCornerShape(999.dp),
                            ) { Text("Allow access") }
                            TextButton(onClick = viewModel::dismissPostRestorePrompt) { Text("Dismiss") }
                        }
                    }
                }
            }

            // Block 1: Auto-capture status row bounded by hairlines
            item(key = "auto-capture-block") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = "Auto-capture",
                        subtitle = captureSubtitle,
                        leadingDotColor = if (captureHealthy) ext.inflow else ext.warning,
                        onClick = onOpenDiagnostics,
                    )
                    HorizontalDivider(color = ext.divider)
                }
            }

            // Block 2: 4 plain user-friendly settings rows
            item(key = "settings-list") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    V2SettingRow(
                        title = "Backup & export",
                        subtitle = lastBackupLabel,
                        onClick = { showBackupSheet = true },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = "Categories & rules",
                        subtitle = "${state.categories.size} categories · ${state.rules.size} rules",
                        onClick = { showCategoriesSheet = true },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = "Appearance",
                        subtitle = themeLabel,
                        onClick = { showAppearanceSheet = true },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = "Currency & security",
                        subtitle = buildString {
                            append(state.settings.baseCurrency)
                            if (state.settings.appLockEnabled) append(" · Lock on")
                        },
                        onClick = { showCurrencySecuritySheet = true },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = "About",
                        subtitle = "Version $appVersion",
                        onClick = onOpenAbout,
                    )
                }
            }
        }
    }

    // Sheet 1: Backup & Export (all existing backup & CSV features preserved)
    if (showBackupSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBackupSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Backup & export", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Backups are encrypted with your passphrase and stored wherever you choose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.pendingReviewCount > 0) {
                    Text(
                        text = "${state.pendingReviewCount} item(s) still in Needs Review won't be included until confirmed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.warning,
                    )
                }

                Button(
                    onClick = {
                        showBackupSheet = false
                        exportBackupLauncher.launch("wtrack-backup.wtrackbackup")
                    },
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("Back up now", style = MaterialTheme.typography.titleMedium) }

                OutlinedButton(
                    onClick = {
                        showBackupSheet = false
                        restoreBackupLauncher.launch(arrayOf("*/*"))
                    },
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) { Text("Restore backup") }

                HorizontalDivider(color = ext.divider)

                Text("Scheduled folder", style = MaterialTheme.typography.titleMedium)
                val savedFolderUri = state.settings.scheduledBackupFolderUri
                if (savedFolderUri == null) {
                    OutlinedButton(
                        onClick = { openBackupFolderLauncher.launch(null) },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Choose backup folder") }
                } else {
                    val folderLabel = Uri.parse(savedFolderUri).lastPathSegment ?: savedFolderUri
                    Text("Folder: $folderLabel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Backup reminders", style = MaterialTheme.typography.bodyLarge)
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
                                label = { Text("Every ${days}d") },
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                showBackupSheet = false
                                pendingScheduledFolderUri = savedFolderUri
                                backupPassphrase = ""
                                backupPassphraseConfirm = ""
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp),
                        ) { Text("Back up here") }
                        TextButton(
                            onClick = {
                                viewModel.setScheduledBackupConfig(null, false, state.settings.scheduledBackupIntervalDays)
                            },
                        ) { Text("Clear") }
                    }
                }

                Text("Reminder frequency", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = state.settings.backupReminderDays == days,
                            onClick = { viewModel.setBackupReminderThresholds(days, state.settings.backupReminderTxCount) },
                            label = { Text("${days}d") },
                        )
                    }
                    listOf(50, 100, 250).forEach { txCount ->
                        FilterChip(
                            selected = state.settings.backupReminderTxCount == txCount,
                            onClick = { viewModel.setBackupReminderThresholds(state.settings.backupReminderDays, txCount) },
                            label = { Text("$txCount tx") },
                        )
                    }
                }

                HorizontalDivider(color = ext.divider)

                TextButton(
                    onClick = {
                        showBackupSheet = false
                        showCsvWarning = true
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Export CSV (unencrypted)") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // Sheet 2: Categories, Rules & Parsers
    if (showCategoriesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategoriesSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Categories & rules",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                V2SettingRow(
                    title = "Categories",
                    trailingText = "${state.categories.size}",
                    onClick = {
                        showCategoriesSheet = false
                        onOpenCategories()
                    },
                )
                HorizontalDivider(color = ext.divider)
                V2SettingRow(
                    title = "Auto-categorization rules",
                    trailingText = "${state.rules.size}",
                    onClick = {
                        showCategoriesSheet = false
                        onOpenRules()
                    },
                )
                HorizontalDivider(color = ext.divider)
                V2SettingRow(
                    title = "Custom notification patterns",
                    trailingText = "${state.customParserRuleCount}",
                    onClick = {
                        showCategoriesSheet = false
                        onOpenCustomParsers()
                    },
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // Sheet 3: Appearance
    if (showAppearanceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppearanceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Choose how W-Track looks on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        AppearanceModeOption(
                            mode = mode,
                            selected = state.settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Sheet 4: Currency, App Lock & Privacy
    if (showCurrencySecuritySheet) {
        val lockSupported = remember(context) { canUseDeviceAppLock(context) }
        var lockUnavailableHint by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showCurrencySecuritySheet = false },
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
                Text("Currency & security", style = MaterialTheme.typography.titleLarge)

                Text("Main currency", style = MaterialTheme.typography.labelLarge)
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

                HorizontalDivider(color = ext.divider)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App lock", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Require fingerprint, face, or PIN to open.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = "Set up a screen lock in Android Settings first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.settings.appLockEnabled) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Immediate", 60 to "1 min", 300 to "5 min").forEach { (seconds, label) ->
                            FilterChip(
                                selected = state.settings.appLockTimeoutSeconds == seconds,
                                onClick = { viewModel.setAppLock(true, seconds) },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                HorizontalDivider(color = ext.divider)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Status reminders", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Alert me if capture pauses or items need review.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                Text("Keep notification history", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = state.settings.verificationWindowDays == days,
                            onClick = { viewModel.setVerificationWindowDays(days) },
                            label = { Text(if (days == 0) "Don't keep" else "${days}d") },
                        )
                    }
                }
                Text("Pause warning after", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 6, 12).forEach { hours ->
                        FilterChip(
                            selected = state.settings.heartbeatStaleHours == hours,
                            onClick = { viewModel.setHeartbeatStaleHours(hours) },
                            label = { Text("${hours}h") },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
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
            title = { Text("Protect backup with a passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose a passphrase (at least 8 characters). Keep it safe — it cannot be reset.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Passphrase") },
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
                ) { Text("Save backup") }
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
            title = { Text("Restore backup") },
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
            title = { Text("Export unencrypted CSV?") },
            text = {
                Text("CSV files are readable by any app. Use encrypted backup for secure storage.")
            },
            confirmButton = {
                Button(onClick = {
                    showCsvWarning = false
                    exportCsvLauncher.launch("wtrack-transactions.csv")
                }) { Text("Export CSV") }
            },
            dismissButton = { TextButton(onClick = { showCsvWarning = false }) { Text("Cancel") } },
        )
    }

    if (showWipeStep1) {
        AlertDialog(
            onDismissRequest = { showWipeStep1 = false },
            title = { Text("Erase all local data?") },
            text = { Text("This permanently deletes all transactions, accounts, logs, and settings from this device.") },
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
            title = { Text("Type WIPE to confirm") },
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
                ) { Text("Erase everything") }
            },
            dismissButton = { TextButton(onClick = { showWipeStep2 = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AppearanceModeOption(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = WtrackTheme.colors
    val label = mode.name.lowercase().replaceFirstChar(Char::uppercase)
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else ext.divider
    val borderWidth = if (selected) 2.dp else 1.dp

    Column(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(shape)
                .border(borderWidth, borderColor, shape),
        ) {
            when (mode) {
                ThemeMode.LIGHT -> MiniThemePreview(dark = false, modifier = Modifier.fillMaxSize())
                ThemeMode.DARK -> MiniThemePreview(dark = true, modifier = Modifier.fillMaxSize())
                ThemeMode.SYSTEM -> Row(modifier = Modifier.fillMaxSize()) {
                    MiniThemePreview(
                        dark = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    MiniThemePreview(
                        dark = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniThemePreview(
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = if (dark) Color(0xFF0B1413) else Color(0xFFFFFFFF)
    val ink = if (dark) Color(0xFFF2F7F6) else Color(0xFF101C1A)
    val accent = if (dark) Color(0xFF1FA392) else Color(0xFF0B6E63)
    val line = if (dark) Color(0xFF1C2B29) else Color(0xFFE6ECEA)

    Column(
        modifier = modifier
            .background(bg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accent),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(ink),
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(line),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(line),
        )
    }
}
