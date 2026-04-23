package com.terrycollins.celltowerid.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.terrycollins.celltowerid.data.AppDatabase
import com.terrycollins.celltowerid.data.entity.TowerCacheEntity
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.RadioType
import com.terrycollins.celltowerid.repository.MeasurementRepository
import com.terrycollins.celltowerid.repository.TowerCacheRepository
import com.terrycollins.celltowerid.util.Event
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CellListViewModel @JvmOverloads constructor(
    application: Application,
    measurementRepo: MeasurementRepository? = null,
    towerCacheRepo: TowerCacheRepository? = null
) : AndroidViewModel(application) {

    companion object {
        // All rows from one scan share a single System.currentTimeMillis() snapshot; 1 s
        // comfortably captures one scan cycle regardless of insert ordering.
        const val SCAN_EPSILON_MS = 1_000L
        // Safety floor above CollectionPowerPolicy's 120 s adaptive cap, so cadence slowdowns
        // never starve the list, but a stopped collection clears after ~3 min.
        const val MAX_STALE_AGE_MS = 180_000L
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

    private val _currentCells = MutableLiveData<List<CellMeasurement>>()
    val currentCells: LiveData<List<CellMeasurement>> = _currentCells

    private val _filterRadioType = MutableLiveData<RadioType?>(null)
    val filterRadioType: LiveData<RadioType?> = _filterRadioType

    val pinnedTowerKeys: LiveData<Set<String>> =
        this.towerCacheRepo.getPinnedTowerEntitiesLive().map { list ->
            list.mapTo(mutableSetOf()) { "${it.radio}-${it.mcc}-${it.mnc}-${it.tacLac}-${it.cid}" }
        }

    private val _pinSnackbar = MutableLiveData<Event<String>>()
    val pinSnackbar: LiveData<Event<String>> = _pinSnackbar

    fun updateCells(measurements: List<CellMeasurement>) {
        val filtered = _filterRadioType.value?.let { radio ->
            measurements.filter { it.radio == radio }
        } ?: measurements

        // Deduplicate: keep most recent measurement per unique cell (radio+mcc+mnc+tac+cid)
        val deduplicated = filtered
            .groupBy { cellKey(it) }
            .map { (_, group) -> group.maxByOrNull { it.timestamp }!! }

        // Sort: registered first, then by signal strength descending
        val sorted = deduplicated.sortedWith(
            compareByDescending<CellMeasurement> { it.isRegistered }
                .thenByDescending { it.rsrp ?: it.rssi ?: Int.MIN_VALUE }
        )
        _currentCells.postValue(sorted)
    }

    private fun cellKey(m: CellMeasurement): String {
        return "${m.radio}-${m.mcc}-${m.mnc}-${m.tacLac}-${m.cid}"
    }

    fun setRadioTypeFilter(type: RadioType?) {
        _filterRadioType.value = type
    }

    fun togglePin(cell: CellMeasurement) {
        val mcc = cell.mcc
        val mnc = cell.mnc
        val tac = cell.tacLac
        val cid = cell.cid
        if (mcc == null || mnc == null || tac == null || cid == null) {
            _pinSnackbar.postValue(Event("Cell identity incomplete — cannot pin"))
            return
        }
        val key = "${cell.radio}-$mcc-$mnc-$tac-$cid"
        val alreadyPinned = pinnedTowerKeys.value.orEmpty().contains(key)
        viewModelScope.launch {
            if (alreadyPinned) {
                towerCacheRepo.unpinTower(cell.radio, mcc, mnc, tac, cid)
            } else {
                val ok = towerCacheRepo.pinTower(
                    cell.radio, mcc, mnc, tac, cid,
                    fallbackLat = cell.latitude,
                    fallbackLon = cell.longitude,
                    fallbackPci = cell.pciPsc
                )
                if (!ok) _pinSnackbar.postValue(Event("Could not pin tower"))
            }
            // Refresh now so the change is visible without waiting for the 5 s auto-refresh.
            loadRecentCells()
        }
    }

    private var refreshJob: Job? = null

    fun loadRecentCells() {
        viewModelScope.launch {
            val sinceMs = System.currentTimeMillis() - MAX_STALE_AGE_MS
            val fresh = measurementRepo.getMeasurementsFromLatestScan(sinceMs, SCAN_EPSILON_MS)
            val pinned = towerCacheRepo.getPinnedTowerEntities()
            updateCells(mergeWithPinned(fresh, pinned))
        }
    }

    private fun mergeWithPinned(
        fresh: List<CellMeasurement>,
        pinned: List<TowerCacheEntity>
    ): List<CellMeasurement> {
        if (pinned.isEmpty()) return fresh
        val freshKeys = fresh.mapTo(mutableSetOf()) { cellKey(it) }
        val stubs = pinned.mapNotNull { entity ->
            val stub = pinnedEntityToStub(entity) ?: return@mapNotNull null
            if (cellKey(stub) in freshKeys) null else stub
        }
        return fresh + stubs
    }

    private fun pinnedEntityToStub(entity: TowerCacheEntity): CellMeasurement? {
        val radio = runCatching { RadioType.valueOf(entity.radio) }.getOrNull()
            ?: return null
        return CellMeasurement(
            timestamp = entity.lastUpdated ?: 0L,
            latitude = entity.latitude ?: 0.0,
            longitude = entity.longitude ?: 0.0,
            radio = radio,
            mcc = entity.mcc,
            mnc = entity.mnc,
            tacLac = entity.tacLac,
            cid = entity.cid,
            pciPsc = entity.pci,
            isRegistered = false
        )
    }

    fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                loadRecentCells()
            }
        }
    }

    fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }
}
