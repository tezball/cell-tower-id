package com.terrycollins.celltowerid.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoDistanceTest {

    @Test
    fun `given identical points, when haversineMeters, then returns 0`() {
        // When
        val d = GeoDistance.haversineMeters(53.0, -6.0, 53.0, -6.0)

        // Then
        assertThat(d).isWithin(0.1).of(0.0)
    }

    @Test
    fun `given two points about 1km apart, when haversineMeters, then returns ~1000m`() {
        // Given - 0.009 degrees of latitude ≈ 1 km
        val d = GeoDistance.haversineMeters(53.0, -6.0, 53.009, -6.0)

        // Then
        assertThat(d).isAtLeast(900.0)
        assertThat(d).isAtMost(1100.0)
    }

    @Test
    fun `given Dublin and London, when haversineMeters, then returns ~463000m`() {
        // Given
        val d = GeoDistance.haversineMeters(53.3498, -6.2603, 51.5074, -0.1278)

        // Then - great-circle distance Dublin→London ≈ 463 km
        assertThat(d).isAtLeast(450_000.0)
        assertThat(d).isAtMost(475_000.0)
    }
}
