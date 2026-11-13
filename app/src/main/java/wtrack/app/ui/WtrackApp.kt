package wtrack.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import wtrack.app.ui.navigation.TopLevelDestination
import wtrack.app.ui.navigation.WtrackNavHost
import wtrack.app.ui.theme.WtrackTheme

/**
 * Application entry composable. Applies the Material 3 theme (PRD section 10.1) and hosts the
 * [Scaffold] with the bottom navigation (PRD section 10.2) and the navigation graph.
 */
@Composable
fun WtrackApp(modifier: Modifier = Modifier) {
    WtrackTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination

        Scaffold(
            modifier = modifier,
            bottomBar = {
                WtrackBottomBar(
                    currentDestination = currentDestination,
                    onNavigate = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier.padding(innerPadding),
            ) {
                WtrackNavHost(navController = navController)
            }
        }
    }
}

@Composable
private fun WtrackBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentDestination.isHierarchyIn(
                    destination.route,
                ),
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.labelRes),
                    )
                },
                label = {
                    Text(text = stringResource(destination.labelRes))
                },
            )
        }
    }
}

private fun NavDestination?.isHierarchyIn(route: String): Boolean =
    this?.hierarchy?.any { it.route == route } == true