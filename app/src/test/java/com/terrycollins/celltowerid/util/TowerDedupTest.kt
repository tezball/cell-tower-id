package com.terrycollins.celltowerid.util

import com.terrycollins.celltowerid.domain.model.CellTower
import com.terrycollins.celltowerid.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TowerDedupTest {

    private fun lte(cid: Long, lat: Double?, lon: Double?, mcc: Int = 272, mnc: Int = 5): CellTower =
        CellTower(
            radio = RadioType.LTE,
            mcc = mcc, mnc = mnc, tacLac = 41002, cid = cid,
            latitude = lat, longitude = lon, source = "ocid"
        )

    @Test
    fun `given three LTE sectors of the same eNB, when collapseLteByEnb, then returns one entry at their average position`() {
        // Given - same enbId (cid shr 8), different sectors
        val enbBase = 87_895L shl 8
        val sectors = listOf(
            lte(enbBase or 1, 53.0, -6.0),
            lte(enbBase or 2, 53.2, -6.2),
            lte(enbBase or 3, 53.4, -6.4),
        )

        // When
        val result = TowerDedup.collapseLteByEnb(sectors)

        // Then
        assertThat(result).hasSize(1)
        val t = result[0]
        assertThat(t.radio).isEqualTo(RadioType.LTE)
        assertThat(t.latitude!!).isWithin(1e-9).of(53.2)
        assertThat(t.longitude!!).isWithin(1e-9).of(-6.2)
    }

    @Test
    fun `given one GSM plus one LTE, when collapseLteByEnb, then the GSM is unchanged`() {
        // Given
        val gsm = CellTower(
            radio = RadioType.GSM, mcc = 272, mnc = 5, tacLac = 1, cid = 123L,
            latitude = 52.0, longitude = -5.0, source = "ocid"
        )
        val lteT = lte(500L, 53.0, -6.0)

        // When
        val result = TowerDedup.collapseLteByEnb(listOf(gsm, lteT))

        // Then
        assertThat(result).hasSize(2)
        assertThat(result).contains(gsm)
    }

    @Test
    fun `given LTE sectors from two different eNBs, when collapseLteByEnb, then returns two entries`() {
        // Given
        val enbA = 1000L shl 8
        val enbB = 2000L shl 8
        val towers = listOf(
            lte(enbA or 1, 53.0, -6.0),
            lte(enbA or 2, 53.2, -6.2),
            lte(enbB or 1, 50.0, -3.0),
        )

        // When
        val result = TowerDedup.collapseLteByEnb(towers)

        // Then
        assertThat(result).hasSize(2)
    }
}
