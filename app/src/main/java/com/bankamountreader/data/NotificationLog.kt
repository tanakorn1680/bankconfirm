package com.bankamountreader.data

/**
 * NotificationLog — raw notification ที่รับมาจากธนาคาร
 * ใช้แสดงใน Debug tab เพื่อให้เห็นข้อมูลดิบก่อน parse
 */
data class NotificationLog(
    val timeMs: Long,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val subText: String,
    val parsedAmount: Double?,   // null = parse ไม่ได้ / ไม่ใช่รายการรับเงิน
    val matched: Boolean = false // true = ส่ง API แล้ว
)
