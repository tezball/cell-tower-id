package com.terrycollins.celltowerid.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.terrycollins.celltowerid.data.AppDatabase
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.CellTower
import com.terrycollins.celltowerid.domain.model.RadioType
import com.terrycollins.celltowerid.repository.MeasurementRepository
import com.terrycollins.celltowerid.repository.TowerCacheRepository
import com.terrycollins.celltowerid.util.UsCarriers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MapViewModel @JvmOverloads constructor(
    application: Application,
    measurementRepo: MeasurementRepository? = null,
    towerCacheRepo: TowerCacheRepository? = null
) : AndroidViewModel(application) {

    private val measurementRepo: MeasurementRepository
    private val towerCacheRepo: TowerCacheRepository

    init {
        if (measurementRepo != null && towerCacheRepo != null) {
            this.measurementRepo = measurementRepo
            this.towerCacheRepo = towerCacheRepo
        } else {
            val db = AppDatabase.getInstance(application)
            this.measurementRepo = measurementRepo ?: MeasurementRepository(db.measurementDao())
            this.towerCacheRepo = towerCacheRepo ?: TowerCacheRepository(db.towerCacheDao())
        }
    }

    private val _measurements = MutableLiveData<List<CellMeasurement>>()
    val measurements: LiveData<List<CellMeasurement>> = _measurements

    private val _towers = MutableLiveData<List<CellTower>>()
    val towers: LiveData<List<CellTower>> = _towers

    private val _filterRadioType = MutableLiveData<RadioType?>(null)
    val filterRadioType: LiveData<RadioType?> = _filterRadioType

    private val _filterCarrier = MutableLiveData<String?>(null)
    val filterCarrier: LiveData<String?> = _filterCarrier

    private var unfilteredMeasurements: List<CellMeasurement> = emptyList()
    private var refreshJob: Job? = null
    private var lastMinLat = 0.0
    private var lastMaxLat = 0.0
    private var lastMinLon = 0.0
    private var lastMaxLon = 0.0
    private var hasBounds = false

    fun loadMeasurementsInArea(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        lastMinLat = minLat
        lastMaxLat = maxLat
        lastMinLon = minLon
        lastMaxLon = maxLon
        hasBounds = true
        viewModelScope.launch {
            val all = measurementRepo.getMeasurementsInArea(minLat, maxLat, minLon, maxLon)
            unfilteredMeasurements = all
            _measurements.postValue(applyFilters(all))

            val cachedTowers = towerCacheRepo.getTowersInArea(minLat, maxLat, minLon, maxLon)
            _towers.postValue(cachedTowers)
        }
    }

    fun loadRecentMeasurements(limit: Int = 500) {
        viewModelScope.launch {
            val all = measurementRepo.getRecentMeasurements(limit)
            unfilteredMeasurements = all
            _measurements.postValue(applyFilters(all))
        }
    }

    fun loadAllTowers() {
        viewModelScope.launch {
            val towers = towerCacheRepo.getTowersInArea(-90.0, 90.0, -180.0, 180.0)
            _towers.postValue(towers)
        }
    }

    fun setRadioTypeFilter(type: RadioType?) {
        _filterRadioType.value = type
        refreshFilters()
    }

    fun setCarrierFilter(carrier: String?) {
        _filterCarrier.value = carrier
        refreshFilters()
    }

    private fun refreshFilters() {
        _measurements.postValue(applyFilters(unfilteredMeasurements))
    }

    fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                if (hasBounds) {
                    loadMeasurementsInArea(lastMinLat, lastMaxLat, lastMinLon, lastMaxLon)
                } else {
                    loadRecentMeasurements()
                }
            }
        }
    }

    fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    internal fun applyFilters(measurements: List<CellMeasurement>): List<CellMeasurement> {
        var filtered = measurements
        _filterRadioType.value?.let { radio ->
            filtered = filtered.filter { it.radio == radio }
        }
        _filterCarrier.value?.let { carrier ->
            filtered = filtered.filter { m ->
                m.mcc != null && m.mnc != null &&
                    UsCarriers.getCarrierName(m.mcc, m.mnc) == carrier
            }
        }
        return filtered
    }
}
