package com.example.assignment.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.assignment.data.SalesAgentHandle
import com.example.assignment.data.Order
import com.example.assignment.data.Purchase
import android.util.Log

class SalesAgentViewModel(private val salesAgentHandle: SalesAgentHandle) : ViewModel() {

    val orders = mutableStateOf<List<Order>>(emptyList())
    val allOrders = mutableStateOf<List<Order>>(emptyList())
    val purchases = mutableStateOf<List<Purchase>>(emptyList())
    val errorMessage = mutableStateOf<String?>(null)

    private var lastSalesAgentId: String = ""

    fun fetchOrders(salesAgentId: String) {
        if (salesAgentId.isNotEmpty()) {
            lastSalesAgentId = salesAgentId
            salesAgentHandle.fetchOrders(salesAgentId) { fetchedOrders, error ->
                if (error != null) {
                    errorMessage.value = error
                    Log.e("SalesAgentViewModel", "Fetch orders error: $error")
                } else {
                    orders.value = fetchedOrders ?: emptyList()
                    errorMessage.value = null
                }
            }
        }
    }

    fun fetchAllOrders() {
        salesAgentHandle.fetchAllOrders { fetchedOrders, error ->
            if (error != null) {
                errorMessage.value = error
                Log.e("SalesAgentViewModel", "Fetch all orders error: $error")
            } else {
                allOrders.value = fetchedOrders ?: emptyList()
                errorMessage.value = null
            }
        }
    }

    fun createOrder(order: Order, onComplete: (Boolean) -> Unit) {
        salesAgentHandle.createOrder(order) { success, error ->
            if (error != null) {
                errorMessage.value = error
                Log.e("SalesAgentViewModel", "Create order error: $error")
                onComplete(false)
            } else {
                if (order.salesAgentId.isNotEmpty()) {
                    fetchOrders(order.salesAgentId)
                }
                errorMessage.value = null
                onComplete(true)
            }
        }
    }

    fun deleteOrder(orderId: String, salesAgentId: String, onComplete: (Boolean) -> Unit) {
        if (salesAgentId.isNotEmpty()) {
            salesAgentHandle.deleteOrder(orderId) { success, error ->
                if (error != null) {
                    errorMessage.value = error
                    Log.e("SalesAgentViewModel", "Delete order error: $error")
                    onComplete(false)
                } else {
                    fetchOrders(salesAgentId)
                    errorMessage.value = null
                    onComplete(true)
                }
            }
        } else {
            errorMessage.value = "Invalid sales agent ID"
            onComplete(false)
        }
    }

    fun fetchPurchases(salesAgentId: String, callback: (List<Purchase>?, String?) -> Unit) {
        salesAgentHandle.fetchPurchases(salesAgentId) { fetchedPurchases, error ->
            if (error != null) {
                errorMessage.value = error
                Log.e("SalesAgentViewModel", "Fetch purchases error: $error")
                purchases.value = emptyList()
                callback(null, error)
            } else {
                purchases.value = fetchedPurchases ?: emptyList()
                errorMessage.value = null
                callback(fetchedPurchases, null)
            }
        }
    }

    fun fetchPurchasesForOrder(orderId: String, callback: (List<Purchase>?, String?) -> Unit) {
        salesAgentHandle.fetchPurchasesForOrder(orderId) { fetchedPurchases, error ->
            if (error != null) {
                errorMessage.value = error
                Log.e("SalesAgentViewModel", "Fetch purchases for order error: $error")
                callback(null, error)
            } else {
                errorMessage.value = null
                callback(fetchedPurchases, null)
            }
        }
    }

    fun createPurchase(order: Order, onComplete: (Boolean) -> Unit) {
        salesAgentHandle.createPurchase(order) { success, error ->
            if (error != null) {
                errorMessage.value = error
                Log.e("SalesAgentViewModel", "Create purchase error: $error")
                onComplete(false)
            } else {
                errorMessage.value = null
                onComplete(true)
            }
        }
    }

    fun updateOrderSummary(orderId: String, callback: (Double, Double) -> Unit) {
        fetchPurchasesForOrder(orderId) { purchases, error ->
            if (error == null && purchases != null) {
                val adminPurchases = purchases.filter { it.isAdminPurchase }
                val newTotalSold = adminPurchases.sumOf { it.totalPrice }
                val newCommission = newTotalSold * 0.15 // adjust rate if needed
                callback(newTotalSold, newCommission)
            } else {
                callback(0.0, 0.0)
            }
        }
    }

    fun listenToPurchasesForOrder(orderId: String, callback: (Double, Double) -> Unit) {
        salesAgentHandle.listenToPurchasesForOrder(orderId, callback)
    }

    fun checkProductNameUnique(salesAgentId: String, productName: String, callback: (Boolean) -> Unit) {
        salesAgentHandle.isProductNameUnique(salesAgentId, productName, callback)
    }

    fun checkOrderCombinationUnique(
        salesAgentId: String,
        productName: String,
        category: String,
        size: String,
        fit: String,
        callback: (Boolean) -> Unit
    ) {
        salesAgentHandle.isOrderCombinationUnique(salesAgentId, productName, category, size, fit, callback)
    }
}
