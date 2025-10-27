package com.example.assignment.data

import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

class SalesAgentHandle {
    private val db = FirebaseFirestore.getInstance()
    private val ordersCollection = db.collection("orders")
    private val purchasesCollection = db.collection("purchases")

    fun updateAgentCommission(salesAgentId: String, commissionAmount: Double, callback: (Boolean, String?) -> Unit) {
        val userDoc = FirebaseFirestore.getInstance().collection("users").document(salesAgentId)
        userDoc.update("commission", FieldValue.increment(commissionAmount))
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { e -> callback(false, e.message) }
    }

    fun fetchOrders(salesAgentId: String, callback: (List<Order>?, String?) -> Unit) {
        ordersCollection
            .whereEqualTo("salesAgentId", salesAgentId)
            .get()
            .addOnSuccessListener { documents ->
                val orders = documents.mapNotNull { doc ->
                    doc.toObject(Order::class.java).copy(orderId = doc.id)
                }
                callback(orders, null)
            }
            .addOnFailureListener { e ->
                Log.e("SalesAgentHandle", "Fetch orders error: ${e.message}")
                callback(null, e.message)
            }
    }

    fun fetchAllOrders(callback: (List<Order>?, String?) -> Unit) {
        ordersCollection
            .get()
            .addOnSuccessListener { documents ->
                val orders = documents.mapNotNull { doc ->
                    doc.toObject(Order::class.java).copy(orderId = doc.id)
                }
                callback(orders, null)
            }
            .addOnFailureListener { e ->
                Log.e("SalesAgentHandle", "Fetch all orders error: ${e.message}")
                callback(null, e.message)
            }
    }

    fun createOrder(order: Order, callback: (Boolean, String?) -> Unit) {
        val orderWithId = order.copy(orderId = ordersCollection.document().id)
        ordersCollection.document(orderWithId.orderId)
            .set(orderWithId)
            .addOnSuccessListener {
                callback(true, null)
            }
            .addOnFailureListener { e ->
                Log.e("SalesAgentHandle", "Create order error: ${e.message}")
                callback(false, e.message)
            }
    }

    fun deleteOrder(orderId: String, callback: (Boolean, String?) -> Unit) {
        ordersCollection.document(orderId)
            .delete()
            .addOnSuccessListener {
                callback(true, null)
            }
            .addOnFailureListener { e ->
                Log.e("SalesAgentHandle", "Delete order error: ${e.message}")
                callback(false, e.message)
            }
    }

    fun fetchPurchases(salesAgentId: String, callback: (List<Purchase>?, String?) -> Unit) {
        purchasesCollection
            .whereEqualTo("salesAgentId", salesAgentId)
            .get()
            .addOnSuccessListener { documents ->
                val purchases = documents.mapNotNull { doc ->
                    doc.toObject(Purchase::class.java).copy(id = doc.id)
                }
                callback(purchases, null)
            }
            .addOnFailureListener { e ->
                Log.e("SalesAgentHandle", "Fetch purchases error: ${e.message}")
                callback(null, e.message)
            }
    }

    fun fetchPurchasesForOrder(orderId: String, callback: (List<Purchase>?, String?) -> Unit) {
        purchasesCollection
            .whereEqualTo("orderId", orderId)
            .get()
            .addOnSuccessListener { documents ->
                val purchases = documents.mapNotNull { doc ->
                    doc.toObject(Purchase::class.java).copy(id = doc.id)
                }
                callback(purchases, null)
            }
            .addOnFailureListener { e ->
                Log.e("SalesAgentHandle", "Fetch purchases for order error: ${e.message}")
                callback(null, e.message)
            }
    }

    fun createPurchase(order: Order, callback: (Boolean, String?) -> Unit) {
        val purchase = Purchase(
            id = purchasesCollection.document().id,
            orderId = order.orderId,
            salesAgentId = order.salesAgentId,
            totalPrice = order.price * order.quantity,
            isAdminPurchase = false,
            purchaseDate = System.currentTimeMillis()
        )
        purchasesCollection.document(purchase.id)
            .set(purchase)
            .addOnSuccessListener {
                callback(true, null)
            }
            .addOnFailureListener { e ->
                Log.e("SalesAgentHandle", "Create purchase error: ${e.message}")
                callback(false, e.message)
            }
    }

