package com.celltowerid.android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.celltowerid.android.data.AppDatabase
import com.celltowerid.android.data.entity.SessionEntity
import com.celltowerid.android.repository.MeasurementRepository
import com.celltowerid.android.repository.SessionRepository
import com.celltowerid.android.service.CollectionService
import kotlinx.coroutines.launch

class CollectionViewModel @JvmOverloads constructor(
    application: Application,
    sessionRepo: SessionRepository? = null,
    measurementRepo: MeasurementRepository? = null
) : AndroidViewModel(application) {
    private val sessionRepo: SessionRepository = sessionRepo ?: run {
        val db = AppDatabase.getInstance(application)
        SessionRepository(db.sessionDao())
    }
    private val measurementRepo: MeasurementRepository = measurementRepo ?: run {
        val db = AppDatabase.getInstance(application)
        MeasurementRepository(db.measurementDao())
    }

    private val _isCollecting = MutableLiveData(false)
    val isCollecting: LiveData<Boolean> = _isCollecting

    private val _collectionInterval = MutableLiveData(CollectionService.DEFAULT_INTERVAL_MS)
    val collectionInterval: LiveData<Long> = _collectionInterval

    private val _totalMeasurements = MutableLiveData(0)
    val totalMeasurements: LiveData<Int> = _totalMeasurements

    private val _sessions = MutableLiveData<List<SessionEntity>>()
    val sessions: LiveData<List<SessionEntity>> = _sessions

    fun setCollecting(collecting: Boolean) {
        _isCollecting.postValue(collecting)
    }

    fun setInterval(intervalMs: Long) {
        _collectionInterval.value = intervalMs
    }

    fun loadStats() {
        viewModelScope.launch {
            _totalMeasurements.postValue(measurementRepo.getMeasurementCount())
            _sessions.postValue(sessionRepo.getAllSessions())
        }
    }
}
