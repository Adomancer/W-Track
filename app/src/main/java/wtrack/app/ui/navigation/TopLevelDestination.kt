package wtrack.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import wtrack.app.R

/**
 * Top-level destinations reachable from the bottom navigation (PRD section 10.2):
 * Home, Activity, Accounts, Insights plus the prominent Needs Review entry.
 */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home),
    ACTIVITY("activity", R.string.nav_activity, Icons.AutoMirrored.Outlined.ReceiptLong),
    ACCOUNTS("accounts", R.string.nav_accounts, Icons.Outlined.AccountBalance),
    INSIGHTS("insights", R.string.nav_insights, Icons.Outlined.BarChart),
    REVIEW("review", R.string.nav_review, Icons.AutoMirrored.Outlined.Rule),
}