    // New method for real-time updates
    fun listenToPurchasesForOrder(orderId: String, callback: (Double, Double) -> Unit) {
        purchasesCollection
            .whereEqualTo("orderId", orderId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("SalesAgentHandle", "Listen to purchases error: ${e.message}")
                    callback(0.0, 0.0)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val purchases = snapshot.mapNotNull { doc ->
                        doc.toObject(Purchase::class.java).copy(id = doc.id)
                    }
                    Log.d("SalesAgentHandle", "Found ${purchases.size} purchases for orderId: $orderId")
                    val adminPurchases = purchases.filter { it.isAdminPurchase }
                    if (adminPurchases.isEmpty() && purchases.isNotEmpty()) {
                        Log.w("SalesAgentHandle", "No admin purchases found; using all purchases as a fallback")
                        val fallbackTotalSold = purchases.sumOf { it.totalPrice }
                        val fallbackCommission = fallbackTotalSold * 0.10
                        callback(fallbackTotalSold, fallbackCommission)
                    } else {
                        val newTotalSold = adminPurchases.sumOf { it.totalPrice }
                        val newCommission = newTotalSold * 0.10
                        Log.d("SalesAgentHandle", "Total Sold: $newTotalSold, Commission: $newCommission")
                        callback(newTotalSold, newCommission)
                    }
                }
            }
    }

    // New method to check product name uniqueness
    fun isProductNameUnique(salesAgentId: String, productName: String, callback: (Boolean) -> Unit) {
        ordersCollection
            .whereEqualTo("salesAgentId", salesAgentId)
            .whereEqualTo("productName", productName)
            .get()
            .addOnSuccessListener { documents ->
                callback(documents.isEmpty())
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    // New method to check full order combination uniqueness
    fun isOrderCombinationUnique(salesAgentId: String, productName: String, category: String, size: String, fit: String, callback: (Boolean) -> Unit) {
        ordersCollection
            .whereEqualTo("salesAgentId", salesAgentId)
            .whereEqualTo("productName", productName)
            .whereEqualTo("category", category)
            .whereEqualTo("size", size)
            .whereEqualTo("fit", fit)
            .get()
            .addOnSuccessListener { documents ->
                callback(documents.isEmpty())
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    fun updateCommission(salesAgentId: String, saleAmount: Double, callback: (Boolean, String?) -> Unit) {
        Log.d("SalesAgentHandle", "Updating commission for agent: $salesAgentId, sale amount: $saleAmount")

        // Commission rate - you can adjust this as needed
        val commissionRate = 0.10 // 10% commission
        val commissionAmount = saleAmount * commissionRate

        db.collection("users").document(salesAgentId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentCommission = document.getDouble("totalCommission") ?: 0.0
                    val newCommission = currentCommission + commissionAmount

                    db.collection("users").document(salesAgentId)
                        .update("totalCommission", newCommission)
                        .addOnSuccessListener {
                            Log.d("SalesAgentHandle", "Commission updated successfully. New total: $newCommission")

                            // Also create a commission record for tracking
                            val commissionRecord = hashMapOf(
                                "salesAgentId" to salesAgentId,
                                "saleAmount" to saleAmount,
                                "commissionAmount" to commissionAmount,
                                "commissionRate" to commissionRate,
                                "date" to System.currentTimeMillis(),
                                "type" to "admin_purchase"
                            )

                            db.collection("commissions")
                                .add(commissionRecord)
                                .addOnSuccessListener {
                                    callback(true, null)
                                }
                                .addOnFailureListener { exception ->
                                    Log.e("SalesAgentHandle", "Error creating commission record", exception)
                                    callback(true, "Commission updated but record creation failed")
                                }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("SalesAgentHandle", "Error updating commission", exception)
                            callback(false, "Failed to update commission: ${exception.message}")
                        }
                } else {
                    callback(false, "Sales agent not found")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("SalesAgentHandle", "Error fetching sales agent", exception)
                callback(false, "Failed to fetch sales agent: ${exception.message}")
            }
    }
}
