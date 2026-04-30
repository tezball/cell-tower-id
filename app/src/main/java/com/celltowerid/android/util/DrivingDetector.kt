package com.celltowerid.android.util

/**
 * Tracks whether the device is currently driving based on a stream of GPS
 * speed samples. Hysteretic so that brief acceleration spikes or stoplights
 * don't flip the mode.
 *
 *   WALKING → DRIVING when speed > 5 m/s (~18 km/h) sustained for ≥ 5 s
 *   DRIVING → WALKING when speed < 2 m/s (~7 km/h) sustained for ≥ 10 s
 *
 * Samples with speedAccuracy worse than [maxSpeedAccuracyMps] are ignored
 * to avoid noisy GPS triggering false promotions. Null speedAccuracy is
 * trusted (devices below API 26 don't report it).
 */
class DrivingDetector(
    private val promoteSpeedMps: Float = 5f,
    private val demoteSpeedMps: Float = 2f,
    private val promoteDwellMs: Long = 5_000L,
    private val demoteDwellMs: Long = 10_000L,
    private val maxSpeedAccuracyMps: Float = 2f,
) {
    var mode: LocateMode = LocateMode.WALKING
        private set

    private var candidateSinceMs: Long? = null

    fun update(speedMps: Float?, speedAccuracyMps: Float?, timestampMs: Long) {
        if (speedMps == null) return
        if (speedAccuracyMps != null && speedAccuracyMps > maxSpeedAccuracyMps) return

        val targetMode: LocateMode? = when (mode) {
            LocateMode.WALKING -> if (speedMps > promoteSpeedMps) LocateMode.DRIVING else null
            LocateMode.DRIVING -> if (speedMps < demoteSpeedMps) LocateMode.WALKING else null
        }

        if (targetMode == null) {
            candidateSinceMs = null
            return
        }

        val started = candidateSinceMs
        if (started == null) {
            candidateSinceMs = timestampMs
            return
        }

        val dwell = if (targetMode == LocateMode.DRIVING) promoteDwellMs else demoteDwellMs
        if (timestampMs - started >= dwell) {
            mode = targetMode
            candidateSinceMs = null
        }
    }
}
