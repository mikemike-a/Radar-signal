package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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

class RadarViewModel(private val context: Context) : ViewModel() {
    private val TAG = "RadarViewModel"
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.deviceDao()

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
    fun addKnownDevice(identifier: String, alias: String, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertKnownDevice(
                KnownDevice(
                    identifier = identifier.trim(),
                    alias = alias.trim(),
                    type = type,
                    rssiThreshold = _rssiThreshold.value
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

    override fun onCleared() {
        unbindPresenceService()
        super.onCleared()
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
