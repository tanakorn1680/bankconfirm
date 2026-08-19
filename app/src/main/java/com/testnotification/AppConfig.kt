package com.testnotification

/**
 * AppConfig — ตั้งค่าก่อน build
 *
 * BANK_APP_1_PACKAGE / BANK_APP_2_PACKAGE:
 *   package name ของแอปธนาคารที่ต้องการ monitor
 *
 * SHOW_ALL_FOR_DEBUG:
 *   true  → service รับทุกแอป (สำหรับทดสอบว่า listener ทำงาน)
 *   false → รับเฉพาะ package ธนาคาร
 */
object AppConfig {

    // ── Bank Packages ──────────────────────────────────────────────────
    const val BANK_APP_1_PACKAGE = "com.kasikorn.retail.mbanking.wap"   // K PLUS / KBank
    const val BANK_APP_2_PACKAGE = "com.truemoney.android.wallet"        // TrueMoney
    const val BANK_1_LABEL       = "bank_1"
    const val BANK_2_LABEL       = "bank_2"
    const val BANK_TEST_LABEL    = "test"

    // ── Debug Mode ──────────────────────────────────────────────────────
    /** true = รับทุกแอป (สำหรับทดสอบ listener), false = กรองแค่ bank */
    const val SHOW_ALL_FOR_DEBUG = true

    // ── API ─────────────────────────────────────────────────────────────
    const val API_BASE_URL        = "https://scid-ecru.vercel.app"
    const val DEVICE_TOKEN        = "mytoken2026"
    const val API_ENDPOINT        = "/api/bank-amount"
    const val API_TIMEOUT_SECONDS = 30L

    // ── Offline Queue ────────────────────────────────────────────────────
    const val QUEUE_PREFS_NAME = "bank_amount_queue"
    const val QUEUE_KEY        = "pending_items"
    const val MAX_QUEUE_SIZE   = 100

    // ── Foreground Notification ──────────────────────────────────────────
    const val NOTIFICATION_CHANNEL_ID    = "bank_reader_service"
    const val FOREGROUND_NOTIFICATION_ID = 1001
}
