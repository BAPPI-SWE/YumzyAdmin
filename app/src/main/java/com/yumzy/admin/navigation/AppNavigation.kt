package com.yumzy.admin.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
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
import com.yumzy.admin.screens.RestaurantListScreen
import com.yumzy.admin.screens.StoreManagementScreen

// A simple sealed class to define our navigation items
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Orders : Screen("orders", "Orders", Icons.Default.List)
    object Restaurants : Screen("restaurants", "Restaurants", Icons.Default.Storefront)
    object Store : Screen("store", "Store", Icons.Default.ShoppingCart) // New screen added
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Add the new screen to the list of items
    val items = listOf(Screen.Orders, Screen.Restaurants, Screen.Store)

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
            // Add the new screen destination to the NavHost
            composable(Screen.Store.route) {
                StoreManagementScreen()
            }
        }
    }
}