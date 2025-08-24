package com.ities45.orion_navigation_bars

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
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
import java.lang.reflect.Method

class BluetoothManager(private val context: Context) {

    companion object {
        const val REQUEST_BLUETOOTH_PERMISSION = 1
        const val REQUEST_ENABLE_BLUETOOTH = 3
        private const val TAG = "BluetoothManager"
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val discoveredDevices = mutableListOf<BluetoothDeviceWrapper>()
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var bluetoothDialog: androidx.appcompat.app.AlertDialog? = null
    private var pendingStartDiscovery: Boolean = false

    interface BluetoothCallback {
        fun onRequestPermissions(permissions: Array<String>, requestCode: Int)
        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
        fun onShowSnackbar(message: String)
    }

    private var callback: BluetoothCallback? = null

    fun setCallback(callback: BluetoothCallback) {
        this.callback = callback
    }

    interface BluetoothDeviceWrapper {
        val name: String
        val address: String
        val bondState: Int
        fun createBond(): Boolean
        fun removeBond(): Boolean
        fun getDevice(): BluetoothDevice?
    }

    private class RealBluetoothDevice(
        private val context: Context,
        private val device: BluetoothDevice
    ) : BluetoothDeviceWrapper {
        override val name: String
            get() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission denied, returning address: ${device.address}")
                    return device.address
                }
                val deviceName = device.name
                Log.d(TAG, "Device name: $deviceName, address: ${device.address}, bondState: ${device.bondState}")
                if (deviceName == null && device.bondState != BluetoothDevice.BOND_BONDED) {
                    try {
                        device.fetchUuidsWithSdp()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch UUIDs for name: ${e.message}")
                    }
                }
                return deviceName ?: if (device.bondState == BluetoothDevice.BOND_BONDED) {
                    "Paired Device (${device.address})"
                } else {
                    "Unpaired Device (${device.address})"
                }
            }

        override val address: String
            get() = device.address

