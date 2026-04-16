package com.terrycollins.celltowerid.service

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class MockCellInfoProviderTest {

    private lateinit var provider: MockCellInfoProvider

    @Before
    fun setUp() {
        provider = MockCellInfoProvider()
    }

    @Test
    fun `when checking availability, then returns true`() {
        assertThat(provider.isAvailable()).isTrue()
    }

    @Test
    fun `when getting measurements, then returns non-empty list`() {
        // When
        val measurements = provider.getCellMeasurements(37.7749, -122.4194, 10f)

        // Then
        assertThat(measurements).isNotEmpty()
        assertThat(measurements.size).isAtLeast(3)
    }

    @Test
    fun `when getting measurements, then includes one registered cell`() {
        // When
        val measurements = provider.getCellMeasurements(37.7749, -122.4194, 10f)

        // Then
        val registeredCells = measurements.filter { it.isRegistered }
        assertThat(registeredCells).hasSize(1)
    }

    @Test
    fun `when getting measurements, then all have valid coordinates`() {
        // When
        val lat = 37.7749
        val lon = -122.4194
        val measurements = provider.getCellMeasurements(lat, lon, 10f)

        // Then
        measurements.forEach {
            assertThat(it.latitude).isEqualTo(lat)
            assertThat(it.longitude).isEqualTo(lon)
        }
    }

    @Test
    fun `when getting measurements, then all have T-Mobile MCC MNC`() {
        // When
        val measurements = provider.getCellMeasurements(37.7749, -122.4194, 10f)

        // Then
        measurements.forEach {
            assertThat(it.mcc).isEqualTo(310)
            assertThat(it.mnc).isEqualTo(260)
        }
    }

    @Test
    fun `when getting measurements twice, then signal values differ slightly`() {
        // When
        val first = provider.getCellMeasurements(37.7749, -122.4194, 10f)
        val second = provider.getCellMeasurements(37.7749, -122.4194, 10f)

        // Then - collect RSRP values from the serving cells (first in each list)
        val firstRsrps = first.mapNotNull { it.rsrp }
        val secondRsrps = second.mapNotNull { it.rsrp }

        // With random +/-3 offset, it's extremely unlikely all values match across 20+ calls
        // We'll try multiple times to confirm they do vary
        var foundDifference = false
        repeat(20) {
            val a = provider.getCellMeasurements(37.7749, -122.4194, 10f)
            val b = provider.getCellMeasurements(37.7749, -122.4194, 10f)
            if (a[0].rsrp != b[0].rsrp) {
                foundDifference = true
            }
        }
        assertThat(foundDifference).isTrue()
    }

    @Test
    fun `when getting measurements, then serving cell has realistic LTE signal values`() {
        // When
        val measurements = provider.getCellMeasurements(37.7749, -122.4194, 10f)
        val serving = measurements.first { it.isRegistered }

        // Then - RSRP should be in realistic range (base -82 +/- 3)
        assertThat(serving.rsrp).isNotNull()
        assertThat(serving.rsrp!!).isAtLeast(-110)
        assertThat(serving.rsrp!!).isAtMost(-70)

        // RSRQ should be in realistic range
        assertThat(serving.rsrq).isNotNull()
        assertThat(serving.rsrq!!).isAtLeast(-20)
        assertThat(serving.rsrq!!).isAtMost(-3)

        // SINR should be in realistic range
        assertThat(serving.sinr).isNotNull()
        assertThat(serving.sinr!!).isAtLeast(0)
        assertThat(serving.sinr!!).isAtMost(30)
    }
}
