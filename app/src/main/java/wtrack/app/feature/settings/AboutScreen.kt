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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wtrack.app.BuildConfig
import wtrack.app.R
import wtrack.app.core.config.AppLinks
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
        packageInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
    }.getOrDefault(BuildConfig.VERSION_NAME)
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
            WtrackTopBar(title = stringResource(R.string.settings_about), onBack = onBack)
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
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.label_version_format, versionName),
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
                        text = stringResource(R.string.about_privacy_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.about_privacy_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = ext.divider)
            }

            // Block 3: Useful links
            Column(modifier = Modifier.fillMaxWidth()) {
                V2SettingRow(
                    title = stringResource(R.string.about_github_title),
                    subtitle = stringResource(R.string.about_github_subtitle),
                    onClick = { openUrl(AppLinks.repositoryUrl) },
                )
                HorizontalDivider(color = ext.divider)
                V2SettingRow(
                    title = stringResource(R.string.about_releases_title),
                    subtitle = stringResource(R.string.about_releases_subtitle),
                    onClick = { openUrl(AppLinks.releasesUrl) },
                )
                HorizontalDivider(color = ext.divider)
                V2SettingRow(
                    title = stringResource(R.string.about_issues_title),
                    subtitle = stringResource(R.string.about_issues_subtitle),
                    onClick = { openUrl(AppLinks.issuesUrl) },
                )
                HorizontalDivider(color = ext.divider)
                V2SettingRow(
                    title = stringResource(R.string.about_license_title),
                    subtitle = stringResource(R.string.about_license_subtitle),
                    onClick = { openUrl(AppLinks.licenseUrl) },
                )
            }
        }
    }
}
