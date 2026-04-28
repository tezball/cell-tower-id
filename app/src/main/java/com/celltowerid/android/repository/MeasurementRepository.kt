package com.celltowerid.android.repository

import com.celltowerid.android.data.dao.MeasurementDao
import com.celltowerid.android.data.mapper.EntityMapper
import com.celltowerid.android.domain.model.CellKey
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MeasurementRepository(private val measurementDao: MeasurementDao) {

    suspend fun insertMeasurements(measurements: List<CellMeasurement>, sessionId: Long) {
        withContext(Dispatchers.IO) {
            val entities = measurements.map { EntityMapper.toEntity(it, sessionId) }
            measurementDao.insertAll(entities)
        }
    }

    suspend fun getMeasurementsBySession(sessionId: Long): List<CellMeasurement> {
        return withContext(Dispatchers.IO) {
            measurementDao.getBySession(sessionId).map { EntityMapper.toDomain(it) }
        }
    }

    suspend fun getMeasurementsInArea(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<CellMeasurement> {
        return withContext(Dispatchers.IO) {
            measurementDao.getInArea(minLat, maxLat, minLon, maxLon)
                .map { EntityMapper.toDomain(it) }
        }
    }

    suspend fun getRecentMeasurements(limit: Int = 100): List<CellMeasurement> {
        return withContext(Dispatchers.IO) {
            measurementDao.getRecentMeasurements(limit).map { EntityMapper.toDomain(it) }
        }
    }

    suspend fun getMeasurementsFromLatestScan(sinceMs: Long, epsilonMs: Long): List<CellMeasurement> {
        return withContext(Dispatchers.IO) {
            measurementDao.getMeasurementsFromLatestScan(sinceMs, epsilonMs).map { EntityMapper.toDomain(it) }
        }
    }

    suspend fun getBestMeasurementsByCellInArea(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): Map<CellKey, CellMeasurement> {
        return withContext(Dispatchers.IO) {
            measurementDao.getBestMeasurementsInArea(minLat, maxLat, minLon, maxLon)
                .mapNotNull { entity ->
                    val mcc = entity.mcc ?: return@mapNotNull null
                    val mnc = entity.mnc ?: return@mapNotNull null
                    val tacLac = entity.tacLac ?: return@mapNotNull null
                    val cid = entity.cid ?: return@mapNotNull null
                    val key = CellKey(
                        radio = RadioType.fromString(entity.radio),
                        mcc = mcc,
                        mnc = mnc,
                        tacLac = tacLac,
                        cid = cid
                    )
                    key to EntityMapper.toDomain(entity)
                }
                .toMap()
        }
    }

    suspend fun getAllMeasurements(): List<CellMeasurement> {
        return withContext(Dispatchers.IO) {
            measurementDao.getAll().map { EntityMapper.toDomain(it) }
        }
    }

    suspend fun getMeasurementCount(): Int {
        return withContext(Dispatchers.IO) { measurementDao.getCount() }
    }

    suspend fun deleteOlderThan(cutoffMs: Long): Int {
        return withContext(Dispatchers.IO) { measurementDao.deleteOlderThan(cutoffMs) }
    }
}
