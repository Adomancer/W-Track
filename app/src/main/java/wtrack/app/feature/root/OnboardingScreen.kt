package wtrack.app.feature.root

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import java.io.InputStream
import wtrack.app.domain.model.AccountType
import wtrack.app.feature.common.CaptureStatusPill
import wtrack.app.feature.common.MoneyAmountTextField
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

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Welcome to W-Track",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Local-first encrypted expense tracker powered by Android Notification Access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.actionMessage != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.actionMessage, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onClearMessage) { Text("OK") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privacy & Boundaries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "• W-Track runs 100% offline with zero mandatory cloud sync.\n" +
                            "• Only notifications from apps you explicitly allowlist are read; OTPs, PINs, and credentials are rejected.\n" +
                            "• It does not use Accessibility Services or retrieve historical bank statements before setup.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("1. Notification Access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        CaptureStatusPill(state.captureSummary.appStatus)
                    }
                    Text(
                        text = if (state.captureSummary.isPermissionGranted) {
                            "Notification Access is granted."
                        } else {
                            "Required to capture transaction alerts automatically. On Android 13–16 sideloaded builds: App Info → ⋮ menu → Allow restricted settings before toggling Notification Access."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Battery & Autostart (ING-10): For baseline Gate G1 testing, keep default battery settings first. If vivo/iQOO OriginOS kills background capture, open App Info → Battery → Allow high background power consumption & Autostart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.captureSummary.isPermissionGranted) "Open Notification Access Settings" else "Grant Notification Access")
                    }
                    OutlinedButton(
                        onClick = {
                            val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(appInfoIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open App Info (Restricted Settings & Battery)")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("2. Base Currency & First Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    if (!state.hasAccounts) {
                        OutlinedTextField(
                            value = accountName,
                            onValueChange = { accountName = it },
                            label = { Text("Account name (e.g., Main Bank or Cash)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = accountProvider,
                            onValueChange = { accountProvider = it },
                            label = { Text("Institution / Wallet name (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save First Account")
                        }
                    } else {
                        Text("Initial account configured. You can add more accounts and link notification sources in the Accounts tab.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("3. Verify Capture (Self-Check)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Runs a local check of Notification Access, listener binding, encrypted DB health, and registered parsers without creating fake ledger rows (ING-08).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onRunSelfCheck,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Run Capture Self-Check")
                    }
                    state.selfCheckResult?.let { check ->
                        Text(
                            text = "• Notification Access: ${if (check.permissionGranted) "Granted" else "Missing"}\n" +
                                "• Listener Bound: ${if (check.listenerConnected) "Connected" else "Waiting"}\n" +
                                "• Encrypted DB: ${if (check.databaseReady) "Ready" else "Locked"}\n" +
                                "• Registered Parsers: ${check.registeredParserCount}\n" +
                                "• Dry-Run Parser: ${if (check.dryRunParserPassed) "Passed" else "Failed"}\n" +
                                check.summary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Restore from Backup Instead")
            }

            Button(
                onClick = onFinish,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enter W-Track Dashboard")
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
