package com.terrycollins.cellid.util

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure math helpers for Hunt Mode: EMA smoothing, great-circle bearing,
 * signal-gradient direction finding, and a rough log-distance path-loss
 * RSRP → meters estimate.
 */
object HuntMath {

    data class Waypoint(val lat: Double, val lon: Double, val rsrpDbm: Int)

    /** Exponential moving average. `alpha` is the weight of the new sample. */
    fun ema(prev: Double, next: Double, alpha: Double = 0.3): Double =
        alpha * next + (1 - alpha) * prev

    /**
     * Initial bearing from (lat1, lon1) → (lat2, lon2) in degrees, 0..360,
     * where 0° = north, 90° = east. Standard forward-azimuth formula.
     */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δλ = Math.toRadians(lon2 - lon1)
        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        val deg = Math.toDegrees(atan2(y, x))
        return (deg + 360.0) % 360.0
    }

    /**
     * Direction of improving signal over the most recent waypoints.
     *
     * For each consecutive pair we compute the walking bearing and weight it
     * by the RSRP delta (positive = improving). We then sum weighted unit
     * vectors and return the bearing of the resultant. Returns null when
     * the total signal change is too small (multipath noise) or the
     * resultant magnitude collapses (random-walk in signal).
     */
    fun gradientBearing(
        waypoints: List<Waypoint>,
        minTotalAbsDb: Double = 3.0,
        minResultantMagnitude: Double = 1.0
    ): Double? {
        if (waypoints.size < 2) return null

        var x = 0.0
        var y = 0.0
        var totalAbs = 0.0
        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            val weight = (b.rsrpDbm - a.rsrpDbm).toDouble()
            if (weight == 0.0) continue
            val bearingRad = Math.toRadians(bearingDegrees(a.lat, a.lon, b.lat, b.lon))
            x += sin(bearingRad) * weight
            y += cos(bearingRad) * weight
            totalAbs += kotlin.math.abs(weight)
        }
        if (totalAbs < minTotalAbsDb) return null
        if (hypot(x, y) < minResultantMagnitude) return null
        val deg = Math.toDegrees(atan2(x, y))
        return (deg + 360.0) % 360.0
    }

    /**
     * Coarse RSRP → distance estimate via the log-distance path-loss model.
     *
     *   d = 10 ^ ((referenceRsrp − rsrp) / (10 · pathLossExponent))
     *
     * Defaults (`referenceRsrp = −40 dBm @ 1 m`, `n = 2.5`) give ~16 m at
     * −70 dBm and ~630 m at −110 dBm — within a factor of two of reality
     * for urban/suburban LTE. Always label the UI value as approximate.
     */
    fun rsrpToDistanceMeters(
        rsrpDbm: Int,
        referenceRsrp: Double = -40.0,
        pathLossExponent: Double = 2.5
    ): Double {
        val exponent = (referenceRsrp - rsrpDbm) / (10.0 * pathLossExponent)
        return 10.0.pow(exponent)
    }

    @Suppress("unused")
    private val TWO_PI = 2.0 * PI
}
