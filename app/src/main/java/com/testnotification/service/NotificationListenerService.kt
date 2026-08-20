package com.testnotification.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.testnotification.MainActivity
import com.testnotification.R
import com.testnotification.data.AppState
import com.testnotification.data.NotificationLog
import com.testnotification.network.ApiClient
import com.testnotification.util.AmountParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * TestNotificationService
 *
 * การแก้ไขเพื่อทำงานพื้นหลังตลอด:
 *
 * 1. startForeground() ใน onCreate()
 *    → Android จัดประเภทเป็น foreground process → ไม่ kill แม้ปิดหน้าจอ
 *
 * 2. WakeLock แบบ PARTIAL_WAKE_LOCK
 *    → ป้องกัน CPU เข้า deep sleep ขณะ coroutine กำลังส่ง API
 *    → release ทันทีหลัง API ตอบกลับ (ไม่ drain battery)
 *
 * 3. android:stopWithTask="false" ใน Manifest
 *    → service ไม่ตายแม้ user swipe ออกจาก recent apps
 *
 * 4. BootReceiver ใน Manifest
 *    → Android rebind service หลัง reboot อัตโนมัติ
 */
class TestNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "TestNotifService"

        private const val CHANNEL_ID        = "payment_service"
        private const val FOREGROUND_NOTIF_ID = 1001
        private const val WAKELOCK_TAG      = "TestNotification:ApiCall"
        private const val WAKELOCK_TIMEOUT  = 60_000L  // 60 วินาที max (กันลืม release)

        private val BANK_PACKAGES = mapOf(
            "com.kasikorn.retail.mbanking.wap" to "kbank",
            "th.co.truemoney.wallet"            to "truemoney",
            "com.truemoney.android.wallet"      to "truemoney",
        )

        const val SHOW_ALL = true
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var wakeLock: PowerManager.WakeLock

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onCreate() {
        super.onCreate()

        // ── 1. WakeLock ────────────────────────────────────────────────────
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)

        // ── 2. Foreground Notification ─────────────────────────────────────
        createNotificationChannel()
        startForeground(FOREGROUND_NOTIF_ID, buildForegroundNotification())

        Log.d(TAG, "Service created — foreground started")
    }

    override fun onDestroy() {
        super.onDestroy()
        // release wakelock ถ้ายังถืออยู่ตอน destroy
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        Log.w(TAG, "Service destroyed")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected")
        // requestRebind ให้ Android reconnect listener อัตโนมัติ
        requestRebind(componentName)
    }

    // =========================================================================
    // Main pipeline — ไม่เปลี่ยน logic เดิม แค่เพิ่ม wakelock รอบ API call
    // =========================================================================

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val pkg = sbn.packageName ?: return

        if (!SHOW_ALL && pkg !in BANK_PACKAGES) return

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (e: Exception) { "-" }

        val extras = sbn.notification?.extras

        fun extra(key: String): String =
            extras?.getCharSequence(key)?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "-"

        val title   = extra("android.title")
        val text    = extra("android.text")
        val bigText = extra("android.bigText")
        val subText = extra("android.subText")

        val log = NotificationLog(
            timeMs      = sbn.postTime,
            packageName = pkg,
            appName     = appName,
            title       = title,
            text        = text,
            bigText     = bigText,
            subText     = subText,
        )
        AppState.addLog(log)
        broadcast()

        // ── Match logic เฉพาะ BANK_PACKAGES ──────────────────────────────
        val bankLabel = BANK_PACKAGES[pkg] ?: return

        val fullText = listOf(title, bigText.takeIf { it != "-" } ?: text)
            .filter { it != "-" }
            .joinToString(" ")

        val amount = AmountParser.parse(fullText)

        if (amount == null) {
            Log.d(TAG, "[$bankLabel] parse ไม่ได้: $fullText")
            AppState.updateLastLog("⚠️ [$bankLabel] อ่านได้แต่ parse ยอดไม่ได้: $fullText")
            broadcast()
            return
        }

        Log.i(TAG, "[$bankLabel] พบยอด ${AmountParser.format(amount)} — ส่ง API...")
        AppState.updateLastLog("⏳ ${AmountParser.format(amount)} [$bankLabel] กำลังจับคู่...")
        broadcast()

        val notifId = "${pkg}_${sbn.postTime}_${amount}"

        // ── WakeLock ก่อน launch coroutine ───────────────────────────────
        // acquire ด้วย timeout 60s กันกรณี release หาย
        wakeLock.acquire(WAKELOCK_TIMEOUT)

        serviceScope.launch {
            try {
                val result = ApiClient.confirmPayment(
                    amount         = amount,
                    bank           = bankLabel,
                    timestampSec   = sbn.postTime / 1000L,
                    notificationId = notifId,
                    isTest         = false,
                )

                val resultText = when (result) {
                    is ApiClient.ConfirmResult.Matched ->
                        "✅ จับคู่สำเร็จ! Order: ${result.orderId}"
                    is ApiClient.ConfirmResult.AwaitingReview ->
                        "🔶 Match แล้ว รอ admin approve: ${result.orderId} (${result.reason})"
                    is ApiClient.ConfirmResult.NoMatch ->
                        "❌ ไม่พบ order ที่ตรง (${AmountParser.format(amount)})"
                    is ApiClient.ConfirmResult.Duplicate ->
                        "⏭️ Notification ซ้ำ — ข้ามแล้ว"
                    is ApiClient.ConfirmResult.TestReceived ->
                        "🧪 Test received"
                    is ApiClient.ConfirmResult.Failure ->
                        "💥 Error: ${result.message}"
                }

                Log.i(TAG, "match result: $resultText")
                AppState.updateLastLog("${AmountParser.format(amount)} [$bankLabel] → $resultText")
                broadcast()
            } finally {
                // release เสมอ ไม่ว่าจะ success หรือ exception
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    // =========================================================================
    // Foreground Notification
    // =========================================================================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW      // LOW = ไม่มีเสียง ไม่สั่น
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(getString(R.string.foreground_notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)        // user ลบไม่ได้ (ป้องกันปิด service โดยไม่ตั้งใจ)
            .setSilent(true)         // ไม่มีเสียง
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private fun broadcast() {
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(AppState.ACTION_NOTIFICATION_RECEIVED))
    }
}
