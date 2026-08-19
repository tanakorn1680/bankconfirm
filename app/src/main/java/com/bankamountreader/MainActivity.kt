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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bankamountreader.data.AppState
import com.bankamountreader.data.NotificationLog
import com.bankamountreader.data.PendingOrder
import com.bankamountreader.databinding.ActivityMainBinding
import com.bankamountreader.network.ApiClient
import com.bankamountreader.network.ApiResult
import com.bankamountreader.util.AmountParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var orderAdapter: OrderAdapter
    private lateinit var logAdapter: LogAdapter

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val amtFmt  = DecimalFormat("#,##0.00")
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    private val handler = Handler(Looper.getMainLooper())
    private val pollOrders = object : Runnable {
        override fun run() { loadPendingOrders(); handler.postDelayed(this, 10_000L) }
    }

    // รับ broadcast จาก Service ทันทีเมื่อมี notification ใหม่
    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshDebugLog() }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAdapters()
        setupButtons()
        showTab(Tab.DEBUG)          // เริ่มที่ Debug tab เสมอ
        refreshAccessStatus()
        refreshServerStatus()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(notifReceiver, IntentFilter(AppState.ACTION_NOTIFICATION_RECEIVED))
        refreshAccessStatus()
        refreshDebugLog()
        handler.post(pollOrders)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollOrders)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notifReceiver)
    }

    // =========================================================================
    // Setup
    // =========================================================================

    private fun setupAdapters() {
        orderAdapter = OrderAdapter(amtFmt, dateFmt)
        binding.rvOrders.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = orderAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        logAdapter = LogAdapter(timeFmt, amtFmt)
        binding.rvLog.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupButtons() {
        binding.btnTabOrders.setOnClickListener { showTab(Tab.ORDERS) }
        binding.btnTabDebug.setOnClickListener  { showTab(Tab.DEBUG) }

        binding.btnRefreshOrders.setOnClickListener {
            binding.btnRefreshOrders.isEnabled = false
            loadPendingOrders(manual = true)
        }
        binding.btnTestConnection.setOnClickListener { runTestConnection() }
        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    // =========================================================================
    // Tab switching
    // =========================================================================

    private enum class Tab { ORDERS, DEBUG }

    private fun showTab(tab: Tab) {
        val isOrders = (tab == Tab.ORDERS)
        binding.layoutOrders.visibility = if (isOrders) View.VISIBLE else View.GONE
        binding.layoutDebug.visibility  = if (isOrders) View.GONE   else View.VISIBLE

        val active   = getColor(R.color.tab_active)
        val inactive = getColor(R.color.tab_inactive)
        binding.btnTabOrders.setTextColor(if (isOrders) active else inactive)
        binding.btnTabDebug.setTextColor(if (isOrders) inactive else active)
    }

    // =========================================================================
    // Orders tab
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
                    binding.rvOrders.visibility      = if (orders.isEmpty()) View.GONE   else View.VISIBLE
                    binding.tvOrderCount.text        = "${orders.size} รายการรอจับคู่"
                    binding.tvLastRefresh.text       = "อัปเดต ${timeFmt.format(Date())}"
                    if (manual) Toast.makeText(this@MainActivity, "รีเฟรชแล้ว", Toast.LENGTH_SHORT).show()
                }
                is ApiClient.PendingOrdersResult.Error -> {
                    AppState.setServerOk(applicationContext, false)
                    refreshServerStatus()
                    if (manual) Toast.makeText(this@MainActivity, "เชื่อมต่อไม่ได้: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshServerStatus() {
        val ok = AppState.isServerOk(this)
        binding.tvServerStatus.text = "● ${if (ok) getString(R.string.status_connected) else getString(R.string.status_disconnected)}"
        binding.tvServerStatus.setTextColor(getColor(if (ok) R.color.status_ok else R.color.status_error))
    }

    private fun runTestConnection() {
        binding.btnTestConnection.isEnabled = false
        binding.btnTestConnection.text = getString(R.string.btn_testing)
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
            binding.btnTestConnection.text = getString(R.string.btn_test_connection)
        }
    }

    // =========================================================================
    // Debug tab
    // =========================================================================

    private fun refreshAccessStatus() {
        val flat    = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val enabled = flat.contains(packageName)
        binding.tvAccessStatus.text = if (enabled) "Notification Access: ON ✓" else "Notification Access: OFF ✗"
        binding.tvAccessStatus.setTextColor(getColor(if (enabled) R.color.status_ok else R.color.status_error))
    }

    private fun refreshDebugLog() {
        val logs = AppState.getLogs()
        logAdapter.submitList(logs)
        binding.tvLogEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        binding.rvLog.visibility      = if (logs.isEmpty()) View.GONE   else View.VISIBLE
    }
}

