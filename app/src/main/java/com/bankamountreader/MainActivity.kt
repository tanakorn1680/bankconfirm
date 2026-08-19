package com.bankamountreader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bankamountreader.data.AppState
import com.bankamountreader.data.PendingOrder
import com.bankamountreader.network.ApiClient
import com.bankamountreader.network.ApiResult
import com.bankamountreader.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MainActivity — รวม 3 ส่วน:
 *
 * 1. สถานะ Server + Notification Access
 * 2. รายการออเดอร์รอจับคู่ (poll ทุก 10 วิ)
 * 3. รับ broadcast จาก BankNotificationService เมื่อจับคู่สำเร็จ
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var orderAdapter: PendingOrderAdapter

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val amtFmt  = DecimalFormat("#,##0.00")
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    // Poll orders ทุก 10 วิ
    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            loadPendingOrders()
            handler.postDelayed(this, 10_000L)
        }
    }

    // รับ broadcast จาก BankNotificationService เมื่อจับคู่สำเร็จ
    private val matchReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val amount  = intent?.getDoubleExtra("amount", 0.0) ?: return
            val orderId = intent.getStringExtra("order_id") ?: return
            Toast.makeText(
                this@MainActivity,
                "จับคู่สำเร็จ ฿${amtFmt.format(amount)} — Order $orderId",
                Toast.LENGTH_LONG
            ).show()
            // refresh รายการทันที
            loadPendingOrders()
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshNotifStatus()
        refreshServerStatus()
        handler.post(pollRunnable)
        ContextCompat.registerReceiver(
            this, matchReceiver,
            IntentFilter("com.bankamountreader.MATCH_SUCCESS"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
        unregisterReceiver(matchReceiver)
    }

    // =========================================================================
    // Setup
    // =========================================================================

    private fun setupRecyclerView() {
        orderAdapter = PendingOrderAdapter(amtFmt, dateFmt)
        binding.rvOrders.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = orderAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupButtons() {
        // เปิด Notification Access Settings
        binding.btnNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.btnTestConnection.setOnClickListener { runTestConnection() }
        binding.btnRefreshOrders.setOnClickListener {
            binding.btnRefreshOrders.isEnabled = false
            loadPendingOrders(manual = true)
        }
    }

    // =========================================================================
    // Notification Access status
    // =========================================================================

    private fun refreshNotifStatus() {
        val granted = isNotifAccessGranted()
        binding.tvNotifStatus.text = if (granted) "● Notification Access: ON" else "● Notification Access: OFF"
        binding.tvNotifStatus.setTextColor(
            getColor(if (granted) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
        // ซ่อนปุ่มถ้าได้ permission แล้ว
        binding.btnNotifAccess.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun isNotifAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return flat.contains(packageName)
    }

    // =========================================================================
    // Server status
    // =========================================================================

    private fun refreshServerStatus() {
        val ok = AppState.isServerOk(this)
        binding.tvServerStatus.text = if (ok) "● Connected" else "● Disconnected"
        binding.tvServerStatus.setTextColor(
            getColor(if (ok) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
    }

    // =========================================================================
    // Load pending orders
    // =========================================================================

    private fun loadPendingOrders(manual: Boolean = false) {
        uiScope.launch {
            val result = withContext(Dispatchers.IO) { ApiClient.fetchPendingOrders() }
            binding.btnRefreshOrders.isEnabled = true

            when (result) {
                is ApiClient.PendingOrdersResult.Success -> {
                    AppState.setServerOk(applicationContext, true)
                    refreshServerStatus()

                    val orders = result.orders
                    orderAdapter.submitList(orders)

                    binding.tvOrdersEmpty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvOrders.visibility      = if (orders.isEmpty()) View.GONE else View.VISIBLE
                    binding.tvOrderCount.text = "${orders.size} รายการรอจับคู่"
                    binding.tvLastRefresh.text = "อัปเดต ${timeFmt.format(Date())}"

                    if (manual) Toast.makeText(this@MainActivity, "รีเฟรชแล้ว", Toast.LENGTH_SHORT).show()
                }
                is ApiClient.PendingOrdersResult.Error -> {
                    AppState.setServerOk(applicationContext, false)
                    refreshServerStatus()
                    if (manual) Toast.makeText(this@MainActivity, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // =========================================================================
    // Test connection
    // =========================================================================

    private fun runTestConnection() {
        binding.btnTestConnection.isEnabled = false
        uiScope.launch {
            val result = withContext(Dispatchers.IO) { ApiClient.testConnection() }
            val (msg, ok) = when (result) {
                is ApiResult.Success      -> Pair("เชื่อมต่อสำเร็จ (${result.code})", true)
                is ApiResult.ServerError  -> Pair("Server error [${result.code}]", false)
                is ApiResult.NetworkError -> Pair("ไม่มีอินเทอร์เน็ต", false)
            }
            AppState.setServerOk(applicationContext, ok)
            refreshServerStatus()
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            binding.btnTestConnection.isEnabled = true
        }
    }
}

// =============================================================================
// RecyclerView Adapter
// =============================================================================

class PendingOrderAdapter(
    private val amtFmt: DecimalFormat,
    private val dateFmt: SimpleDateFormat,
) : RecyclerView.Adapter<PendingOrderAdapter.VH>() {

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private var items: List<PendingOrder> = emptyList()

    fun submitList(list: List<PendingOrder>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_pending_order, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvProduct = view.findViewById<TextView>(R.id.tvProduct)
        private val tvAmount  = view.findViewById<TextView>(R.id.tvAmount)
        private val tvEmail   = view.findViewById<TextView>(R.id.tvEmail)
        private val tvTime    = view.findViewById<TextView>(R.id.tvTime)

        fun bind(order: PendingOrder) {
            tvProduct.text = order.productLabel
            tvAmount.text  = "฿${amtFmt.format(order.uniqueAmount)}"
            tvEmail.text   = order.userEmail
            val parsed = try { isoFmt.parse(order.createdAt.take(19)) } catch (e: Exception) { null }
            tvTime.text = parsed?.let { dateFmt.format(it) } ?: order.createdAt.take(16)
        }
    }
}
