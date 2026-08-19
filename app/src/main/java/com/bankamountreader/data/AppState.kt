package com.bankamountreader.data

import android.content.Context
import android.content.SharedPreferences

/**
 * AppState — shared state ระหว่าง BankNotificationService และ MainActivity
 *
 * - SharedPreferences: ข้อมูล persist ที่ต้องอยู่ข้าม lifecycle (server status, last amount)
 * - In-memory log: NotificationLog ล่าสุด MAX_LOG_SIZE รายการ (ล้างเมื่อปิดแอป)
 * - LocalBroadcast: แจ้ง MainActivity ทันทีเมื่อรับ notification ใหม่
 */
object AppState {

    const val ACTION_NOTIFICATION_RECEIVED = "com.bankamountreader.ACTION_NOTIFICATION_RECEIVED"
    const val MAX_LOG_SIZE = 50

    private const val PREFS_NAME      = "app_state"
    private const val KEY_SERVER_OK   = "server_ok"
    private const val KEY_QUEUE_COUNT = "queue_count"

    // ── In-memory notification log ─────────────────────────────────────────
    private val logs = mutableListOf<NotificationLog>()

    @Synchronized
    fun addLog(log: NotificationLog) {
        logs.add(0, log)
        if (logs.size > MAX_LOG_SIZE) logs.removeAt(logs.size - 1)
    }

    @Synchronized
    fun getLogs(): List<NotificationLog> = logs.toList()

    // ── SharedPreferences helpers ──────────────────────────────────────────
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setServerOk(context: Context, ok: Boolean) {
        prefs(context).edit().putBoolean(KEY_SERVER_OK, ok).apply()
    }

    fun isServerOk(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVER_OK, false)

    fun setQueueCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_QUEUE_COUNT, count).apply()
    }

    fun getQueueCount(context: Context): Int =
        prefs(context).getInt(KEY_QUEUE_COUNT, 0)
}
