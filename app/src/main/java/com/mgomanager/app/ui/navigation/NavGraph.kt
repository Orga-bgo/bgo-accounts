package com.mgomanager.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mgomanager.app.ui.components.AppBottomNavigation
import com.mgomanager.app.ui.components.BottomNavItem
import com.mgomanager.app.ui.screens.backup.BackupScreen
import com.mgomanager.app.ui.screens.detail.DetailScreen
import com.mgomanager.app.ui.screens.home.HomeScreen
import com.mgomanager.app.ui.screens.settings.SettingsScreen
import com.mgomanager.app.ui.screens.logs.LogScreen
import com.mgomanager.app.ui.screens.idcompare.IdCompareScreen

sealed class Screen(val route: String) {
    // Bottom nav routes (mapped to BottomNavItem routes)
    object Home : Screen("accounts")  // Maps to BottomNavItem.Accounts
    object Backup : Screen("backup")
    object Settings : Screen("more")  // Maps to BottomNavItem.More
    object Logs : Screen("log")

    // Detail screens (not in bottom nav)
    object Detail : Screen("detail/{accountId}") {
        fun createRoute(accountId: Long) = "detail/$accountId"
    }
    object IdCompare : Screen("id_compare")
}

/**
 * Routes that should show the bottom navigation bar
 */
private val bottomNavRoutes = listOf(
    Screen.Home.route,
    Screen.Backup.route,
    Screen.Settings.route,
    Screen.Logs.route
)

/**
 * Check if current route should show bottom navigation
 */
private fun shouldShowBottomNav(route: String?): Boolean {
    return route in bottomNavRoutes
}

/**
 * Main screen with bottom navigation
 * This wraps the main app content with a persistent bottom navigation bar
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (shouldShowBottomNav(currentRoute)) {
                AppBottomNavigation(
                    currentRoute = currentRoute ?: Screen.Home.route,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            // Pop up to start destination to avoid building up back stack
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            // Avoid multiple copies of same destination
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected tab
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        AppNavGraph(
            navController = navController,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        // Bottom Nav Destinations
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(Screen.Backup.route) {
            BackupScreen(navController = navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }

        composable(Screen.Logs.route) {
            LogScreen(navController = navController)
        }

        // Detail screens (no bottom nav)
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId") ?: return@composable
            DetailScreen(navController = navController, accountId = accountId)
        }

        composable(Screen.IdCompare.route) {
            IdCompareScreen(navController = navController)
        }
    }
}
