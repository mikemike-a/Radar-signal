package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.KnownDevice
import com.example.data.HistoryEntry
import com.example.scanning.DetectedDevice
import com.example.service.PresenceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

class RadarViewModel(private val context: Context) : ViewModel() {
    private val TAG = "RadarViewModel"
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.deviceDao()

    // --- BAROMETER & ELEVATION TRACKER ---
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val _isBarometerAvailable = MutableStateFlow(false)
    val isBarometerAvailable = _isBarometerAvailable.asStateFlow()

    private val _currentPressure = MutableStateFlow<Float?>(null)
    val currentPressure = _currentPressure.asStateFlow()

    private val _referencePressure = MutableStateFlow(1013.25f) // hPa reference level
    val referencePressure = _referencePressure.asStateFlow()

    private val _estimatedRelativeAltitude = MutableStateFlow(0f) // in meters
    val estimatedRelativeAltitude = _estimatedRelativeAltitude.asStateFlow()

    private val _estimatedFloor = MutableStateFlow(0) // estimated floor difference
    val estimatedFloor = _estimatedFloor.asStateFlow()

    private val pressureListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
                val pressure = event.values[0]
                updatePressureMetrics(pressure)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // --- MODE CHASSE (HUNTING / FIND MY PHONE) ---
    private val _huntingDevice = MutableStateFlow<DetectedDevice?>(null)
    val huntingDevice = _huntingDevice.asStateFlow()

    private val _huntRssiTrend = MutableStateFlow("Stable ➖")
    val huntRssiTrend = _huntRssiTrend.asStateFlow()

    private val _huntDistanceText = MutableStateFlow("Glacial 🧊")
    val huntDistanceText = _huntDistanceText.asStateFlow()

    private val _huntDistanceProgress = MutableStateFlow(0f) // Proximity gauge: 0 to 1
    val huntDistanceProgress = _huntDistanceProgress.asStateFlow()

    private val _huntSignalLost = MutableStateFlow(true)
    val huntSignalLost = _huntSignalLost.asStateFlow()

    private val huntRssiHistory = mutableListOf<Int>()

    // --- CARTOGRAPHIE & HEATMAP ---
    // Represents a 2D map: (X, Y) -> RSSI
    private val _heatmapData = MutableStateFlow<Map<Pair<Int, Int>, Int>>(emptyMap())
    val heatmapData = _heatmapData.asStateFlow()

    // Are we in Heatmap mapping mode in the Hunt tab?
    private val _isHeatmapMode = MutableStateFlow(false)
    val isHeatmapMode = _isHeatmapMode.asStateFlow()

    fun toggleHeatmapMode() {
        _isHeatmapMode.value = !_isHeatmapMode.value
    }

    fun recordHeatmapPoint(x: Int, y: Int) {
        val currentDevice = _huntingDevice.value
        val currentRssi = currentDevice?.rssi
        if (currentRssi != null && !_huntSignalLost.value) {
            val currentMap = _heatmapData.value.toMutableMap()
            currentMap[Pair(x, y)] = currentRssi
            _heatmapData.value = currentMap
        }
    }

    fun clearHeatmap() {
        _heatmapData.value = emptyMap()
    }

    // Database flow streams
    val knownDevices: StateFlow<List<KnownDevice>> = dao.getKnownDevicesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntry>> = dao.getHistoryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active screen navigation
    val activeTab = mutableStateOf("radar") // "radar", "known", "history", "settings"

    // Filter text for history or device lists
    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery = _historySearchQuery.asStateFlow()

    // Live scan results
    private val _detectedDevices = MutableStateFlow<List<DetectedDevice>>(emptyList())
    val detectedDevices: StateFlow<List<DetectedDevice>> = _detectedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Settings
    private val _rssiThreshold = MutableStateFlow(-90)
    val rssiThreshold = _rssiThreshold.asStateFlow()

    private val _isPowerSaver = MutableStateFlow(false)
    val isPowerSaver = _isPowerSaver.asStateFlow()

    private val _departureDelaySec = MutableStateFlow(45)
    val departureDelaySec = _departureDelaySec.asStateFlow()

    private var presenceService: PresenceService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? PresenceService.LocalBinder
            presenceService = binder?.getService()
            isBound = true
            Log.d(TAG, "Service Bound")

            // Sync scanner settings
            applyScannerSettings()

