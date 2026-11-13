package wtrack.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import wtrack.app.feature.accounts.AccountsScreen
import wtrack.app.feature.activity.ActivityScreen
import wtrack.app.feature.dashboard.DashboardScreen
import wtrack.app.feature.insights.InsightsScreen
import wtrack.app.feature.review.ReviewScreen

/**
 * The app navigation graph (PRD section 10.2): Home, Activity, Accounts, Insights
 * plus the prominent Needs Review entry. All are reachable from the bottom navigation bar.
 *
 * These destinations currently host contract-independent placeholders; feature content
 * is wired as the domain/repository contracts (docs/FRONTEND_CONTRACT.md) are implemented.
 */
@Composable
fun WtrackNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = TopLevelDestination.HOME.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        screenRoutes()
    }
}

private fun NavGraphBuilder.screenRoutes() {
    composable(TopLevelDestination.HOME.route) { DashboardScreen() }
    composable(TopLevelDestination.ACTIVITY.route) { ActivityScreen() }
    composable(TopLevelDestination.ACCOUNTS.route) { AccountsScreen() }
    composable(TopLevelDestination.INSIGHTS.route) { InsightsScreen() }
    composable(TopLevelDestination.REVIEW.route) { ReviewScreen() }
}