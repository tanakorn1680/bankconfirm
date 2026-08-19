package com.testnotification.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.testnotification.data.AppState
import com.testnotification.data.NotificationLog

/**
 * TestNotificationService — NotificationListenerService แบบ Pure Raw Display
 *
 * Flow:
 *   onNotificationPosted()
 *     → ตรวจ package (TARGET_PACKAGES หรือ catch-all ถ้าเปิด SHOW_ALL)
 *     → ดึงข้อมูล Raw จาก extras โดยไม่ตีความ
 *     → เพิ่มลง AppState (in-memory)
 *     → broadcast → MainActivity อัปเดท list
 *
 * ไม่มี: API, Network, Database, Parser, DuplicateGuard, ForegroundService
 */
class TestNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "TestNotifService"

        /**
         * [กำหนด Package Name ของแอปที่ต้องการ Monitor]
         *
         * K PLUS (KBank):   com.kasikorn.retail.mbanking.wap
         * TrueMoney:        com.truemoney.android.wallet
         *
         * ถ้าต้องการแสดง Notification จากทุกแอป (ช่วงทดสอบ) → ตั้ง SHOW_ALL = true
         */
        val TARGET_PACKAGES = setOf(
            "com.kasikorn.retail.mbanking.wap",   // K PLUS / KBank
            "com.truemoney.android.wallet"         // TrueMoney
        )

        /**
         * SHOW_ALL = true  → รับ Notification จากทุกแอป (สำหรับทดสอบว่า Service ทำงาน)
         * SHOW_ALL = false → กรองเฉพาะ TARGET_PACKAGES
         */
        const val SHOW_ALL = true
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created — SHOW_ALL=$SHOW_ALL, targets=$TARGET_PACKAGES")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val pkg = sbn.packageName ?: return

        // ── Package Filter ─────────────────────────────────────────────
        if (!SHOW_ALL && pkg !in TARGET_PACKAGES) return

        Log.d(TAG, "Notification from: $pkg")

        // ── ดึง App Name ───────────────────────────────────────────────
        val appName = try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            "-"
        }

        // ── ดึงข้อมูล Raw จาก extras ──────────────────────────────────
        val extras = sbn.notification?.extras

        fun extra(key: String): String =
            extras?.getCharSequence(key)?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "-"

        val log = NotificationLog(
            timeMs      = sbn.postTime,
            packageName = pkg,
            appName     = appName,
            title       = extra("android.title"),
            text        = extra("android.text"),
            bigText     = extra("android.bigText"),
            subText     = extra("android.subText")
        )

        AppState.addLog(log)

        // ── Broadcast → MainActivity ───────────────────────────────────
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(AppState.ACTION_NOTIFICATION_RECEIVED))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // ไม่จำเป็น
    }
}
