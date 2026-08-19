package com.bankamountreader.data

data class NotificationLog(
    val timeMs:      Long,
    val packageName: String,
    val appName:     String,
    val title:       String,
    val text:        String,
    val bigText:     String,
    val subText:     String,
    val parsedAmount: Double? = null,
    val matched:     Boolean  = false
)
