package com.celltowerid.android.util

/**
 * Pure helpers for converting `SensorManager.getOrientation()` output into a
 * 0–360° azimuth and applying a wraparound-safe low-pass filter.
 */
object HeadingMath {

    /**
     * Converts the azimuth component of `SensorManager.getOrientation()` (in
     * radians, range -π..π) into degrees in the 0..360 range, where 0° = north
     * and 90° = east.
     */
    fun azimuthDegreesFromRadians(radians: Double): Double {
        val deg = Math.toDegrees(radians)
        return (deg + 360.0) % 360.0
    }

    /**
     * Low-pass filter on a circular azimuth value. Walks toward [next] along
     * the shorter arc so that 350° → 10° smooths through 0° rather than the
     * long way around.
     */
    fun lowPassAzimuth(prev: Double, next: Double, alpha: Double): Double {
        var delta = next - prev
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        val smoothed = prev + alpha * delta
        return (smoothed + 360.0) % 360.0
    }
}