// =============================================================================
// OrderAdapter
// =============================================================================

class OrderAdapter(
    private val amtFmt: DecimalFormat,
    private val dateFmt: SimpleDateFormat,
) : RecyclerView.Adapter<OrderAdapter.VH>() {

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private var items: List<PendingOrder> = emptyList()

    fun submitList(list: List<PendingOrder>) { items = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_pending_order, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvProduct = view.findViewById<TextView>(R.id.tvProduct)
        private val tvAmount  = view.findViewById<TextView>(R.id.tvAmount)
        private val tvEmail   = view.findViewById<TextView>(R.id.tvEmail)
        private val tvTime    = view.findViewById<TextView>(R.id.tvTime)

        fun bind(o: PendingOrder) {
            tvProduct.text = o.productLabel
            tvAmount.text  = "฿${amtFmt.format(o.uniqueAmount)}"
            tvEmail.text   = o.userEmail
            val date = try { isoFmt.parse(o.createdAt.take(19)) } catch (e: Exception) { null }
            tvTime.text    = date?.let { dateFmt.format(it) } ?: o.createdAt.take(16)
        }
    }
}

// =============================================================================
// LogAdapter
// =============================================================================

class LogAdapter(
    private val timeFmt: SimpleDateFormat,
    private val amtFmt: DecimalFormat,
) : RecyclerView.Adapter<LogAdapter.VH>() {

    private var items: List<NotificationLog> = emptyList()

    fun submitList(list: List<NotificationLog>) { items = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notification_log, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTime    = view.findViewById<TextView>(R.id.tvTime)
        private val tvPackage = view.findViewById<TextView>(R.id.tvPackage)
        private val tvApp     = view.findViewById<TextView>(R.id.tvApp)
        private val tvTitle   = view.findViewById<TextView>(R.id.tvTitle)
        private val tvText    = view.findViewById<TextView>(R.id.tvText)
        private val tvBig     = view.findViewById<TextView>(R.id.tvBig)
        private val tvSub     = view.findViewById<TextView>(R.id.tvSub)
        private val tvAmount  = view.findViewById<TextView>(R.id.tvAmount)
        private val rowBig    = view.findViewById<View>(R.id.rowBig)
        private val rowSub    = view.findViewById<View>(R.id.rowSub)
        private val rowAmount = view.findViewById<View>(R.id.rowAmount)

        fun bind(log: NotificationLog) {
            tvTime.text    = timeFmt.format(Date(log.timeMs))
            tvPackage.text = log.packageName
            tvApp.text     = log.appName
            tvTitle.text   = log.title
            tvText.text    = log.text

            rowBig.visibility = if (log.bigText == "-") View.GONE else View.VISIBLE
            tvBig.text        = log.bigText

            rowSub.visibility = if (log.subText == "-") View.GONE else View.VISIBLE
            tvSub.text        = log.subText

            if (log.parsedAmount != null) {
                rowAmount.visibility = View.VISIBLE
                val status = if (log.matched) " ✓ ส่งแล้ว" else " → รอส่ง API"
                tvAmount.text = "฿${amtFmt.format(log.parsedAmount)}$status"
                tvAmount.setTextColor(
                    itemView.context.getColor(if (log.matched) R.color.status_ok else R.color.amount_color)
                )
            } else {
                rowAmount.visibility = View.GONE
            }
        }
    }
}
