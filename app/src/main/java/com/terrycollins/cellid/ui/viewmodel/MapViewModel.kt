package com.terrycollins.cellid.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.terrycollins.cellid.data.AppDatabase
import com.terrycollins.cellid.domain.model.CellMeasurement
import com.terrycollins.cellid.domain.model.CellTower
import com.terrycollins.cellid.domain.model.RadioType
import com.terrycollins.cellid.repository.MeasurementRepository
import com.terrycollins.cellid.repository.TowerCacheRepository
import com.terrycollins.cellid.util.UsCarriers
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

    // Store unfiltered measurements so we can re-apply filters without re-fetching
    private var unfilteredMeasurements: List<CellMeasurement> = emptyList()

    fun loadMeasurementsInArea(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
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
