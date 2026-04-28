package com.celltowerid.android.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.service.RealCellInfoProvider
import com.celltowerid.android.util.AppLog
import com.celltowerid.android.util.LocateMath
import com.celltowerid.android.util.LocateMath.Waypoint
import com.celltowerid.android.util.TowerLocator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocateViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // ~2m moved before we record a new waypoint; otherwise the current one
        // is updated in place with a running average. Keeps waypoint density
        // sane while standing still.
        internal const val NEW_WAYPOINT_THRESHOLD_M = 2.0

        internal fun appendWaypoint(current: List<Waypoint>, next: Waypoint): List<Waypoint> {
            val last = current.lastOrNull()
            if (last != null) {
                val dLat = (next.lat - last.lat) * 111_000.0
                val dLon = (next.lon - last.lon) * 111_000.0 *
                    Math.cos(Math.toRadians(next.lat))
                if (Math.hypot(dLat, dLon) < NEW_WAYPOINT_THRESHOLD_M) {
                    val updated = last.copy(rsrpDbm = ((last.rsrpDbm + next.rsrpDbm) / 2))
                    return current.dropLast(1) + updated
                }
            }
            return current + next
        }

        internal fun matchesTarget(
            m: CellMeasurement,
            targetRadio: RadioType,
            targetMcc: Int?,
            targetMnc: Int?,
            targetTac: Int?,
            targetCid: Long?
        ): Boolean {
            if (m.radio != targetRadio) return false
            if (targetMcc != null && m.mcc != targetMcc) return false
            if (targetMnc != null && m.mnc != targetMnc) return false
            if (targetTac != null && m.tacLac != targetTac) return false
            if (targetCid != null && m.cid != targetCid) return false
            return m.isRegistered
        }
    }

    data class LocateState(
        val rawRsrp: Int? = null,
        val smoothedRsrp: Double? = null,
        val deltaDb: Double = 0.0,
        val waypoints: List<Waypoint> = emptyList(),
        val bearing: Double? = null,
        val estimatedTower: Pair<Double, Double>? = null,
        val distanceMeters: Double? = null,
        val lostContact: Boolean = false,
        val currentLocation: Pair<Double, Double>? = null
    )

    private val provider = RealCellInfoProvider(application)
    private val fusedLocation: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val _state = MutableLiveData(LocateState())
    val state: LiveData<LocateState> = _state

    private var targetRadio: RadioType = RadioType.UNKNOWN
    private var targetMcc: Int? = null
    private var targetMnc: Int? = null
    private var targetTac: Int? = null
    private var targetCid: Long? = null

    private var samplingJob: Job? = null
    private var lastLocation: Location? = null
    private var historyRsrpForDelta: ArrayDeque<Pair<Long, Double>> = ArrayDeque()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { lastLocation = it }
        }
    }

    fun start(
        radio: RadioType,
        mcc: Int?,
        mnc: Int?,
        tac: Int?,
        cid: Long?
    ) {
        targetRadio = radio
        targetMcc = mcc
        targetMnc = mnc
        targetTac = tac
        targetCid = cid
        startLocationUpdates()
        startSampling()
    }

    private fun startLocationUpdates() {
        val ctx = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        try {
            fusedLocation.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            fusedLocation.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && lastLocation == null) lastLocation = loc
            }
        } catch (e: SecurityException) {
            AppLog.e("LocateViewModel", "requestLocationUpdates denied", e)
        }
    }

    private fun startSampling() {
        samplingJob?.cancel()
        samplingJob = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(1000L)
            }
        }
    }

    private fun tick() {
        val loc = lastLocation ?: return
        val measurements = provider.getCellMeasurements(
            latitude = loc.latitude,
            longitude = loc.longitude,
            gpsAccuracy = loc.accuracy
        )
        val target = measurements.firstOrNull {
            matchesTarget(it, targetRadio, targetMcc, targetMnc, targetTac, targetCid)
        }
        val prevState = _state.value ?: LocateState()

        val rawRsrp = target?.rsrp
        if (rawRsrp == null) {
            _state.postValue(
                prevState.copy(
                    lostContact = true,
                    currentLocation = Pair(loc.latitude, loc.longitude)
                )
            )
            return
        }

        val smoothed = prevState.smoothedRsrp?.let { LocateMath.ema(it, rawRsrp.toDouble()) }
            ?: rawRsrp.toDouble()

        val now = System.currentTimeMillis()
        historyRsrpForDelta.addLast(now to smoothed)
        while (historyRsrpForDelta.isNotEmpty() && now - historyRsrpForDelta.first().first > 10_000L) {
            historyRsrpForDelta.removeFirst()
        }
        val delta = smoothed - historyRsrpForDelta.first().second

        val nextWaypoints = appendWaypoint(
            prevState.waypoints,
            Waypoint(loc.latitude, loc.longitude, rawRsrp)
        )
        val bearing = LocateMath.gradientBearing(nextWaypoints.takeLast(20))
        val tower = TowerLocator.estimate(
            nextWaypoints.map { wp ->
                CellMeasurement(
                    timestamp = now,
                    latitude = wp.lat,
                    longitude = wp.lon,
                    radio = targetRadio,
                    rsrp = wp.rsrpDbm,
                    isRegistered = true
                )
            }
        )
        val distance = LocateMath.rsrpToDistanceMeters(rawRsrp)

        _state.postValue(
            LocateState(
                rawRsrp = rawRsrp,
                smoothedRsrp = smoothed,
                deltaDb = delta,
                waypoints = nextWaypoints,
                bearing = bearing,
                estimatedTower = tower,
                distanceMeters = distance,
                lostContact = false,
                currentLocation = Pair(loc.latitude, loc.longitude)
            )
        )
    }

    fun reset() {
        historyRsrpForDelta.clear()
        _state.postValue(
            (_state.value ?: LocateState()).copy(
                waypoints = emptyList(),
                smoothedRsrp = null,
                bearing = null,
                estimatedTower = null,
                deltaDb = 0.0
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        samplingJob?.cancel()
        try {
            fusedLocation.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            AppLog.e("LocateViewModel", "removeLocationUpdates failed", e)
        }
    }
}
