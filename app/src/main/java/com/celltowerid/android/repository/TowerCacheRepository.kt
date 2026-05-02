package com.celltowerid.android.repository

import androidx.lifecycle.LiveData
import com.celltowerid.android.data.dao.TowerCacheDao
import com.celltowerid.android.data.entity.TowerCacheEntity
import com.celltowerid.android.data.mapper.EntityMapper
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.CellTower
import com.celltowerid.android.domain.model.RadioType
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

    /**
     * Upsert the tower identified by this measurement into the local cache
     * with source = "observed". No-ops for unregistered or partially-identified
     * measurements — the cache is keyed on (radio, mcc, mnc, tacLac, cid).
     */
    suspend fun recordObservation(measurement: CellMeasurement) {
        if (!measurement.isRegistered) return
        val mcc = measurement.mcc ?: return
        val mnc = measurement.mnc ?: return
        val tacLac = measurement.tacLac ?: return
        val cid = measurement.cid ?: return
        withContext(Dispatchers.IO) {
            val existing = towerCacheDao.findTower(measurement.radio.name, mcc, mnc, tacLac, cid)
            val tower = CellTower(
                radio = measurement.radio,
                mcc = mcc,
                mnc = mnc,
                tacLac = tacLac,
                cid = cid,
                latitude = measurement.latitude,
                longitude = measurement.longitude,
                samples = 1,
                source = "observed",
                pci = measurement.pciPsc,
                isPinned = existing?.isPinned ?: false
            )
            towerCacheDao.insert(EntityMapper.toEntity(tower))
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

    suspend fun deleteBySource(source: String): Int {
        return withContext(Dispatchers.IO) { towerCacheDao.deleteBySource(source) }
    }

    /**
     * Pin a tower by 5-tuple. If no row exists yet and fallback coords are provided,
     * inserts a stub row (source = "pinned") so the pin is immediately visible on the map.
     * Returns true if pinned, false if no row existed and no fallback coords were given.
     */
    suspend fun pinTower(
        radio: RadioType,
        mcc: Int,
        mnc: Int,
        tacLac: Int,
        cid: Long,
        fallbackLat: Double? = null,
        fallbackLon: Double? = null,
        fallbackPci: Int? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val updated = towerCacheDao.setPinned(radio.name, mcc, mnc, tacLac, cid, true)
            if (updated > 0) return@withContext true
            if (fallbackLat == null || fallbackLon == null) return@withContext false
            val stub = TowerCacheEntity().apply {
                this.radio = radio.name
                this.mcc = mcc
                this.mnc = mnc
                this.tacLac = tacLac
                this.cid = cid
                latitude = fallbackLat
                longitude = fallbackLon
                pci = fallbackPci
                samples = 0
                source = "pinned"
                lastUpdated = System.currentTimeMillis()
                isPinned = true
            }
            towerCacheDao.insert(stub)
            true
        }
    }

    /**
     * Insert a user-specified tower position with source = "manual" and
     * isPinned = true. Used when the user knows where a physical tower is
     * (e.g. by sight) and wants to mark it on the map. REPLACE semantics on
     * the (radio, mcc, mnc, tacLac, cid) unique index mean repeat calls
     * overwrite the prior coordinates.
     */
    suspend fun addManualTower(
        radio: RadioType,
        mcc: Int,
        mnc: Int,
        tacLac: Int,
        cid: Long,
        latitude: Double,
        longitude: Double,
        pci: Int? = null
    ) {
        withContext(Dispatchers.IO) {
            val entity = TowerCacheEntity().apply {
                this.radio = radio.name
                this.mcc = mcc
                this.mnc = mnc
                this.tacLac = tacLac
                this.cid = cid
                this.latitude = latitude
                this.longitude = longitude
                this.pci = pci
                samples = 0
                source = "manual"
                lastUpdated = System.currentTimeMillis()
                isPinned = true
            }
            towerCacheDao.insert(entity)
        }
    }

    suspend fun unpinTower(
        radio: RadioType,
        mcc: Int,
        mnc: Int,
        tacLac: Int,
        cid: Long
    ) {
        withContext(Dispatchers.IO) {
            towerCacheDao.setPinned(radio.name, mcc, mnc, tacLac, cid, false)
        }
    }

    /**
     * LiveData of all pinned rows as entities. Exposed as entity-level so the
     * ViewModel can derive keys without another round-trip through the mapper.
     */
    fun getPinnedTowerEntitiesLive(): LiveData<List<TowerCacheEntity>> {
        return towerCacheDao.getPinnedTowersLive()
    }

    suspend fun getPinnedTowerEntities(): List<TowerCacheEntity> {
        return withContext(Dispatchers.IO) { towerCacheDao.getPinnedTowers() }
    }
}
