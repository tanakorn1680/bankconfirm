package com.testnotification.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.testnotification.MainActivity
import com.testnotification.R

/**
 * KeepAliveService — Foreground Service
 *
 * ทำงานแค่อย่างเดียวคือ แสดง persistent notification เพื่อให้
 * Android รู้ว่า process นี้ต้องมีชีวิตอยู่ตลอด
 * ป้องกัน OS kill NotificationListenerService เมื่อปิดหน้าจอ/ปัดแอพออก
 *
 * ไม่มี logic อะไรเพิ่ม — การทำงานหลักยังอยู่ใน NotificationListenerService ทั้งหมด
 */
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID   = "payment_keepalive"
        private const val NOTIF_ID     = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY — ถ้าถูก kill ให้ Android restart อัตโนมัติ
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // restart ตัวเองถ้าถูก kill
        val restart = Intent(applicationContext, KeepAliveService::class.java)
        startService(restart)
    }

    // =========================================================================
    // Notification (persistent — แสดงตลอดเพื่อค้ำ foreground)
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
            .setOngoing(true)          // ผู้ใช้ปัดออกไม่ได้
            .setSilent(true)           // ไม่มีเสียง/สั่น
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Payment Service",
            NotificationManager.IMPORTANCE_LOW  // ไม่รบกวน ไม่มีเสียง
        ).apply {
            description = "แสดงสถานะการทำงานพื้นหลัง"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
