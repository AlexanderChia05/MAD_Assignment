package com.example.assignment.ui.theme.screens

import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.assignment.data.Order
import com.example.assignment.viewmodel.SalesAgentViewModel
import com.example.assignment.viewmodel.KeyedStateSupport.getOrCreateStateFlow
import com.example.assignment.viewmodel.KeyedStateSupport.updateKey
import com.google.gson.Gson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    navController: NavController,
    orderJson: String?,
    salesViewModel: SalesAgentViewModel
) {
    val uiVm: OrderDetailUiStateViewModel = viewModel() // must be public class

    val order = remember(orderJson) {
        try {
            val decodedJson = Uri.decode(orderJson)
            Gson().fromJson(decodedJson, Order::class.java)
        } catch (e: Exception) {
            Log.e("OrderDetailScreen", "Deserialization error: ${e.message}")
            null
        }
    }

    // Observe UI state
    val showEditDialog by uiVm.showEditDialog.collectAsState()
    val editProductName by uiVm.editProductName.collectAsState()
    val editSize by uiVm.editSize.collectAsState()
    val editQuantity by uiVm.editQuantity.collectAsState()
    val editPrice by uiVm.editPrice.collectAsState()
    val isLoadingGraph by uiVm.isLoadingGraph.collectAsState()
    val totalSold by uiVm.totalSold.collectAsState()
    val commission by uiVm.commission.collectAsState()

    // Seed edit fields once per order
    LaunchedEffect(order?.orderId) {
        order?.let {
            uiVm.seedEditFieldsIfEmpty(
                it.productName,
                it.size,
                it.quantity.toString(),
                it.price.toString()
            )
        }
    }

    val salesAgentId = navController.currentBackStackEntry
        ?.arguments?.getString("salesAgentId") ?: ""

    // Listen to sales totals
    LaunchedEffect(order?.orderId) {
        val safe = order
        if (safe != null && safe.orderId.isNotEmpty()) {
            uiVm.setLoadingGraph(true)
            salesViewModel.listenToPurchasesForOrder(safe.orderId) { newTotal, newComm ->
                uiVm.setTotals(newTotal, newComm)
                uiVm.setLoadingGraph(false)
            }
        } else {
            uiVm.setLoadingGraph(false)
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (order == null) {
                    Text("Invalid order data", color = MaterialTheme.colorScheme.error)
                    return@Column
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Product Name: ${order.productName}", style = MaterialTheme.typography.bodyLarge)
                        Text("Category: ${order.category}", style = MaterialTheme.typography.bodyLarge)
                        Text("Size: ${order.size}", style = MaterialTheme.typography.bodyLarge)
                        Text("Quantity: ${order.quantity}", style = MaterialTheme.typography.bodyLarge)
                        Text("Price: $${order.price}", style = MaterialTheme.typography.bodyLarge)
                        val showFit = order.category in listOf(
                            "Long Sleeve Shirts", "Short Sleeve Shirts", "Tuxedo", "Long Pants", "Jeans"
                        ) && order.fit.isNotEmpty()
                        if (showFit) Text("Fit: ${order.fit}", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Text("Sales Summary for this Product", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(20.dp))
                if (isLoadingGraph) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp))
                } else {
                    AnimatedBarGraph(totalSold, commission)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("Total Sold: $${"%.2f".format(totalSold)}")
                        Text("Commission: $${"%.2f".format(commission)}")
                    }
                }

                Spacer(Modifier.height(24.dp))
                Divider()
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { uiVm.setShowEditDialog(true) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) { Text("Edit Order") }

                Button(
                    onClick = {
                        order.let { safeOrder ->
                            if (salesAgentId.isNotEmpty() && safeOrder?.orderId?.isNotEmpty() == true) {
                                salesViewModel.deleteOrder(safeOrder.orderId, salesAgentId) { success ->
                                    if (success) {
                                        navController.navigate("all_orders?salesAgentId=$salesAgentId") {
                                            popUpTo("all_orders") { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    } else errorMessage = "Failed to remove order."
                                }
                            } else errorMessage = "Order ID is missing. Cannot remove order."
                        } ?: run { errorMessage = "Order data is invalid." }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove Order") }

                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { uiVm.setShowEditDialog(false) },
                title = { Text("Edit Order") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = editProductName,
                            onValueChange = { uiVm.setEditProductName(it) },
                            label = { Text("Product Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = editSize,
                            onValueChange = { uiVm.setEditSize(it) },
                            label = { Text("Size") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = editQuantity,
                            onValueChange = { if (it.all(Char::isDigit)) uiVm.setEditQuantity(it) },
                            label = { Text("Quantity") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = editPrice,
                            onValueChange = {
                                val filtered = it.filter { c -> c.isDigit() || c == '.' }
                                uiVm.setEditPrice(filtered)
                            },
                            label = { Text("Price") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                    }
                },
                confirmButton = {
                    val valid = editProductName.isNotBlank() &&
                            editSize.isNotBlank() &&
                            (editQuantity.toIntOrNull() ?: 0) > 0 &&
                            (editPrice.toDoubleOrNull() ?: 0.0) > 0.0

                    Button(
                        onClick = {
                            order?.let { o ->
                                val updated = o.copy(
                                    productName = editProductName,
                                    size = editSize,
                                    quantity = editQuantity.toIntOrNull() ?: o.quantity,
                                    price = editPrice.toDoubleOrNull() ?: o.price
                                )
                                salesViewModel.createOrder(updated) { ok ->
                                    if (ok) {
                                        uiVm.setShowEditDialog(false)
                                        navController.navigate("all_orders?salesAgentId=$salesAgentId") {
                                            popUpTo("all_orders") { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        Log.e("OrderDetailScreen", "Failed to update order")
                                    }
                                }
                            }
                        },
                        enabled = valid
                    ) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { uiVm.setShowEditDialog(false) }) { Text("Cancel") } }
            )
        }
    }
}

/* ---------- Public ViewModel; holds rotation-safe UI state ---------- */
class OrderDetailUiStateViewModel(
    private val saved: SavedStateHandle
) : ViewModel() {

    // Basic, non-scoped keys (simple + safe)
    val showEditDialog = saved.getOrCreateStateFlow("ods_showEdit", false)
    val editProductName = saved.getOrCreateStateFlow("ods_name", "")
    val editSize = saved.getOrCreateStateFlow("ods_size", "")
    val editQuantity = saved.getOrCreateStateFlow("ods_qty", "")
    val editPrice = saved.getOrCreateStateFlow("ods_price", "")

    val isLoadingGraph = saved.getOrCreateStateFlow("ods_loadingGraph", true)
    val totalSold = saved.getOrCreateStateFlow("ods_totalSold", 0.0)
    val commission = saved.getOrCreateStateFlow("ods_commission", 0.0)

    fun seedEditFieldsIfEmpty(product: String, size: String, qty: String, price: String) {
        if (editProductName.value.isEmpty()) saved.updateKey("ods_name", product)
        if (editSize.value.isEmpty()) saved.updateKey("ods_size", size)
        if (editQuantity.value.isEmpty()) saved.updateKey("ods_qty", qty)
        if (editPrice.value.isEmpty()) saved.updateKey("ods_price", price)
    }

    fun setShowEditDialog(v: Boolean) = saved.updateKey("ods_showEdit", v)
    fun setEditProductName(v: String) = saved.updateKey("ods_name", v)
    fun setEditSize(v: String) = saved.updateKey("ods_size", v)
    fun setEditQuantity(v: String) = saved.updateKey("ods_qty", v)
    fun setEditPrice(v: String) = saved.updateKey("ods_price", v)

    fun setLoadingGraph(v: Boolean) = saved.updateKey("ods_loadingGraph", v)
    fun setTotals(total: Double, comm: Double) {
        saved.updateKey("ods_totalSold", total)
        saved.updateKey("ods_commission", comm)
    }
}

/* ---------- Chart bits (unchanged) ---------- */
@Composable
private fun AnimatedBarGraph(totalSold: Double, commission: Double) {
    val maxValue = maxOf(totalSold, commission, 1.0)
    val soldBarHeight = remember { Animatable(0f) }
    val commissionBarHeight = remember { Animatable(0f) }

    LaunchedEffect(totalSold, commission) {
        soldBarHeight.animateTo((totalSold / maxValue).toFloat(), animationSpec = tween(1000))
        commissionBarHeight.animateTo((commission / maxValue).toFloat(), animationSpec = tween(1000))
    }

    Column(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            Bar(soldBarHeight.value, Color.Blue, "Total Sold: $${"%.2f".format(totalSold)}")
            Bar(commissionBarHeight.value, Color.Green, "Commission: $${"%.2f".format(commission)}")
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("Total Sold", style = MaterialTheme.typography.bodySmall)
            Text("Commission", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Bar(heightFraction: Float, color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.width(100.dp).fillMaxHeight()) {
            val barWidth = size.width
            val barHeight = size.height * heightFraction
            drawRect(
                color = color,
                topLeft = Offset(0f, size.height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}
