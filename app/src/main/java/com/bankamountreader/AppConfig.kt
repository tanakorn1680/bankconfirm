package com.bankamountreader

/**
 * AppConfig — Central configuration
 * ค่าทั้งหมดมาจาก BuildConfig (กำหนดใน app/build.gradle.kts)
 */
object AppConfig {

    // =========================================================================
    // Bank App Package Names
    // =========================================================================
    val BANK_APP_1_PACKAGE: String get() = BuildConfig.BANK_APP_1_PACKAGE
    val BANK_APP_2_PACKAGE: String get() = BuildConfig.BANK_APP_2_PACKAGE

    // =========================================================================
    // Bank display names
    // =========================================================================
    const val BANK_1_LABEL    = "bank_1"
    const val BANK_2_LABEL    = "bank_2"
    const val BANK_TEST_LABEL = "test"

    // =========================================================================
    // API Configuration
    // =========================================================================
    val API_BASE_URL: String  get() = BuildConfig.API_BASE_URL
    val DEVICE_TOKEN: String  get() = BuildConfig.DEVICE_TOKEN
    const val API_ENDPOINT          = "/api/bank-amount"
    const val API_TIMEOUT_SECONDS   = 30L

    // =========================================================================
    // Offline Queue
    // =========================================================================
    const val QUEUE_PREFS_NAME = "bank_amount_queue"
    const val QUEUE_KEY        = "pending_items"
    const val MAX_QUEUE_SIZE   = 100

    // =========================================================================
    // Notification Channel (Foreground Service)
    // =========================================================================
    const val NOTIFICATION_CHANNEL_ID    = "bank_reader_service"
    const val FOREGROUND_NOTIFICATION_ID = 1001
}
