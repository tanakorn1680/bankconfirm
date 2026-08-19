package com.bankamountreader.data

data class PendingOrder(
    val id:           String,
    val productLabel: String,
    val amount:       Double,
    val uniqueAmount: Double,
    val createdAt:    String,
    val userEmail:    String
)
