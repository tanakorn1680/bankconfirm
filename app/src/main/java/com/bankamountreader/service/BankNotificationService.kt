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
import com.bankamountreader.AppConfig
import com.bankamountreader.MainActivity
import com.bankamountreader.R
import com.bankamountreader.data.AppState
import com.bankamountreader.data.BankAmount
import com.bankamountreader.network.ApiClient
import com.bankamountreader.network.ApiResult
import com.bankamountreader.network.OfflineQueue
import com.bankamountreader.util.AmountParser
import com.bankamountreader.util.DuplicateGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BankNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "BankNotifService"
        const val ACTION_MATCH_SUCCESS = "com.bankamountreader.MATCH_SUCCESS"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
        Log.d(TAG, "Listener connected")
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

        // 1. กรอง package — รับเฉพาะธนาคารที่ตั้งค่า
        val bankLabel = when (sbn.packageName) {
            AppConfig.BANK_APP_1_PACKAGE -> AppConfig.BANK_1_LABEL
            AppConfig.BANK_APP_2_PACKAGE -> AppConfig.BANK_2_LABEL
            else -> return
        }

        // 2. ดึงข้อความ
        val extras  = sbn.notification?.extras ?: return
        val title   = extras.getCharSequence("android.title")?.toString() ?: ""
        val text    = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val body    = bigText.ifEmpty { text }

        Log.d(TAG, "[$bankLabel] title='$title' body='$body'")

        // 3. Duplicate check
        val notifId = duplicateGuard.generateId(sbn.packageName, sbn.postTime, title, text)
        if (duplicateGuard.isDuplicate(notifId)) {
            Log.d(TAG, "Duplicate — skip")
            return
        }

        // 4. Parse ยอด (per-bank)
        val amount = when (sbn.packageName) {
            AppConfig.BANK_APP_1_PACKAGE -> AmountParser.parseKBank(title, body)
            AppConfig.BANK_APP_2_PACKAGE -> AmountParser.parseTrueMoney(title, body)
            else -> null
        } ?: run {
            Log.d(TAG, "No amount — skip")
            return
        }

        Log.i(TAG, "Amount: ${AmountParser.format(amount)} from $bankLabel")
        duplicateGuard.markSeen(notifId)

        // 5. อัปเดต UI state
        AppState.setLastAmount(applicationContext, amount, bankLabel, sbn.postTime, notifId)

        val bankAmount = BankAmount(
            amount         = amount,
            currency       = "THB",
            bank           = bankLabel,
            timestamp      = sbn.postTime / 1000L,
            notificationId = notifId,
            isTest         = false
        )

        // 6. ส่ง API (match + approve อัตโนมัติใน backend)
        serviceScope.launch {
            sendOrEnqueue(bankAmount, amount)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    // =========================================================================
    // Send / Queue
    // =========================================================================

    private fun sendOrEnqueue(bankAmount: BankAmount, amount: Double) {
        if (!isOnline()) {
            offlineQueue.enqueue(bankAmount)
            Log.d(TAG, "Offline — queued")
            return
        }

        when (val result = ApiClient.send(bankAmount)) {
            is ApiResult.Success -> {
                Log.i(TAG, "Sent OK — backend will match+approve")
                AppState.setServerOk(applicationContext, true)

                // parse order_id จาก response ถ้ามี
                val orderId = try {
                    com.google.gson.JsonParser.parseString(result.body)
                        ?.asJsonObject?.get("order_id")?.asString ?: ""
                } catch (e: Exception) { "" }

                // broadcast แจ้ง MainActivity
                sendBroadcast(Intent(ACTION_MATCH_SUCCESS).apply {
                    putExtra("amount", amount)
                    putExtra("order_id", orderId)
                })

                flushQueueIfOnline()
            }
            is ApiResult.NetworkError -> {
                AppState.setServerOk(applicationContext, false)
                offlineQueue.enqueue(bankAmount)
                Log.w(TAG, "Network error — queued")
            }
            is ApiResult.ServerError -> {
                AppState.setServerOk(applicationContext, false)
                Log.e(TAG, "Server error ${result.code} — not queued")
            }
        }
    }

    private fun flushQueueIfOnline() {
        serviceScope.launch {
            if (!isOnline() || offlineQueue.isEmpty()) return@launch
            val sent = offlineQueue.flush()
            if (sent > 0) {
                AppState.setServerOk(applicationContext, true)
                Log.i(TAG, "Queue flushed: $sent items")
            }
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
