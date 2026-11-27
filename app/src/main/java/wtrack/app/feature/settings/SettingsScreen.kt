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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wtrack.app.R
import wtrack.app.core.i18n.AppLocaleHelper
import wtrack.app.core.theme.DarkColors
import wtrack.app.core.theme.DarkWtrackExtendedColors
import wtrack.app.core.theme.LightColors
import wtrack.app.core.theme.LightWtrackExtendedColors
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.domain.model.CaptureStatus
import wtrack.app.domain.model.CurrencyRegistry
import wtrack.app.domain.model.ThemeMode
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.UiDateHelper
import wtrack.app.feature.common.V2SettingRow
import wtrack.app.feature.common.WtrackTopBar
import wtrack.app.feature.common.labelRes
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

    val activeLocale = AppLocaleHelper.activeLocale(context)
    val captureHealthy = state.captureSummary.appStatus == CaptureStatus.ACTIVE
    val captureSubtitle = when (state.captureSummary.appStatus) {
        CaptureStatus.ACTIVE -> stringResource(R.string.settings_capture_working)
        CaptureStatus.DISCONNECTED -> stringResource(R.string.settings_capture_paused)
        CaptureStatus.ATTENTION -> stringResource(R.string.settings_capture_attention)
        CaptureStatus.UNSUPPORTED -> stringResource(R.string.settings_capture_manual)
    }
    val lastBackupLabel = if (state.settings.lastBackupAt > 0L) {
        stringResource(
            R.string.settings_last_backed_up,
            UiDateHelper.formatDaysAgo(state.settings.lastBackupAt, System.currentTimeMillis()).lowercase(activeLocale),
        )
    } else {
        stringResource(R.string.settings_not_backed_up_yet)
    }
    val themeLabel = stringResource(state.settings.themeMode.labelRes())
    val appVersion = remember(context) { getAppVersionName(context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                WtrackTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
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
                        text = stringResource(R.string.settings_erase_all_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringResource(R.string.settings_footer_version, appVersion),
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
                            text = stringResource(R.string.settings_post_restore_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                                shape = RoundedCornerShape(999.dp),
                            ) { Text(stringResource(R.string.post_restore_allow_access)) }
                            TextButton(onClick = viewModel::dismissPostRestorePrompt) {
                                Text(stringResource(R.string.action_dismiss))
                            }
                        }
                    }
                }
            }

            // Block 1: Auto-capture status row bounded by hairlines
            item(key = "auto-capture-block") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = stringResource(R.string.settings_auto_capture),
                        subtitle = captureSubtitle,
                        leadingDotColor = if (captureHealthy) ext.inflow else ext.warning,
                        onClick = onOpenDiagnostics,
                    )
                    HorizontalDivider(color = ext.divider)
                }
            }

            // Block 2: 5 plain user-friendly settings rows
            item(key = "settings-list") {
                val categoriesSummary = pluralStringResource(
                    R.plurals.settings_categories_count,
                    state.categories.size,
                    state.categories.size,
                )
                val rulesSummary = pluralStringResource(
                    R.plurals.settings_rules_count,
                    state.rules.size,
                    state.rules.size,
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    V2SettingRow(
                        title = stringResource(R.string.settings_backup_export),
                        subtitle = lastBackupLabel,
                        onClick = { showBackupSheet = true },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = stringResource(R.string.settings_categories_rules),
                        subtitle = "$categoriesSummary · $rulesSummary",
                        onClick = { showCategoriesSheet = true },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = stringResource(R.string.settings_appearance),
                        subtitle = themeLabel,
                        onClick = { showAppearanceSheet = true },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = stringResource(R.string.settings_currency_security),
                        subtitle = buildString {
                            append(state.settings.baseCurrency)
                            if (state.settings.appLockEnabled) append(stringResource(R.string.settings_lock_on_suffix))
                        },
                        onClick = { showCurrencySecuritySheet = true },
                    )
                    HorizontalDivider(color = ext.divider)
                    V2SettingRow(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.label_version_format, appVersion),
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
                Text(stringResource(R.string.settings_backup_export), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.settings_backup_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.pendingReviewCount > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.settings_backup_pending_review_warning,
                            state.pendingReviewCount,
                            state.pendingReviewCount,
                        ),
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
                ) { Text(stringResource(R.string.settings_back_up_now), style = MaterialTheme.typography.titleMedium) }

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
                ) { Text(stringResource(R.string.onboarding_restore_backup)) }

                HorizontalDivider(color = ext.divider)

                Text(stringResource(R.string.settings_scheduled_folder), style = MaterialTheme.typography.titleMedium)
                val savedFolderUri = state.settings.scheduledBackupFolderUri
                if (savedFolderUri == null) {
                    OutlinedButton(
                        onClick = { openBackupFolderLauncher.launch(null) },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_choose_backup_folder)) }
                } else {
                    val folderLabel = Uri.parse(savedFolderUri).lastPathSegment ?: savedFolderUri
                    Text(
                        stringResource(R.string.settings_folder_label, folderLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_backup_reminders), style = MaterialTheme.typography.bodyLarge)
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
                                label = { Text(stringResource(R.string.settings_every_n_days, days)) },
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
                        ) { Text(stringResource(R.string.settings_back_up_here)) }
                        TextButton(
                            onClick = {
                                viewModel.setScheduledBackupConfig(null, false, state.settings.scheduledBackupIntervalDays)
                            },
                        ) { Text(stringResource(R.string.action_clear)) }
                    }
                }

                Text(stringResource(R.string.settings_reminder_frequency), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = state.settings.backupReminderDays == days,
                            onClick = { viewModel.setBackupReminderThresholds(days, state.settings.backupReminderTxCount) },
                            label = { Text(stringResource(R.string.settings_n_days, days)) },
                        )
                    }
                    listOf(50, 100, 250).forEach { txCount ->
                        FilterChip(
                            selected = state.settings.backupReminderTxCount == txCount,
                            onClick = { viewModel.setBackupReminderThresholds(state.settings.backupReminderDays, txCount) },
                            label = { Text(stringResource(R.string.settings_n_tx, txCount)) },
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
                ) { Text(stringResource(R.string.settings_export_csv_unencrypted)) }
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
                    text = stringResource(R.string.settings_categories_rules),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                V2SettingRow(
                    title = stringResource(R.string.categories_title),
                    trailingText = "${state.categories.size}",
                    onClick = {
                        showCategoriesSheet = false
                        onOpenCategories()
                    },
                )
                HorizontalDivider(color = ext.divider)
                V2SettingRow(
                    title = stringResource(R.string.settings_auto_categorization_rules),
                    trailingText = "${state.rules.size}",
                    onClick = {
                        showCategoriesSheet = false
                        onOpenRules()
                    },
                )
                HorizontalDivider(color = ext.divider)
                V2SettingRow(
                    title = stringResource(R.string.settings_custom_notification_patterns),
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
                    Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(R.string.settings_appearance_desc),
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
                Text(stringResource(R.string.settings_currency_security), style = MaterialTheme.typography.titleLarge)

                Text(stringResource(R.string.settings_main_currency), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CurrencyRegistry.supportedCodes.forEach { cur ->
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
                        Text(stringResource(R.string.settings_app_lock), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.settings_app_lock_desc),
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
                        text = stringResource(R.string.settings_app_lock_setup_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.settings.appLockEnabled) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0 to R.string.settings_lock_immediate,
                            60 to R.string.settings_lock_1_min,
                            300 to R.string.settings_lock_5_min,
                        ).forEach { (seconds, labelRes) ->
                            FilterChip(
                                selected = state.settings.appLockTimeoutSeconds == seconds,
                                onClick = { viewModel.setAppLock(true, seconds) },
                                label = { Text(stringResource(labelRes)) },
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
                        Text(stringResource(R.string.settings_status_reminders), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.settings_status_reminders_desc),
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

                Text(stringResource(R.string.settings_keep_history), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = state.settings.verificationWindowDays == days,
                            onClick = { viewModel.setVerificationWindowDays(days) },
                            label = {
                                Text(
                                    if (days == 0) {
                                        stringResource(R.string.settings_dont_keep)
                                    } else {
                                        stringResource(R.string.settings_n_days, days)
                                    },
                                )
                            },
                        )
                    }
                }
                Text(stringResource(R.string.settings_pause_warning_after), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 6, 12).forEach { hours ->
                        FilterChip(
                            selected = state.settings.heartbeatStaleHours == hours,
                            onClick = { viewModel.setHeartbeatStaleHours(hours) },
                            label = { Text(stringResource(R.string.settings_n_hours, hours)) },
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
            title = { Text(stringResource(R.string.settings_protect_backup_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_protect_backup_desc),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text(stringResource(R.string.label_passphrase)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = backupPassphraseConfirm,
                        onValueChange = { backupPassphraseConfirm = it },
                        label = { Text(stringResource(R.string.label_confirm_passphrase)) },
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
                ) { Text(stringResource(R.string.settings_save_backup)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingExportUri = null
                    pendingScheduledFolderUri = null
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (pendingRestoreUri != null) {
        val uri = pendingRestoreUri!!
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.onboarding_restore_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = backupPassphrase,
                    onValueChange = { backupPassphrase = it },
                    label = { Text(stringResource(R.string.label_passphrase)) },
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
                }) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showCsvWarning) {
        AlertDialog(
            onDismissRequest = { showCsvWarning = false },
            title = { Text(stringResource(R.string.settings_csv_warning_title)) },
            text = {
                Text(stringResource(R.string.settings_csv_warning_desc))
            },
            confirmButton = {
                Button(onClick = {
                    showCsvWarning = false
                    exportCsvLauncher.launch("wtrack-transactions.csv")
                }) { Text(stringResource(R.string.settings_csv_export_action)) }
            },
            dismissButton = { TextButton(onClick = { showCsvWarning = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showWipeStep1) {
        AlertDialog(
            onDismissRequest = { showWipeStep1 = false },
            title = { Text(stringResource(R.string.settings_wipe_step1_title)) },
            text = { Text(stringResource(R.string.settings_wipe_step1_desc)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showWipeStep1 = false
                        wipeConfirmInput = ""
                        showWipeStep2 = true
                    },
                ) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = { TextButton(onClick = { showWipeStep1 = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showWipeStep2) {
        AlertDialog(
            onDismissRequest = { showWipeStep2 = false },
            title = { Text(stringResource(R.string.settings_wipe_step2_title, "WIPE")) },
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
                ) { Text(stringResource(R.string.settings_wipe_confirm_action)) }
            },
            dismissButton = { TextButton(onClick = { showWipeStep2 = false }) { Text(stringResource(R.string.action_cancel)) } },
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
    val label = stringResource(mode.labelRes())
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
    val scheme = if (dark) DarkColors else LightColors
    val ext = if (dark) DarkWtrackExtendedColors else LightWtrackExtendedColors
    val bg = scheme.background
    val ink = scheme.onBackground
    val accent = scheme.primary
    val line = ext.divider

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
