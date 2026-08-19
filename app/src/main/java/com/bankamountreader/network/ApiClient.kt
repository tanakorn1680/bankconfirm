package com.bankamountreader.network

import android.util.Log
import com.bankamountreader.AppConfig
import com.bankamountreader.data.BankAmount
import com.bankamountreader.data.PendingOrder
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ApiClient — Phase 5
 *
 * ส่ง BankAmount ไปยัง Backend ผ่าน HTTPS
 *
 * POST {API_BASE_URL}/api/bank-amount
 * Authorization: Bearer {DEVICE_TOKEN}
 * Content-Type: application/json
 *
 * {
 *   "amount": 10.25,
 *   "currency": "THB",
 *   "bank": "bank_1",
 *   "timestamp": 1755432000,
 *   "notification_id": "unique-id",
 *   "is_test": false
 * }
 *
 * Return: ApiResult (sealed class)
 *   - Success   → HTTP 2xx
 *   - ServerError → HTTP 4xx/5xx (body มี detail)
 *   - NetworkError → IOException (ไม่มี internet หรือ timeout)
 */
object ApiClient {

    private const val TAG = "ApiClient"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(AppConfig.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(AppConfig.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(AppConfig.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * send() — ส่ง BankAmount ไปยัง backend
     *
     * ต้อง call จาก IO thread (ไม่ใช่ Main thread)
     * BankNotificationService ใช้ serviceScope (Dispatchers.IO) อยู่แล้ว
     */
    fun send(bankAmount: BankAmount): ApiResult {
        val url = "${AppConfig.API_BASE_URL}${AppConfig.API_ENDPOINT}"
        val payload = toPayload(bankAmount)
        val bodyJson = gson.toJson(payload)

        Log.d(TAG, "POST $url → $bodyJson")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${AppConfig.DEVICE_TOKEN}")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    Log.i(TAG, "API success [$code]: $body")
                    ApiResult.Success(code, body)
                } else {
                    Log.w(TAG, "API server error [$code]: $body")
                    ApiResult.ServerError(code, body)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "API network error: ${e.message}")
            ApiResult.NetworkError(e.message ?: "Unknown network error")
        } catch (e: IllegalArgumentException) {
            // URL malformed
            Log.e(TAG, "Invalid URL: $url — ${e.message}")
            ApiResult.ServerError(-1, "Invalid API URL: ${e.message}")
        }
    }

    /**
     * testConnection() — ping backend เพื่อตรวจ connectivity
     *
     * ส่ง test payload พิเศษ (is_test=true, bank="test")
     * backend ต้องตรวจ is_test=true ก่อน process order จริง
     */
    fun testConnection(): ApiResult {
        val testAmount = BankAmount(
            amount = 10.25,
            currency = "THB",
            bank = AppConfig.BANK_TEST_LABEL,
            timestamp = System.currentTimeMillis() / 1000L,
            notificationId = "test-${System.currentTimeMillis()}",
            isTest = true
        )
        return send(testAmount)
    }

    // =========================================================================
    // Payload builder
    // =========================================================================

    private data class ApiPayload(
        val amount: Double,
        val currency: String,
        val bank: String,
        val timestamp: Long,
        val notification_id: String,
        val is_test: Boolean
    )

    private fun toPayload(b: BankAmount) = ApiPayload(
        amount = b.amount,
        currency = b.currency,
        bank = b.bank,
        timestamp = b.timestamp,
        notification_id = b.notificationId,
        is_test = b.isTest
    )

    // =========================================================================
    // fetchPendingOrders — ดึงออเดอร์รอจับคู่จาก backend
    //
    // GET {API_BASE_URL}/api/bank-amount?action=pending
    // ต้อง call จาก IO thread
    // =========================================================================

    sealed class PendingOrdersResult {
        data class Success(val orders: List<PendingOrder>) : PendingOrdersResult()
        data class Error(val message: String)              : PendingOrdersResult()
    }

    fun fetchPendingOrders(): PendingOrdersResult {
        val url = "${AppConfig.API_BASE_URL}${AppConfig.API_ENDPOINT}?action=pending"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${AppConfig.DEVICE_TOKEN}")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    Log.w(TAG, "fetchPendingOrders server error [${response.code}]: $body")
                    return PendingOrdersResult.Error("Server error ${response.code}")
                }

                val json     = gson.fromJson(body, JsonObject::class.java)
                val ordersArr: JsonArray = json.getAsJsonArray("orders") ?: JsonArray()
                val orders = mutableListOf<PendingOrder>()

                for (el in ordersArr) {
                    val o = el.asJsonObject
                    orders.add(
                        PendingOrder(
                            id           = o.get("id")?.asString ?: continue,
                            productLabel = o.get("product_label")?.asString ?: "",
                            amount       = o.get("amount")?.asDouble ?: 0.0,
                            uniqueAmount = o.get("unique_amount")?.asDouble ?: 0.0,
                            createdAt    = o.get("created_at")?.asString ?: "",
                            userEmail    = o.get("user_email")?.asString ?: "",
                        )
                    )
                }

                Log.d(TAG, "fetchPendingOrders: ${orders.size} orders")
                PendingOrdersResult.Success(orders)
            }
        } catch (e: IOException) {
            Log.w(TAG, "fetchPendingOrders network error: ${e.message}")
            PendingOrdersResult.Error(e.message ?: "Network error")
        }
    }
}
