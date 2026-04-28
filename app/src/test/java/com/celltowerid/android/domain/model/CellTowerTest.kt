package com.celltowerid.android.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CellTowerTest {

    @Test
    fun `given an LTE tower when accessing enbId then returns eNB portion of CID`() {
        // Given
        val tower = CellTower(
            radio = RadioType.LTE,
            mcc = 310,
            mnc = 260,
            tacLac = 1234,
            cid = 0x123456L // eNB = 0x1234 (4660)
        )

        // When / Then
        assertThat(tower.enbId).isEqualTo(0x1234)
    }

    @Test
    fun `given an LTE tower when accessing sectorId then returns sector portion of CID`() {
        // Given
        val tower = CellTower(
            radio = RadioType.LTE,
            mcc = 310,
            mnc = 260,
            tacLac = 1234,
            cid = 0x123456L // sector = 0x56 (86)
        )

        // When / Then
        assertThat(tower.sectorId).isEqualTo(0x56)
    }

    @Test
    fun `given a GSM tower when accessing enbId then returns null`() {
        // Given
        val tower = CellTower(
            radio = RadioType.GSM,
            mcc = 310,
            mnc = 260,
            tacLac = 1234,
            cid = 12345L
        )

        // When / Then
        assertThat(tower.enbId).isNull()
    }

    @Test
    fun `given a GSM tower when accessing sectorId then returns null`() {
        // Given
        val tower = CellTower(
            radio = RadioType.GSM,
            mcc = 310,
            mnc = 260,
            tacLac = 1234,
            cid = 12345L
        )

        // When / Then
        assertThat(tower.sectorId).isNull()
    }

    @Test
    fun `given an NR tower when accessing enbId then returns null`() {
        // Given
        val tower = CellTower(
            radio = RadioType.NR,
            mcc = 310,
            mnc = 260,
            tacLac = 1234,
            cid = 0xABCDEF123L
        )

        // When / Then
        assertThat(tower.enbId).isNull()
    }

    @Test
    fun `given an LTE tower with sector 0 when accessing sectorId then returns zero`() {
        // Given
        val tower = CellTower(
            radio = RadioType.LTE,
            mcc = 310,
            mnc = 260,
            tacLac = 1234,
            cid = 0x100000L // sector = 0x00
        )

        // When / Then
        assertThat(tower.sectorId).isEqualTo(0)
    }

    @Test
    fun `given an LTE tower with max sector when accessing sectorId then returns 255`() {
        // Given
        val tower = CellTower(
            radio = RadioType.LTE,
            mcc = 310,
            mnc = 260,
            tacLac = 1234,
            cid = 0x10FFL // sector = 0xFF (255)
        )

        // When / Then
        assertThat(tower.sectorId).isEqualTo(255)
    }
}
