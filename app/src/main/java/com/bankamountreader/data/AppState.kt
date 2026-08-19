package com.bankamountreader.data

import android.content.Context
import android.content.SharedPreferences

/**
 * AppState — Shared state ระหว่าง BankNotificationService และ MainActivity
 *
 * ทำไมใช้ SharedPreferences แทน LiveData / EventBus:
 *   - Service และ Activity คนละ process lifecycle
 *   - ไม่ต้องการ dependency เพิ่ม (Lifecycle, EventBus)
 *   - MainActivity อ่านค่าผ่าน polling ทุก 3 วินาทีอยู่แล้ว
 *   - SharedPreferences.apply() = async write ไม่ block service thread
 *
 * ถ้าต้องการ real-time ในอนาคต → เปลี่ยนเป็น LocalBroadcastManager หรือ Flow
 */
object AppState {

    private const val PREFS_NAME = "app_state"

    // Keys
    private const val KEY_LAST_AMOUNT = "last_amount"
    private const val KEY_LAST_BANK = "last_bank"
    private const val KEY_LAST_TIMESTAMP = "last_timestamp"
    private const val KEY_QUEUE_COUNT = "queue_count"
    private const val KEY_SERVER_OK = "server_ok"
    private const val KEY_LAST_NOTIF_ID = "last_notif_id"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // =========================================================================
    // Write (Service calls these)
    // =========================================================================

    fun setLastAmount(context: Context, amount: Double, bank: String, timestampMs: Long, notifId: String) {
        prefs(context).edit()
            .putFloat(KEY_LAST_AMOUNT, amount.toFloat())
            .putString(KEY_LAST_BANK, bank)
            .putLong(KEY_LAST_TIMESTAMP, timestampMs)
            .putString(KEY_LAST_NOTIF_ID, notifId)
            .apply()
    }

    fun setQueueCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_QUEUE_COUNT, count).apply()
    }

    fun setServerOk(context: Context, ok: Boolean) {
        prefs(context).edit().putBoolean(KEY_SERVER_OK, ok).apply()
    }

    // =========================================================================
    // Read (MainActivity calls these)
    // =========================================================================

    fun getLastAmount(context: Context): Double? {
        val p = prefs(context)
        if (!p.contains(KEY_LAST_AMOUNT)) return null
        return p.getFloat(KEY_LAST_AMOUNT, 0f).toDouble()
    }

    fun getLastBank(context: Context): String =
        prefs(context).getString(KEY_LAST_BANK, "") ?: ""

    fun getLastTimestampMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_TIMESTAMP, 0L)

    fun getQueueCount(context: Context): Int =
        prefs(context).getInt(KEY_QUEUE_COUNT, 0)

    fun isServerOk(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVER_OK, false)
}
