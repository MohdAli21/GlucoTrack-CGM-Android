package com.mohammed.glucotrack

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glucose_history")
data class GlucoseRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val value: Float,
    val eventType: String? = null
)