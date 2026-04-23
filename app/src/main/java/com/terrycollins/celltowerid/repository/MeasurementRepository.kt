package com.terrycollins.celltowerid.repository

import com.terrycollins.celltowerid.data.dao.MeasurementDao
import com.terrycollins.celltowerid.data.mapper.EntityMapper
import com.terrycollins.celltowerid.domain.model.CellMeasurement
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

    suspend fun getMeasurementsSince(cutoffMs: Long): List<CellMeasurement> {
        return withContext(Dispatchers.IO) {
            measurementDao.getMeasurementsSince(cutoffMs).map { EntityMapper.toDomain(it) }
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
