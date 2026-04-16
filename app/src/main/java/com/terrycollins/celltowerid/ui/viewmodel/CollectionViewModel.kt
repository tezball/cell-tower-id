package com.terrycollins.celltowerid.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.terrycollins.celltowerid.data.AppDatabase
import com.terrycollins.celltowerid.data.entity.SessionEntity
import com.terrycollins.celltowerid.repository.MeasurementRepository
import com.terrycollins.celltowerid.repository.SessionRepository
import kotlinx.coroutines.launch

class CollectionViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val sessionRepo = SessionRepository(db.sessionDao())
    private val measurementRepo = MeasurementRepository(db.measurementDao())

    private val _isCollecting = MutableLiveData(false)
    val isCollecting: LiveData<Boolean> = _isCollecting

    private val _collectionInterval = MutableLiveData(5000L)
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
