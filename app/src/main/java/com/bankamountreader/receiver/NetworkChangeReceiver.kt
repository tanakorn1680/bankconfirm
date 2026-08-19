package com.bankamountreader.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.bankamountreader.data.AppState
import com.bankamountreader.network.OfflineQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NetworkChangeReceiver : BroadcastReceiver() {

    companion object { private const val TAG = "NetworkChangeReceiver" }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (!isOnline(context)) return
        val pending = goAsync()
        scope.launch {
            try {
                val queue = OfflineQueue(context)
                if (queue.isEmpty()) return@launch
                val sent = queue.flush()
                if (sent > 0) {
                    AppState.setServerOk(context, true)
                    Log.i(TAG, "Auto-flush: $sent sent")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val cm  = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
