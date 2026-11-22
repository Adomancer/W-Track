package wtrack.app.feature.root

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal const val WTRACK_BIOMETRIC_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

internal fun canUseDeviceAppLock(context: Context): Boolean {
    val result = BiometricManager.from(context).canAuthenticate(WTRACK_BIOMETRIC_AUTHENTICATORS)
    return result == BiometricManager.BIOMETRIC_SUCCESS
}

@Composable
internal fun AppLockOverlay(
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val triggerPrompt = remember(activity) {
        {
            if (activity == null) {
                statusMessage = "Unable to attach biometric prompt to host window."
            } else if (!canUseDeviceAppLock(activity)) {
                statusMessage = "No biometric or device PIN/pattern is enrolled on this device."
            } else {
                statusMessage = null
                launchDeviceCredentialPrompt(
                    activity = activity,
                    onSucceeded = onUnlocked,
                    onErrorOrCancel = { message ->
                        statusMessage = message
                    },
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        triggerPrompt()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "W-Track is Locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Authenticate with biometrics or your device PIN/pattern to view your ledger.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = triggerPrompt) {
                Text("Unlock W-Track")
            }
        }
    }
}

private fun launchDeviceCredentialPrompt(
    activity: FragmentActivity,
    onSucceeded: () -> Unit,
    onErrorOrCancel: (String) -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSucceeded()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onErrorOrCancel(errString.toString().ifBlank { "Authentication cancelled." })
            }

            override fun onAuthenticationFailed() {
                onErrorOrCancel("Authentication not recognized. Try again.")
            }
        },
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock W-Track")
        .setSubtitle("Verify your identity to access your private financial ledger")
        .setAllowedAuthenticators(WTRACK_BIOMETRIC_AUTHENTICATORS)
        .build()
    prompt.authenticate(promptInfo)
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
