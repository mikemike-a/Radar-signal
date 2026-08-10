package com.example.scanning

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.HistoryEntry
import com.example.data.KnownDevice
import com.example.service.PresenceService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

data class DetectedDevice(
    val identifier: String, // MAC, BSSID or Exact Name
    val name: String?,
    val rssi: Int,
    val type: String, // "BLE" or "WIFI"
    val lastSeen: Long = System.currentTimeMillis(),
    val isKnown: Boolean = false,
    val alias: String? = null
) {
    // Standard visual distance estimation based on RSSI
    val estimatedDistanceMeters: Double
        get() = 10.0.pow((-60.0 - rssi) / 30.0)

    val distanceCategory: String
        get() = when {
            rssi >= -65 -> "Très proche"
            rssi >= -80 -> "Proche"
            else -> "Éloigné"
        }
}

@SuppressLint("MissingPermission")
class PresenceScanner(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "PresenceScanner"

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.deviceDao()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bleScanner: BluetoothLeScanner? = null

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    // Settings (updated dynamically from ViewModel/Preferences)
    var rssiThreshold = -95
    var departureDelayMs = 45000L // Time without packets before declaring a device departed
    var isPowerSaverMode = false
    var scanIntervalBleMs = 15000L // Periodical BLE restart cycle to refresh scans

    // Thread-safe map of current active devices
    private val activeDevicesMap = java.util.concurrent.ConcurrentHashMap<String, DetectedDevice>()

    private val _detectedDevices = MutableStateFlow<List<DetectedDevice>>(emptyList())
    val detectedDevices: StateFlow<List<DetectedDevice>> = _detectedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null
    private var pruneJob: Job? = null

    // Known devices cache for ultra-fast lookup inside scan callbacks
    private var knownDevicesList = emptyList<KnownDevice>()
    private var cacheUpdateJob: Job? = null

    init {
        // Monitor DB changes to keep local cache in sync
        cacheUpdateJob = scope.launch(Dispatchers.IO) {
            dao.getKnownDevicesFlow().collect { devices ->
                knownDevicesList = devices
                updateActiveDevicesMetadata()
            }
        }
    }

    private fun updateActiveDevicesMetadata() {
        val currentKeys = activeDevicesMap.keys().toList()
        for (key in currentKeys) {
            val device = activeDevicesMap[key] ?: continue
            val known = knownDevicesList.find {
                it.identifier.equals(device.identifier, ignoreCase = true) ||
                        (device.name != null && it.identifier.equals(device.name, ignoreCase = true))
            }
            if (known != null) {
                activeDevicesMap[key] = device.copy(isKnown = true, alias = known.alias)
            } else {
                activeDevicesMap[key] = device.copy(isKnown = false, alias = null)
            }
        }
        publishDevices()
    }

    // BLE scan callback
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleBleResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { handleBleResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with error: $errorCode")
        }
    }

    private fun handleBleResult(result: ScanResult) {
        val device = result.device
        val mac = device.address ?: return
        val name = result.scanRecord?.deviceName ?: device.name
        val rssi = result.rssi

        if (rssi < rssiThreshold) return

        // Match either MAC or Name
        val known = knownDevicesList.find {
            it.identifier.equals(mac, ignoreCase = true) ||
                    (name != null && it.identifier.equals(name, ignoreCase = true))
        }

        val identifier = known?.identifier ?: mac
        val now = System.currentTimeMillis()

        val existing = activeDevicesMap[identifier]
        val isNewArrival = existing == null

        val updatedDevice = DetectedDevice(
            identifier = identifier,
            name = name,
            rssi = rssi,
            type = "BLE",
            lastSeen = now,
            isKnown = known != null,
            alias = known?.alias
        )

        activeDevicesMap[identifier] = updatedDevice

        if (isNewArrival && known != null) {
            triggerArrival(known)
        }

        publishDevices()
    }

    // Wi-Fi scan receiver
    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            Log.d(TAG, "Wifi scan result received, success: $success")
            handleWifiResults()
        }
    }

    private fun handleWifiResults() {
        val results = wifiManager?.scanResults ?: return
        val now = System.currentTimeMillis()

        for (result in results) {
            val bssid = result.BSSID ?: continue
            val ssid = result.SSID
            val rssi = result.level

            if (rssi < rssiThreshold) continue

            // Match SSID (name) or BSSID
            val known = knownDevicesList.find {
                it.identifier.equals(bssid, ignoreCase = true) ||
                        (!ssid.isNullOrEmpty() && it.identifier.equals(ssid, ignoreCase = true))
            }

            val identifier = known?.identifier ?: bssid
            val existing = activeDevicesMap[identifier]
            val isNewArrival = existing == null

            val updatedDevice = DetectedDevice(
                identifier = identifier,
                name = if (ssid.isNullOrEmpty()) "Réseau Wi-Fi" else ssid,
                rssi = rssi,
                type = "WIFI",
                lastSeen = now,
                isKnown = known != null,
                alias = known?.alias
            )

            activeDevicesMap[identifier] = updatedDevice

            if (isNewArrival && known != null) {
                triggerArrival(known)
            }
        }
        publishDevices()
    }

    private fun getLastKnownLocation(): Location? {
        return try {
            val gpsLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            when {
                gpsLocation != null && networkLocation != null -> {
                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                }
                gpsLocation != null -> gpsLocation
                else -> networkLocation
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
            null
        }
    }

    private fun triggerArrival(known: KnownDevice) {
        scope.launch(Dispatchers.IO) {
            val loc = getLastKnownLocation()
            dao.insertHistoryEntry(
                HistoryEntry(
                    identifier = known.identifier,
                    alias = known.alias,
                    deviceType = known.type,
                    eventType = "ARRIVED",
                    latitude = loc?.latitude,
                    longitude = loc?.longitude
                )
            )

            if (known.notifyOnArrival) {
                sendPresenceNotification(
                    notificationId = known.identifier.hashCode() + 1000,
                    title = "Appareil détecté : ${known.alias}",
                    message = "${known.alias} (${known.type}) est désormais à portée de votre radar."
                )
            }
        }
    }

    private fun triggerDeparture(known: KnownDevice) {
        scope.launch(Dispatchers.IO) {
            val loc = getLastKnownLocation()
            dao.insertHistoryEntry(
                HistoryEntry(
                    identifier = known.identifier,
                    alias = known.alias,
                    deviceType = known.type,
                    eventType = "DEPARTED",
                    latitude = loc?.latitude,
                    longitude = loc?.longitude
                )
            )

            if (known.notifyOnDeparture) {
                sendPresenceNotification(
                    notificationId = known.identifier.hashCode() + 2000,
                    title = "Alerte Anti-oubli : ${known.alias}",
                    message = "${known.alias} (${known.type}) a quitté le champ de détection !"
                )
            }
        }
    }

    private fun sendPresenceNotification(notificationId: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, PresenceService.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager?.notify(notificationId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting presence notification", e)
        }
    }

    private fun publishDevices() {
        _detectedDevices.value = activeDevicesMap.values.toList()
            .sortedWith(compareByDescending<DetectedDevice> { it.isKnown }
                .thenByDescending { it.rssi })
    }

    // Main Scanning control
    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true

        activeDevicesMap.clear()
        publishDevices()

        // Register Wi-Fi Receiver
        try {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            context.registerReceiver(wifiScanReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering wifi scan receiver", e)
        }

        // Initialize BLE scanner
        bleScanner = bluetoothAdapter?.bluetoothLeScanner

        // Start active scanning jobs
        scanJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                startBleScan()
                startWifiScan()

                // Interval cycles: power saver is more generous
                val sleepTime = if (isPowerSaverMode) 25000L else scanIntervalBleMs
                delay(sleepTime)

                // Restart BLE scanning occasionally to prevent stale scanning states on Android
                stopBleScan()
            }
        }

        // Pruning job to detect departures
        pruneJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5000)
                val now = System.currentTimeMillis()
                val currentKeys = activeDevicesMap.keys().toList()

                for (key in currentKeys) {
                    val device = activeDevicesMap[key] ?: continue
                    if (now - device.lastSeen > departureDelayMs) {
                        // Device has departed
                        activeDevicesMap.remove(key)
                        val known = knownDevicesList.find {
                            it.identifier.equals(device.identifier, ignoreCase = true) ||
                                    (device.name != null && it.identifier.equals(device.name, ignoreCase = true))
                        }
                        if (known != null) {
                            triggerDeparture(known)
                        } else if (device.isKnown && device.alias != null) {
                            // Fallback if known object was not found directly
                            val tempKnown = KnownDevice(
                                identifier = device.identifier,
                                alias = device.alias,
                                type = device.type,
                                notifyOnArrival = true,
                                notifyOnDeparture = true
                            )
                            triggerDeparture(tempKnown)
                        }
                    }
                }
                publishDevices()
            }
        }
    }

    fun stopScanning() {
        if (!_isScanning.value) return
        _isScanning.value = false

        scanJob?.cancel()
        scanJob = null

        pruneJob?.cancel()
        pruneJob = null

        stopBleScan()

        try {
            context.unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Wifi receiver already unregistered or not registered")
        }
    }

    private fun startBleScan() {
        val scanner = bleScanner ?: bluetoothAdapter?.bluetoothLeScanner ?: return
        try {
            val mode = if (isPowerSaverMode) {
                ScanSettings.SCAN_MODE_LOW_POWER
            } else {
                ScanSettings.SCAN_MODE_LOW_LATENCY
            }

            val settings = ScanSettings.Builder()
                .setScanMode(mode)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()

            scanner.startScan(null, settings, bleScanCallback)
            Log.d(TAG, "BLE scan started successfully. Mode: $mode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE Scan", e)
        }
    }

    private fun stopBleScan() {
        val scanner = bleScanner ?: bluetoothAdapter?.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(bleScanCallback)
            Log.d(TAG, "BLE scan stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop BLE Scan", e)
        }
    }

    private fun startWifiScan() {
        val wm = wifiManager ?: return
        try {
            // Note: WifiManager.startScan() is deprecated, but is still functional and
            // needed on many devices to refresh results. We also read existing cached results.
            wm.startScan()
            handleWifiResults()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Wi-Fi Scan", e)
            // Fallback: read cached scan results
            handleWifiResults()
        }
    }

    fun destroy() {
        stopScanning()
        cacheUpdateJob?.cancel()
    }
}
