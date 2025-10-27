package com.example.assignment.ui.theme.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.assignment.data.Order
import com.example.assignment.viewmodel.SalesAgentViewModel
import com.example.assignment.viewmodel.KeyedStateSupport.getOrCreateStateFlow
import com.example.assignment.viewmodel.KeyedStateSupport.updateKey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrderScreen(navController: NavHostController, salesViewModel: SalesAgentViewModel) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val uiVm: AddOrderUiStateViewModel = viewModel() // must be public class

    val productName by uiVm.productName.collectAsState()
    val category by uiVm.category.collectAsState()
    val size by uiVm.size.collectAsState()
    val quantity by uiVm.quantity.collectAsState()
    val price by uiVm.price.collectAsState()
    val fit by uiVm.fit.collectAsState()
    val type by uiVm.type.collectAsState()

    val isLoading by uiVm.isLoading.collectAsState()
    val errorMessage by uiVm.errorMessage.collectAsState()
    val successMessage by uiVm.successMessage.collectAsState()

    val expandedType by uiVm.expandedType.collectAsState()
    val expandedCategory by uiVm.expandedCategory.collectAsState()
    val expandedSize by uiVm.expandedSize.collectAsState()
    val expandedFit by uiVm.expandedFit.collectAsState()

    val shirtCategories = listOf(
        "Long Sleeve Shirts", "Half Sleeve Shirts", "Short Sleeve Shirts",
        "Sleeveless Shirts", "Sweatshirts", "Hoodie", "Tuxedo", "Polo",
        "Blouses", "Bra Tops", "Sweaters", "Jackets", "Coats"
    )
    val pantsCategories = listOf(
        "Shorts", "Jeans", "Cargo", "Chinos", "Wide Pants",
        "Ankle Pants", "Sweat Pants", "Easy Pants", "Leggings Pants"
    )
    val categories = if (type == "Tops") shirtCategories else pantsCategories
    val sizes = listOf("XS", "S", "M", "L", "XL", "XXL")
    val fits = listOf("Slim Fit", "Regular Fit")
    val showFit = category in listOf("Long Sleeve Shirts", "Short Sleeve Shirts", "Tuxedo", "Long Pants", "Jeans")

    val salesAgentId = navController.currentBackStackEntry?.arguments?.getString("salesAgentId") ?: run {
        Log.e("AddOrderScreen", "salesAgentId missing")
        uiVm.setError("No sales agent logged in. Please log in again.")
        ""
    }

    fun validateInputs(): String? {
        if (salesAgentId.isEmpty()) return "No sales agent logged in. Please log in again."
        if (productName.isBlank()) return "Product name is required."
        if (category.isBlank()) return "Category is required."
        if (size.isBlank()) return "Size is required."
        val qty = quantity.toIntOrNull() ?: return "Quantity must be a positive number."
        if (qty <= 0) return "Quantity must be a positive number."
        val unitPrice = price.toDoubleOrNull() ?: return "Price must be a positive number."
        if (unitPrice <= 0.0) return "Price must be a positive number."
        if (showFit && fit.isBlank()) return "Fit is required for the selected category."
        return null
    }

    fun clearInputs() {
        uiVm.setProductName(""); uiVm.setCategory(""); uiVm.setSize("")
        uiVm.setQuantity(""); uiVm.setPrice(""); uiVm.setFit("")
    }

    suspend fun createOrder(onDone: (Boolean, String?) -> Unit) {
        val err = validateInputs()
        if (err != null) { onDone(false, err); return }
        val order = Order(
            orderId = "",
            salesAgentId = salesAgentId,
            productName = productName.trim(),
            category = category.trim(),
            size = size.trim(),
            quantity = quantity.toInt(),
            price = price.toDouble(),
            fit = fit.trim()
        )
        salesViewModel.checkOrderCombinationUnique(
            salesAgentId, order.productName, order.category, order.size, order.fit
        ) { isUnique ->
            if (!isUnique) onDone(false, "An identical order already exists for this agent.")
            else salesViewModel.createOrder(order) { success ->
                onDone(success, if (success) null else "Failed to create order.")
            }
        }
    }

    fun onSubmit() {
        focus.clearFocus()
        uiVm.setLoading(true)
        uiVm.setError(null)
        uiVm.setSuccess(null)
        scope.launch {
            createOrder { success, err ->
                uiVm.setLoading(false)
                if (success) {
                    uiVm.setSuccess("Order created successfully.")
                    clearInputs()
                    salesViewModel.fetchOrders(salesAgentId)
                } else {
                    uiVm.setError(err ?: "Failed to create order.")
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Add New Order") }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        val topInset = innerPadding.calculateTopPadding()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = productName, onValueChange = { uiVm.setProductName(it) },
                label = { Text("Product Name") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true
            )

            ExposedDropdownMenuBox(
                expanded = expandedType,
                onExpandedChange = { uiVm.setExpandedType(!expandedType) }
            ) {
                OutlinedTextField(
                    value = type, onValueChange = {},
                    label = { Text("Type") }, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().padding(bottom = 12.dp)
                )
                ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { uiVm.setExpandedType(false) }) {
                    listOf("Tops", "Bottoms").forEach { item ->
                        DropdownMenuItem(text = { Text(item) }, onClick = {
                            uiVm.setType(item); uiVm.setCategory(""); uiVm.setFit(""); uiVm.setExpandedType(false)
                        })
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = expandedCategory,
                onExpandedChange = { uiVm.setExpandedCategory(!expandedCategory) }
            ) {
                OutlinedTextField(
                    value = category, onValueChange = {},
                    label = { Text("Category") }, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().padding(bottom = 12.dp)
                )
                ExposedDropdownMenu(expanded = expandedCategory, onDismissRequest = { uiVm.setExpandedCategory(false) }) {
                    categories.forEach { item ->
                        DropdownMenuItem(text = { Text(item) }, onClick = {
                            uiVm.setCategory(item); uiVm.setFit(""); uiVm.setExpandedCategory(false)
                        })
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = expandedSize,
                onExpandedChange = { uiVm.setExpandedSize(!expandedSize) }
            ) {
                OutlinedTextField(
                    value = size, onValueChange = {},
                    label = { Text("Size") }, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSize) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().padding(bottom = 12.dp)
                )
                ExposedDropdownMenu(expanded = expandedSize, onDismissRequest = { uiVm.setExpandedSize(false) }) {
                    sizes.forEach { item ->
                        DropdownMenuItem(text = { Text(item) }, onClick = {
                            uiVm.setSize(item); uiVm.setExpandedSize(false)
                        })
                    }
                }
            }

            if (showFit) {
                ExposedDropdownMenuBox(
                    expanded = expandedFit,
                    onExpandedChange = { uiVm.setExpandedFit(!expandedFit) }
                ) {
                    OutlinedTextField(
                        value = fit, onValueChange = {},
                        label = { Text("Fit") }, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFit) },
                        modifier = Modifier.menuAnchor().fillMaxWidth().padding(bottom = 12.dp)
                    )
                    ExposedDropdownMenu(expanded = expandedFit, onDismissRequest = { uiVm.setExpandedFit(false) }) {
                        fits.forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = {
                                uiVm.setFit(item); uiVm.setExpandedFit(false)
                            })
                        }
                    }
                }
            }

            OutlinedTextField(
                value = quantity,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } || input.isEmpty()) uiVm.setQuantity(input)
                },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = price,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1) uiVm.setPrice(filtered)
                },
                label = { Text("Price per Item") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true
            )

            Button(
                onClick = { onSubmit() },
                enabled = salesAgentId.isNotEmpty() && !isLoading,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp).padding(end = 8.dp))
                }
                Text(if (isLoading) "Creating..." else "Create Order")
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
            successMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ---------- Public ViewModel; holds rotation-safe UI state ---------- */
