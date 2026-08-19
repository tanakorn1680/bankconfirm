package com.testnotification.data

/**
 * NotificationLog — In-memory data model สำหรับ Notification ที่รับมา
 * ไม่มี Database ทุกอย่างอยู่ใน memory เท่านั้น
 */
data class NotificationLog(
    val timeMs: Long,           // System.currentTimeMillis() ตอนรับ
    val packageName: String,
    val appName: String,        // ชื่อแอปจาก PackageManager หรือ "-"
    val title: String,          // android.title หรือ "-"
    val text: String,           // android.text หรือ "-"
    val bigText: String,        // android.bigText หรือ "-"
    val subText: String         // android.subText หรือ "-"
)
