package wtrack.app.feature.root

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import wtrack.app.feature.common.WtrackCard
import wtrack.app.feature.dashboard.DashboardScreen
import wtrack.app.feature.dashboard.DashboardViewModel
import wtrack.app.feature.insights.InsightsScreen
import wtrack.app.feature.insights.InsightsViewModel
import wtrack.app.feature.review.ReviewScreen
import wtrack.app.feature.review.ReviewViewModel
import wtrack.app.feature.settings.AboutScreen
import wtrack.app.feature.settings.CategoriesScreen
import wtrack.app.feature.settings.CustomParsersScreen
import wtrack.app.feature.settings.CustomParsersViewModel
import wtrack.app.feature.settings.DiagnosticsScreen
import wtrack.app.feature.settings.RulesScreen
import wtrack.app.feature.settings.SettingsScreen
import wtrack.app.feature.settings.SettingsViewModel

private sealed class WtrackRoute(val route: String, val label: String, val icon: ImageVector? = null) {
    data object Dashboard : WtrackRoute("dashboard", "Home", Icons.Outlined.Home)
    data object Activity : WtrackRoute("activity", "Activity", Icons.AutoMirrored.Outlined.ReceiptLong)
    data object Accounts : WtrackRoute("accounts", "Accounts", Icons.Outlined.AccountBalanceWallet)
    data object Insights : WtrackRoute("insights", "Insights", Icons.Outlined.BarChart)
    data object Review : WtrackRoute("review", "Review")
    data object Settings : WtrackRoute("settings", "Settings")
    data object Rules : WtrackRoute("rules", "Rules")
    data object Categories : WtrackRoute("categories", "Categories")
    data object Diagnostics : WtrackRoute("diagnostics", "Diagnostics")
    data object About : WtrackRoute("about", "About")
    data object CustomParsers : WtrackRoute("custom_parsers?sourceId={sourceId}", "Parsers")
    data object TransactionDetail : WtrackRoute("transaction_detail/{txId}", "Detail")
}

private val topLevelTabs = listOf(
    WtrackRoute.Dashboard,
    WtrackRoute.Activity,
    WtrackRoute.Accounts,
    WtrackRoute.Insights,
)

private val topLevelRouteSet = topLevelTabs.map { it.route }.toSet()

