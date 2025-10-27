package com.example.assignment.data

data class Order(
    val orderId: String = "",
    val salesAgentId: String = "",
    val productName: String = "", // New field for product name
    val category: String = "",
    val size: String = "",
    val quantity: Int = 0,
    val price: Double = 0.0,
    val fit: String = "" // Added fit property
)
