package com.mgomanager.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom navigation tabs for the main app
 */
sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Accounts : BottomNavItem(
        route = "accounts",
        title = "Accounts",
        selectedIcon = Icons.Filled.AccountCircle,
        unselectedIcon = Icons.Outlined.AccountCircle
    )

    object Backup : BottomNavItem(
        route = "backup",
        title = "Backup",
        selectedIcon = Icons.Filled.Backup,
        unselectedIcon = Icons.Outlined.Backup
    )

    object More : BottomNavItem(
        route = "more",
        title = "Mehr",
        selectedIcon = Icons.Filled.Menu,
        unselectedIcon = Icons.Outlined.Menu
    )

    object Log : BottomNavItem(
        route = "log",
        title = "Log",
        selectedIcon = Icons.Filled.Article,
        unselectedIcon = Icons.Outlined.Article
    )
}

val bottomNavItems = listOf(
    BottomNavItem.Accounts,
    BottomNavItem.Backup,
    BottomNavItem.More,
    BottomNavItem.Log
)

@Composable
fun AppBottomNavigation(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title
                    )
                },
                label = { Text(item.title) }
            )
        }
    }
}
