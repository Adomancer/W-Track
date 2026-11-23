package wtrack.app.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.feature.common.V2SettingRow
import wtrack.app.feature.common.WtrackBrandMark
import wtrack.app.feature.common.WtrackTopBar

internal fun getAppVersionName(context: Context): String {
    return runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName?.takeIf { it.isNotBlank() } ?: "1.0.0"
    }.getOrDefault("1.0.0")
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val ext = WtrackTheme.colors
    val versionName = remember(context) { getAppVersionName(context) }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WtrackTopBar(title = "About", onBack = onBack)
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
            // Block 1: App identity header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WtrackBrandMark(size = 64.dp, cornerRadius = 18.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "W-Track",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Private, local-first expense tracker",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.mutedText,
                )
            }

            // Block 2: Privacy promise
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = ext.divider)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Local & private",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "All transactions, accounts, and backups stay on your device. No cloud sync, no ads, and no tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = ext.divider)
            }

            // Block 3: Useful links
            Column(modifier = Modifier.fillMaxWidth()) {
                V2SettingRow(
                    title = "GitHub repository",
                    subtitle = "Source code & documentation",
                    onClick = { openUrl("https://github.com/Adomancer/W-Track") },
                )
                HorizontalDivider(color = ext.divider)
                V2SettingRow(
                    title = "Releases & changelog",
                    subtitle = "Check for the latest version",
                    onClick = { openUrl("https://github.com/Adomancer/W-Track/releases") },
                )
                HorizontalDivider(color = ext.divider)
                V2SettingRow(
                    title = "Report an issue",
                    subtitle = "Share feedback or report a bug",
                    onClick = { openUrl("https://github.com/Adomancer/W-Track/issues") },
                )
                HorizontalDivider(color = ext.divider)
                V2SettingRow(
                    title = "License",
                    subtitle = "GNU General Public License v3.0",
                    onClick = { openUrl("https://github.com/Adomancer/W-Track/blob/main/LICENSE") },
                )
            }
        }
    }
}
