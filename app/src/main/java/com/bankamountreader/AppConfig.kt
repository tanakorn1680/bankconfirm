package com.bankamountreader

/**
 * AppConfig — Central configuration
 *
 * เปลี่ยน Bank Package Names ได้ที่นี่ หรือใน app/build.gradle.kts
 * (BuildConfig fields จะ override ค่าเหล่านี้โดยอัตโนมัติ)
 *
 * Phase 1: โครงสร้าง config พร้อมใช้
 * Phase 2+: ค่าจริงจะถูกใช้งาน
 */
object AppConfig {

    // =========================================================================
    // Bank App Package Names
    // เปลี่ยนตาม Package Name ของแอปธนาคารที่ต้องการ monitor
    // =========================================================================
    val BANK_APP_1_PACKAGE: String get() = BuildConfig.BANK_APP_1_PACKAGE
    val BANK_APP_2_PACKAGE: String get() = BuildConfig.BANK_APP_2_PACKAGE

    // =========================================================================
    // Bank display names (สำหรับแสดงใน UI และส่งไป API)
    // =========================================================================
    const val BANK_1_LABEL = "bank_1"
    const val BANK_2_LABEL = "bank_2"
    const val BANK_TEST_LABEL = "test"

    // =========================================================================
    // API Configuration
    // =========================================================================
    val API_BASE_URL: String get() = BuildConfig.API_BASE_URL
    val DEVICE_TOKEN: String get() = BuildConfig.DEVICE_TOKEN
    const val API_ENDPOINT = "/api/bank-amount"
    const val API_TIMEOUT_SECONDS = 30L

    // =========================================================================
    // Offline Queue
    // =========================================================================
    const val QUEUE_PREFS_NAME = "bank_amount_queue"
    const val QUEUE_KEY = "pending_items"
    const val MAX_QUEUE_SIZE = 100 // ป้องกัน queue โต unlimited

    // =========================================================================
    // Notification Channel (Foreground Service)
    // =========================================================================
    const val NOTIFICATION_CHANNEL_ID = "bank_reader_service"
    const val FOREGROUND_NOTIFICATION_ID = 1001
}
