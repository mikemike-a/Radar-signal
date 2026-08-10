package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM known_devices ORDER BY addedAt DESC")
    fun getKnownDevicesFlow(): Flow<List<KnownDevice>>

    @Query("SELECT * FROM known_devices")
    suspend fun getKnownDevicesList(): List<KnownDevice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnownDevice(device: KnownDevice)

    @Delete
    suspend fun deleteKnownDevice(device: KnownDevice)

    @Query("SELECT * FROM presence_history ORDER BY timestamp DESC LIMIT 500")
    fun getHistoryFlow(): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(entry: HistoryEntry)

    @Query("DELETE FROM presence_history")
    suspend fun clearHistory()
}
