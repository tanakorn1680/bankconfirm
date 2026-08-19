package com.bankamountreader.service

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
import com.bankamountreader.AppConfig
import com.bankamountreader.MainActivity
import com.bankamountreader.data.AppState
import com.bankamountreader.data.BankAmount
import com.bankamountreader.data.NotificationLog
import com.bankamountreader.network.ApiClient
import com.bankamountreader.network.ApiResult
import com.bankamountreader.network.OfflineQueue
import com.bankamountreader.util.AmountParser
import com.bankamountreader.util.DuplicateGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BankNotificationService — rebuilt on TestNotificationApp foundation
 *
 * สิ่งที่ต่างจาก TestApp:
 *   1. กรองเฉพาะ package ธนาคารที่กำหนด
 *   2. log notification ทุกรายการ (รวมที่ parse ไม่ได้) → Debug tab
 *   3. parse ยอดเงิน → ส่ง API → offline queue
 *   4. ForegroundService (เพื่อคง lifecycle)
 *
 * สิ่งที่ยึดจาก TestApp (แก้ปัญหาหลัก):
 *   - ไม่มี default_filter_types → รับทุก importance level
 *   - ไม่มี startService() เพิ่มเติม (Android rebind เอง)
 */
class BankNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "BankNotifSvc"
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
        Log.d(TAG, "Service created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected — monitoring: ${AppConfig.BANK_APP_1_PACKAGE}, ${AppConfig.BANK_APP_2_PACKAGE}")
        scope.launch { flushIfOnline() }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected")
    }

    // =========================================================================
    // Main pipeline — ยึดโครงจาก TestNotificationService ทุกอย่าง
    // =========================================================================

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return

        // ── Step 1: กรองเฉพาะแอปธนาคาร ─────────────────────────────────────
        val bankLabel = when (pkg) {
            AppConfig.BANK_APP_1_PACKAGE -> AppConfig.BANK_1_LABEL
            AppConfig.BANK_APP_2_PACKAGE -> AppConfig.BANK_2_LABEL
            else -> return
        }

        // ── Step 2: ดึง raw text จาก extras (เหมือน TestApp ทุกอย่าง) ────────
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

        Log.d(TAG, "[$bankLabel] title=$title | text=$text | bigText=$bigText")

        // ── Step 3: parse ยอดเงิน ────────────────────────────────────────────
        val fullText    = listOf(title, bigText.takeIf { it != "-" } ?: text).filter { it != "-" }.joinToString(" ")
        val parsedAmount = AmountParser.parse(fullText)

        // ── Step 4: log ทุกรายการ (ก่อน duplicate check) → Debug tab ────────
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

        // ── Step 5: ถ้า parse ไม่ได้ หยุดแค่นี้ ─────────────────────────────
        if (parsedAmount == null) {
            Log.d(TAG, "No amount parsed — logged only")
            return
        }

        // ── Step 6: Duplicate check ───────────────────────────────────────────
        val notifId = duplicateGuard.generateId(pkg, sbn.postTime, title, text)
        if (duplicateGuard.isDuplicate(notifId)) {
            Log.d(TAG, "Duplicate — ignored: $notifId")
            return
        }
        duplicateGuard.markSeen(notifId)

        Log.i(TAG, "Amount: ${AmountParser.format(parsedAmount)} from $bankLabel")

        val bankAmount = BankAmount(
            amount         = parsedAmount,
            currency       = "THB",
            bank           = bankLabel,
            timestamp      = sbn.postTime / 1000L,
            notificationId = notifId,
            isTest         = false
        )

        // ── Step 7: ส่ง API ───────────────────────────────────────────────────
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
            getString(com.bankamountreader.R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(com.bankamountreader.R.string.notification_channel_desc)
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
            .setContentTitle(getString(com.bankamountreader.R.string.foreground_notification_title))
            .setContentText(getString(com.bankamountreader.R.string.foreground_notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