        override val bondState: Int
            get() = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                BluetoothDevice.BOND_NONE
            } else {
                device.bondState
            }

        override fun createBond(): Boolean {
            return if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Cannot create bond: BLUETOOTH_CONNECT permission denied")
                false
            } else {
                Log.d(TAG, "Creating bond for device: ${device.address}")
                device.createBond()
            }
        }

        override fun removeBond(): Boolean {
            return if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Cannot remove bond: BLUETOOTH_CONNECT permission denied")
                false
            } else {
                try {
                    Log.d(TAG, "Removing bond for device: ${device.address}")
                    val method: Method = device.javaClass.getMethod("removeBond")
                    method.invoke(device) as Boolean
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove bond: ${e.message}")
                    false
                }
            }
        }

        override fun getDevice(): BluetoothDevice? {
            return device
        }
    }

    private class DeviceAdapter(
        private val devices: List<BluetoothDeviceWrapper>,
        private val onClick: (BluetoothDeviceWrapper) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
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
            val device = devices[position]
            holder.nameText.text = device.name
            holder.statusText.text = when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> "Paired"
                BluetoothDevice.BOND_BONDING -> "Pairing..."
                else -> "Not Paired (${device.address})"
            }
            holder.view.setOnClickListener { onClick(device) }
        }

        override fun getItemCount(): Int = devices.size
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun startBluetoothDiscovery() {
        if (bluetoothAdapter == null) {
            showSnackbar("Bluetooth not supported on this device")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            if (context is AppCompatActivity) {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Enable Bluetooth")
                    .setMessage("Bluetooth must be enabled to discover devices")
                    .setPositiveButton("Enable") { _, _ ->
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        callback?.onActivityResult(REQUEST_ENABLE_BLUETOOTH, AppCompatActivity.RESULT_OK, enableBtIntent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            return
        }

        requestBluetoothPermission(startDiscoveryAfterGrant = true)
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun startDiscovery() {
        if (!bluetoothAdapter!!.isEnabled) {
            showSnackbar("Bluetooth is disabled")
            return
        }

        if (isLocationEnabled()) {
            showLocationPrompt()
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermission(startDiscoveryAfterGrant = true)
            return
        }

        discoveredDevices.clear()
        unregisterReceiver()
        registerReceiver()

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        if (bluetoothAdapter.startDiscovery()) {
            showBluetoothDialog()
        } else {
            showSnackbar("Failed to start Bluetooth discovery")
        }
    }

    @SuppressLint("MissingPermission")
    private fun showBluetoothDialog() {
        if (context !is AppCompatActivity) return

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_device_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.device_list)
        val progressIndicator = dialogView.findViewById<ProgressBar>(R.id.progress_indicator)
        val emptyMessage = dialogView.findViewById<TextView>(R.id.empty_message)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = DeviceAdapter(discoveredDevices) { device ->
            bluetoothDialog?.dismiss()
            handleDeviceClick(device)
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

        if (discoveredDevices.isEmpty()) {
            progressIndicator.visibility = View.VISIBLE
            emptyMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            progressIndicator.visibility = View.GONE
            emptyMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        bluetoothDialog = MaterialAlertDialogBuilder(context)
            .setTitle("Bluetooth Devices")
            .setView(dialogView)
            .setNegativeButton("Cancel") { _, _ ->
                unregisterReceiver()
                bluetoothDialog?.dismiss()
            }
            .create()

        bluetoothDialog?.setOnDismissListener {
            unregisterReceiver()
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        }

        bluetoothDialog?.show()
    }

    @SuppressLint("MissingPermission")
    private fun registerReceiver() {
        if (bluetoothReceiver != null) return

        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            val wrapper = RealBluetoothDevice(context, it)
                            if (!discoveredDevices.any { d -> d.address == wrapper.address }) {
                                discoveredDevices.add(wrapper)
                                updateDialog()
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        bluetoothDialog?.findViewById<ProgressBar>(R.id.progress_indicator)?.visibility = View.VISIBLE
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        bluetoothDialog?.findViewById<ProgressBar>(R.id.progress_indicator)?.visibility = View.GONE
                        if (discoveredDevices.isEmpty()) {
                            bluetoothDialog?.findViewById<TextView>(R.id.empty_message)?.visibility = View.VISIBLE
                            bluetoothDialog?.findViewById<RecyclerView>(R.id.device_list)?.visibility = View.GONE
                        } else {
                            bluetoothDialog?.findViewById<TextView>(R.id.empty_message)?.visibility = View.GONE
                            bluetoothDialog?.findViewById<RecyclerView>(R.id.device_list)?.visibility = View.VISIBLE
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        device?.let {
                            val wrapper = discoveredDevices.find { d -> d.address == it.address }
                            if (wrapper != null) {
                                updateDialog()
                            }
                        }
                    }
                }
            }
        }

        bluetoothReceiver = newReceiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(newReceiver, filter)
    }

    private fun unregisterReceiver() {
        bluetoothReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering Bluetooth receiver: ${e.message}")
            }
        }
        bluetoothReceiver = null
    }

    private fun updateDialog() {
        bluetoothDialog?.findViewById<RecyclerView>(R.id.device_list)?.adapter?.notifyDataSetChanged()
        if (discoveredDevices.isEmpty()) {
            bluetoothDialog?.findViewById<ProgressBar>(R.id.progress_indicator)?.visibility = View.VISIBLE
            bluetoothDialog?.findViewById<TextView>(R.id.empty_message)?.visibility = View.VISIBLE
            bluetoothDialog?.findViewById<RecyclerView>(R.id.device_list)?.visibility = View.GONE
        } else {
            bluetoothDialog?.findViewById<ProgressBar>(R.id.progress_indicator)?.visibility = View.GONE
            bluetoothDialog?.findViewById<TextView>(R.id.empty_message)?.visibility = View.GONE
            bluetoothDialog?.findViewById<RecyclerView>(R.id.device_list)?.visibility = View.VISIBLE
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
                .setMessage("Location must be enabled for Bluetooth scanning")
                .setPositiveButton("Open Settings") { _, _ ->
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun requestBluetoothPermission(startDiscoveryAfterGrant: Boolean = false) {
        pendingStartDiscovery = startDiscoveryAfterGrant
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (needed.isNotEmpty()) {
            if (context is AppCompatActivity) {
                if (needed.any { context.shouldShowRequestPermissionRationale(it) }) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Permission Required")
                        .setMessage("Bluetooth and Location permissions are needed to scan for and manage devices.")
                        .setPositiveButton("OK") { _, _ ->
                            callback?.onRequestPermissions(needed.toTypedArray(), REQUEST_BLUETOOTH_PERMISSION)
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            showSnackbar("Bluetooth permissions denied")
                            pendingStartDiscovery = false
                        }
                        .show()
                } else {
                    callback?.onRequestPermissions(needed.toTypedArray(), REQUEST_BLUETOOTH_PERMISSION)
                }
            }
            return
        }

        if (pendingStartDiscovery) {
            startDiscovery()
            pendingStartDiscovery = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectDevice(device: BluetoothDeviceWrapper) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothPermission(startDiscoveryAfterGrant = false)
            return
        }

        val btDevice = (device as? RealBluetoothDevice)?.getDevice()
        if (btDevice != null) {
            try {
                val profiles = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.GATT)
                var disconnected = false
                for (profile in profiles) {
                    bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile?) {
                            try {
                                val disconnectMethod = proxy?.javaClass?.getMethod("disconnect", BluetoothDevice::class.java)
                                disconnectMethod?.invoke(proxy, btDevice)
                                disconnected = true
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to disconnect profile $profileId: ${e.message}")
                            } finally {
                                bluetoothAdapter?.closeProfileProxy(profileId, proxy)
                            }
                        }

                        override fun onServiceDisconnected(profileId: Int) {}
                    }, profile)
                }
                showSnackbar(if (disconnected) "Disconnected from ${device.name}" else "No active connection for ${device.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting device: ${e.message}")
                showSnackbar("Failed to disconnect ${device.name}")
            }
        } else {
            showSnackbar("Failed to disconnect ${device.name}")
        }
    }

    private fun handleDeviceClick(device: BluetoothDeviceWrapper) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            device is RealBluetoothDevice &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothPermission(startDiscoveryAfterGrant = false)
            return
        }

        if (context is AppCompatActivity) {
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(device.name)
                    .setItems(arrayOf("Disconnect", "Unpair")) { _, which ->
                        when (which) {
                            0 -> disconnectDevice(device)
                            1 -> {
                                if (device.removeBond()) {
                                    showSnackbar("Unpairing ${device.name}")
                                } else {
                                    showSnackbar("Failed to unpair ${device.name}")
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else if (device.bondState == BluetoothDevice.BOND_NONE) {
                if (device.createBond()) {
                    showSnackbar("Pairing with ${device.name}")
                } else {
                    showSnackbar("Failed to initiate pairing with ${device.name}")
                }
            } else {
                showSnackbar("Device ${device.name} is in pairing process")
            }
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
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (pendingStartDiscovery) {
                    startDiscovery()
                    pendingStartDiscovery = false
                } else {
                    showSnackbar("Bluetooth permissions granted")
                    if (bluetoothDialog?.isShowing == true) {
                        discoveredDevices.clear()
                        startDiscovery()
                    }
                }
            } else {
                pendingStartDiscovery = false
                showSnackbar("Bluetooth permissions denied")
            }
            return true
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun handleActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                startDiscovery()
            } else {
                showSnackbar("Bluetooth not enabled")
            }
            return true
        }
        return false
    }

    fun cleanup() {
        unregisterReceiver()
        bluetoothDialog?.dismiss()
    }
}