            // Observe service scanner state
            viewModelScope.launch {
                presenceService?.scanner?.detectedDevices?.collect { devices ->
                    _detectedDevices.value = devices
                    updateHuntingDeviceFromScanned(devices)
                }
            }
            viewModelScope.launch {
                presenceService?.scanner?.isScanning?.collect { scanning ->
                    _isScanning.value = scanning
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            presenceService = null
            isBound = false
            _isScanning.value = false
            Log.d(TAG, "Service Unbound")
        }
    }

    init {
        // Attempt to bind if already running
        if (PresenceService.isRunning()) {
            bindPresenceService()
        }
        initBarometer()
    }

    fun bindPresenceService() {
        if (!isBound) {
            val intent = Intent(context, PresenceService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindPresenceService() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service", e)
            }
            isBound = false
            presenceService = null
            _isScanning.value = false
        }
    }

    fun toggleScanning(start: Boolean) {
        val intent = Intent(context, PresenceService::class.java)
        if (start) {
            intent.action = PresenceService.ACTION_START_SCAN
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            bindPresenceService()
        } else {
            intent.action = PresenceService.ACTION_STOP_SCAN
            context.startService(intent)
            unbindPresenceService()
            _detectedDevices.value = emptyList()
            _isScanning.value = false
        }
    }

