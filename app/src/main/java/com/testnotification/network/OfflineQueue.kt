package com.testnotification.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.testnotification.AppConfig
import com.testnotification.data.AppState
import com.testnotification.data.BankAmount

class OfflineQueue(private val context: Context) {

    companion object { private const val TAG = "OfflineQueue" }

    private val gson  = Gson()
    private val prefs = context.getSharedPreferences(AppConfig.QUEUE_PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun enqueue(item: BankAmount): Boolean {
        val queue = loadQueue()
        if (queue.any { it.notificationId == item.notificationId }) return false
        if (queue.size >= AppConfig.MAX_QUEUE_SIZE) queue.removeAt(0)
        queue.add(item)
        save(queue)
        AppState.setQueueCount(context, queue.size)
        Log.d(TAG, "Enqueued ${item.notificationId} — size: ${queue.size}")
        return true
    }

    @Synchronized
    fun flush(): Int {
        val queue = loadQueue()
        if (queue.isEmpty()) return 0
        var sent = 0
        val remove = mutableListOf<BankAmount>()
        for (item in queue) {
            when (ApiClient.send(item)) {
                is ApiResult.Success      -> { remove.add(item); sent++ }
                is ApiResult.ServerError  -> { remove.add(item) }
                is ApiResult.NetworkError -> break
            }
        }
        queue.removeAll(remove.toSet())
        save(queue)
        AppState.setQueueCount(context, queue.size)
        return sent
    }

    @Synchronized fun isEmpty(): Boolean = loadQueue().isEmpty()

    private fun loadQueue(): MutableList<BankAmount> {
        val json = prefs.getString(AppConfig.QUEUE_KEY, null) ?: return mutableListOf()
        return try {
            gson.fromJson(json, object : TypeToken<MutableList<BankAmount>>() {}.type) ?: mutableListOf()
        } catch (e: Exception) { mutableListOf() }
    }

    private fun save(queue: List<BankAmount>) {
        prefs.edit().putString(AppConfig.QUEUE_KEY, gson.toJson(queue)).apply()
    }
}
