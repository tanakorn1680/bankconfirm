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
import com.bankamountreader.R
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
 * BankNotificationService
 *
 * รวม logic จาก TestNotificationApp (อ่านแจ้งเตือนดิบ) +
 * BankAmountReader (parse → ส่ง API → auto-match order)
 *
 * Flow:
 *   onNotificationPosted()
 *     → กรอง package ธนาคาร (Bank 1 / Bank 2)
 *     → ดึง raw text จาก extras (เหมือน TestApp)
 *     → บันทึก NotificationLog → broadcast → MainActivity อัปเดท Debug tab ทันที
 *     → ตรวจ duplicate
 *     → parse ยอดเงิน (AmountParser)
 *     → ส่ง API หรือ enqueue ถ้า offline
 */
class BankNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "BankNotifService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var duplicateGuard: DuplicateGuard
    private lateinit var offlineQueue: OfflineQueue

    // =========================================================================
    // Lifecycle
    // =========================================================================

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
        flushQueueIfOnline()
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

        // ── Step 1: กรองเฉพาะแอปธนาคารที่กำหนด ────────────────────────────
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

        val fullText = listOf(title, bigText.takeIf { it != "-" } ?: text)
            .filter { it != "-" }
            .joinToString(" ")

        Log.d(TAG, "[$bankLabel] title=$title text=$text bigText=$bigText")

        // ── Step 3: parse ยอดเงิน (ยังไม่รู้ว่า match ได้หรือเปล่า) ──────────
        val parsedAmount = AmountParser.parse(fullText)

        // ── Step 4: บันทึก log ทุกรายการ (ก่อน duplicate check) ─────────────
        // เพื่อให้ Debug tab เห็น notification ดิบทั้งหมด รวมถึงที่ parse ไม่ได้
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

        // broadcast → MainActivity อัปเดท Debug tab ทันที
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(AppState.ACTION_NOTIFICATION_RECEIVED))

        // ── Step 5: ถ้า parse ไม่ได้ยอด → หยุดที่นี่ (แสดงใน log อยู่แล้ว) ──
        if (parsedAmount == null) {
            Log.d(TAG, "No amount parsed — logged only, not sent to API")
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
    // Send / Queue
    // =========================================================================

    private fun sendOrEnqueue(bankAmount: BankAmount) {
        if (!isOnline()) {
            offlineQueue.enqueue(bankAmount)
            AppState.setServerOk(applicationContext, false)
            return
        }

        when (val result = ApiClient.send(bankAmount)) {
            is ApiResult.Success -> {
                AppState.setServerOk(applicationContext, true)
                flushQueueIfOnline()
            }
            is ApiResult.NetworkError -> {
                AppState.setServerOk(applicationContext, false)
                offlineQueue.enqueue(bankAmount)
            }
            is ApiResult.ServerError -> {
                AppState.setServerOk(applicationContext, false)
                Log.e(TAG, "Server rejected: ${result.summary()}")
            }
        }
    }

    private fun flushQueueIfOnline() {
        scope.launch {
            if (!isOnline() || offlineQueue.isEmpty()) return@launch
            val sent = offlineQueue.flush()
            if (sent > 0) AppState.setServerOk(applicationContext, true)
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun isOnline(): Boolean {
        val cm  = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            AppConfig.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }

    private fun buildForegroundNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AppConfig.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(getString(R.string.foreground_notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
