package com.bankamountreader.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver — Phase 7
 *
 * Android จะ kill service เมื่อรีบูต
 * BankNotificationService เป็น NotificationListenerService → Android restart ให้อัตโนมัติ
 * ถ้า user เปิด Notification Access ไว้แล้ว
 *
 * BootReceiver นี้ใช้เพื่อ:
 *   1. Log ว่า device boot ขึ้นมา (debug)
 *   2. flush OfflineQueue ที่ค้างอยู่ตั้งแต่ก่อน reboot
 *
 * NotificationListenerService Android จะ bind ให้เองโดยอัตโนมัติ
 * ไม่ต้อง startService() เอง
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Device booted — Android will rebind NotificationListenerService automatically")

        // NetworkChangeReceiver จะ flush queue เมื่อ internet พร้อม
        // ไม่ต้อง flush ที่นี่เพราะ network อาจยังไม่พร้อมตอน boot
    }
}
