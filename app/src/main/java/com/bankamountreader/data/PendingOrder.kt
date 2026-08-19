package com.bankamountreader.data

/**
 * PendingOrder — order ที่สถานะ pending_payment รอจับคู่กับการโอนเงิน
 *
 * map จาก JSON ที่ GET /api/bank-amount?action=pending คืนมา
 */
data class PendingOrder(
    val id: String,
    val productLabel: String,
    val amount: Double,
    val uniqueAmount: Double,   // ยอดที่ต้องโอนจริง (มีเศษ .XX สำหรับจับคู่)
    val createdAt: String,      // ISO 8601
    val userEmail: String,
)
