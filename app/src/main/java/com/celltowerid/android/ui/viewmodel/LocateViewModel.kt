package com.celltowerid.android.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
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
import com.celltowerid.android.util.DrivingDetector
import com.celltowerid.android.util.LocateMath
import com.celltowerid.android.util.LocateMath.Waypoint
import com.celltowerid.android.util.LocateMode
import com.celltowerid.android.util.LocateModeConfig
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

    enum class BearingSource { GRADIENT, TOWER_ESTIMATE, STALE, NONE }

    data class BearingResolution(
        val bearing: Double?,
        val source: BearingSource,
        val isFresh: Boolean,
    )

    companion object {

        internal fun appendWaypoint(
            current: List<Waypoint>,
            next: Waypoint,
            minDistanceM: Double,
        ): List<Waypoint> {
            val last = current.lastOrNull()
            if (last != null) {
                val dLat = (next.lat - last.lat) * 111_000.0
                val dLon = (next.lon - last.lon) * 111_000.0 *
                    Math.cos(Math.toRadians(next.lat))
                if (Math.hypot(dLat, dLon) < minDistanceM) {
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

        /** Manual override (if set) wins over the auto detector. */
        internal fun effectiveMode(auto: LocateMode, manualOverride: LocateMode?): LocateMode =
            manualOverride ?: auto

        /**
         * Picks the best bearing for the UI:
         *
         *   1. fresh gradient (the ideal — direction of improving signal)
         *   2. derived bearing from current location → estimated tower position
         *      (radar feel: rotate with you even when stationary)
         *   3. last-known bearing held over from a previous tick (stale)
         *   4. nothing (UI shows the progress hint)
         */
        internal fun resolveBearing(
            gradientBearing: Double?,
            estimatedTower: Pair<Double, Double>?,
            currentLocation: Pair<Double, Double>?,
            lastResolved: Double?,
        ): BearingResolution {
            if (gradientBearing != null) {
                return BearingResolution(gradientBearing, BearingSource.GRADIENT, isFresh = true)
            }
            if (estimatedTower != null && currentLocation != null) {
                val (towerLat, towerLon) = estimatedTower
                val (myLat, myLon) = currentLocation
                val brg = LocateMath.bearingDegrees(myLat, myLon, towerLat, towerLon)
                return BearingResolution(brg, BearingSource.TOWER_ESTIMATE, isFresh = true)
            }
            if (lastResolved != null) {
                return BearingResolution(lastResolved, BearingSource.STALE, isFresh = false)
            }
            return BearingResolution(null, BearingSource.NONE, isFresh = false)
        }
    }

    data class LocateState(
        val rawRsrp: Int? = null,
        val smoothedRsrp: Double? = null,
        val deltaDb: Double = 0.0,
        val waypoints: List<Waypoint> = emptyList(),
        val bearing: Double? = null,
        val bearingSource: BearingSource = BearingSource.NONE,
        val bearingFresh: Boolean = false,
        val estimatedTower: Pair<Double, Double>? = null,
        val distanceMeters: Double? = null,
        val lostContact: Boolean = false,
        val currentLocation: Pair<Double, Double>? = null,
        val autoMode: LocateMode = LocateMode.WALKING,
        val manualOverride: LocateMode? = null,
        val effectiveMode: LocateMode = LocateMode.WALKING,
        val waypointsForResolve: Int = 0,
    )

    private val provider = RealCellInfoProvider(application)
    private val fusedLocation: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)
    private val drivingDetector = DrivingDetector()

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
    private var lastResolvedBearing: Double? = null
    private var locationRequestIntervalMs: Long = -1L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                lastLocation = loc
                val accuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && loc.hasSpeedAccuracy()) {
                    loc.speedAccuracyMetersPerSecond
                } else null
                val speed = if (loc.hasSpeed()) loc.speed else null
                drivingDetector.update(speed, accuracy, loc.elapsedRealtimeNanos / 1_000_000L)
            }
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
        applyLocationRequestForMode(LocateModeConfig.forMode(currentEffectiveMode()))
        startSampling()
    }

    /**
     * `null` clears the manual override (returns to auto).
     */
    fun setManualOverride(mode: LocateMode?) {
        val current = _state.value ?: LocateState()
        if (current.manualOverride == mode) return
        val effective = effectiveMode(current.autoMode, mode)
        _state.postValue(current.copy(manualOverride = mode, effectiveMode = effective))
        applyLocationRequestForMode(LocateModeConfig.forMode(effective))
    }

    /** Cycles Auto -> Manual WALKING -> Manual DRIVING -> Auto. */
    fun cycleManualOverride() {
        val current = _state.value?.manualOverride
        val next = when (current) {
            null -> LocateMode.WALKING
            LocateMode.WALKING -> LocateMode.DRIVING
            LocateMode.DRIVING -> null
        }
        setManualOverride(next)
    }

    private fun currentEffectiveMode(): LocateMode {
        val s = _state.value ?: return LocateMode.WALKING
        return effectiveMode(s.autoMode, s.manualOverride)
    }

    private fun applyLocationRequestForMode(config: LocateModeConfig) {
        val ctx = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        if (config.locationIntervalMs == locationRequestIntervalMs) return
        locationRequestIntervalMs = config.locationIntervalMs
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, config.locationIntervalMs)
            .setMinUpdateIntervalMillis(config.locationMinIntervalMs)
            .build()
        try {
            fusedLocation.removeLocationUpdates(locationCallback)
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
                val intervalMs = LocateModeConfig.forMode(currentEffectiveMode()).sampleIntervalMs
                delay(intervalMs)
            }
        }
    }

    private suspend fun tick() {
        val loc = lastLocation ?: return
        val auto = drivingDetector.mode
        val prevState = _state.value ?: LocateState()
        val effective = effectiveMode(auto, prevState.manualOverride)
        val config = LocateModeConfig.forMode(effective)

        // If the auto detector flipped, the location request may need re-tuning.
        if (auto != prevState.autoMode) {
            applyLocationRequestForMode(config)
        }

        val measurements = provider.getCellMeasurementsFresh(
            latitude = loc.latitude,
            longitude = loc.longitude,
            gpsAccuracy = loc.accuracy,
            speedMps = if (loc.hasSpeed()) loc.speed else null,
        )
        val target = measurements.firstOrNull {
            matchesTarget(it, targetRadio, targetMcc, targetMnc, targetTac, targetCid)
        }

        val rawRsrp = target?.rsrp
        if (rawRsrp == null) {
            _state.postValue(
                prevState.copy(
                    lostContact = true,
                    autoMode = auto,
                    effectiveMode = effective,
                    currentLocation = Pair(loc.latitude, loc.longitude),
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
            Waypoint(loc.latitude, loc.longitude, rawRsrp),
            minDistanceM = config.waypointMinDistanceM,
        )
        val gradient = LocateMath.gradientBearing(
            waypoints = nextWaypoints.takeLast(config.gradientWindowSize),
            minTotalAbsDb = config.gradientMinTotalAbsDb,
            minResultantMagnitude = config.gradientMinResultantMagnitude,
        )
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
        val currentLocation = Pair(loc.latitude, loc.longitude)
        val resolution = resolveBearing(
            gradientBearing = gradient,
            estimatedTower = tower,
            currentLocation = currentLocation,
            lastResolved = lastResolvedBearing,
        )
        if (resolution.isFresh && resolution.bearing != null) {
            lastResolvedBearing = resolution.bearing
        }

        // "Walk N more waypoints" hint counter — only meaningful when bearing is unresolved.
        val waypointsForResolve = if (resolution.source == BearingSource.NONE) {
            (config.gradientWindowSize / 4) - nextWaypoints.size
        } else 0

        _state.postValue(
            LocateState(
                rawRsrp = rawRsrp,
                smoothedRsrp = smoothed,
                deltaDb = delta,
                waypoints = nextWaypoints,
                bearing = resolution.bearing,
                bearingSource = resolution.source,
                bearingFresh = resolution.isFresh,
                estimatedTower = tower,
                distanceMeters = distance,
                lostContact = false,
                currentLocation = currentLocation,
                autoMode = auto,
                manualOverride = prevState.manualOverride,
                effectiveMode = effective,
                waypointsForResolve = waypointsForResolve.coerceAtLeast(0),
            )
        )
    }

    fun reset() {
        historyRsrpForDelta.clear()
        lastResolvedBearing = null
        val prev = _state.value ?: LocateState()
        _state.postValue(
            prev.copy(
                waypoints = emptyList(),
                smoothedRsrp = null,
                bearing = null,
                bearingSource = BearingSource.NONE,
                bearingFresh = false,
                estimatedTower = null,
                deltaDb = 0.0,
                waypointsForResolve = 0,
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
