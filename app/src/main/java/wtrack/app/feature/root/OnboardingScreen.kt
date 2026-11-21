package wtrack.app.feature.root

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.InputStream
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.domain.model.AccountType
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.CaptureStatusPill
import wtrack.app.feature.common.MoneyAmountTextField
import wtrack.app.feature.common.ProviderBadgeAvatar
import wtrack.app.feature.common.StatusPill
import wtrack.app.feature.common.WtrackBrandMark
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.common.isValidMoneyInput

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    state: RootUiState.Onboarding,
    onSelectBaseCurrency: (String) -> Unit,
    onCreateFirstAccount: (String, String, AccountType, String, String) -> Unit,
    onRestoreBackup: (String, () -> InputStream?) -> Unit,
    onRunSelfCheck: () -> Unit = {},
    onFinish: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val context = LocalContext.current
    val ext = WtrackTheme.colors
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }

    var accountName by remember { mutableStateOf("") }
    var accountProvider by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf(AccountType.BANK) }
    var selectedCurrency by remember(state.settings.baseCurrency) { mutableStateOf(state.settings.baseCurrency) }
    var openingBalance by remember { mutableStateOf("") }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassphrase by remember { mutableStateOf("") }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            restorePassphrase = ""
        }
    }

    val quickProviders = remember {
        listOf(
            "Jago" to AccountType.BANK,
            "myBCA" to AccountType.BANK,
            "Mandiri" to AccountType.BANK,
            "DANA" to AccountType.EWALLET,
            "OVO" to AccountType.EWALLET,
            "GoPay" to AccountType.EWALLET,
            "Cash" to AccountType.CASH,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top step indicator row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (stepIndex > 0) {
                        IconButton(
                            onClick = { stepIndex = (stepIndex - 1).coerceAtLeast(0) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Previous step",
                            )
                        }
                    } else {
                        WtrackBrandMark(size = 28.dp, cornerRadius = 8.dp)
                    }
                    Text(
                        text = "Step ${stepIndex + 1} of 3",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (idx in 0..2) {
                        Box(
                            modifier = Modifier
                                .size(width = if (idx == stepIndex) 26.dp else 14.dp, height = 6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (idx <= stepIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                }
            }

            if (state.actionMessage != null) {
                ActionFeedbackBanner(
                    message = state.actionMessage,
                    onDismiss = onClearMessage,
                )
            }

            when (stepIndex) {
                0 -> {
                    // Step 1: Privacy & Trust Hero (Screen 07)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            WtrackBrandMark(size = 56.dp, cornerRadius = 16.dp)
                        }
                        Text(
                            text = "Welcome to W-Track",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Local-first encrypted expense tracker powered by Android Notification Access.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ext.mutedText,
                            textAlign = TextAlign.Center,
                        )
                    }

                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = "Privacy & Security Boundaries",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            PrivacyPillarRow(
                                icon = Icons.Outlined.CloudOff,
                                title = "100% Offline & Encrypted",
                                description = "W-Track runs 100% offline with zero mandatory cloud sync. Your ledger is encrypted locally with SQLCipher.",
                            )
                            HorizontalDivider(color = ext.divider)
                            PrivacyPillarRow(
                                icon = Icons.Outlined.FilterAlt,
                                title = "Strict Allowlist Capture",
                                description = "Only notifications from apps you explicitly allowlist are read; OTPs, PINs, and credentials are rejected.",
                            )
                            HorizontalDivider(color = ext.divider)
                            PrivacyPillarRow(
                                icon = Icons.Outlined.VerifiedUser,
                                title = "No Accessibility Services",
                                description = "Never uses Accessibility Services and never retrieves historical bank statements before setup.",
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Button(
                        onClick = { stepIndex = 1 },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text("Continue Setup", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Restore from Backup Instead")
                    }
                }

                1 -> {
                    // Step 2: Notification Access, Battery & Self-Check (Screen 08)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Enable Auto-Capture",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "Grant Notification Access so W-Track can parse transaction alerts automatically on-device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                        )
                    }

                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text("Notification Access", style = MaterialTheme.typography.titleMedium)
                                }
                                CaptureStatusPill(state.captureSummary.appStatus)
                            }

                            Text(
                                text = if (state.captureSummary.isPermissionGranted) {
                                    "Notification Access is granted."
                                } else {
                                    "Required to capture transaction alerts automatically. On Android 13–16 sideloaded builds: App Info → ⋮ menu → Allow restricted settings before toggling Notification Access."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.captureSummary.isPermissionGranted) ext.inflow else MaterialTheme.colorScheme.onSurface,
                            )

                            if (state.captureSummary.isPermissionGranted) {
                                OutlinedButton(
                                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Open Notification Access Settings")
                                }
                            } else {
                                Button(
                                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Grant Notification Access")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(appInfoIntent)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" Open App Info (Restricted Settings & Battery)")
                            }
                        }
                    }

                    // Battery & OriginOS Guidance (ING-10)
                    Surface(
                        color = ext.infoTintBg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ext.infoTintBorder),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BatteryChargingFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Battery & Autostart (ING-10): For baseline Gate G1 testing, keep default battery settings first. If vivo/iQOO OriginOS kills background capture, open App Info → Battery → Allow high background power consumption & Autostart.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Supported PoC Parsers Card
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                                ProviderBadgeAvatar("Jago", size = 32.dp)
                                ProviderBadgeAvatar("DANA", size = 32.dp)
                                ProviderBadgeAvatar("myBCA", size = 32.dp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Built-in Indonesian Parsers Ready", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "On-device templates for Bank Jago, DANA, and myBCA with zero external calls.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                )
                            }
                        }
                    }

                    // Capture Self-Check Card (ING-08)
                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Verify Capture (Self-Check)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Runs a local check of Notification Access, listener binding, encrypted DB health, and registered parsers without creating fake ledger rows (ING-08).",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                            OutlinedButton(
                                onClick = onRunSelfCheck,
                                enabled = !state.isBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Run Capture Self-Check")
                            }
                            state.selfCheckResult?.let { check ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        SelfCheckCell(
                                            label = "Notification Access",
                                            value = if (check.permissionGranted) "Granted" else "Missing",
                                            ok = check.permissionGranted,
                                            modifier = Modifier.weight(1f),
                                        )
                                        SelfCheckCell(
                                            label = "Listener Bound",
                                            value = if (check.listenerConnected) "Connected" else "Waiting",
                                            ok = check.listenerConnected,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        SelfCheckCell(
                                            label = "Encrypted DB",
                                            value = if (check.databaseReady) "Ready" else "Locked",
                                            ok = check.databaseReady,
                                            modifier = Modifier.weight(1f),
                                        )
                                        SelfCheckCell(
                                            label = "Dry-Run Parser (${check.registeredParserCount})",
                                            value = if (check.dryRunParserPassed) "Passed" else "Failed",
                                            ok = check.dryRunParserPassed,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Text(
                                        text = check.summary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { stepIndex = 0 },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text("Back") }
                        Button(
                            onClick = { stepIndex = 2 },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.4f),
                        ) { Text("Next: Currency & Account") }
                    }
                }

                else -> {
                    // Step 3: Base Currency & First Account
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Currency & First Account",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "Choose your reporting currency and add an initial account to begin tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                        )
                    }

                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Base Currency", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Used for dashboard totals, budgets, and spending comparisons.",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                listOf("IDR", "USD", "SGD", "MYR", "EUR", "JPY").forEach { code ->
                                    FilterChip(
                                        selected = selectedCurrency == code,
                                        onClick = {
                                            selectedCurrency = code
                                            onSelectBaseCurrency(code)
                                        },
                                        label = { Text(code) },
                                    )
                                }
                            }
                        }
                    }

                    WtrackCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("First Account", style = MaterialTheme.typography.titleMedium)
                            if (!state.hasAccounts) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    quickProviders.forEach { (preset, presetType) ->
                                        FilterChip(
                                            selected = accountProvider.equals(preset, ignoreCase = true),
                                            onClick = {
                                                accountProvider = preset
                                                accountType = presetType
                                                if (accountName.isBlank()) accountName = "$preset Main"
                                            },
                                            label = { Text(preset) },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = accountName,
                                    onValueChange = { accountName = it },
                                    label = { Text("Account name (e.g., Main Bank or Cash)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = accountProvider,
                                    onValueChange = { accountProvider = it },
                                    label = { Text("Institution / Wallet name (optional)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    AccountType.entries.forEach { t ->
                                        FilterChip(
                                            selected = accountType == t,
                                            onClick = { accountType = t },
                                            label = { Text(t.name) },
                                        )
                                    }
                                }
                                MoneyAmountTextField(
                                    value = openingBalance,
                                    onValueChange = { openingBalance = it },
                                    currency = selectedCurrency,
                                    label = "Opening balance ($selectedCurrency, optional)",
                                    allowZero = true,
                                    allowNegative = true,
                                    isOptional = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedButton(
                                    enabled = accountName.isNotBlank() &&
                                        isValidMoneyInput(openingBalance, selectedCurrency, allowZero = true, allowNegative = true, isOptional = true),
                                    onClick = {
                                        onCreateFirstAccount(accountName, accountProvider, accountType, selectedCurrency, openingBalance)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Save First Account")
                                }
                            } else {
                                Surface(
                                    color = ext.inflowContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircleOutline,
                                            contentDescription = null,
                                            tint = ext.inflow,
                                        )
                                        Text(
                                            text = "Initial account configured. You can add more accounts and link notification sources in the Accounts tab.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ext.onInflowContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { stepIndex = 1 },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(0.75f),
                        ) { Text("Back") }
                        Button(
                            onClick = onFinish,
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.5f),
                        ) {
                            Text("Enter W-Track Dashboard")
                        }
                    }

                    TextButton(
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Restore from Encrypted Backup Instead")
                    }
                }
            }
        }
    }

    if (pendingRestoreUri != null) {
        val uri = pendingRestoreUri!!
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore Backup") },
            text = {
                OutlinedTextField(
                    value = restorePassphrase,
                    onValueChange = { restorePassphrase = it },
                    label = { Text("Backup passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    val pw = restorePassphrase
                    pendingRestoreUri = null
                    onRestoreBackup(pw) { context.contentResolver.openInputStream(uri) }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PrivacyPillarRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .size(18.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = WtrackTheme.colors.mutedText,
            )
        }
    }
}

@Composable
private fun SelfCheckCell(
    label: String,
    value: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
) {
    val ext = WtrackTheme.colors
    Surface(
        modifier = modifier,
        color = if (ok) ext.inflowContainer else ext.warningContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (ok) ext.inflow.copy(alpha = 0.25f) else ext.warningBorder),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ext.mutedText,
            )
            StatusPill(
                label = value,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                contentColor = if (ok) ext.onInflowContainer else ext.onWarningContainer,
                dotColor = if (ok) ext.inflow else ext.warning,
            )
        }
    }
}