    // Known device actions
    fun addKnownDevice(
        identifier: String,
        alias: String,
        type: String,
        floor: Int = 0,
        notifyOnArrival: Boolean = true,
        notifyOnDeparture: Boolean = true
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertKnownDevice(
                KnownDevice(
                    identifier = identifier.trim(),
                    alias = alias.trim(),
                    type = type,
                    rssiThreshold = _rssiThreshold.value,
                    floor = floor,
                    notifyOnArrival = notifyOnArrival,
                    notifyOnDeparture = notifyOnDeparture
                )
            )
        }
    }

    fun updateKnownDeviceNotifications(device: KnownDevice, notifyArrival: Boolean, notifyDeparture: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertKnownDevice(
                device.copy(
                    notifyOnArrival = notifyArrival,
                    notifyOnDeparture = notifyDeparture
                )
            )
        }
    }

    fun deleteKnownDevice(device: KnownDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteKnownDevice(device)
        }
    }

    // History actions
    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearHistory()
        }
    }

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    // Settings actions
    fun sendTestNotification(isDeparture: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return

        val title = if (isDeparture) "Alerte Anti-oubli (Test) 🚨" else "Appareil détecté (Test) 🔔"
        val message = if (isDeparture) {
            "Votre appareil enregistré de test a quitté le champ de détection !"
        } else {
            "Votre appareil enregistré de test est désormais à portée de votre radar."
        }

        val intent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            9999,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, PresenceService.NOTIFICATION_ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }

    fun updateRssiThreshold(value: Int) {
        _rssiThreshold.value = value
        applyScannerSettings()
    }

    fun updatePowerSaver(enabled: Boolean) {
        _isPowerSaver.value = enabled
        applyScannerSettings()
    }

    fun updateDepartureDelay(seconds: Int) {
        _departureDelaySec.value = seconds
        applyScannerSettings()
    }

    private fun applyScannerSettings() {
        presenceService?.scanner?.let { s ->
            s.rssiThreshold = _rssiThreshold.value
            s.isPowerSaverMode = _isPowerSaver.value
            s.departureDelayMs = _departureDelaySec.value * 1000L
        }
    }

    // --- Barometer Methods ---
    private fun initBarometer() {
        if (pressureSensor != null) {
            _isBarometerAvailable.value = true
            sensorManager?.registerListener(pressureListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            _isBarometerAvailable.value = false
        }
    }

    private fun updatePressureMetrics(pressure: Float) {
        _currentPressure.value = pressure
        val ref = _referencePressure.value
        // Standard formula: altitude = 44330 * (1 - (p/p0)^0.1903)
        val currentAlt = 44330f * (1f - (pressure / 1013.25f).toDouble().pow(0.1902949571836346).toFloat())
        val refAlt = 44330f * (1f - (ref / 1013.25f).toDouble().pow(0.1902949571836346).toFloat())
        val relativeAlt = currentAlt - refAlt
        _estimatedRelativeAltitude.value = relativeAlt
        _estimatedFloor.value = kotlin.math.round(relativeAlt / 3.0f).toInt()
    }

    fun calibrateGroundLevel() {
        val current = _currentPressure.value ?: 1013.25f
        _referencePressure.value = current
        updatePressureMetrics(current)
    }

    // --- Mode Chasse Methods ---
    fun startHunting(device: DetectedDevice) {
        _huntingDevice.value = device
        _huntSignalLost.value = false
        huntRssiHistory.clear()
        huntRssiHistory.add(device.rssi)
        updateHuntMetrics(device.rssi)
        activeTab.value = "hunt" // Bascule automatiquement sur l'onglet de Chasse !
    }

    fun stopHunting() {
        _huntingDevice.value = null
        _huntSignalLost.value = true
        huntRssiHistory.clear()
    }

    private fun updateHuntingDeviceFromScanned(devices: List<DetectedDevice>) {
        val target = _huntingDevice.value ?: return
        val updated = devices.find { it.identifier == target.identifier }
        if (updated != null) {
            _huntingDevice.value = updated
            _huntSignalLost.value = false
            
            // Smoothed RSSI history & trends
            huntRssiHistory.add(updated.rssi)
            if (huntRssiHistory.size > 8) {
                huntRssiHistory.removeAt(0)
            }
            updateHuntMetrics(updated.rssi)
        } else {
            // Check if last seen was more than 15 seconds ago to declare lost
            val elapsed = System.currentTimeMillis() - target.lastSeen
            if (elapsed > 15000L) {
                _huntSignalLost.value = true
            }
        }
    }

    private fun updateHuntMetrics(rssi: Int) {
        // Calculate Trend using simple comparison of first half vs second half of sliding history
        if (huntRssiHistory.size >= 3) {
            val size = huntRssiHistory.size
            val firstHalf = huntRssiHistory.take(size / 2).average()
            val secondHalf = huntRssiHistory.takeLast(size / 2).average()
            
            _huntRssiTrend.value = when {
                secondHalf > firstHalf + 1.2 -> "S'approche 📈"
                secondHalf < firstHalf - 1.2 -> "S'éloigne 📉"
                else -> "Stable ➖"
            }
        } else {
            _huntRssiTrend.value = "Analyse... ⏳"
        }

        // Relative gauge progress (-100 dBm to -40 dBm mapped to 0f to 1f)
        val progress = ((rssi - (-100f)) / ((-40f) - (-100f))).coerceIn(0f, 1f)
        _huntDistanceProgress.value = progress

        // Proximity warm/cold labels
        _huntDistanceText.value = when {
            rssi >= -55 -> "Brûlant ! 🔥"       // Direct proximity
            rssi >= -68 -> "Chaud ☀️"           // Very close
            rssi >= -80 -> "Tiède 🌤"           // Proche
            rssi >= -90 -> "Froid ❄️"           // Modéré
            else -> "Glacial 🧊"               // Très éloigné
        }
    }

    override fun onCleared() {
        _beaconManager.onDestroy()
        unbindPresenceService()
        sensorManager?.unregisterListener(pressureListener)
        super.onCleared()
    }

    // --- EMERGENCY SOS BEACON ---
    private val _beaconManager = com.example.service.EmergencyBeaconManager(context)
    val isBeaconActive = _beaconManager.isBeaconActive
    val isMonitoringInactivity = _beaconManager.isMonitoringInactivity
    val countdownSecondsRemaining = _beaconManager.countdownSecondsRemaining
    val lastInactivityProgress = _beaconManager.lastInactivityProgress

    var isBeaconSoundEnabled: Boolean
        get() = _beaconManager.isSoundEnabled
        set(value) { _beaconManager.isSoundEnabled = value }

    var isBeaconFlashlightEnabled: Boolean
        get() = _beaconManager.isFlashlightEnabled
        set(value) { _beaconManager.isFlashlightEnabled = value }

    var isBeaconBleEnabled: Boolean
        get() = _beaconManager.isBleEnabled
        set(value) { _beaconManager.isBleEnabled = value }

    fun startCountdownBeacon(seconds: Int) {
        _beaconManager.startCountdownBeacon(seconds)
    }

    fun startInactivityMonitoring(seconds: Int) {
        _beaconManager.startInactivityMonitoring(seconds)
    }

    fun stopInactivityMonitoring() {
        _beaconManager.stopMonitoringInactivity()
    }

    fun cancelBeaconCountdown() {
        _beaconManager.cancelCountdown()
    }

    fun stopBeacon() {
        _beaconManager.stopBeacon()
    }

    fun activateBeaconImmediately() {
        _beaconManager.activateBeacon()
    }
}

class RadarViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RadarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RadarViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

