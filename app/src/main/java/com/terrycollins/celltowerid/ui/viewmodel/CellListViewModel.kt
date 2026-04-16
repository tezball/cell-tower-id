package com.terrycollins.celltowerid.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.terrycollins.celltowerid.data.AppDatabase
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.RadioType
import com.terrycollins.celltowerid.repository.MeasurementRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CellListViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val measurementRepo = MeasurementRepository(db.measurementDao())

    private val _currentCells = MutableLiveData<List<CellMeasurement>>()
    val currentCells: LiveData<List<CellMeasurement>> = _currentCells

    private val _filterRadioType = MutableLiveData<RadioType?>(null)
    val filterRadioType: LiveData<RadioType?> = _filterRadioType

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

    private var refreshJob: Job? = null

    fun loadRecentCells() {
        viewModelScope.launch {
            val recent = measurementRepo.getRecentMeasurements(100)
            updateCells(recent)
        }
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
