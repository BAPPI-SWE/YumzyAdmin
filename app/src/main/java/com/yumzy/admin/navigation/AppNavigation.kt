package com.yumzy.admin.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Storefront // The icon now works!
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yumzy.admin.screens.LiveOrdersScreen
import com.yumzy.admin.screens.RestaurantListScreen // This is the 'address' we were missing!

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Orders : Screen("orders", "Orders", Icons.Default.List)
    object Restaurants : Screen("restaurants", "Restaurants", Icons.Default.Storefront)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val items = listOf(Screen.Orders, Screen.Restaurants)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Orders.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Orders.route) {
                LiveOrdersScreen()
            }
            composable(Screen.Restaurants.route) {
                RestaurantListScreen()
            }
        }
    }
}