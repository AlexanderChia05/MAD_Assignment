package com.example.assignment.ui.theme.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.example.assignment.data.Order
import com.example.assignment.viewmodel.SalesAgentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllOrdersScreen(
    navController: NavController,
    salesViewModel: SalesAgentViewModel
) {
    val allOrders by salesViewModel.orders
    val salesAgentId = navController.currentBackStackEntry?.arguments?.getString("salesAgentId") ?: ""

    LaunchedEffect(salesAgentId) {
        if (salesAgentId.isNotEmpty()) {
            salesViewModel.fetchOrders(salesAgentId)
        }
    }

    // Same category groups as AddOrderScreen (for Type inference + Fit visibility)
    val shirtCategories = listOf(
        "Long Sleeve Shirts", "Half Sleeve Shirts", "Short Sleeve Shirts",
        "Sleeveless Shirts", "Sweatshirts", "Hoodie", "Tuxedo", "Polo",
        "Blouses", "Bra Tops", "Sweaters", "Jackets", "Coats"
    )
    val pantsCategories = listOf(
        "Shorts", "Jeans", "Cargo", "Chinos", "Wide Pants",
        "Ankle Pants", "Sweat Pants", "Easy Pants", "Leggings Pants"
    )

    fun typeFor(category: String): String =
        if (category in shirtCategories) "Tops"
        else if (category in pantsCategories) "Bottoms"
        else "Unknown"

    fun showFitFor(category: String, fit: String): Boolean =
        category in listOf("Long Sleeve Shirts", "Short Sleeve Shirts", "Tuxedo", "Long Pants", "Jeans") && fit.isNotEmpty()

    Scaffold(
        topBar = { TopAppBar(title = { Text("All Orders") }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (allOrders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No orders available",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                return@Column
            }

            val chunkedOrders = allOrders.chunked(2)
            chunkedOrders.forEachIndexed { chunkIndex, pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    pair.forEachIndexed { pairIndex, order: Order ->
                        val globalIndex = (chunkIndex * 2) + pairIndex + 1
                        val type = typeFor(order.category)
                        val showFit = showFitFor(order.category, order.fit)

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable {
                                    val json = Uri.encode(Gson().toJson(order))
                                    navController.navigate("order_detail/$json?salesAgentId=$salesAgentId") {
                                        launchSingleTop = true
                                    }
                                },
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Order $globalIndex",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .padding(bottom = 6.dp)
                                        .align(Alignment.CenterHorizontally)
                                )
                                Text(
                                    "Product Name: ${order.productName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Text(
                                    "Type: $type",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Text(
                                    "Category: ${order.category}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Text(
                                    "Size: ${order.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Text(
                                    "Quantity: ${order.quantity}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Text(
                                    "Price per Item: $${"%.2f".format(order.price)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (showFit) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Fit: ${order.fit}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                    if (pair.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}
