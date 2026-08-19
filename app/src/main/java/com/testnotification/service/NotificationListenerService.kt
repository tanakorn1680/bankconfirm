package com.testnotification.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.testnotification.AppConfig
import com.testnotification.MainActivity
import com.testnotification.data.AppState
import com.testnotification.data.BankAmount
import com.testnotification.data.NotificationLog
import com.testnotification.network.ApiClient
import com.testnotification.network.ApiResult
import com.testnotification.network.OfflineQueue
import com.testnotification.util.AmountParser
import com.testnotification.util.DuplicateGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * TestNotificationService (Merged)
 *
 * รวม 2 พฤติกรรมไว้ใน service เดียว:
 *
 * [A] Debug Mode (AppConfig.SHOW_ALL_FOR_DEBUG = true)
 *     → รับทุกแอป → log raw ไว้ดูใน Debug tab
 *     → ถ้ามี keyword รับเงิน → parse + ส่ง API เสมอ
 *
 * [B] Production Mode (SHOW_ALL_FOR_DEBUG = false)
 *     → กรองเฉพาะ package ธนาคาร → log + parse + ส่ง API
 *
 * สิ่งที่ยืมจาก TestApp (ทำให้ listener ทำงานได้):
 *   - ไม่มี default_filter_types ใน Manifest → รับทุก importance level
 *   - ไม่มี startService() เพิ่มเติม (Android rebind เอง)
 *   - startForeground() เพื่อคง lifecycle ไม่ให้ถูก kill
 */
class TestNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "TestNotifService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var duplicateGuard: DuplicateGuard
    private lateinit var offlineQueue: OfflineQueue

    override fun onCreate() {
        super.onCreate()
        duplicateGuard = DuplicateGuard(applicationContext)
        offlineQueue   = OfflineQueue(applicationContext)
        createNotificationChannel()
        startForeground(AppConfig.FOREGROUND_NOTIFICATION_ID, buildForegroundNotification())
        Log.d(TAG, "Service created — SHOW_ALL_FOR_DEBUG=${AppConfig.SHOW_ALL_FOR_DEBUG}")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
        scope.launch { flushIfOnline() }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected")
    }

    // =========================================================================
    // Main pipeline
    // =========================================================================

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return

        // ── Step 1: Package Filter ────────────────────────────────────────────
        val bankLabel: String? = when (pkg) {
            AppConfig.BANK_APP_1_PACKAGE -> AppConfig.BANK_1_LABEL
            AppConfig.BANK_APP_2_PACKAGE -> AppConfig.BANK_2_LABEL
            else -> null
        }

        // ถ้าไม่ใช่ bank และ debug mode ปิดอยู่ → ข้าม
        if (bankLabel == null && !AppConfig.SHOW_ALL_FOR_DEBUG) return

        // ── Step 2: ดึง raw text จาก extras ──────────────────────────────────
        val extras = sbn.notification?.extras ?: return

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }

        fun extra(key: String): String =
            extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "-"

        val title   = extra("android.title")
        val text    = extra("android.text")
        val bigText = extra("android.bigText")
        val subText = extra("android.subText")

        Log.d(TAG, "[${bankLabel ?: pkg}] title=$title | text=$text | bigText=$bigText")

        // ── Step 3: parse ยอดเงิน ─────────────────────────────────────────────
        val fullText     = listOf(title, bigText.takeIf { it != "-" } ?: text).filter { it != "-" }.joinToString(" ")
        val parsedAmount = AmountParser.parse(fullText)

        // ── Step 4: log ทุกรายการ → Debug tab ────────────────────────────────
        val log = NotificationLog(
            timeMs       = sbn.postTime,
            packageName  = pkg,
            appName      = appName,
            title        = title,
            text         = text,
            bigText      = bigText,
            subText      = subText,
            parsedAmount = parsedAmount,
            matched      = false
        )
        AppState.addLog(log)
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(AppState.ACTION_NOTIFICATION_RECEIVED))

        // ── Step 5: ถ้า parse ไม่ได้ หยุดแค่นี้ ──────────────────────────────
        if (parsedAmount == null) {
            Log.d(TAG, "No amount parsed — logged only")
            return
        }

        // ── Step 6: Duplicate check ────────────────────────────────────────────
        val notifId = duplicateGuard.generateId(pkg, sbn.postTime, title, text)
        if (duplicateGuard.isDuplicate(notifId)) {
            Log.d(TAG, "Duplicate — ignored: $notifId")
            return
        }
        duplicateGuard.markSeen(notifId)

        val effectiveLabel = bankLabel ?: AppConfig.BANK_TEST_LABEL
        Log.i(TAG, "Amount: ${AmountParser.format(parsedAmount)} from $effectiveLabel")

        val bankAmount = BankAmount(
            amount         = parsedAmount,
            currency       = "THB",
            bank           = effectiveLabel,
            timestamp      = sbn.postTime / 1000L,
            notificationId = notifId,
            isTest         = (bankLabel == null) // ถ้าไม่ใช่ bank จริง → ถือว่า test
        )

        // ── Step 7: ส่ง API ────────────────────────────────────────────────────
        scope.launch { sendOrEnqueue(bankAmount) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    // =========================================================================
    // Network helpers
    // =========================================================================

    private fun sendOrEnqueue(bankAmount: BankAmount) {
        if (!isOnline()) {
            offlineQueue.enqueue(bankAmount)
            AppState.setServerOk(applicationContext, false)
            return
        }
        when (val result = ApiClient.send(bankAmount)) {
            is ApiResult.Success      -> { AppState.setServerOk(applicationContext, true); flushIfOnline() }
            is ApiResult.NetworkError -> { AppState.setServerOk(applicationContext, false); offlineQueue.enqueue(bankAmount) }
            is ApiResult.ServerError  -> { AppState.setServerOk(applicationContext, false); Log.e(TAG, "Server rejected: ${result.summary()}") }
        }
    }

    private fun flushIfOnline() {
        if (!isOnline() || offlineQueue.isEmpty()) return
        val sent = offlineQueue.flush()
        if (sent > 0) AppState.setServerOk(applicationContext, true)
    }

    private fun isOnline(): Boolean {
        val cm  = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // =========================================================================
    // Foreground notification
    // =========================================================================

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            AppConfig.NOTIFICATION_CHANNEL_ID,
            "Bank Reader Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the notification listener running"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildForegroundNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AppConfig.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Test Notification App")
            .setContentText("Monitoring notifications")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
