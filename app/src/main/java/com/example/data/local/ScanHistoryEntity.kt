package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rawValue: String,
    val formatName: String,
    val formatCode: Int,
    val contentType: String,
    val displayTitle: String,
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)
