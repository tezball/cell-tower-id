package com.celltowerid.android.util

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TowerLocatorTest {

    private fun lteMeasurement(lat: Double, lon: Double, rsrp: Int?): CellMeasurement =
        CellMeasurement(
            timestamp = 0L,
            latitude = lat,
            longitude = lon,
            radio = RadioType.LTE,
            rsrp = rsrp,
            isRegistered = true
        )

    @Test
    fun `given fewer than two measurements, when estimate, then returns null`() {
        // Given
        val one = listOf(lteMeasurement(53.0, -6.0, -80))

        // When
        val result = TowerLocator.estimate(one)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `given measurements clustered under 50m, when estimate, then returns null`() {
        // Given - two points ~10m apart
        val close = listOf(
            lteMeasurement(53.0, -6.0, -80),
            lteMeasurement(53.00009, -6.00009, -85)
        )

        // When
        val result = TowerLocator.estimate(close)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `given three spread measurements with strongest near point A, when estimate, then returns a point biased toward A`() {
        // Given - points ~1km apart, strongest at A
        val a = lteMeasurement(53.0000, -6.0000, -70)   // strongest
        val b = lteMeasurement(53.0100, -6.0000, -110)  // ~1.1km N
        val c = lteMeasurement(53.0000, -5.9900, -110)  // ~0.66km E

        // When
        val result = TowerLocator.estimate(listOf(a, b, c))

        // Then
        val (lat, lon) = requireNotNull(result)
        val distToA = Math.hypot(lat - a.latitude, lon - a.longitude)
        val distToB = Math.hypot(lat - b.latitude, lon - b.longitude)
        val distToC = Math.hypot(lat - c.latitude, lon - c.longitude)
        assertThat(distToA).isLessThan(distToB)
        assertThat(distToA).isLessThan(distToC)
    }

    @Test
    fun `given all rsrp null, when estimate, then returns null`() {
        // Given
        val nulls = listOf(
            lteMeasurement(53.0, -6.0, null),
            lteMeasurement(53.01, -6.01, null)
        )

        // When
        val result = TowerLocator.estimate(nulls)

        // Then
        assertThat(result).isNull()
    }
}
