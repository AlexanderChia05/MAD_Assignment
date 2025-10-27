package com.example.assignment.data

data class Purchase(
    val id: String = "",
    val orderId: String = "",
    val salesAgentId: String = "",
    val totalPrice: Double = 0.0, // Ensure this is included
    val isAdminPurchase: Boolean = false, // Ensure this is included
    val purchaseDate: Long = System.currentTimeMillis(),
    val adminId: String = "", // Added to match your document
    val category: String = "", // Added to match your document
    val size: String = "", // Added to match your document
    val quantity: Int = 0, // Added to match your document
    val price: Double = 0.0, // Added to match your document, though totalPrice should be used
    val productName: String = "",
    val serialNumber: String? = null
)