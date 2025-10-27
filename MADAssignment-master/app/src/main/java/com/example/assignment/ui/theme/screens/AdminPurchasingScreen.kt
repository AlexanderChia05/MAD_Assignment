package com.example.assignment.ui.theme.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.navigation.NavHostController
import com.example.assignment.data.AdminPurchasingHandle
import com.example.assignment.data.Order
import com.example.assignment.data.SalesAgentHandle
import com.example.assignment.viewmodel.AdminPurchasingViewModel
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun AdminPurchasingScreen(navController: NavHostController) {
    val purchasingViewModel: AdminPurchasingViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val saved = extras.createSavedStateHandle()
                val adminPurchasingHandle = AdminPurchasingHandle()
                val salesAgentHandle = SalesAgentHandle()
                return AdminPurchasingViewModel(
                    saved,
                    adminPurchasingHandle,
                    salesAgentHandle
                ) as T
            }
        }
    )

    val availableItems by purchasingViewModel.availableItems
    val errorMessage by purchasingViewModel.errorMessage
    val isLoading by purchasingViewModel.isLoading
    val successMessage by purchasingViewModel.successMessage
    val showPurchaseDialog by purchasingViewModel.showPurchaseDialog
    val purchaseOrder by purchasingViewModel.purchaseOrder
    val purchaseQuantity by purchasingViewModel.purchaseQuantity
    val purchaseStep by purchasingViewModel.purchaseStep
    val adminId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Local dialog state for Order Actions + Remove confirmation
    var showActionsDialog by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        purchasingViewModel.fetchItems()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Purchasing (Admin)") }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {

            // Make entire screen scrollable
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "Select an item to purchase from sales agents:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    errorMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    successMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (availableItems.isEmpty()) {
                        Text(
                            text = "No items available for purchase.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        // Latest → Oldest: render reversed without mutating VM data
                        val displayList: List<Order> = remember(availableItems) {
                            availableItems.asReversed()
                        }

                        // NON-SCROLLING order cards (in a grid of 2 per row), parent handles scroll
                        val chunkedOrders = displayList.chunked(2)
                        chunkedOrders.forEachIndexed { rowIndex, pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                pair.forEachIndexed { pairIndex, order ->
                                    val globalIndex = (rowIndex * 2) + pairIndex + 1
                                    val type = if (listOf(
                                            "Long Sleeve Shirts",
                                            "Short Sleeve Shirts",
                                            "T-shirt",
                                            "Hoodie",
                                            "Tuxedo"
                                        ).contains(order.category)
                                    ) "shirt" else "pants"

                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .clickable {
                                                selectedOrder = order
                                                showActionsDialog = true
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                        border = BorderStroke(1.dp, Color.Gray)
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
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontSize = 18.sp,
                                                modifier = Modifier
                                                    .padding(bottom = 4.dp)
                                                    .align(Alignment.CenterHorizontally)
                                            )
                                            Text(
                                                text = "Product Name: ${order.productName}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                            Text(
                                                text = "Type: $type",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                            Text(
                                                text = "Category: ${order.category}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                            Text(
                                                text = "Size: ${order.size}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                            Text(
                                                text = "Quantity: ${order.quantity}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                            Text(
                                                text = "Price: $${order.price}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (pair.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Divider(color = Color.Gray.copy(alpha = 0.2f))
                        }
                    }
                }
            }

            /* ===========================
               Order Actions Dialog
               - "Purchase Product": reuse existing onBuyClicked + purchase flow
               - "Remove Product": confirm then remove via ViewModel wrapper (no-arg callback)
               =========================== */
            if (showActionsDialog && selectedOrder != null) {
                AlertDialog(
                    onDismissRequest = { showActionsDialog = false },
                    title = { Text("Order Actions") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Product: ${selectedOrder!!.productName}")
                            Text("Category: ${selectedOrder!!.category} • Size: ${selectedOrder!!.size}")
                            Text("Quantity: ${selectedOrder!!.quantity} • Price: $${selectedOrder!!.price}")
                        }
                    },
                    confirmButton = {
                        // Purchase Product
                        Button(
                            onClick = {
                                purchasingViewModel.onBuyClicked(selectedOrder!!)
                                showActionsDialog = false
                            }
                        ) { Text("Purchase Product") }
                    },
                    dismissButton = {
                        // Remove Product
                        TextButton(
                            onClick = {
                                showActionsDialog = false
                                showRemoveConfirm = true
                            }
                        ) { Text("Remove Product", color = MaterialTheme.colorScheme.error) }
                    }
                )
            }

            // Remove confirmation dialog (uses ViewModel.removeOrder with NO-ARG callback)
            if (showRemoveConfirm && selectedOrder != null) {
                AlertDialog(
                    onDismissRequest = { showRemoveConfirm = false },
                    title = { Text("Remove Product") },
                    text = { Text("Are you sure you want to remove ${selectedOrder!!.productName}?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val toRemove = selectedOrder!!
                                // No-arg callback matches (() -> Unit)?
                                purchasingViewModel.removeOrder(toRemove) {
                                    // Optional: any UI-side cleanup
                                }
                                showRemoveConfirm = false
                            }
                        ) { Text("Yes, remove", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
                    }
                )
            }

            // Existing purchase flow dialog (unchanged)
            if (showPurchaseDialog) {
                AlertDialog(
                    onDismissRequest = { purchasingViewModel.showPurchaseDialog.value = false },
                    title = { Text("Purchase ${purchaseOrder?.category ?: ""} (${purchaseOrder?.size ?: ""})") },
                    text = {
                        if (purchaseStep == 1) {
                            Column {
                                Text("Enter quantity to purchase:")
                                TextField(
                                    value = purchaseQuantity,
                                    onValueChange = { purchasingViewModel.purchaseQuantity.value = it },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number
                                    ),
                                    isError = (purchaseQuantity.toIntOrNull() ?: 0) <= 0 || (purchaseQuantity.toIntOrNull()
                                        ?: 0) > (purchaseOrder?.quantity ?: 0)
                                )
                                val qtyValid =
                                    (purchaseQuantity.toIntOrNull() ?: 0) in 1..(purchaseOrder?.quantity ?: 0)
                                if (!qtyValid && purchaseQuantity.isNotEmpty()) {
                                    Text(
                                        color = MaterialTheme.colorScheme.error,
                                        text = "Please enter a quantity between 1 and ${purchaseOrder?.quantity ?: 0}"
                                    )
                                } else if ((purchaseQuantity.toIntOrNull() ?: 0) > 0) {
                                    val total =
                                        (purchaseOrder?.price ?: 0.0) * (purchaseQuantity.toIntOrNull() ?: 0)
                                    Text(
                                        text = "Total: $${"%.2f".format(total)}",
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        } else {
                            val qty = (purchaseQuantity.toIntOrNull() ?: 0)
                            val total = (purchaseOrder?.price ?: 0.0).times(qty)
                            Column {
                                Text("Are you sure you want to purchase $qty x ${purchaseOrder?.category} (${purchaseOrder?.size}) for $${"%.2f".format(total)}?")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { purchasingViewModel.showPurchaseDialog.value = false }
                        ) { Text("Cancel") }
                    },
                    confirmButton = {
                        if (purchaseStep == 1) {
                            val qtyValid =
                                (purchaseQuantity.toIntOrNull() ?: 0) in 1..(purchaseOrder?.quantity ?: 0)
                            Button(
                                enabled = qtyValid,
                                onClick = { purchasingViewModel.purchaseStep.value = 2 }
                            ) {
                                Text("Next")
                            }
                        } else {
                            val qty = (purchaseQuantity.toIntOrNull() ?: 0)
                            Button(
                                enabled = qty > 0,
                                onClick = {
                                    purchasingViewModel.confirmPurchase(adminId, qty) {
                                        purchasingViewModel.purchaseStep.value = 1
                                    }
                                }
                            ) {
                                Text("Purchase")
                            }
                        }
                    }
                )
            }
        }
    }
}
