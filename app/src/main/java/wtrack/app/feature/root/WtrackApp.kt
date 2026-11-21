package wtrack.app.feature.root

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import wtrack.app.core.theme.WtrackTheme
import wtrack.app.domain.model.ThemeMode
import wtrack.app.feature.accounts.AccountsScreen
import wtrack.app.feature.accounts.AccountsViewModel
import wtrack.app.feature.activity.ActivityScreen
import wtrack.app.feature.activity.ActivityViewModel
import wtrack.app.feature.activity.TransactionDetailScreen
import wtrack.app.feature.dashboard.DashboardScreen
import wtrack.app.feature.dashboard.DashboardViewModel
import wtrack.app.feature.insights.InsightsScreen
import wtrack.app.feature.insights.InsightsViewModel
import wtrack.app.feature.review.ReviewScreen
import wtrack.app.feature.review.ReviewViewModel
import wtrack.app.feature.settings.CategoriesScreen
import wtrack.app.feature.settings.DiagnosticsScreen
import wtrack.app.feature.settings.RulesScreen
import wtrack.app.feature.settings.SettingsScreen
import wtrack.app.feature.settings.SettingsViewModel

private sealed class WtrackRoute(val route: String, val label: String, val icon: ImageVector? = null) {
    data object Dashboard : WtrackRoute("dashboard", "Dashboard", Icons.Outlined.Dashboard)
    data object Activity : WtrackRoute("activity", "Activity", Icons.AutoMirrored.Outlined.ReceiptLong)
    data object Accounts : WtrackRoute("accounts", "Accounts", Icons.Outlined.AccountBalanceWallet)
    data object Review : WtrackRoute("review", "Review", Icons.Outlined.RateReview)
    data object Settings : WtrackRoute("settings", "Settings", Icons.Outlined.Settings)
    data object Insights : WtrackRoute("insights", "Insights")
    data object Rules : WtrackRoute("rules", "Rules")
    data object Categories : WtrackRoute("categories", "Categories")
    data object Diagnostics : WtrackRoute("diagnostics", "Diagnostics")
    data object TransactionDetail : WtrackRoute("transaction_detail/{txId}", "Detail")
}

private val topLevelTabs = listOf(
    WtrackRoute.Dashboard,
    WtrackRoute.Activity,
    WtrackRoute.Accounts,
    WtrackRoute.Review,
    WtrackRoute.Settings,
)

@Composable
fun WtrackApp(rootViewModel: MainRootViewModel = hiltViewModel()) {
    val rootState by rootViewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                rootViewModel.refreshCaptureStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val themeMode = (rootState as? RootUiState.Ready)?.themeMode ?: ThemeMode.SYSTEM

    WtrackTheme(themeMode = themeMode) {
        when (val state = rootState) {
            is RootUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is RootUiState.Locked -> {
                DatabaseLockedScreen(
                    state = state,
                    onRestoreBackup = rootViewModel::restoreFromBackup,
                    onWipeAllData = rootViewModel::wipeAllDataFromLocked,
                    onClearMessage = rootViewModel::clearMessage,
                )
            }

            is RootUiState.Onboarding -> {
                OnboardingScreen(
                    state = state,
                    onSelectBaseCurrency = rootViewModel::selectBaseCurrency,
                    onCreateFirstAccount = rootViewModel::createInitialAccount,
                    onRestoreBackup = rootViewModel::restoreFromBackup,
                    onFinish = rootViewModel::finishOnboarding,
                    onClearMessage = rootViewModel::clearMessage,
                )
            }

            is RootUiState.Ready -> {
                WtrackMainScaffold(
                    pendingReviewCount = state.pendingReviewCount,
                    showPostRestorePermissionPrompt = state.showPostRestorePermissionPrompt,
                    onDismissPostRestorePrompt = rootViewModel::dismissPostRestorePrompt,
                )
            }
        }
    }
}

@Composable
private fun WtrackMainScaffold(
    pendingReviewCount: Int,
    showPostRestorePermissionPrompt: Boolean,
    onDismissPostRestorePrompt: () -> Unit,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = topLevelTabs.any { it.route == currentRoute }

    val dashboardVm: DashboardViewModel = hiltViewModel()
    val activityVm: ActivityViewModel = hiltViewModel()
    val accountsVm: AccountsViewModel = hiltViewModel()
    val reviewVm: ReviewViewModel = hiltViewModel()
    val insightsVm: InsightsViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()

    fun navigateSingleTop(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigateSingleTop(tab.route) },
                            icon = {
                                val icon = tab.icon!!
                                if (tab == WtrackRoute.Review && pendingReviewCount > 0) {
                                    BadgedBox(badge = { Badge { Text(pendingReviewCount.toString()) } }) {
                                        Icon(icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (showPostRestorePermissionPrompt) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Backup restored — grant Notification Access to resume capture (ING-02).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }) { Text("Grant Notification Access") }
                            TextButton(onClick = onDismissPostRestorePrompt) { Text("Dismiss") }
                        }
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = WtrackRoute.Dashboard.route,
                modifier = Modifier.weight(1f),
            ) {
            composable(WtrackRoute.Dashboard.route) {
                DashboardScreen(
                    viewModel = dashboardVm,
                    onOpenReview = { navigateSingleTop(WtrackRoute.Review.route) },
                    onOpenInsights = { navController.navigate(WtrackRoute.Insights.route) },
                    onOpenDiagnostics = { navController.navigate(WtrackRoute.Diagnostics.route) },
                    onOpenAccounts = { navigateSingleTop(WtrackRoute.Accounts.route) },
                    onOpenTransaction = { txId -> navController.navigate("transaction_detail/$txId") },
                    onOpenSettingsBackup = { navigateSingleTop(WtrackRoute.Settings.route) },
                )
            }
            composable(WtrackRoute.Activity.route) {
                ActivityScreen(
                    viewModel = activityVm,
                    onOpenTransaction = { txId -> navController.navigate("transaction_detail/$txId") },
                )
            }
            composable(WtrackRoute.Accounts.route) {
                AccountsScreen(
                    viewModel = accountsVm,
                    onOpenDiagnostics = { navController.navigate(WtrackRoute.Diagnostics.route) },
                )
            }
            composable(WtrackRoute.Review.route) {
                ReviewScreen(viewModel = reviewVm)
            }
            composable(WtrackRoute.Settings.route) {
                SettingsScreen(
                    viewModel = settingsVm,
                    onOpenRules = { navController.navigate(WtrackRoute.Rules.route) },
                    onOpenCategories = { navController.navigate(WtrackRoute.Categories.route) },
                    onOpenDiagnostics = { navController.navigate(WtrackRoute.Diagnostics.route) },
                )
            }
            composable(WtrackRoute.Insights.route) {
                InsightsScreen(viewModel = insightsVm, onBack = { navController.popBackStack() })
            }
            composable(WtrackRoute.Rules.route) {
                RulesScreen(viewModel = settingsVm, onBack = { navController.popBackStack() })
            }
            composable(WtrackRoute.Categories.route) {
                CategoriesScreen(viewModel = settingsVm, onBack = { navController.popBackStack() })
            }
            composable(WtrackRoute.Diagnostics.route) {
                DiagnosticsScreen(viewModel = settingsVm, onBack = { navController.popBackStack() })
            }
            composable(
                route = WtrackRoute.TransactionDetail.route,
                arguments = listOf(navArgument("txId") { type = NavType.LongType }),
            ) { entry ->
                val txId = entry.arguments?.getLong("txId") ?: -1L
                TransactionDetailScreen(
                    transactionId = txId,
                    viewModel = activityVm,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        }
    }
}
