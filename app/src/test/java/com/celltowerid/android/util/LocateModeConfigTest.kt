package com.celltowerid.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocateModeConfigTest {

    @Test
    fun `when fetching walking config, then sample interval is one second`() {
        // When
        val config = LocateModeConfig.forMode(LocateMode.WALKING)

        // Then
        assertThat(config.sampleIntervalMs).isEqualTo(1000L)
    }

    @Test
    fun `when fetching driving config, then sample interval is 250ms for ~4Hz`() {
        // When
        val config = LocateModeConfig.forMode(LocateMode.DRIVING)

        // Then
        assertThat(config.sampleIntervalMs).isEqualTo(250L)
    }

    @Test
    fun `given driving config, when comparing waypoint threshold to walking, then driving is wider`() {
        // Vehicle speeds cover meters fast — pile-up at 2m would fill the gradient
        // window with sub-second-spaced waypoints over a few car-lengths.

        // When
        val walking = LocateModeConfig.forMode(LocateMode.WALKING)
        val driving = LocateModeConfig.forMode(LocateMode.DRIVING)

        // Then
        assertThat(driving.waypointMinDistanceM).isGreaterThan(walking.waypointMinDistanceM)
    }

    @Test
    fun `given driving config, when comparing gradient window to walking, then driving holds more waypoints`() {
        // When
        val walking = LocateModeConfig.forMode(LocateMode.WALKING)
        val driving = LocateModeConfig.forMode(LocateMode.DRIVING)

        // Then
        assertThat(driving.gradientWindowSize).isGreaterThan(walking.gradientWindowSize)
    }

    @Test
    fun `given driving config, when comparing min db threshold to walking, then driving is more permissive`() {
        // When
        val walking = LocateModeConfig.forMode(LocateMode.WALKING)
        val driving = LocateModeConfig.forMode(LocateMode.DRIVING)

        // Then -- driving threshold is at most walking, since gradient resolves
        // faster (more dB change per second of movement at vehicle speeds).
        assertThat(driving.gradientMinTotalAbsDb).isLessThan(walking.gradientMinTotalAbsDb)
    }

    @Test
    fun `given any mode config, when checking location intervals, then min interval does not exceed nominal`() {
        // Given / When / Then -- LocationRequest.setMinUpdateIntervalMillis must
        // be <= LocationRequest interval, else FusedLocation throws.
        for (mode in LocateMode.values()) {
            val c = LocateModeConfig.forMode(mode)
            assertThat(c.locationMinIntervalMs).isAtMost(c.locationIntervalMs)
        }
    }

    @Test
    fun `given any mode config, when checking sample interval, then it is positive`() {
        for (mode in LocateMode.values()) {
            val c = LocateModeConfig.forMode(mode)
            assertThat(c.sampleIntervalMs).isGreaterThan(0L)
        }
    }
}
