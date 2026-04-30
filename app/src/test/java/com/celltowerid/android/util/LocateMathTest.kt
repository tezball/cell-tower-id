package com.celltowerid.android.util

import com.celltowerid.android.util.LocateMath.Waypoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocateMathTest {

    @Test
    fun `given prev=-100 and next=-90 with alpha 0_3, when ema, then returns -97`() {
        // When
        val result = LocateMath.ema(-100.0, -90.0, 0.3)

        // Then
        assertThat(result).isWithin(0.01).of(-97.0)
    }

    @Test
    fun `given two latlngs due north, when bearingTo, then returns ~0 degrees`() {
        // When
        val bearing = LocateMath.bearingDegrees(53.0, -6.0, 53.001, -6.0)

        // Then
        assertThat(bearing).isWithin(1.0).of(0.0)
    }

    @Test
    fun `given two latlngs due east, when bearingTo, then returns ~90 degrees`() {
        // When
        val bearing = LocateMath.bearingDegrees(53.0, -6.0, 53.0, -5.999)

        // Then
        assertThat(bearing).isWithin(1.0).of(90.0)
    }

    @Test
    fun `given waypoints with improving signal walking north, when gradientBearing, then returns ~0 degrees`() {
        // Given 5 waypoints moving north, signal improving
        val waypoints = (0..4).map { i ->
            Waypoint(lat = 53.0 + i * 0.0002, lon = -6.0, rsrpDbm = -110 + i * 5)
        }

        // When
        val bearing = requireNotNull(LocateMath.gradientBearing(waypoints))

        // Then
        assertThat(bearing).isWithin(10.0).of(0.0)
    }

    @Test
    fun `given waypoints with improving signal walking east, when gradientBearing, then returns ~90 degrees`() {
        // Given 5 waypoints moving east, signal improving
        val waypoints = (0..4).map { i ->
            Waypoint(lat = 53.0, lon = -6.0 + i * 0.0003, rsrpDbm = -110 + i * 5)
        }

        // When
        val bearing = requireNotNull(LocateMath.gradientBearing(waypoints))

        // Then
        assertThat(bearing).isWithin(10.0).of(90.0)
    }

    @Test
    fun `given waypoints with flat signal, when gradientBearing, then returns null`() {
        // Given
        val waypoints = (0..4).map { i ->
            Waypoint(lat = 53.0 + i * 0.0002, lon = -6.0, rsrpDbm = -100)
        }

        // When
        val bearing = LocateMath.gradientBearing(waypoints)

        // Then
        assertThat(bearing).isNull()
    }

    @Test
    fun `given small 2dB total change with strict default threshold, when gradientBearing, then returns null`() {
        // Total |delta| = 2 dB, below the 3 dB strict default. Should reject.
        val waypoints = listOf(
            Waypoint(lat = 53.0, lon = -6.0, rsrpDbm = -100),
            Waypoint(lat = 53.0001, lon = -6.0, rsrpDbm = -99),
            Waypoint(lat = 53.0002, lon = -6.0, rsrpDbm = -98),
        )

        val bearing = LocateMath.gradientBearing(waypoints, minTotalAbsDb = 3.0)

        assertThat(bearing).isNull()
    }

    @Test
    fun `given small 2dB total change with relaxed driving threshold, when gradientBearing, then returns a bearing`() {
        // Same waypoints, but with the driving-mode 2 dB threshold; should resolve.
        val waypoints = listOf(
            Waypoint(lat = 53.0, lon = -6.0, rsrpDbm = -100),
            Waypoint(lat = 53.0001, lon = -6.0, rsrpDbm = -99),
            Waypoint(lat = 53.0002, lon = -6.0, rsrpDbm = -98),
        )

        val bearing = LocateMath.gradientBearing(
            waypoints,
            minTotalAbsDb = 2.0,
            minResultantMagnitude = 0.7,
        )

        assertThat(bearing).isNotNull()
        assertThat(bearing!!).isWithin(10.0).of(0.0)
    }

    @Test
    fun `given empty list, when gradientBearing, then returns null`() {
        assertThat(LocateMath.gradientBearing(emptyList())).isNull()
    }

    @Test
    fun `given single waypoint, when gradientBearing, then returns null`() {
        val waypoints = listOf(Waypoint(lat = 53.0, lon = -6.0, rsrpDbm = -100))
        assertThat(LocateMath.gradientBearing(waypoints)).isNull()
    }

    @Test
    fun `given an rsrp of -70, when rsrpToDistanceMeters, then returns within factor of 2 of 10m`() {
        // When
        val d = LocateMath.rsrpToDistanceMeters(-70)

        // Then
        assertThat(d).isAtLeast(5.0)
        assertThat(d).isAtMost(20.0)
    }

    @Test
    fun `given an rsrp of -110, when rsrpToDistanceMeters, then returns within factor of 2 of 1000m`() {
        // When
        val d = LocateMath.rsrpToDistanceMeters(-110)

        // Then
        assertThat(d).isAtLeast(500.0)
        assertThat(d).isAtMost(2000.0)
    }
}
