package com.example.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class EmergencyBeaconManager(private val context: Context) : SensorEventListener {

    private val TAG = "EmergencyBeacon"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State flows for UI observation
    private val _isBeaconActive = MutableStateFlow(false)
    val isBeaconActive = _isBeaconActive.asStateFlow()

    private val _isMonitoringInactivity = MutableStateFlow(false)
    val isMonitoringInactivity = _isMonitoringInactivity.asStateFlow()

    private val _countdownSecondsRemaining = MutableStateFlow(0)
    val countdownSecondsRemaining = _countdownSecondsRemaining.asStateFlow()

    private val _lastInactivityProgress = MutableStateFlow(0f) // 0f to 1f progress towards trigger
    val lastInactivityProgress = _lastInactivityProgress.asStateFlow()

    // Configurable switches
    var isSoundEnabled = true
    var isFlashlightEnabled = true
    var isBleEnabled = true

    // Timers and Sensors
    private var countdownJob: Job? = null
    private var flashJob: Job? = null
    private var soundJob: Job? = null

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    // Inactivity Tracking
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastMovementTime = System.currentTimeMillis()
    private var inactivityThresholdMs = 30000L // Default 30s for demo, can be customized
    private var motionCheckingJob: Job? = null

    // BLE Advertising
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    private var advertiseCallback: AdvertiseCallback? = null

    // Audio
    private var mediaPlayer: MediaPlayer? = null

    init {
        Log.d(TAG, "EmergencyBeaconManager Initialized")
    }

    // --- BEACON TRIGGER & CONTROL ---

    fun startCountdownBeacon(seconds: Int) {
        stopBeacon()
        stopMonitoringInactivity()
        _countdownSecondsRemaining.value = seconds

        countdownJob = scope.launch {
            while (_countdownSecondsRemaining.value > 0) {
                delay(1000)
                _countdownSecondsRemaining.value -= 1
            }
            activateBeacon()
        }
    }

    fun startInactivityMonitoring(timeoutSeconds: Int) {
        stopBeacon()
        stopMonitoringInactivity()
        inactivityThresholdMs = timeoutSeconds * 1000L
        lastMovementTime = System.currentTimeMillis()
        _isMonitoringInactivity.value = true
        _lastInactivityProgress.value = 0f

        // Register sensor listener
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Start safety monitoring loop
        motionCheckingJob = scope.launch {
            while (_isMonitoringInactivity.value) {
                delay(500)
                val now = System.currentTimeMillis()
                val elapsed = now - lastMovementTime
                val progress = (elapsed.toFloat() / inactivityThresholdMs).coerceIn(0f, 1f)
                _lastInactivityProgress.value = progress

                if (elapsed >= inactivityThresholdMs) {
                    _isMonitoringInactivity.value = false
                    sensorManager?.unregisterListener(this@EmergencyBeaconManager)
                    activateBeacon()
                    break
                }
            }
        }
    }

    fun stopMonitoringInactivity() {
        _isMonitoringInactivity.value = false
        _lastInactivityProgress.value = 0f
        motionCheckingJob?.cancel()
        sensorManager?.unregisterListener(this)
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        _countdownSecondsRemaining.value = 0
    }

    fun activateBeacon() {
        if (_isBeaconActive.value) return
        _isBeaconActive.value = true
        _countdownSecondsRemaining.value = 0
        stopMonitoringInactivity()

        Log.d(TAG, "🚨 EMERGENY SOS BEACON ACTIVATED! 🚨")

        // 1. Start BLE Radio Beacon
        if (isBleEnabled) {
            startBleBeaconAdvertising()
        }

        // 2. Start Flashlight Flashing (SOS Morse Code)
        if (isFlashlightEnabled) {
            startFlashlightSOS()
        }

        // 3. Start High Volume Alarm Siren
        if (isSoundEnabled) {
            startSirenSound()
        }
    }

    fun stopBeacon() {
        if (!_isBeaconActive.value) return
        _isBeaconActive.value = false
        Log.d(TAG, "Emergency beacon stopped")

        // Stop all emitters
        stopBleBeaconAdvertising()
        stopFlashlightSOS()
        stopSirenSound()
        cancelCountdown()
    }

    // --- ACCELEROMETER SENSOR LISTENER ---

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Compute delta motion
        val deltaX = abs(x - lastX)
        val deltaY = abs(y - lastY)
        val deltaZ = abs(z - lastZ)

        val totalDelta = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble())

        // Threshold of motion to reset the timer (0.45 is a stable pocket/table threshold)
        if (totalDelta > 0.45) {
            lastMovementTime = System.currentTimeMillis()
            _lastInactivityProgress.value = 0f
        }

        lastX = x
        lastY = y
        lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- 1. BLE ADVERTISING BEACON ---

    private fun startBleBeaconAdvertising() {
        try {
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            if (advertiser == null) {
                Log.w(TAG, "BLE Advertiser not supported or Bluetooth disabled")
                return
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()

            // Custom unique UUID for emergency beacons detectable by our app
            val beaconUuid = UUID.fromString("00005050-0000-1000-8000-00805f9b34fb")

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(beaconUuid))
                .build()

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "BLE advertising started successfully")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "BLE advertising failed with error code: $errorCode")
                }
            }

            // Temporarily update bluetooth name to match search beacon signature
            try {
                bluetoothAdapter.setName("SOS_RADAR_BEACON")
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot set BT name due to permission restrictions", e)
            }

            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions to advertise", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting BLE Beacon advertising", e)
        }
    }

    private fun stopBleBeaconAdvertising() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser != null && advertiseCallback != null) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
                // Restore default name if desired, or let it reset on reboot
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while stopping advertising", e)
            }
            advertiseCallback = null
        }
    }

    // --- 2. FLASHLIGHT MORSE SOS LOOP ---

    private fun startFlashlightSOS() {
        flashJob = scope.launch(Dispatchers.IO) {
            val cameraId = getCameraIdWithFlash()
            if (cameraId == null) {
                Log.w(TAG, "No camera with flashlight found")
                return@launch
            }

            // Morse code timing: Dit = 150ms, Dah = 450ms
            // S = ...
            // O = ---
            // S = ...
            while (isActive) {
                // S
                for (i in 0 until 3) {
                    setFlashState(cameraId, true)
                    delay(150)
                    setFlashState(cameraId, false)
                    delay(150)
                }
                delay(300) // letter spacing

                // O
                for (i in 0 until 3) {
                    setFlashState(cameraId, true)
                    delay(450)
                    setFlashState(cameraId, false)
                    delay(150)
                }
                delay(300) // letter spacing

                // S
                for (i in 0 until 3) {
                    setFlashState(cameraId, true)
                    delay(150)
                    setFlashState(cameraId, false)
                    delay(150)
                }

                delay(1500) // word spacing before next SOS loop
            }
        }
    }

    private fun stopFlashlightSOS() {
        flashJob?.cancel()
        flashJob = null
        scope.launch(Dispatchers.IO) {
            val cameraId = getCameraIdWithFlash()
            if (cameraId != null) {
                setFlashState(cameraId, false)
            }
        }
    }

    private fun getCameraIdWithFlash(): String? {
        val manager = cameraManager ?: return null
        try {
            for (id in manager.cameraIdList) {
                val hasFlash = manager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (hasFlash) return id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up camera ID with flash", e)
        }
        return null
    }

    private fun setFlashState(cameraId: String, state: Boolean) {
        try {
            cameraManager?.setTorchMode(cameraId, state)
        } catch (e: Exception) {
            // Torch state cannot be changed or camera in use
        }
    }

    // --- 3. HIGH VOLUME ALARM SIREN ---

    private fun startSirenSound() {
        soundJob = scope.launch(Dispatchers.IO) {
            // Force stream volume to maximum for alarm stream
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.let { am ->
                val maxVolume = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)
            }

            try {
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, alertUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d(TAG, "SOS sound playing successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed playing standard alarm, using backup tone", e)
                // If media player fails, generate fallback tone
                playFallbackBeeps()
            }
        }
    }

    private fun playFallbackBeeps() {
        val toneG = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
        soundJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                toneG.startTone(android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1500)
                delay(3000)
            }
        }
    }

    private fun stopSirenSound() {
        soundJob?.cancel()
        soundJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
    }

    fun onDestroy() {
        stopBeacon()
        stopMonitoringInactivity()
        scope.cancel()
    }
}
