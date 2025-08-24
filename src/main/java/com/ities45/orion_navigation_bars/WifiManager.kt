package com.ities45.orion_navigation_bars

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

class WifiManager(private val context: Context) {

    companion object {
        const val REQUEST_WIFI_PERMISSION = 2
        private const val TAG = "WifiManager"
        private const val CONNECTION_TIMEOUT_MS = 10_000L
    }

    private val wifiManager: WifiManager by lazy { context.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private val wifiNetworks = mutableListOf<WifiNetworkWrapper>()
    private var wifiReceiver: BroadcastReceiver? = null
    private var wifiDialog: androidx.appcompat.app.AlertDialog? = null
    private var connectionDialog: androidx.appcompat.app.AlertDialog? = null
    private var pendingStartWifiScan: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    data class WifiNetworkWrapper(
        val ssid: String,
        val bssid: String,
        val level: Int,
        val capabilities: String
    )

    interface WifiCallback {
        fun onRequestPermissions(permissions: Array<String>, requestCode: Int)
        fun onShowSnackbar(message: String)
    }

    private var callback: WifiCallback? = null

    fun setCallback(callback: WifiCallback) {
        this.callback = callback
    }

    private class WifiAdapter(
        private val networks: List<WifiNetworkWrapper>,
        private val currentSsid: String?,
        private val onClick: (WifiNetworkWrapper) -> Unit
    ) : RecyclerView.Adapter<WifiAdapter.ViewHolder>() {
        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.deviceTextView)
            val statusText: TextView = view.findViewById(R.id.deviceInfoTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.device_list_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val network = networks[position]
            holder.nameText.text = network.ssid
            val isConnected = network.ssid == currentSsid
            holder.statusText.text = buildString {
                append("Signal: ${network.level}dBm")
                if (network.capabilities.contains("PSK") || network.capabilities.contains("WEP")) append(" (Secured)")
                if (isConnected) append(" (Connected)")
            }
            holder.view.setOnClickListener { onClick(network) }
        }

        override fun getItemCount(): Int = networks.size
    }

    init {
        registerWifiStateReceiver()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun startWifiScan() {
        requestWifiPermission(startScanAfterGrant = true)
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun startScan() {
        if (!wifiManager.isWifiEnabled) {
            if (context is AppCompatActivity) {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Enable Wi-Fi")
                    .setMessage("Wi-Fi must be enabled to scan for networks")
                    .setPositiveButton("Enable") { _, _ ->
                        wifiManager.isWifiEnabled = true
                        startScan()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            return
        }

        if (isLocationEnabled()) {
            showLocationPrompt()
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestWifiPermission(startScanAfterGrant = true)
            return
        }

        wifiNetworks.clear()
        unregisterReceiver()
        registerReceiver()

        if (wifiManager.startScan()) {
            showWifiDialog()
        } else {
            showSnackbar("Failed to start Wi-Fi scan")
        }
    }

    @SuppressLint("MissingPermission")
    private fun showWifiDialog() {
        if (context !is AppCompatActivity) return

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_device_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.device_list)
        val progressIndicator = dialogView.findViewById<ProgressBar>(R.id.progress_indicator)
        val emptyMessage = dialogView.findViewById<TextView>(R.id.empty_message)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = WifiAdapter(wifiNetworks, getCurrentSsid()) { network ->
            wifiDialog?.dismiss()
            handleNetworkClick(network)
        }

        recyclerView.apply {
            alpha = 0f
            scaleY = 0.8f
            ViewCompat.animate(this)
                .alpha(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator())
                .start()
        }

        if (wifiNetworks.isEmpty()) {
            progressIndicator.visibility = View.VISIBLE
            emptyMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            progressIndicator.visibility = View.GONE
            emptyMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        wifiDialog = MaterialAlertDialogBuilder(context)
            .setTitle("Wi-Fi Networks")
            .setView(dialogView)
            .setNegativeButton("Cancel") { _, _ ->
                unregisterReceiver()
                wifiDialog?.dismiss()
            }
            .create()

        wifiDialog?.setOnDismissListener {
            unregisterReceiver()
        }

        wifiDialog?.show()
    }

    @SuppressLint("MissingPermission")
    private fun registerReceiver() {
        if (wifiReceiver != null) return

        val newReceiver = object : BroadcastReceiver() {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            requestWifiPermission(startScanAfterGrant = false)
                            return
                        }
                        val results = wifiManager.scanResults
                        wifiNetworks.clear()
                        results.forEach { result ->
                            if (result.SSID.isNotBlank()) {
                                wifiNetworks.add(
                                    WifiNetworkWrapper(
                                        ssid = result.SSID,
                                        bssid = result.BSSID,
                                        level = result.level,
                                        capabilities = result.capabilities
                                    )
                                )
                            }
                        }
                        wifiNetworks.sortByDescending { it.level }
                        updateDialog()
                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                        val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                        if (networkInfo?.isConnected == true) {
                            handler.removeCallbacksAndMessages(null)
                            connectionDialog?.dismiss()
                            wifiDialog?.findViewById<RecyclerView>(R.id.device_list)?.adapter = WifiAdapter(wifiNetworks, getCurrentSsid()) { network ->
                                wifiDialog?.dismiss()
                                handleNetworkClick(network)
                            }
                            showSnackbar("Connected to ${getCurrentSsid()}")
                        }
                    }
                }
            }
        }

        wifiReceiver = newReceiver
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        context.registerReceiver(newReceiver, filter)
    }

