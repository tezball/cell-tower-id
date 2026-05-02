package com.celltowerid.android.util

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.CellTower
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TowerMarkerPositionTest {

    private fun tower(lat: Double? = 53.0, lon: Double? = -6.0): CellTower =
        CellTower(
            radio = RadioType.LTE,
            mcc = 272,
            mnc = 1,
            tacLac = 7,
            cid = 22501123,
            latitude = lat,
            longitude = lon
        )

    private fun reading(lat: Double, lon: Double, rsrp: Int? = -85): CellMeasurement =
        CellMeasurement(
            timestamp = 0L,
            latitude = lat,
            longitude = lon,
            radio = RadioType.LTE,
            rsrp = rsrp,
            isRegistered = true
        )

    @Test
    fun `given a best reading, when pick, then returns the reading lat lon`() {
        // Given - cached tower at one spot, best reading at another
        val t = tower(lat = 53.0, lon = -6.0)
        val best = reading(lat = 53.5, lon = -6.5)

        // When
        val result = TowerMarkerPosition.pick(t, best)

        // Then - dot follows the reading, not the cached estimate
        assertThat(result).isEqualTo(53.5 to -6.5)
    }

    @Test
    fun `given no best reading and a cached tower position, when pick, then falls back to cached lat lon`() {
        // Given
        val t = tower(lat = 53.1, lon = -6.2)

        // When
        val result = TowerMarkerPosition.pick(t, null)

        // Then
        assertThat(result).isEqualTo(53.1 to -6.2)
    }

    @Test
    fun `given no best reading and no cached tower position, when pick, then returns null`() {
        // Given
        val t = tower(lat = null, lon = null)

        // When
        val result = TowerMarkerPosition.pick(t, null)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `given a best reading and no cached tower position, when pick, then still returns the reading lat lon`() {
        // Given - pinned/observed tower with no cached coords yet
        val t = tower(lat = null, lon = null)
        val best = reading(lat = 40.7128, lon = -74.0060)

        // When
        val result = TowerMarkerPosition.pick(t, best)

        // Then
        assertThat(result).isEqualTo(40.7128 to -74.0060)
    }
}
