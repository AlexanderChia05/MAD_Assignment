package com.example.assignment.ui.theme.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

// Tabs for Admin area
enum class AdminTab { Inventory, Batching, Home, Sales, Purchase }

@Composable
fun AdminFloatingTabBar(
    navController: NavController,
    currentAdminTab: MutableState<AdminTab>,
    modifier: Modifier = Modifier
) {
    val items = listOf(AdminTab.Inventory, AdminTab.Batching, AdminTab.Home, AdminTab.Sales, AdminTab.Purchase)
    val current = currentAdminTab.value

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(26.dp),
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding()
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = current == tab,
                        onClick = {
                            if (current != tab) {
                                currentAdminTab.value = tab
                                when (tab) {
                                    AdminTab.Inventory -> {
                                        navController.navigate("admin_inventory") {
                                            launchSingleTop = true
                                        }
                                    }
                                    AdminTab.Batching -> {
                                        navController.navigate("admin_batching") {
                                            launchSingleTop = true
                                        }
                                    }
                                    AdminTab.Home -> {
                                        navController.navigate("admin") {
                                            launchSingleTop = true
                                        }
                                    }
                                    AdminTab.Sales -> {
                                        navController.navigate("admin_sales_dashboard") {
                                            launchSingleTop = true
                                        }
                                    }
                                    AdminTab.Purchase -> {
                                        navController.navigate("admin_purchasing") {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        },
                        border = BorderStroke(0.dp, Color.Transparent),
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size)
                    ) {
                        when (tab) {
                            AdminTab.Inventory -> Icon(
                                imageVector = Icons.Filled.Category,
                                contentDescription = "Inventory"
                            )
                            AdminTab.Batching -> Icon(
                                imageVector = Icons.Filled.ViewModule,
                                contentDescription = "Batching"
                            )
                            AdminTab.Home -> Icon(
                                imageVector = Icons.Filled.Home,
                                contentDescription = "Home"
                            )
                            AdminTab.Sales -> Icon(
                                imageVector = Icons.Filled.TrendingUp,
                                contentDescription = "Sales"
                            )
                            AdminTab.Purchase -> Icon(
                                imageVector = Icons.Filled.ShoppingCart,
                                contentDescription = "Purchase"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminLeftRail(
    navController: NavController,
    currentAdminTab: MutableState<AdminTab>,
    modifier: Modifier = Modifier
) {
    val items = listOf(AdminTab.Inventory, AdminTab.Batching, AdminTab.Home, AdminTab.Sales, AdminTab.Purchase)
    val current = currentAdminTab.value

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        modifier = modifier
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(12.dp)) {
            items.forEach { tab ->
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    SegmentedButton(
                        selected = current == tab,
                        onClick = {
                            if (current != tab) {
                                currentAdminTab.value = tab
                                when (tab) {
                                    AdminTab.Inventory -> {
                                        navController.navigate("admin_inventory") {
                                            launchSingleTop = true
                                        }
                                    }
                                    AdminTab.Batching -> {
                                        navController.navigate("admin_batching") {
                                            launchSingleTop = true
                                        }
                                    }
                                    AdminTab.Home -> {
                                        navController.navigate("admin") {
                                            launchSingleTop = true
                                        }
                                    }
                                    AdminTab.Sales -> {
                                        navController.navigate("admin_sales_dashboard") {
                                            launchSingleTop = true
                                        }
                                    }
                                    AdminTab.Purchase -> {
                                        navController.navigate("admin_purchasing") {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 1),
                        border = BorderStroke(0.dp, Color.Transparent),
                    ) {
                        when (tab) {
                            AdminTab.Inventory -> Icon(
                                imageVector = Icons.Filled.Category,
                                contentDescription = "Inventory"
                            )
                            AdminTab.Batching -> Icon(
                                imageVector = Icons.Filled.ViewModule,
                                contentDescription = "Batching"
                            )
                            AdminTab.Home -> Icon(
                                imageVector = Icons.Filled.Home,
                                contentDescription = "Home"
                            )
                            AdminTab.Sales -> Icon(
                                imageVector = Icons.Filled.TrendingUp,
                                contentDescription = "Sales"
                            )
                            AdminTab.Purchase -> Icon(
                                imageVector = Icons.Filled.ShoppingCart,
                                contentDescription = "Purchase"
                            )
                        }
                    }
                }
            }
        }
    }
}