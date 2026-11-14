package com.example.data.repository

import com.example.data.local.ScanHistoryDao
import com.example.data.local.ScanHistoryEntity
import com.example.data.model.ScanResultData
import kotlinx.coroutines.flow.Flow

class ScanHistoryRepository(private val dao: ScanHistoryDao) {

    val allScans: Flow<List<ScanHistoryEntity>> = dao.getAllScans()

    fun recentScans(limit: Int = 3): Flow<List<ScanHistoryEntity>> = dao.getRecentScans(limit)

    suspend fun saveScan(result: ScanResultData): Result<Long> = runCatching {
        val entity = ScanHistoryEntity(
            rawValue = result.rawValue,
            formatName = result.formatName,
            formatCode = result.formatCode,
            contentType = result.contentType.name,
            displayTitle = result.displayTitle,
            source = result.source.name,
            timestamp = result.timestamp
        )
        dao.insertScan(entity)
    }

    suspend fun getById(id: Long): ScanHistoryEntity? = runCatching {
        dao.getScanById(id)
    }.getOrNull()

    suspend fun deleteById(id: Long): Result<Unit> = runCatching {
        dao.deleteScanById(id)
    }

    suspend fun clearAll(): Result<Unit> = runCatching {
        dao.clearAllScans()
    }
}
