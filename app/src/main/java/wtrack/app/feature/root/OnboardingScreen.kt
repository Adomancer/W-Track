package wtrack.app.feature.root

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircleOutline
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.InputStream
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.domain.model.AccountType
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.MoneyAmountTextField
import wtrack.app.feature.common.V2SettingRow
import wtrack.app.feature.common.V2StatusDot
import wtrack.app.feature.common.WtrackBrandMark
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
    var showBatteryGuide by remember { mutableStateOf(false) }

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
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (stepIndex) {
                    0 -> {
                        Button(
                            onClick = { stepIndex = 1 },
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) { Text("Get started", style = MaterialTheme.typography.titleMedium) }
                        TextButton(
                            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                            enabled = !state.isBusy,
                            modifier = Modifier.height(44.dp),
                        ) {
                            Text(
                                text = "Restore backup",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    1 -> {
                        Button(
                            onClick = { stepIndex = 2 },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) { Text("Continue", style = MaterialTheme.typography.titleMedium) }
                    }

                    else -> {
                        Button(
                            onClick = onFinish,
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) { Text("Finish", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            // Thin 3-segment progress stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (stepIndex > 0) {
                    IconButton(
                        onClick = { stepIndex = (stepIndex - 1).coerceAtLeast(0) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (idx in 0..2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (idx <= stepIndex) MaterialTheme.colorScheme.primary
                                    else ext.divider,
                                ),
                        )
                    }
                }
                Text(
                    text = "${stepIndex + 1} of 3",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.actionMessage != null) {
                ActionFeedbackBanner(
                    message = state.actionMessage,
                    onDismiss = onClearMessage,
                )
            }

            when (stepIndex) {
                0 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WtrackBrandMark(size = 48.dp, cornerRadius = 14.dp)
                        Text(
                            text = "Private by\ndefault.",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Automatic local ledger from your bank & e-wallet alerts.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        TrustLineRow(
                            title = "Stays on your phone",
                            subtitle = "No cloud servers, no bank logins",
                        )
                        HorizontalDivider(color = ext.divider)
                        TrustLineRow(
                            title = "Only apps you choose",
                            subtitle = "OTPs and login codes are ignored",
                        )
                        HorizontalDivider(color = ext.divider)
                        TrustLineRow(
                            title = "You stay in control",
                            subtitle = "Uncertain alerts wait for your confirmation",
                        )
                    }
                }

                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Turn on auto-capture",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Allow notification access so W-Track can read alerts on your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = ext.divider)
                        V2SettingRow(
                            title = "Notification access",
                            trailingText = if (state.captureSummary.isPermissionGranted) "Allowed" else "Turn on",
                            trailingColor = if (state.captureSummary.isPermissionGranted) ext.inflow else ext.warning,
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                        )
                        HorizontalDivider(color = ext.divider)
                        V2SettingRow(
                            title = "Run quick check",
                            subtitle = state.selfCheckResult?.summary ?: "Verify capture is ready",
                            trailingText = if (state.selfCheckResult?.dryRunParserPassed == true) "Ready" else null,
                            trailingColor = ext.inflow,
                            onClick = onRunSelfCheck,
                        )
                        HorizontalDivider(color = ext.divider)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Supported out of the box: Bank Jago, myBCA, DANA.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Need help with battery or restricted settings? ›",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showBatteryGuide = true }
                                .padding(vertical = 4.dp),
                        )
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Currency & first account",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Pick your main currency and optionally add your first account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Currency", style = MaterialTheme.typography.labelLarge)
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

                    if (!state.hasAccounts) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("First account", style = MaterialTheme.typography.labelLarge)
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
                                            if (accountName.isBlank()) accountName = preset
                                        },
                                        label = { Text(preset) },
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = accountName,
                                onValueChange = { accountName = it },
                                label = { Text("Account name") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MoneyAmountTextField(
                                value = openingBalance,
                                onValueChange = { openingBalance = it },
                                currency = selectedCurrency,
                                label = "Opening balance (optional)",
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
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            ) { Text("Save account") }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircleOutline,
                                contentDescription = null,
                                tint = ext.inflow,
                            )
                            Text(
                                text = "First account added.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = ext.inflow,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBatteryGuide) {
        AlertDialog(
            onDismissRequest = { showBatteryGuide = false },
            title = { Text("Battery & restricted settings") },
            text = {
                Text(
                    "If Android 13+ blocks Notification Access for sideloaded apps, open App Info → ⋮ → Allow restricted settings.\n\nIf alerts stop arriving in the background, allow high background power and Autostart in App Info → Battery.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = {
                    showBatteryGuide = false
                    val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(appInfoIntent)
                }) { Text("Open App Info") }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryGuide = false }) { Text("Close") }
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
private fun TrustLineRow(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
