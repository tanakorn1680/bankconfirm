package com.bankamountreader.data

/**
 * BankAmount — Data model ที่ส่งไป Backend API
 *
 * ตรงกับ spec:
 * {
 *   "amount": 10.25,
 *   "currency": "THB",
 *   "bank": "bank_1",
 *   "timestamp": 1755432000,
 *   "notification_id": "unique-id"
 * }
 */
data class BankAmount(
    val amount: Double,
    val currency: String = "THB",
    val bank: String,
    val timestamp: Long,           // Unix epoch seconds
    val notificationId: String,    // Unique ID สำหรับ duplicate detection
    val isTest: Boolean = false    // Test flag — backend ต้องตรวจสอบ field นี้ก่อน match order
)
