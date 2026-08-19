package com.bankamountreader.network

import android.content.Context
import android.util.Log
import com.bankamountreader.AppConfig
import com.bankamountreader.data.AppState
import com.bankamountreader.data.BankAmount
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * OfflineQueue — Phase 5/6
 *
 * เก็บ BankAmount ที่ส่งไม่สำเร็จ (ไม่มี internet)
 * แล้วส่งซ้ำเมื่อ internet กลับมา
 *
 * กลยุทธ์ง่ายที่สุดที่เสถียร:
 *   - เก็บใน SharedPreferences เป็น JSON list
 *   - MAX_QUEUE_SIZE = 100 (AppConfig) — ป้องกัน queue โต
 *   - Duplicate prevention: ตรวจ notificationId ก่อน enqueue
 *   - Flush: ส่งทีละรายการ, ลบออกเมื่อสำเร็จ, หยุดเมื่อ NetworkError
 */
class OfflineQueue(private val context: Context) {

    companion object {
        private const val TAG = "OfflineQueue"
    }

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(
        AppConfig.QUEUE_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * enqueue() — เพิ่ม BankAmount เข้า queue
     *
     * ข้ามถ้า:
     *   - notificationId ซ้ำ (duplicate detection)
     *   - queue เต็ม (MAX_QUEUE_SIZE)
     *
     * @return true ถ้าเพิ่มสำเร็จ
     */
    @Synchronized
    fun enqueue(item: BankAmount): Boolean {
        val current = loadQueue()

        // ตรวจ duplicate ด้วย notificationId
        if (current.any { it.notificationId == item.notificationId }) {
            Log.d(TAG, "Duplicate in queue, skip: ${item.notificationId}")
            return false
        }

        // ตรวจ queue เต็ม
        if (current.size >= AppConfig.MAX_QUEUE_SIZE) {
            Log.w(TAG, "Queue full (${AppConfig.MAX_QUEUE_SIZE}), dropping oldest item")
            current.removeAt(0)
        }

        current.add(item)
        saveQueue(current)
        updateQueueCount(current.size)

        Log.d(TAG, "Enqueued ${item.notificationId} — queue size: ${current.size}")
        return true
    }

    /**
     * flush() — ส่งทุกรายการใน queue ไปยัง backend
     *
     * ต้อง call จาก IO thread
     *
     * Logic:
     *   - วน loop ส่งทีละรายการ
     *   - Success → ลบออกจาก queue
     *   - ServerError (4xx) → ลบออก (ส่งซ้ำไม่มีประโยชน์ — payload มีปัญหา)
     *   - NetworkError → หยุด flush (รอ internet กลับมา)
     *
     * @return จำนวนรายการที่ส่งสำเร็จ
     */
    @Synchronized
    fun flush(): Int {
        val queue = loadQueue()
        if (queue.isEmpty()) return 0

        Log.d(TAG, "Flushing ${queue.size} queued items")

        var successCount = 0
        val toRemove = mutableListOf<BankAmount>()

        for (item in queue) {
            when (val result = ApiClient.send(item)) {
                is ApiResult.Success -> {
                    Log.i(TAG, "Flushed: ${item.notificationId} → ${result.summary()}")
                    toRemove.add(item)
                    successCount++
                }
                is ApiResult.ServerError -> {
                    // 4xx = data มีปัญหา ส่งซ้ำไม่มีประโยชน์ → drop
                    Log.w(TAG, "Drop bad item ${item.notificationId}: ${result.summary()}")
                    toRemove.add(item)
                }
                is ApiResult.NetworkError -> {
                    // Network ยังไม่กลับมา → หยุด flush ไว้ก่อน
                    Log.d(TAG, "Network still down — stopping flush at ${item.notificationId}")
                    break
                }
            }
        }

        if (toRemove.isNotEmpty()) {
            queue.removeAll(toRemove.toSet())
            saveQueue(queue)
            updateQueueCount(queue.size)
        }

        Log.d(TAG, "Flush done — sent: $successCount, remaining: ${queue.size}")
        return successCount
    }

    /**
     * size() — จำนวนรายการใน queue (อ่าน SharedPreferences)
     */
    @Synchronized
    fun size(): Int = loadQueue().size

    /**
     * isEmpty() — ตรวจว่า queue ว่างเปล่า
     */
    @Synchronized
    fun isEmpty(): Boolean = loadQueue().isEmpty()

    // =========================================================================
    // Private helpers
    // =========================================================================

    private fun loadQueue(): MutableList<BankAmount> {
        val json = prefs.getString(AppConfig.QUEUE_KEY, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<BankAmount>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load queue, resetting: ${e.message}")
            mutableListOf()
        }
    }

    private fun saveQueue(queue: List<BankAmount>) {
        prefs.edit()
            .putString(AppConfig.QUEUE_KEY, gson.toJson(queue))
            .apply()
    }

    private fun updateQueueCount(count: Int) {
        AppState.setQueueCount(context, count)
    }
}
