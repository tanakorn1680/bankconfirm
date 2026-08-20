package com.testnotification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver — รับ BOOT_COMPLETED และ MY_PACKAGE_REPLACED
 *
 * NotificationListenerService เป็น system-bound service
 * Android จะ rebind ให้อัตโนมัติหลัง reboot ถ้า user เปิด Notification Access ไว้แล้ว
 * ไม่ต้อง startForegroundService() เอง — Android จัดการให้
 *
 * receiver นี้มีไว้เพื่อ log เท่านั้น
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("BootReceiver", "Received: $action — NotificationListenerService will rebind automatically")
    }
}