class AddOrderUiStateViewModel(
    private val saved: SavedStateHandle
) : ViewModel() {
    // Inputs
    val productName = saved.getOrCreateStateFlow("aos_productName", "")
    val category = saved.getOrCreateStateFlow("aos_category", "")
    val size = saved.getOrCreateStateFlow("aos_size", "")
    val quantity = saved.getOrCreateStateFlow("aos_quantity", "")
    val price = saved.getOrCreateStateFlow("aos_price", "")
    val fit = saved.getOrCreateStateFlow("aos_fit", "")
    val type = saved.getOrCreateStateFlow("aos_type", "Tops")

    // Flags/messages
    val isLoading = saved.getOrCreateStateFlow("aos_loading", false)
    val errorMessage = saved.getOrCreateStateFlow<String?>("aos_error", null)
    val successMessage = saved.getOrCreateStateFlow<String?>("aos_success", null)

    // Dropdown expansion
    val expandedType = saved.getOrCreateStateFlow("aos_exp_type", false)
    val expandedCategory = saved.getOrCreateStateFlow("aos_exp_category", false)
    val expandedSize = saved.getOrCreateStateFlow("aos_exp_size", false)
    val expandedFit = saved.getOrCreateStateFlow("aos_exp_fit", false)

    // Setters
    fun setProductName(v: String) = saved.updateKey("aos_productName", v)
    fun setCategory(v: String) = saved.updateKey("aos_category", v)
    fun setSize(v: String) = saved.updateKey("aos_size", v)
    fun setQuantity(v: String) = saved.updateKey("aos_quantity", v)
    fun setPrice(v: String) = saved.updateKey("aos_price", v)
    fun setFit(v: String) = saved.updateKey("aos_fit", v)
    fun setType(v: String) = saved.updateKey("aos_type", v)

    fun setLoading(v: Boolean) = saved.updateKey("aos_loading", v)
    fun setError(v: String?) = saved.updateKey("aos_error", v)
    fun setSuccess(v: String?) = saved.updateKey("aos_success", v)

    fun setExpandedType(v: Boolean) = saved.updateKey("aos_exp_type", v)
    fun setExpandedCategory(v: Boolean) = saved.updateKey("aos_exp_category", v)
    fun setExpandedSize(v: Boolean) = saved.updateKey("aos_exp_size", v)
    fun setExpandedFit(v: Boolean) = saved.updateKey("aos_exp_fit", v)
}
