package com.testnotification.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.testnotification.MainActivity
import com.testnotification.R

/**
 * KeepAliveService
 *
 * สิ่งที่เพิ่มจากเดิม:
 * 1. WakeLock (PARTIAL) — กัน CPU sleep ขณะรอ notification
 * 2. requestRebind() — สั่ง reconnect NotificationListener ถ้า disconnect
 * 3. onTaskRemoved() — restart เมื่อผู้ใช้ปัดแอพออกจาก recent (MIUI)
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG        = "KeepAliveService"
        private const val CHANNEL_ID = "payment_keepalive"
        private const val NOTIF_ID   = 1001
        private const val WAKE_TAG   = "com.testnotification:keepalive"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        acquireWakeLock()
        Log.d(TAG, "created + wakelock acquired")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // reconnect NotificationListener ทุกครั้งที่ service start
        reconnectNotificationListener()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "task removed — scheduling restart")
        // MIUI ปัดแอพออก → restart service ใหม่ทันที
        val restart = Intent(applicationContext, KeepAliveService::class.java)
        startService(restart)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "destroyed — restarting")
        releaseWakeLock()
        val restart = Intent(applicationContext, KeepAliveService::class.java)
        startService(restart)
    }

    // =========================================================================
    // WakeLock — กัน CPU sleep (PARTIAL_WAKE_LOCK ไม่กัน screen off)
    // =========================================================================

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
            setReferenceCounted(false)
            // acquire แบบ timeout 1 ชั่วโมง — ป้องกัน leak ถ้า release ไม่ถูกเรียก
            acquire(60 * 60 * 1000L)
        }
        Log.d(TAG, "wakelock acquired")
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "wakelock release error: ${e.message}")
        }
    }

    // =========================================================================
    // Reconnect NotificationListener
    // =========================================================================

    private fun reconnectNotificationListener() {
        try {
            val component = ComponentName(this, TestNotificationService::class.java)
            NotificationListenerService.requestRebind(component)
            Log.d(TAG, "requestRebind sent")
        } catch (e: Exception) {
            Log.w(TAG, "requestRebind failed: ${e.message}")
        }
    }

    // =========================================================================
    // Notification
    // =========================================================================

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Payment")
            .setContentText("กำลังตรวจสอบการชำระเงิน...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Payment Service", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "แสดงสถานะการทำงานพื้นหลัง"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
