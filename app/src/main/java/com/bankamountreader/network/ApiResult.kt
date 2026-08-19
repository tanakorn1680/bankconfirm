package com.bankamountreader.network

/**
 * ApiResult — sealed class สำหรับผลลัพธ์จาก ApiClient
 *
 * Success      → HTTP 2xx — backend รับข้อมูลแล้ว
 * ServerError  → HTTP 4xx/5xx — backend ตอบ error (token ผิด, payload ผิด ฯลฯ)
 * NetworkError → IOException — ไม่มี internet, timeout, DNS fail
 */
sealed class ApiResult {

    data class Success(
        val code: Int,
        val body: String
    ) : ApiResult()

    data class ServerError(
        val code: Int,
        val body: String
    ) : ApiResult()

    data class NetworkError(
        val message: String
    ) : ApiResult()

    /** สะดวกตรวจว่าสำเร็จหรือไม่ */
    fun isSuccess() = this is Success

    /** ข้อความสรุปสำหรับ log / toast */
    fun summary(): String = when (this) {
        is Success      -> "OK [$code]"
        is ServerError  -> "Server error [$code]: $body"
        is NetworkError -> "Network error: $message"
    }
}
