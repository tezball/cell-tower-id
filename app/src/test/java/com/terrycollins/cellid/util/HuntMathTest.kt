package com.terrycollins.cellid.util

import com.terrycollins.cellid.util.HuntMath.Waypoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HuntMathTest {

    @Test
    fun `given prev=-100 and next=-90 with alpha 0_3, when ema, then returns -97`() {
        // When
        val result = HuntMath.ema(-100.0, -90.0, 0.3)

        // Then
        assertThat(result).isWithin(0.01).of(-97.0)
    }

    @Test
    fun `given two latlngs due north, when bearingTo, then returns ~0 degrees`() {
        // When
        val bearing = HuntMath.bearingDegrees(53.0, -6.0, 53.001, -6.0)

        // Then
        assertThat(bearing).isWithin(1.0).of(0.0)
    }

    @Test
    fun `given two latlngs due east, when bearingTo, then returns ~90 degrees`() {
        // When
        val bearing = HuntMath.bearingDegrees(53.0, -6.0, 53.0, -5.999)

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
        val bearing = HuntMath.gradientBearing(waypoints)

        // Then
        assertThat(bearing).isNotNull()
        assertThat(bearing!!).isWithin(10.0).of(0.0)
    }

    @Test
    fun `given waypoints with improving signal walking east, when gradientBearing, then returns ~90 degrees`() {
        // Given 5 waypoints moving east, signal improving
        val waypoints = (0..4).map { i ->
            Waypoint(lat = 53.0, lon = -6.0 + i * 0.0003, rsrpDbm = -110 + i * 5)
        }

        // When
        val bearing = HuntMath.gradientBearing(waypoints)

        // Then
        assertThat(bearing).isNotNull()
        assertThat(bearing!!).isWithin(10.0).of(90.0)
    }

    @Test
    fun `given waypoints with flat signal, when gradientBearing, then returns null`() {
        // Given
        val waypoints = (0..4).map { i ->
            Waypoint(lat = 53.0 + i * 0.0002, lon = -6.0, rsrpDbm = -100)
        }

        // When
        val bearing = HuntMath.gradientBearing(waypoints)

        // Then
        assertThat(bearing).isNull()
    }

    @Test
    fun `given an rsrp of -70, when rsrpToDistanceMeters, then returns within factor of 2 of 10m`() {
        // When
        val d = HuntMath.rsrpToDistanceMeters(-70)

        // Then
        assertThat(d).isAtLeast(5.0)
        assertThat(d).isAtMost(20.0)
    }

    @Test
    fun `given an rsrp of -110, when rsrpToDistanceMeters, then returns within factor of 2 of 1000m`() {
        // When
        val d = HuntMath.rsrpToDistanceMeters(-110)

        // Then
        assertThat(d).isAtLeast(500.0)
        assertThat(d).isAtMost(2000.0)
    }
}
