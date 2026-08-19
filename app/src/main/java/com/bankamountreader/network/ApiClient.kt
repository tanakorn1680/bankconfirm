package com.bankamountreader.network

import android.util.Log
import com.bankamountreader.AppConfig
import com.bankamountreader.data.BankAmount
import com.bankamountreader.data.PendingOrder
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG = "ApiClient"
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(AppConfig.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(AppConfig.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(AppConfig.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun send(bankAmount: BankAmount): ApiResult {
        val url  = "${AppConfig.API_BASE_URL}${AppConfig.API_ENDPOINT}"
        val body = gson.toJson(toPayload(bankAmount))
        Log.d(TAG, "POST $url → $body")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${AppConfig.DEVICE_TOKEN}")
            .post(body.toRequestBody(JSON_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                val code     = resp.code
                val respBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    Log.i(TAG, "API success [$code]: $respBody")
                    ApiResult.Success(code, respBody)
                } else {
                    Log.w(TAG, "API server error [$code]: $respBody")
                    ApiResult.ServerError(code, respBody)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "API network error: ${e.message}")
            ApiResult.NetworkError(e.message ?: "Unknown")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid URL: $url")
            ApiResult.ServerError(-1, "Invalid URL")
        }
    }

    fun testConnection(): ApiResult = send(
        BankAmount(
            amount         = 10.25,
            currency       = "THB",
            bank           = AppConfig.BANK_TEST_LABEL,
            timestamp      = System.currentTimeMillis() / 1000L,
            notificationId = "test-${System.currentTimeMillis()}",
            isTest         = true
        )
    )

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
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) return PendingOrdersResult.Error("Server ${resp.code}")
                val json = gson.fromJson(body, JsonObject::class.java)
                val arr: JsonArray = json.getAsJsonArray("orders") ?: JsonArray()
                val orders = mutableListOf<PendingOrder>()
                for (el in arr) {
                    val o = el.asJsonObject
                    orders.add(PendingOrder(
                        id           = o.get("id")?.asString ?: continue,
                        productLabel = o.get("product_label")?.asString ?: "",
                        amount       = o.get("amount")?.asDouble ?: 0.0,
                        uniqueAmount = o.get("unique_amount")?.asDouble ?: 0.0,
                        createdAt    = o.get("created_at")?.asString ?: "",
                        userEmail    = o.get("user_email")?.asString ?: ""
                    ))
                }
                PendingOrdersResult.Success(orders)
            }
        } catch (e: IOException) {
            PendingOrdersResult.Error(e.message ?: "Network error")
        }
    }

    private data class Payload(
        val amount: Double, val currency: String, val bank: String,
        val timestamp: Long, val notification_id: String, val is_test: Boolean
    )

    private fun toPayload(b: BankAmount) = Payload(
        amount          = b.amount,
        currency        = b.currency,
        bank            = b.bank,
        timestamp       = b.timestamp,
        notification_id = b.notificationId,
        is_test         = b.isTest
    )
}
