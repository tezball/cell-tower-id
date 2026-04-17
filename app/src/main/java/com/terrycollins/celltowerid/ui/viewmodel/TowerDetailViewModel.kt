package com.terrycollins.celltowerid.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.terrycollins.celltowerid.data.AppDatabase
import com.terrycollins.celltowerid.data.mapper.EntityMapper
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.CellTower
import com.terrycollins.celltowerid.domain.model.RadioType
import com.terrycollins.celltowerid.repository.TowerCacheRepository
import com.terrycollins.celltowerid.util.TowerLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TowerDetailViewModel @JvmOverloads constructor(
    application: Application,
    private val measurementDao: com.terrycollins.celltowerid.data.dao.MeasurementDao? = null,
    private val towerCacheRepo: TowerCacheRepository? = null
) : AndroidViewModel(application) {

    data class TowerDetailState(
        val history: List<CellMeasurement> = emptyList(),
        val towerLat: Double? = null,
        val towerLon: Double? = null,
        val distanceMeters: Double? = null,
        val allPoints: List<CellMeasurement> = emptyList()
    )

    private val _state = MutableLiveData<TowerDetailState>()
    val state: LiveData<TowerDetailState> = _state

    private val db by lazy { AppDatabase.getInstance(getApplication()) }
    private val dao get() = measurementDao ?: db.measurementDao()
    private val cacheRepo get() = towerCacheRepo ?: TowerCacheRepository(db.towerCacheDao())

    fun loadHistory(current: CellMeasurement) {
        val mcc = current.mcc ?: return
        val mnc = current.mnc ?: return
        val tacLac = current.tacLac ?: return
        val cid = current.cid ?: return

        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) {
                dao.getMeasurementsByCell(mcc, mnc, tacLac, cid)
                    .map { EntityMapper.toDomain(it) }
                    .sortedByDescending { it.timestamp }
                    .take(50)
            }

            val cachedTower = withContext(Dispatchers.IO) {
                db.towerCacheDao().findTower(current.radio.name, mcc, mnc, tacLac, cid)
            }

            val allPoints = history + current
            val towerLat: Double?
            val towerLon: Double?

            if (cachedTower?.latitude != null && cachedTower.longitude != null) {
                towerLat = cachedTower.latitude
                towerLon = cachedTower.longitude
            } else {
                val estimated = TowerLocator.estimate(allPoints)
                towerLat = estimated?.first
                towerLon = estimated?.second

                if (estimated != null && cachedTower == null && allPoints.size >= 5) {
                    val learned = CellTower(
                        radio = current.radio,
                        mcc = mcc,
                        mnc = mnc,
                        tacLac = tacLac,
                        cid = cid,
                        latitude = estimated.first,
                        longitude = estimated.second,
                        samples = allPoints.size,
                        source = "learned"
                    )
                    withContext(Dispatchers.IO) { cacheRepo.learnPosition(learned) }
                }
            }

            val distanceM = current.timingAdvance?.let {
                if (current.radio == RadioType.LTE && it > 0) it * 78.12 else null
            }

            _state.postValue(
                TowerDetailState(
                    history = history,
                    towerLat = towerLat,
                    towerLon = towerLon,
                    distanceMeters = distanceM,
                    allPoints = allPoints
                )
            )
        }
    }
}
