package com.mohammed.glucotrack

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GlucoseDao {
    @Insert
    suspend fun insert(record: GlucoseRecord)

    @Query("SELECT * FROM glucose_history ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<GlucoseRecord>

    // NEW: This function will set the eventType to null for a specific record
    @Query("UPDATE glucose_history SET eventType = NULL WHERE id = :recordId")
    suspend fun clearEventFromRecord(recordId: Int)
}