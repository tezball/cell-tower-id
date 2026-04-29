package com.celltowerid.android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.celltowerid.android.data.AppDatabase
import com.celltowerid.android.data.mapper.EntityMapper
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.CellTower
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.repository.TowerCacheRepository
import com.celltowerid.android.util.MeasurementCoalescer
import com.celltowerid.android.util.TowerLocator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TowerDetailViewModel @JvmOverloads constructor(
    application: Application,
    private val measurementDao: com.celltowerid.android.data.dao.MeasurementDao? = null,
    private val towerCacheRepo: TowerCacheRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    data class TowerDetailState(
        val history: List<CellMeasurement> = emptyList(),
        val towerLat: Double? = null,
        val towerLon: Double? = null,
        val distanceMeters: Double? = null,
        val allPoints: List<CellMeasurement> = emptyList(),
        val coalesced: CellMeasurement? = null
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
            val history = withContext(ioDispatcher) {
                dao.getMeasurementsByCell(mcc, mnc, tacLac, cid)
                    .map { EntityMapper.toDomain(it) }
                    .sortedByDescending { it.timestamp }
                    .take(50)
            }

            val cachedTower = cacheRepo.findTower(current.radio.name, mcc, mnc, tacLac, cid)

            val allPoints = history + current
            val towerLat: Double?
            val towerLon: Double?

            val cachedLat = cachedTower?.latitude
            val cachedLon = cachedTower?.longitude
            if (cachedLat != null && cachedLon != null) {
                towerLat = cachedLat
                towerLon = cachedLon
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
                    withContext(ioDispatcher) { cacheRepo.learnPosition(learned) }
                }
            }

            val coalesced = MeasurementCoalescer.coalesce(current, history)

            val distanceM = coalesced.timingAdvance?.let {
                if (current.radio == RadioType.LTE && it > 0) it * 78.12 else null
            }

            _state.postValue(
                TowerDetailState(
                    history = history,
                    towerLat = towerLat,
                    towerLon = towerLon,
                    distanceMeters = distanceM,
                    allPoints = allPoints,
                    coalesced = coalesced
                )
            )
        }
    }
}
