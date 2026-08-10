package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_devices")
data class KnownDevice(
    @PrimaryKey val identifier: String, // MAC Address, BSSID, or exact Device Name
    val alias: String,
    val type: String, // "BLE" or "WIFI"
    val addedAt: Long = System.currentTimeMillis(),
    val rssiThreshold: Int = -95
)

@Entity(tableName = "presence_history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identifier: String,
    val alias: String,
    val deviceType: String, // "BLE" or "WIFI"
    val eventType: String, // "ARRIVED" (Arrivé) or "DEPARTED" (Parti)
    val timestamp: Long = System.currentTimeMillis()
)