    private fun registerWifiStateReceiver() {
        val stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                    val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    when (state) {
                        WifiManager.WIFI_STATE_DISABLED -> {
                            showSnackbar("Wi-Fi turned off")
                            wifiDialog?.dismiss()
                            unregisterReceiver()
                        }
                    }
                }
            }
        }
        context.registerReceiver(stateReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
    }

    private fun unregisterReceiver() {
        wifiReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering Wi-Fi receiver: ${e.message}")
            }
        }
        wifiReceiver = null
    }

    private fun updateDialog() {
        wifiDialog?.findViewById<RecyclerView>(R.id.device_list)?.adapter?.notifyDataSetChanged()
        if (wifiNetworks.isEmpty()) {
            wifiDialog?.findViewById<ProgressBar>(R.id.progress_indicator)?.visibility = View.VISIBLE
            wifiDialog?.findViewById<TextView>(R.id.empty_message)?.visibility = View.VISIBLE
            wifiDialog?.findViewById<RecyclerView>(R.id.device_list)?.visibility = View.GONE
        } else {
            wifiDialog?.findViewById<ProgressBar>(R.id.progress_indicator)?.visibility = View.GONE
            wifiDialog?.findViewById<TextView>(R.id.empty_message)?.visibility = View.GONE
            wifiDialog?.findViewById<RecyclerView>(R.id.device_list)?.visibility = View.VISIBLE
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            !lm.isLocationEnabled
        } else {
            try {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE) == Settings.Secure.LOCATION_MODE_OFF
            } catch (e: Exception) {
                true
            }
        }
    }

    private fun showLocationPrompt() {
        if (context is AppCompatActivity) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Enable Location")
                .setMessage("Location must be enabled for Wi-Fi scanning")
                .setPositiveButton("Open Settings") { _, _ ->
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentSsid(): String? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val wifiInfo = wifiManager.connectionInfo
        return wifiInfo?.ssid?.trim('"')
    }

    @SuppressLint("MissingPermission")
    private fun handleNetworkClick(network: WifiNetworkWrapper) {
        if (context !is AppCompatActivity) return

        if (network.ssid == getCurrentSsid()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(network.ssid)
                .setItems(arrayOf("Disconnect")) { _, _ ->
                    wifiManager.disconnect()
                    showSnackbar("Disconnected from ${network.ssid}")
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else if (network.capabilities.contains("PSK") || network.capabilities.contains("WEP")) {
            showPasswordDialog(network)
        } else {
            connectToNetwork(network, null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPasswordDialog(network: WifiNetworkWrapper) {
        if (context !is AppCompatActivity) return

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_wifi_password, null)
        val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.password_input)

        connectionDialog = MaterialAlertDialogBuilder(context)
            .setTitle("Enter Password for ${network.ssid}")
            .setView(dialogView)
            .setPositiveButton("Connect") { _, _ ->
                val password = passwordInput.text?.toString()
                if (!password.isNullOrEmpty()) {
                    connectToNetwork(network, password)
                } else {
                    showSnackbar("Password cannot be empty")
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                connectionDialog?.dismiss()
            }
            .create()

        connectionDialog?.show()
    }

    @SuppressLint("MissingPermission")
    private fun connectToNetwork(network: WifiNetworkWrapper, password: String?) {
        if (context !is AppCompatActivity) return

        val config = WifiConfiguration().apply {
            SSID = "\"${network.ssid}\""
            BSSID = network.bssid
            if (password != null) {
                if (network.capabilities.contains("WEP")) {
                    wepKeys[0] = "\"$password\""
                    wepTxKeyIndex = 0
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                } else if (network.capabilities.contains("PSK")) {
                    preSharedKey = "\"$password\""
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                }
            } else {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
        }

        val networkId = wifiManager.addNetwork(config)
        if (networkId == -1) {
            showSnackbar("Failed to configure network ${network.ssid}")
            return
        }

        wifiManager.disconnect()
        wifiManager.enableNetwork(networkId, true)
        wifiManager.reconnect()

        connectionDialog = MaterialAlertDialogBuilder(context)
            .setTitle("Connecting to ${network.ssid}")
            .setView(R.layout.dialog_device_list)
            .setCancelable(false)
            .create()

        connectionDialog?.show()

        handler.postDelayed({
            if (getCurrentSsid() != network.ssid) {
                if (context is AppCompatActivity) {
                    context.runOnUiThread {
                        handler.removeCallbacksAndMessages(null)
                        connectionDialog?.dismiss()
                        wifiDialog?.findViewById<RecyclerView>(R.id.device_list)?.adapter = WifiAdapter(wifiNetworks, getCurrentSsid()) { network ->
                            wifiDialog?.dismiss()
                            handleNetworkClick(network)
                        }
                        if (connectionDialog != null) {
                            showSnackbar("Failed to connect to ${network.ssid}")
                        }
                    }
                }
            }
        }, CONNECTION_TIMEOUT_MS)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestWifiPermission(startScanAfterGrant: Boolean = false) {
        pendingStartWifiScan = startScanAfterGrant
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_WIFI_STATE)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CHANGE_WIFI_STATE)
        }

        if (needed.isNotEmpty()) {
            if (context is AppCompatActivity) {
                if (needed.any { context.shouldShowRequestPermissionRationale(it) }) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Permission Required")
                        .setMessage("Location and Wi-Fi permissions are needed to scan for and connect to networks")
                        .setPositiveButton("OK") { _, _ ->
                            callback?.onRequestPermissions(needed.toTypedArray(), REQUEST_WIFI_PERMISSION)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    callback?.onRequestPermissions(needed.toTypedArray(), REQUEST_WIFI_PERMISSION)
                }
            }
            return
        }

        if (pendingStartWifiScan) {
            startScan()
            pendingStartWifiScan = false
        }
    }

    private fun showSnackbar(message: String) {
        callback?.onShowSnackbar(message) ?: run {
            if (context is AppCompatActivity) {
                Snackbar.make(context.findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handlePermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        if (requestCode == REQUEST_WIFI_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (pendingStartWifiScan) {
                    startScan()
                    pendingStartWifiScan = false
                } else {
                    showSnackbar("Wi-Fi permissions granted")
                }
            } else {
                pendingStartWifiScan = false
                showSnackbar("Wi-Fi permissions denied")
            }
            return true
        }
        return false
    }

    fun cleanup() {
        unregisterReceiver()
        wifiDialog?.dismiss()
        connectionDialog?.dismiss()
        handler.removeCallbacksAndMessages(null)
    }
}
