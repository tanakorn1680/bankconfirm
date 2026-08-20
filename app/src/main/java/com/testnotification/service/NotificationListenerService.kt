package com.testnotification.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
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

class TestNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "TestNotifService"

        private val BANK_PACKAGES = mapOf(
            "com.kasikorn.retail.mbanking.wap" to "kbank",
            "th.co.truemoney.wallet"            to "truemoney",
            "com.truemoney.android.wallet"      to "truemoney",
        )

        const val SHOW_ALL = true
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── เพิ่มใหม่เพื่อทำงานพื้นหลัง ─────────────────────────────────────
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        // WakeLock — ป้องกัน CPU sleep ขณะส่ง API
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TestNotification:ApiCall")
        // Foreground notification — ป้องกัน Android kill service
        createNotificationChannel()
        startForeground(1001, buildForegroundNotification())
        Log.d(TAG, "Service created — SHOW_ALL=$SHOW_ALL")
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.w(TAG, "Service destroyed")
    }
    // ─────────────────────────────────────────────────────────────────────

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected")
        requestRebind(ComponentName(this, TestNotificationService::class.java))
    }

    // =========================================================================
    // ส่วนนี้คือของเดิมทั้งหมด ไม่มีการแก้ไขใดๆ
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

        val bankLabel = BANK_PACKAGES[pkg] ?: return

        val fullText = listOf(title, bigText.takeIf { it != "-" } ?: text)
            .filter { it != "-" }
            .joinToString(" ")

        val amount = AmountParser.parse(fullText)

        if (amount == null) {
            Log.d(TAG, "[$bankLabel] ไม่พบยอดเงินใน: $fullText")
            AppState.updateLastLog("⚠️ [$bankLabel] อ่านได้แต่ parse ยอดไม่ได้: $fullText")
            broadcast()
            return
        }

        Log.i(TAG, "[$bankLabel] พบยอด ${AmountParser.format(amount)} — ส่ง API...")
        AppState.updateLastLog("⏳ ${AmountParser.format(amount)} [$bankLabel] กำลังจับคู่...")
        broadcast()

        val notifId = "${pkg}_${sbn.postTime}_${amount}"

        // acquire wakelock ก่อน launch — release ใน finally
        wakeLock?.acquire(60_000L)

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
                wakeLock?.let { if (it.isHeld) it.release() }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun broadcast() {
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(AppState.ACTION_NOTIFICATION_RECEIVED))
    }

    // ── เพิ่มใหม่ ─────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            "payment_service", "Payment Service", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }

    private fun buildForegroundNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "payment_service")
            .setContentTitle("Payment กำลังทำงาน")
            .setContentText("รอรับการแจ้งเตือนจากธนาคาร")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    // ─────────────────────────────────────────────────────────────────────
}
