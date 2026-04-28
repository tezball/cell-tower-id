package com.celltowerid.android.service

/**
 * Pure function that computes an adaptive scan interval given device state.
 * Extracted from CollectionService so it can be unit-tested without
 * instantiating the service (which needs a live BatteryManager and Context).
 */
object CollectionPowerPolicy {
    const val MAX_INTERVAL_MS = 120_000L
    private const val STATIONARY_SPEED_MPS = 0.5f
    private const val LOW_BATTERY_THRESHOLD = 19

    fun effectiveIntervalMs(
        baseMs: Long,
        powerSaverEnabled: Boolean,
        speedMps: Float?,
        batteryCapacity: Int,
        isCharging: Boolean
    ): Long {
        if (!powerSaverEnabled) return baseMs
        var multiplier = 1.0
        val speed = speedMps ?: 0f
        if (speed < STATIONARY_SPEED_MPS) multiplier *= 2.0
        if (batteryCapacity in 1..LOW_BATTERY_THRESHOLD && !isCharging) multiplier *= 2.0
        return (baseMs * multiplier).toLong().coerceAtMost(MAX_INTERVAL_MS)
    }
}
