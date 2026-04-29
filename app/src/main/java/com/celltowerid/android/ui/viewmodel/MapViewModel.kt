package com.celltowerid.android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.celltowerid.android.data.AppDatabase
import com.celltowerid.android.data.entity.TowerCacheEntity
import com.celltowerid.android.domain.model.CellKey
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.CellTower
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.repository.MeasurementRepository
import com.celltowerid.android.repository.TowerCacheRepository
import com.celltowerid.android.util.AppLog
import com.celltowerid.android.util.UsCarriers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MapViewModel @JvmOverloads constructor(
    application: Application,
    measurementRepo: MeasurementRepository? = null,
    towerCacheRepo: TowerCacheRepository? = null
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CellTowerID.MapViewModel"
        private const val MAX_VIEWPORT_MEASUREMENTS = 2000
        private const val AUTO_REFRESH_INTERVAL_MS = 15_000L
    }

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

    private val _filteredTowers = MutableLiveData<List<CellTower>>(emptyList())
    val filteredTowers: LiveData<List<CellTower>> = _filteredTowers

    private val _bestReadings = MutableLiveData<Map<CellKey, CellMeasurement>>()
    val bestReadings: LiveData<Map<CellKey, CellMeasurement>> = _bestReadings

    /**
     * Reactive stream of pinned-tower keys. MapFragment observes this and
     * calls [loadAllTowers] on change so pin/unpin actions land on the map
     * without waiting for the 15 s auto-refresh tick.
     */
    fun pinnedTowerEntities(): LiveData<List<TowerCacheEntity>> =
        towerCacheRepo.getPinnedTowerEntitiesLive()

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
            val start = System.nanoTime()
            val all = measurementRepo.getMeasurementsInArea(minLat, maxLat, minLon, maxLon)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            val capped = if (all.size > MAX_VIEWPORT_MEASUREMENTS) all.take(MAX_VIEWPORT_MEASUREMENTS) else all
            AppLog.d(TAG, "loadMeasurementsInArea: n=${all.size} capped=${capped.size} took=${elapsed}ms")
            unfilteredMeasurements = capped
            _measurements.postValue(applyFilters(capped))
        }
    }

    fun loadRecentMeasurements(limit: Int = 500) {
        viewModelScope.launch {
            val start = System.nanoTime()
            val all = measurementRepo.getRecentMeasurements(limit)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            AppLog.d(TAG, "loadRecentMeasurements: n=${all.size} took=${elapsed}ms")
            unfilteredMeasurements = all
            _measurements.postValue(applyFilters(all))
        }
    }

    fun loadAllTowers() {
        viewModelScope.launch {
            val start = System.nanoTime()
            val towers = towerCacheRepo.getTowersInArea(-90.0, 90.0, -180.0, 180.0)
            val best = measurementRepo.getBestMeasurementsByCellInArea(-90.0, 90.0, -180.0, 180.0)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            AppLog.d(TAG, "loadAllTowers: n=${towers.size} best=${best.size} took=${elapsed}ms")
            _towers.postValue(towers)
            _bestReadings.postValue(best)
            _filteredTowers.postValue(applyTowerFilters(towers))
        }
    }

    fun unpinTower(radio: RadioType, mcc: Int, mnc: Int, tacLac: Int, cid: Long) {
        viewModelScope.launch {
            towerCacheRepo.unpinTower(radio, mcc, mnc, tacLac, cid)
            loadAllTowers()
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
        _filteredTowers.postValue(applyTowerFilters(_towers.value ?: emptyList()))
    }

    fun startAutoRefresh() {
        AppLog.d(TAG, "startAutoRefresh")
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                tick++
                AppLog.d(TAG, "autoRefresh tick $tick: hasBounds=$hasBounds")
                if (hasBounds) {
                    loadMeasurementsInArea(lastMinLat, lastMaxLat, lastMinLon, lastMaxLon)
                } else {
                    loadRecentMeasurements()
                }
                loadAllTowers()
            }
        }
    }

    fun stopAutoRefresh() {
        AppLog.d(TAG, "stopAutoRefresh")
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

    internal fun applyTowerFilters(towers: List<CellTower>): List<CellTower> {
        val radio = _filterRadioType.value
        val carrier = _filterCarrier.value
        return towers.filter { t ->
            (radio == null || t.radio == radio) &&
                (carrier == null || UsCarriers.getCarrierName(t.mcc, t.mnc) == carrier)
        }
    }
}
