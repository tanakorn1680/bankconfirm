package com.bankamountreader.data

data class BankAmount(
    val amount: Double,
    val currency: String = "THB",
    val bank: String,
    val timestamp: Long,           // Unix epoch seconds
    val notificationId: String,
    val isTest: Boolean = false
)
