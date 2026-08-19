package com.bankamountreader.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.bankamountreader.data.AppState
import com.bankamountreader.network.OfflineQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * NetworkChangeReceiver — Phase 7
 *
 * รับ broadcast เมื่อ network เปลี่ยนสถานะ
 * ถ้า internet กลับมา → flush OfflineQueue อัตโนมัติ
 *
 * ลงทะเบียนใน AndroidManifest (ทำงานได้แม้แอปปิดอยู่)
 *
 * หมายเหตุ: CONNECTIVITY_ACTION deprecated ใน API 28+ แต่ยังใช้ได้
 * สำหรับ static receiver ใน manifest — ใช้ร่วมกับ
 * ConnectivityManager.getNetworkCapabilities() ตรวจสถานะจริง
 */
class NetworkChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkChangeReceiver"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (!isOnline(context)) {
            Log.d(TAG, "Network lost — skip flush")
            return
        }

        Log.d(TAG, "Network available — flushing offline queue")

        // goAsync() ทำให้ BroadcastReceiver มีเวลาทำงาน async มากขึ้น
        val pendingResult = goAsync()

        scope.launch {
            try {
                val queue = OfflineQueue(context)
                if (queue.isEmpty()) {
                    Log.d(TAG, "Queue empty — nothing to flush")
                    return@launch
                }

                val sent = queue.flush()
                if (sent > 0) {
                    AppState.setServerOk(context, true)
                    Log.i(TAG, "Auto-flush on reconnect: $sent item(s) sent")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
