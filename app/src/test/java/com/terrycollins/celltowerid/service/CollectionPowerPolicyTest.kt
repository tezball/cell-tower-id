package com.terrycollins.celltowerid.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CollectionPowerPolicyTest {

    private val base = 10_000L

    @Test
    fun `given power saver disabled, when computing, then returns base interval unchanged`() {
        // Given / When
        val result = CollectionPowerPolicy.effectiveIntervalMs(
            baseMs = base,
            powerSaverEnabled = false,
            speedMps = 0f,
            batteryCapacity = 10,
            isCharging = false
        )

        // Then
        assertThat(result).isEqualTo(base)
    }

    @Test
    fun `given stationary with healthy battery, when computing, then doubles interval`() {
        // Given / When
        val result = CollectionPowerPolicy.effectiveIntervalMs(
            baseMs = base,
            powerSaverEnabled = true,
            speedMps = 0f,
            batteryCapacity = 80,
            isCharging = false
        )

        // Then
        assertThat(result).isEqualTo(base * 2)
    }

    @Test
    fun `given moving with healthy battery, when computing, then returns base interval`() {
        // Given / When
        val result = CollectionPowerPolicy.effectiveIntervalMs(
            baseMs = base,
            powerSaverEnabled = true,
            speedMps = 5f,
            batteryCapacity = 80,
            isCharging = false
        )

        // Then
        assertThat(result).isEqualTo(base)
    }

    @Test
    fun `given moving with low battery not charging, when computing, then doubles interval`() {
        // Given / When
        val result = CollectionPowerPolicy.effectiveIntervalMs(
            baseMs = base,
            powerSaverEnabled = true,
            speedMps = 5f,
            batteryCapacity = 15,
            isCharging = false
        )

        // Then
        assertThat(result).isEqualTo(base * 2)
    }

    @Test
    fun `given low battery but charging, when computing, then ignores low-battery back-off`() {
        // Given / When — charging cable plugged in, so power isn't scarce
        val result = CollectionPowerPolicy.effectiveIntervalMs(
            baseMs = base,
            powerSaverEnabled = true,
            speedMps = 5f,
            batteryCapacity = 15,
            isCharging = true
        )

        // Then
        assertThat(result).isEqualTo(base)
    }

    @Test
    fun `given stationary AND low battery, when computing, then stacks multipliers`() {
        // Given / When — both conditions fire, 2x * 2x = 4x
        val result = CollectionPowerPolicy.effectiveIntervalMs(
            baseMs = base,
            powerSaverEnabled = true,
            speedMps = 0f,
            batteryCapacity = 10,
            isCharging = false
        )

        // Then
        assertThat(result).isEqualTo(base * 4)
    }

    @Test
    fun `given both multipliers and large base, when computing, then caps at max interval`() {
        // Given / When — 60s base * 4 = 240s, should cap at 120s
        val result = CollectionPowerPolicy.effectiveIntervalMs(
            baseMs = 60_000L,
            powerSaverEnabled = true,
            speedMps = 0f,
            batteryCapacity = 10,
            isCharging = false
        )

        // Then
        assertThat(result).isEqualTo(CollectionPowerPolicy.MAX_INTERVAL_MS)
    }

    @Test
    fun `given null speed, when computing, then treats device as stationary`() {
        // Given / When — no GPS fix yet (speed unknown) should back off, not run hot
        val result = CollectionPowerPolicy.effectiveIntervalMs(
            baseMs = base,
            powerSaverEnabled = true,
            speedMps = null,
            batteryCapacity = 80,
            isCharging = false
        )

        // Then
        assertThat(result).isEqualTo(base * 2)
    }

    @Test
    fun `given zero battery reading, when computing, then does not treat as low battery`() {
        // Given / When — BATTERY_PROPERTY_CAPACITY returns 0 on emulators/unknown states
        val result = CollectionPowerPolicy.effectiveIntervalMs(
            baseMs = base,
            powerSaverEnabled = true,
            speedMps = 5f,
            batteryCapacity = 0,
            isCharging = false
        )

        // Then
        assertThat(result).isEqualTo(base)
    }
}
