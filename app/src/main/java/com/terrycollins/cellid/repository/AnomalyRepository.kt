package com.terrycollins.cellid.repository

import com.terrycollins.cellid.data.dao.AnomalyDao
import com.terrycollins.cellid.data.mapper.EntityMapper
import com.terrycollins.cellid.domain.model.AnomalyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnomalyRepository(private val anomalyDao: AnomalyDao) {

    suspend fun insertAnomaly(event: AnomalyEvent, sessionId: Long?): Long {
        return withContext(Dispatchers.IO) {
            val radio = event.cellRadio
            val mcc = event.cellMcc
            val mnc = event.cellMnc
            val tac = event.cellTacLac
            val cid = event.cellCid
            if (radio != null && mcc != null && mnc != null && tac != null && cid != null) {
                val existing = anomalyDao.countByCellIdentity(
                    event.type.name, radio.name, mcc, mnc, tac, cid
                )
                if (existing > 0) return@withContext -1L
            }
            anomalyDao.insert(EntityMapper.toEntity(event, sessionId))
        }
    }

    suspend fun deleteDuplicateCellAnomalies(): Int {
        return withContext(Dispatchers.IO) { anomalyDao.deleteDuplicateCellAnomalies() }
    }

    suspend fun getUndismissedAnomalies(): List<AnomalyEvent> {
        return withContext(Dispatchers.IO) {
            anomalyDao.getUndismissed().map { EntityMapper.toDomain(it) }
        }
    }

    suspend fun getAllAnomalies(): List<AnomalyEvent> {
        return withContext(Dispatchers.IO) {
            anomalyDao.getAll().map { EntityMapper.toDomain(it) }
        }
    }

    suspend fun getRecentAnomalies(limit: Int = 50): List<AnomalyEvent> {
        return withContext(Dispatchers.IO) {
            anomalyDao.getRecentAnomalies(limit).map { EntityMapper.toDomain(it) }
        }
    }

    suspend fun dismissAll() {
        withContext(Dispatchers.IO) { anomalyDao.dismissAll() }
    }

    suspend fun dismiss(id: Long) {
        withContext(Dispatchers.IO) { anomalyDao.dismissById(id) }
    }

    suspend fun undismiss(id: Long) {
        withContext(Dispatchers.IO) { anomalyDao.undismissById(id) }
    }

    suspend fun deleteOlderThan(cutoffMs: Long): Int {
        return withContext(Dispatchers.IO) { anomalyDao.deleteOlderThan(cutoffMs) }
    }
}
