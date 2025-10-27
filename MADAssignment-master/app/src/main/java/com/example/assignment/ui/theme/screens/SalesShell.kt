package com.example.assignment.ui.theme.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.assignment.data.SalesAgentHandle
import com.example.assignment.viewmodel.LoginScreenViewModel
import com.example.assignment.viewmodel.SalesAgentViewModel

@Composable
fun SalesShell(
    parentNav: NavHostController,
    loginViewModel: LoginScreenViewModel,
    salesViewModel: SalesAgentViewModel,
    currentTab: MutableState<SalesTab>
) {
    val role by loginViewModel.role
    if (role != "sales") {
        LaunchedEffect(Unit) {
            parentNav.navigate("login") { popUpTo("sales") { inclusive = true } }
        }
        return
    }

    val salesAgentId = loginViewModel.currentUser.value?.uid ?: run {
        Log.e("SalesShell", "No user logged in")
        LaunchedEffect(Unit) {
            parentNav.navigate("login") { popUpTo("sales") { inclusive = true } }
        }
        return
    }

    val salesNav = rememberNavController()

    LaunchedEffect(salesAgentId) {
        salesNav.currentBackStackEntry?.arguments?.putString("salesAgentId", salesAgentId)
    }

    AdaptiveSalesScaffold(
        salesNav = salesNav,
        salesAgentId = salesAgentId,
        currentTab = currentTab
    ) { innerPadding ->
        // Don’t apply full inner padding here; each screen will take only the TOP inset.
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = salesNav,
                startDestination = "sales_dashboard",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("sales_dashboard") {
                    SalesAgentScreen(
                        navController = salesNav,
                        parentNavController = parentNav,
                        loginViewModel = loginViewModel,
                        salesViewModel = salesViewModel,
                        currentTab = currentTab
                    )
                }
                composable("agent_profile") { AgentProfileScreen(navController = salesNav) }
                composable("add_order?salesAgentId={salesAgentId}") {
                    AddOrderScreen(salesNav, salesViewModel)
                }
                composable("commission?salesAgentId={salesAgentId}") {
                    CommissionScreen(salesNav, salesAgentId, SalesAgentHandle())
                }
                composable("all_orders?salesAgentId={salesAgentId}") {
                    AllOrdersScreen(salesNav, salesViewModel)
                }
                composable("order_detail/{orderJson}?salesAgentId={salesAgentId}") { backStackEntry ->
                    val orderJson = backStackEntry.arguments?.getString("orderJson")
                    OrderDetailScreen(
                        navController = salesNav,
                        orderJson = orderJson,
                        salesViewModel = salesViewModel
                    )
                }
            }
        }
    }
}
