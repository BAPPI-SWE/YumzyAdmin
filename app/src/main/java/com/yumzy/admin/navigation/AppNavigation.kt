package com.yumzy.admin.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yumzy.admin.screens.*

// Updated Screen definitions to include the new details screen with arguments
sealed class Screen(val route: String) {
    object Orders : Screen("orders")
    object Restaurants : Screen("restaurants")
    object Store : Screen("store")
    object Analytics : Screen("analytics")
    object RiderDetails : Screen("rider_details/{riderId}/{riderName}/{dateMillis}") {
        fun createRoute(riderId: String, riderName: String, dateMillis: Long): String {
            return "rider_details/$riderId/$riderName/$dateMillis"
        }
    }
}

// Data class to hold info for the bottom bar items
data class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem(Screen.Orders, "Orders", Icons.Default.List),
        BottomNavItem(Screen.Restaurants, "Restaurants", Icons.Default.Storefront),
        BottomNavItem(Screen.Store, "Store", Icons.Default.ShoppingCart),
        BottomNavItem(Screen.Analytics, "Analytics", Icons.Default.Analytics)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
            composable(Screen.Orders.route) { LiveOrdersScreen() }
            composable(Screen.Restaurants.route) { RestaurantListScreen() }
            composable(Screen.Store.route) { StoreManagementScreen() }
            composable(Screen.Analytics.route) { AnalyticsScreen(navController = navController) }
            composable(
                route = Screen.RiderDetails.route,
                arguments = listOf(
                    navArgument("riderId") { type = NavType.StringType },
                    navArgument("riderName") { type = NavType.StringType },
                    navArgument("dateMillis") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val riderId = backStackEntry.arguments?.getString("riderId") ?: ""
                val riderName = backStackEntry.arguments?.getString("riderName") ?: ""
                val dateMillis = backStackEntry.arguments?.getLong("dateMillis") ?: 0L
                RiderDetailsScreen(
                    riderId = riderId,
                    riderName = riderName,
                    dateMillis = dateMillis,
                    navController = navController
                )
            }
        }
    }
}