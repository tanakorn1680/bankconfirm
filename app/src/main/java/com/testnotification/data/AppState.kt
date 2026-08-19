package com.testnotification.data

/**
 * AppState — Singleton ที่เก็บ Notification Log แบบ in-memory
 *
 * ใช้ LocalBroadcastManager แทน SharedPreferences เพราะ:
 *  - ข้อมูลไม่ต้อง persist (ปิดแอป = ล้างหมด)
 *  - Service ส่ง broadcast → MainActivity อัปเดท list ทันที
 *
 * เก็บสูงสุด MAX_LOG_SIZE รายการ (เก่าสุดหลุดออก)
 */
object AppState {

    const val ACTION_NOTIFICATION_RECEIVED = "com.testnotification.ACTION_NOTIFICATION_RECEIVED"
    const val MAX_LOG_SIZE = 50

    // list เรียงจากใหม่ → เก่า
    private val logs = mutableListOf<NotificationLog>()

    @Synchronized
    fun addLog(log: NotificationLog) {
        logs.add(0, log)
        if (logs.size > MAX_LOG_SIZE) {
            logs.removeAt(logs.size - 1)
        }
    }

    @Synchronized
    fun getLogs(): List<NotificationLog> = logs.toList()
}
