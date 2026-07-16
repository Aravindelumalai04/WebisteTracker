package com.aravind.orderalert

data class Order(
    val orderId: String,
    val customerName: String,
    val guestName: String,
    val paymentType: String,
    val status: String,
    val amount: String
)
