package wtrack.app.feature.root

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.InputStream
import wtrack.app.R
import wtrack.app.feature.common.ActionFeedbackBanner
import wtrack.app.feature.common.WtrackBrandMark
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.common.titleRes

@Composable
fun DatabaseLockedScreen(
    state: RootUiState.Locked,
    onRestoreBackup: (String, () -> InputStream?) -> Unit,
    onWipeAllData: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val context = LocalContext.current
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var showWipeStep1 by remember { mutableStateOf(false) }
    var showWipeStep2 by remember { mutableStateOf(false) }
    var wipeConfirmInput by remember { mutableStateOf("") }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            passphrase = ""
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WtrackBrandMark(size = 34.dp, cornerRadius = 10.dp)
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.db_locked_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            WtrackCard(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(state.reason.titleRes()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.db_locked_preserved_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp),
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

            if (state.isBusy) {
                CircularProgressIndicator()
            }

            Button(
                onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                enabled = !state.isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(stringResource(R.string.db_locked_restore_button))
            }

            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = { showWipeStep1 = true },
                enabled = !state.isBusy,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(stringResource(R.string.db_locked_erase_button))
            }
        }
    }

    if (pendingRestoreUri != null) {
        val uri = pendingRestoreUri!!
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.db_locked_enter_passphrase)) },
            text = {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.label_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    val pw = passphrase
                    pendingRestoreUri = null
                    onRestoreBackup(pw) { context.contentResolver.openInputStream(uri) }
                }) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showWipeStep1) {
        AlertDialog(
            onDismissRequest = { showWipeStep1 = false },
            title = { Text(stringResource(R.string.db_locked_wipe_step1_title)) },
            text = {
                Text(stringResource(R.string.db_locked_wipe_step1_body))
            },
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
            title = { Text(stringResource(R.string.db_locked_wipe_step2_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.db_locked_wipe_step2_prompt, "WIPE"))
                    OutlinedTextField(
                        value = wipeConfirmInput,
                        onValueChange = { wipeConfirmInput = it },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = wipeConfirmInput.trim() == "WIPE",
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showWipeStep2 = false
                        onWipeAllData()
                    },
                ) { Text(stringResource(R.string.db_locked_wipe_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { showWipeStep2 = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
