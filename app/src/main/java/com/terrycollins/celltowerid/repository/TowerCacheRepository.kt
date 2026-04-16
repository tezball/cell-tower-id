package com.terrycollins.celltowerid.repository

import com.terrycollins.celltowerid.data.dao.TowerCacheDao
import com.terrycollins.celltowerid.data.mapper.EntityMapper
import com.terrycollins.celltowerid.domain.model.CellTower
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TowerCacheRepository(private val towerCacheDao: TowerCacheDao) {

    suspend fun findTower(
        radio: String,
        mcc: Int,
        mnc: Int,
        tacLac: Int,
        cid: Long
    ): CellTower? {
        return withContext(Dispatchers.IO) {
            towerCacheDao.findTower(radio, mcc, mnc, tacLac, cid)
                ?.let { EntityMapper.toDomain(it) }
        }
    }

    suspend fun getTowersInArea(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<CellTower> {
        return withContext(Dispatchers.IO) {
            towerCacheDao.getTowersInArea(minLat, maxLat, minLon, maxLon)
                .map { EntityMapper.toDomain(it) }
        }
    }

    suspend fun addTower(tower: CellTower) {
        withContext(Dispatchers.IO) {
            towerCacheDao.insert(EntityMapper.toEntity(tower))
        }
    }

    /**
     * Persist a locally-estimated tower position as source = "learned".
     * Uses REPLACE semantics via [TowerCacheDao.insert], so repeat calls
     * update the cached coordinates.
     */
    suspend fun learnPosition(tower: CellTower) {
        withContext(Dispatchers.IO) {
            towerCacheDao.insert(EntityMapper.toEntity(tower.copy(source = "learned")))
        }
    }

    suspend fun addTowers(towers: List<CellTower>) {
        withContext(Dispatchers.IO) {
            towerCacheDao.insertAll(towers.map { EntityMapper.toEntity(it) })
        }
    }

    suspend fun getTowerCount(): Int {
        return withContext(Dispatchers.IO) { towerCacheDao.getCount() }
    }
}