@Composable
fun WtrackApp(rootViewModel: MainRootViewModel = hiltViewModel()) {
    val rootState by rootViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalActivity.current

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> rootViewModel.onAppForegrounded()
                Lifecycle.Event.ON_RESUME -> rootViewModel.refreshCaptureStatus()
                Lifecycle.Event.ON_STOP -> {
                    if (activity?.isChangingConfigurations != true) {
                        rootViewModel.onAppBackgrounded()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val themeMode = (rootState as? RootUiState.Ready)?.themeMode ?: ThemeMode.SYSTEM
    val showAppLockOverlay = when (val state = rootState) {
        is RootUiState.Ready -> state.isAppLocked
        is RootUiState.Onboarding -> state.isAppLocked
        else -> false
    }

    WtrackTheme(themeMode = themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = rootState) {
                is RootUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                        onRunSelfCheck = rootViewModel::runCaptureSelfCheck,
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

            if (showAppLockOverlay) {
                AppLockOverlay(onUnlocked = rootViewModel::unlockApp)
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(color = WtrackTheme.colors.divider)
                    NavigationBar(
                        containerColor = WtrackTheme.colors.navBarSurface,
                        tonalElevation = 0.dp,
                    ) {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != tab.route) {
                                        navigateSingleTop(tab.route)
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon!!,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(21.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = WtrackTheme.colors.mutedText,
                                    unselectedTextColor = WtrackTheme.colors.mutedText,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (showPostRestorePermissionPrompt) {
                WtrackCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Backup restored — grant Notification Access to resume capture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                                shape = RoundedCornerShape(999.dp),
                            ) { Text("Allow access") }
                            TextButton(onClick = onDismissPostRestorePrompt) { Text("Dismiss") }
                        }
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = WtrackRoute.Dashboard.route,
                modifier = Modifier.weight(1f),
                enterTransition = {
                    val isTabSwitch = initialState.destination.route in topLevelRouteSet &&
                        targetState.destination.route in topLevelRouteSet
                    if (isTabSwitch) {
                        fadeIn(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing))
                    } else {
                        slideInHorizontally(
                            animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> fullWidth / 5 },
                        ) + fadeIn(animationSpec = tween(durationMillis = 170))
                    }
                },
                exitTransition = {
                    val isTabSwitch = initialState.destination.route in topLevelRouteSet &&
                        targetState.destination.route in topLevelRouteSet
                    if (isTabSwitch) {
                        fadeOut(animationSpec = tween(durationMillis = 90, easing = LinearOutSlowInEasing))
                    } else {
                        slideOutHorizontally(
                            animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> -fullWidth / 10 },
                        ) + fadeOut(animationSpec = tween(durationMillis = 130))
                    }
                },
                popEnterTransition = {
                    val isTabSwitch = initialState.destination.route in topLevelRouteSet &&
                        targetState.destination.route in topLevelRouteSet
                    if (isTabSwitch) {
                        fadeIn(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing))
                    } else {
                        slideInHorizontally(
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> -fullWidth / 10 },
                        ) + fadeIn(animationSpec = tween(durationMillis = 160))
                    }
                },
                popExitTransition = {
                    val isTabSwitch = initialState.destination.route in topLevelRouteSet &&
                        targetState.destination.route in topLevelRouteSet
                    if (isTabSwitch) {
                        fadeOut(animationSpec = tween(durationMillis = 90, easing = LinearOutSlowInEasing))
                    } else {
                        slideOutHorizontally(
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> fullWidth / 5 },
                        ) + fadeOut(animationSpec = tween(durationMillis = 130))
                    }
                },
            ) {
                composable(WtrackRoute.Dashboard.route) {
                    DashboardScreen(
                        viewModel = dashboardVm,
                        onOpenReview = { navController.navigate(WtrackRoute.Review.route) },
                        onOpenInsights = { navigateSingleTop(WtrackRoute.Insights.route) },
                        onOpenDiagnostics = { navController.navigate(WtrackRoute.Diagnostics.route) },
                        onOpenAccounts = { navigateSingleTop(WtrackRoute.Accounts.route) },
                        onOpenActivity = { navigateSingleTop(WtrackRoute.Activity.route) },
                        onOpenTransaction = { txId -> navController.navigate("transaction_detail/$txId") },
                        onOpenSettings = { navController.navigate(WtrackRoute.Settings.route) },
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
                        onOpenCustomParsers = { sourceId ->
                            val target = if (sourceId.isNullOrBlank()) {
                                "custom_parsers"
                            } else {
                                "custom_parsers?sourceId=${Uri.encode(sourceId)}"
                            }
                            navController.navigate(target)
                        },
                    )
                }
                composable(WtrackRoute.Insights.route) {
                    InsightsScreen(viewModel = insightsVm)
                }
                composable(WtrackRoute.Review.route) {
                    ReviewScreen(
                        viewModel = reviewVm,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(WtrackRoute.Settings.route) {
                    SettingsScreen(
                        viewModel = settingsVm,
                        onBack = { navController.popBackStack() },
                        onOpenRules = { navController.navigate(WtrackRoute.Rules.route) },
                        onOpenCategories = { navController.navigate(WtrackRoute.Categories.route) },
                        onOpenDiagnostics = { navController.navigate(WtrackRoute.Diagnostics.route) },
                        onOpenCustomParsers = { navController.navigate("custom_parsers") },
                        onOpenAbout = { navController.navigate(WtrackRoute.About.route) },
                    )
                }
                composable(WtrackRoute.About.route) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = WtrackRoute.CustomParsers.route,
                    arguments = listOf(
                        navArgument("sourceId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { entry ->
                    val customParsersVm: CustomParsersViewModel = hiltViewModel()
                    val initialSourceId = entry.arguments?.getString("sourceId")
                    CustomParsersScreen(
                        viewModel = customParsersVm,
                        initialSourceId = initialSourceId,
                        onBack = { navController.popBackStack() },
                    )
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
