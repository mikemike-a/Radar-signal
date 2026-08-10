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
        createNotificationChannel()
        scanner = PresenceScanner(applicationContext, serviceScope)
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
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Radar de Présence Actif")
            .setContentText("Recherche en arrière-plan d'appareils BLE et réseaux Wi-Fi...")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Radar de Présence Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification persistante pour la recherche d'appareils à proximité."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
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
