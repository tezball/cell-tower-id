package com.celltowerid.android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.celltowerid.android.data.AppDatabase
import com.celltowerid.android.domain.model.AnomalyEvent
import com.celltowerid.android.domain.model.AnomalySeverity
import com.celltowerid.android.repository.AnomalyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AnomalyViewModel @JvmOverloads constructor(
    application: Application,
    anomalyRepo: AnomalyRepository? = null
) : AndroidViewModel(application) {

    private val anomalyRepo: AnomalyRepository = anomalyRepo ?: run {
        val db = AppDatabase.getInstance(application)
        AnomalyRepository(db.anomalyDao())
    }

    private val _anomalies = MutableLiveData<List<AnomalyEvent>>()
    val anomalies: LiveData<List<AnomalyEvent>> = _anomalies

    private val _undismissedCount = MutableLiveData(0)
    val undismissedCount: LiveData<Int> = _undismissedCount

    // Default: show HIGH only. User toggles MEDIUM / LOW chips to widen.
    private val severityFilter: MutableSet<AnomalySeverity> =
        mutableSetOf(AnomalySeverity.HIGH)

    private var unfiltered: List<AnomalyEvent> = emptyList()

    @Volatile
    private var showingAll: Boolean = false
    private var refreshJob: Job? = null

    fun loadAnomalies() {
        showingAll = false
        viewModelScope.launch {
            val all = anomalyRepo.getUndismissedAnomalies()
            unfiltered = all
            _anomalies.postValue(applyFilter(all))
            _undismissedCount.postValue(all.size)
        }
    }

    fun loadAllAnomalies() {
        showingAll = true
        viewModelScope.launch {
            val all = anomalyRepo.getAllAnomalies()
            unfiltered = all
            _anomalies.postValue(applyFilter(all))
        }
    }

    private fun reload() {
        if (showingAll) loadAllAnomalies() else loadAnomalies()
    }

    fun dismiss(id: Long) {
        viewModelScope.launch {
            anomalyRepo.dismiss(id)
            reload()
        }
    }

    fun undismiss(id: Long) {
        viewModelScope.launch {
            anomalyRepo.undismiss(id)
            reload()
        }
    }

    fun setSeverityEnabled(severity: AnomalySeverity, enabled: Boolean) {
        if (enabled) severityFilter.add(severity) else severityFilter.remove(severity)
        _anomalies.postValue(applyFilter(unfiltered))
    }

    fun isSeverityEnabled(severity: AnomalySeverity): Boolean =
        severity in severityFilter

    private fun applyFilter(list: List<AnomalyEvent>): List<AnomalyEvent> =
        AnomalyViewModelSort.sortForDisplay(list.filter { it.severity in severityFilter })

    fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                reload()
            }
        }
    }

    fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun dismissAll() {
        viewModelScope.launch {
            anomalyRepo.dismissAll()
            loadAnomalies()
        }
    }
}
