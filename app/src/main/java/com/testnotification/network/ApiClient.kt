package com.testnotification.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.testnotification.data.PendingOrder
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG        = "ApiClient"
    private const val API_BASE   = "https://scid-ecru.vercel.app"
    private const val API_PATH   = "/api/bank-amount"
    private const val TOKEN      = "mytoken2026"
    private const val TIMEOUT    = 30L

    private val gson   = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT, TimeUnit.SECONDS)
        .build()

    sealed class PendingOrdersResult {
        data class Success(val orders: List<PendingOrder>) : PendingOrdersResult()
        data class Error(val message: String)              : PendingOrdersResult()
    }

    fun fetchPendingOrders(): PendingOrdersResult {
        val url = "$API_BASE$API_PATH?action=pending"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $TOKEN")
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
            Log.w(TAG, "Network error: ${e.message}")
            PendingOrdersResult.Error(e.message ?: "Network error")
        }
    }
}
