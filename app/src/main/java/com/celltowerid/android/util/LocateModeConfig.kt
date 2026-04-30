package com.celltowerid.android.util

/**
 * Locate-mode presets: WALKING is the default 1 Hz cadence with a tight
 * 2 m waypoint threshold; DRIVING bumps the sampling loop to 4 Hz, widens
 * the waypoint threshold for vehicle speeds, and relaxes the gradient
 * thresholds so direction resolves on shorter timescales.
 */
enum class LocateMode { WALKING, DRIVING }

data class LocateModeConfig(
    val sampleIntervalMs: Long,
    val locationIntervalMs: Long,
    val locationMinIntervalMs: Long,
    val waypointMinDistanceM: Double,
    val gradientWindowSize: Int,
    val gradientMinTotalAbsDb: Double,
    val gradientMinResultantMagnitude: Double,
) {
    companion object {
        val WALKING = LocateModeConfig(
            sampleIntervalMs = 1000L,
            locationIntervalMs = 1000L,
            locationMinIntervalMs = 500L,
            waypointMinDistanceM = 2.0,
            gradientWindowSize = 20,
            gradientMinTotalAbsDb = 3.0,
            gradientMinResultantMagnitude = 1.0,
        )

        val DRIVING = LocateModeConfig(
            sampleIntervalMs = 250L,
            locationIntervalMs = 500L,
            locationMinIntervalMs = 250L,
            waypointMinDistanceM = 10.0,
            gradientWindowSize = 40,
            gradientMinTotalAbsDb = 2.0,
            gradientMinResultantMagnitude = 0.7,
        )

        fun forMode(mode: LocateMode): LocateModeConfig = when (mode) {
            LocateMode.WALKING -> WALKING
            LocateMode.DRIVING -> DRIVING
        }
    }
}
