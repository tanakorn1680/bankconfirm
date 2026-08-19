package com.bankamountreader.data

data class BankAmount(
    val amount:         Double,
    val currency:       String = "THB",
    val bank:           String,
    val timestamp:      Long,
    val notificationId: String,
    val isTest:         Boolean = false
)
