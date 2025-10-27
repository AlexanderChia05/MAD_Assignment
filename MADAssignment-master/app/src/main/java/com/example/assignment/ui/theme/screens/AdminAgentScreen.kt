package com.example.assignment.ui.theme.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.assignment.viewmodel.LoginScreenViewModel

@Composable
fun AdminAgentScreen(
    parentNav: NavController,
    loginViewModel: LoginScreenViewModel
) {
    // Admin internal nav controller (separate from parent)
    val adminNav = rememberNavController()
    val currentAdminTab = remember { mutableStateOf(AdminTab.Home) }

    val backStack by adminNav.currentBackStackEntryAsState()
    LaunchedEffect(backStack?.destination?.route) {
        when (backStack?.destination?.route?.substringBefore("?")) {
            "admin_inventory"      -> currentAdminTab.value = AdminTab.Inventory
            "admin_batching"       -> currentAdminTab.value = AdminTab.Batching
            "admin"                -> currentAdminTab.value = AdminTab.Home
            "admin_sales_dashboard"-> currentAdminTab.value = AdminTab.Sales
            "admin_purchasing"     -> currentAdminTab.value = AdminTab.Purchase
        }
    }

    AdaptiveAdminScaffold(
        adminNav = adminNav,
        currentAdminTab = currentAdminTab
    ) { innerPadding ->
        // Your existing NavHost stays the same
        NavHost(navController = adminNav, startDestination = "admin",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding))
        {
            composable("admin_inventory") { AdminInventoryScreen(adminNav) }
            composable("admin_batching")  { AdminBatchingScreen(adminNav, category = null) }
            composable("admin_batching?category={category}") { e ->
                AdminBatchingScreen(adminNav, e.arguments?.getString("category"))
            }
            composable("admin")                { AdminScreen(adminNav, loginViewModel, parentNav) }
            composable("admin_sales_dashboard"){ AdminSalesDashboardScreen(adminNav) }
            composable("admin_purchasing")     { AdminPurchasingScreen(adminNav) }
        }
    }
}