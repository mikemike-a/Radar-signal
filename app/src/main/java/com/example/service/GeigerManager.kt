package com.example.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GeigerManager(private val context: Context) {
    private val TAG = "GeigerManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var geigerJob: Job? = null

    private val _isAudioEnabled = MutableStateFlow(false)
    val isAudioEnabled = _isAudioEnabled.asStateFlow()

    private val _isHapticEnabled = MutableStateFlow(false)
    val isHapticEnabled = _isHapticEnabled.asStateFlow()

    private var currentRssi = -100
    private var isSignalLost = true
    private var isHunting = false

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            Log.d(TAG, "Geiger ToneGenerator initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }
    }

    fun setAudioEnabled(enabled: Boolean) {
        _isAudioEnabled.value = enabled
        checkLoopState()
    }

    fun setHapticEnabled(enabled: Boolean) {
        _isHapticEnabled.value = enabled
        checkLoopState()
    }

    fun updateTargetState(hunting: Boolean, rssi: Int, lost: Boolean) {
        this.isHunting = hunting
        this.currentRssi = rssi
        this.isSignalLost = lost
        checkLoopState()
    }

    private fun checkLoopState() {
        val shouldRun = isHunting && !isSignalLost && (_isAudioEnabled.value || _isHapticEnabled.value)
        if (shouldRun) {
            startLoop()
        } else {
            stopLoop()
        }
    }

    private fun startLoop() {
        if (geigerJob != null) return
        Log.d(TAG, "Starting Geiger feedback loop")
        geigerJob = scope.launch {
            while (isActive) {
                // Determine ticker interval rate based on current RSSI.
                // Clamped range: -95 (very weak/far) to -40 (extremely strong/close).
                val clampedRssi = currentRssi.coerceIn(-95, -40)
                val ratio = (clampedRssi - (-95)).toFloat() / (-40 - (-95))
                
                // delay interpolates between 1600ms (slow search clicking) and 70ms (frantic ticking)
                val delayMs = (1600 - (ratio * (1600 - 70))).toLong().coerceIn(70, 1600)

                // Trigger Audio Tick Beep
                if (_isAudioEnabled.value) {
                    try {
                        // Use a short warning beep to represent the Geiger tick
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed playing Geiger audio tick", e)
                    }
                }

                // Trigger Haptic Vibration Pulse
                if (_isHapticEnabled.value) {
                    try {
                        vibrator?.let { v ->
                            if (v.hasVibrator()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    v.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    v.vibrate(12)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Vibrator trigger failed", e)
                    }
                }

                delay(delayMs)
            }
        }
    }

    private fun stopLoop() {
        if (geigerJob != null) {
            Log.d(TAG, "Stopping Geiger feedback loop")
            geigerJob?.cancel()
            geigerJob = null
        }
    }

    fun onDestroy() {
        stopLoop()
        scope.cancel()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {}
        toneGenerator = null
    }
}
