package com.example.assignment.data

import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import java.util.UUID

class AdminPurchasingHandle {
    private val db = FirebaseFirestore.getInstance()

    fun fetchAllOrders(callback: (List<Order>?, String?) -> Unit) {
        Log.d("AdminPurchasingHandle", "Fetching all available orders")

        db.collection("orders")
            .orderBy("orderId", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val orders = documents.mapNotNull { document ->
                    try {
                        // Normalize: ensure Order.orderId is never blank.
                        val normalizedOrderId =
                            (document.getString("orderId") ?: "").ifBlank { document.id }

                        Order(
                            orderId = normalizedOrderId,
                            salesAgentId = document.getString("salesAgentId") ?: "",
                            productName = document.getString("productName") ?: "",
                            category = document.getString("category") ?: "",
                            size = document.getString("size") ?: "",
                            quantity = (document.getLong("quantity") ?: 0L).toInt(),
                            price = document.getDouble("price") ?: 0.0,
                            fit = document.getString("fit") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e("AdminPurchasingHandle", "Error parsing order document: ${document.id}", e)
                        null
                    }
                }
                Log.d("AdminPurchasingHandle", "Successfully fetched ${orders.size} orders")
                callback(orders, null)
            }
            .addOnFailureListener { exception ->
                Log.e("AdminPurchasingHandle", "Error fetching orders", exception)
                callback(null, "Failed to fetch orders: ${exception.message}")
            }
    }

    fun purchaseOrder(
        order: Order,
        adminId: String,
        quantity: Int,
        salesAgentHandle: SalesAgentHandle,
        callback: (Boolean, String?) -> Unit
    ) {
        Log.d("AdminPurchasingHandle", "Processing purchase for order: ${order.orderId}, quantity: $quantity")

        if (quantity > order.quantity) {
            callback(false, "Cannot purchase more than available quantity")
            return
        }

        val totalPrice = order.price * quantity
        val serialNumber = generateSerialNumber(order.category, order.orderId)

        val purchase = Purchase(
            id = "",
            orderId = order.orderId,
            salesAgentId = order.salesAgentId,
            totalPrice = totalPrice,
            isAdminPurchase = true,
            purchaseDate = System.currentTimeMillis(),
            adminId = adminId,
            category = order.category,
            size = order.size,
            quantity = quantity,
            price = order.price,
            productName = order.productName,
            serialNumber = serialNumber
        )

        val purchaseData = hashMapOf(
            "orderId" to purchase.orderId,
            "salesAgentId" to purchase.salesAgentId,
            "totalPrice" to purchase.totalPrice,
            "isAdminPurchase" to purchase.isAdminPurchase,
            "purchaseDate" to purchase.purchaseDate,
            "adminId" to purchase.adminId,
            "category" to purchase.category,
            "size" to purchase.size,
            "quantity" to purchase.quantity,
            "price" to purchase.price,
            "productName" to purchase.productName,
            "serialNumber" to purchase.serialNumber
        )

        db.collection("purchases")
            .add(purchaseData)
            .addOnSuccessListener { purchaseDocumentReference ->
                Log.d("AdminPurchasingHandle", "Purchase created with ID: ${purchaseDocumentReference.id}")

                if (quantity == order.quantity) {
                    // FULLY purchased -> remove all matching order docs
                    deleteOrderByBestEffort(order.orderId) { ok, err ->
                        if (!ok) {
                            Log.e("AdminPurchasingHandle", "Error deleting order after full purchase: $err")
                            callback(false, "Purchase created but failed to update order: $err")
                        } else {
                            updateSalesAgentCommission(order.salesAgentId, totalPrice, salesAgentHandle, callback)
                        }
                    }
                } else {
                    // PARTIAL purchase -> reduce quantity on all matching docs (usually one)
                    val newQuantity = order.quantity - quantity
                    updateOrderQuantityBestEffort(order.orderId, newQuantity) { ok, err ->
                        if (!ok) {
                            Log.e("AdminPurchasingHandle", "Error updating order quantity: $err")
                            callback(false, "Purchase created but failed to update order: $err")
                        } else {
                            updateSalesAgentCommission(order.salesAgentId, totalPrice, salesAgentHandle, callback)
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("AdminPurchasingHandle", "Error creating purchase", exception)
                callback(false, "Failed to create purchase: ${exception.message}")
            }
    }

    /**
     * Remove an order by best-effort:
     *  1) delete all docs where field `orderId` == value
     *  2) if none matched, try deleting a doc whose **document ID** == value
     */
    fun removeOrder(
        order: Order,
        callback: (Boolean, String?) -> Unit
    ) {
        val id = order.orderId
        if (id.isBlank()) {
            Log.e("AdminPurchasingHandle", "removeOrder: blank orderId")
            callback(false, "Invalid order id")
            return
        }
        Log.d("AdminPurchasingHandle", "Removing order best-effort for key: $id")
        deleteOrderByBestEffort(id, callback)
    }

    /* ----------------------------------------------------------------
       Best-effort helpers: work with either field `orderId` or docId
       ---------------------------------------------------------------- */

    private fun deleteOrderByBestEffort(orderKey: String, callback: (Boolean, String?) -> Unit) {
        // First, delete all where field orderId == orderKey
        db.collection("orders")
            .whereEqualTo("orderId", orderKey)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    // Fall back: try deleting document with ID == orderKey
                    db.collection("orders")
                        .document(orderKey)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (!doc.exists()) {
                                Log.w("AdminPurchasingHandle", "No order found by field or docId for key=$orderKey")
                                callback(true, null) // nothing to delete; treat as success
                            } else {
                                doc.reference.delete()
                                    .addOnSuccessListener {
                                        Log.d("AdminPurchasingHandle", "Deleted docId=$orderKey")
                                        callback(true, null)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("AdminPurchasingHandle", "Failed to delete docId=$orderKey", e)
                                        callback(false, "Failed to remove order: ${e.message}")
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("AdminPurchasingHandle", "Failed to lookup docId=$orderKey", e)
                            callback(false, "Failed to query order: ${e.message}")
                        }
                    return@addOnSuccessListener
                }

                // Batch delete all matches by field
                val batch: WriteBatch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit()
                    .addOnSuccessListener {
                        Log.d("AdminPurchasingHandle", "Deleted ${snap.size()} doc(s) by field orderId=$orderKey")
                        callback(true, null)
                    }
                    .addOnFailureListener { e ->
                        Log.e("AdminPurchasingHandle", "Batch delete failed for orderId=$orderKey", e)
                        callback(false, "Failed to remove order: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("AdminPurchasingHandle", "Failed to query by orderId=$orderKey", e)
                callback(false, "Failed to query order: ${e.message}")
            }
    }

    private fun updateOrderQuantityBestEffort(orderKey: String, newQuantity: Int, callback: (Boolean, String?) -> Unit) {
        // Try field match first
        db.collection("orders")
            .whereEqualTo("orderId", orderKey)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    // Fall back: try docId
                    db.collection("orders")
                        .document(orderKey)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (!doc.exists()) {
                                Log.w("AdminPurchasingHandle", "No order to update for key=$orderKey")
                                callback(false, "Order not found")
                            } else {
                                doc.reference.update("quantity", newQuantity)
                                    .addOnSuccessListener {
                                        Log.d("AdminPurchasingHandle", "Updated docId=$orderKey quantity -> $newQuantity")
                                        callback(true, null)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("AdminPurchasingHandle", "Failed to update docId=$orderKey", e)
                                        callback(false, "Failed to update order: ${e.message}")
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("AdminPurchasingHandle", "Failed to lookup docId=$orderKey", e)
                            callback(false, "Failed to query order: ${e.message}")
                        }
                    return@addOnSuccessListener
                }

                // Update all matched (usually one)
                val batch = db.batch()
                snap.documents.forEach { doc ->
                    batch.update(doc.reference, "quantity", newQuantity)
                }
                batch.commit()
                    .addOnSuccessListener {
                        Log.d("AdminPurchasingHandle", "Updated ${snap.size()} doc(s) quantity -> $newQuantity")
                        callback(true, null)
                    }
                    .addOnFailureListener { e ->
                        Log.e("AdminPurchasingHandle", "Batch update failed for key=$orderKey", e)
                        callback(false, "Failed to update order: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("AdminPurchasingHandle", "Failed to query by orderId=$orderKey", e)
                callback(false, "Failed to query order: ${e.message}")
            }
    }

    /* ---------------------------------------------------------------- */

    private fun updateSalesAgentCommission(
        salesAgentId: String,
        saleAmount: Double,
        salesAgentHandle: SalesAgentHandle,
        callback: (Boolean, String?) -> Unit
    ) {
        Log.d("AdminPurchasingHandle", "Updating commission for sales agent: $salesAgentId, amount: $saleAmount")
        salesAgentHandle.updateCommission(salesAgentId, saleAmount) { success, error ->
            if (success) {
                Log.d("AdminPurchasingHandle", "Commission updated successfully")
                callback(true, null)
            } else {
                Log.e("AdminPurchasingHandle", "Failed to update commission: $error")
                // still return success for purchase flow; propagate warning string
                callback(true, "Purchase completed but commission update failed: $error")
            }
        }
    }

    private fun generateSerialNumber(category: String, orderId: String): String {
        val categoryCode = category.take(3).uppercase()
        val orderCode = orderId.take(4).uppercase()
        val randomCode = UUID.randomUUID().toString().take(4).uppercase()
        return "$categoryCode-$orderCode-$randomCode"
    }
}
