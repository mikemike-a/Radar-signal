package com.example.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.scanning.PresenceScanner
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class PresenceService : Service() {
    private val TAG = "PresenceService"

    companion object {
        const val ACTION_START_SCAN = "com.example.action.START_SCAN"
        const val ACTION_STOP_SCAN = "com.example.action.STOP_SCAN"
        const val NOTIFICATION_CHANNEL_ID = "presence_radar_service_channel"
        const val NOTIFICATION_ALERT_CHANNEL_ID = "presence_radar_alert_channel"
        const val NOTIFICATION_ID = 4124
        
        private val isServiceRunning = AtomicBoolean(false)
        fun isRunning(): Boolean = isServiceRunning.get()
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    var scanner: PresenceScanner? = null
        private set

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): PresenceService = this@PresenceService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PresenceService Created")
        isServiceRunning.set(true)
        createNotificationChannels()
        scanner = PresenceScanner(applicationContext, serviceScope)
        observeScanner()
    }

    private fun observeScanner() {
        serviceScope.launch {
            scanner?.detectedDevices?.collect { devices ->
                val knownCount = devices.count { it.isKnown }
                updateForegroundNotification(devices.size, knownCount)
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand with Action: $action")

        when (action) {
            ACTION_START_SCAN -> {
                startForegroundNotification()
                scanner?.startScanning()
            }
            ACTION_STOP_SCAN -> {
                scanner?.stopScanning()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun startForegroundNotification() {
        val notification = buildServiceNotification(0, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground with type connected device, falling back to standard", e)
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun updateForegroundNotification(totalDevices: Int, knownDevices: Int) {
        if (!isServiceRunning.get()) return
        val notification = buildServiceNotification(totalDevices, knownDevices)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildServiceNotification(totalDevices: Int, knownDevices: Int): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, PresenceService::class.java).apply {
            action = ACTION_STOP_SCAN
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val textContent = if (totalDevices == 0) {
            "Recherche en arrière-plan d'appareils BLE et Wi-Fi..."
        } else {
            "Radar actif • $totalDevices appareil(s) à portée ($knownDevices enregistré(s))"
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Radar de Présence Actif 🛰️")
            .setContentText(textContent)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Arrêter le scan", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            // Channel 1: Persistent Service Status
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Statut du Radar (Arrière-plan)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification de statut indiquant que le scan d'arrière-plan est actif."
            }
            manager.createNotificationChannel(serviceChannel)

            // Channel 2: High Priority Arrival & Anti-oubli Alerts
            val alertChannel = NotificationChannel(
                NOTIFICATION_ALERT_CHANNEL_ID,
                "Alertes d'Arrivée & Anti-Oubli 🚨",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertes prioritaires lorsqu'un appareil connu entre ou quitte le périmètre."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                enableLights(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "PresenceService Destroyed")
        scanner?.destroy()
        isServiceRunning.set(false)
        serviceJob.cancel()
        super.onDestroy()
    }
}
