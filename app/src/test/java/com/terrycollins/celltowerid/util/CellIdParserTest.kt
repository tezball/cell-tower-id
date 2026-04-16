package com.terrycollins.celltowerid.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CellIdParserTest {

    // --- parseEutranCid ---

    @Test
    fun `given a typical E-UTRAN CID when parsed then returns correct eNB ID and sector ID`() {
        // Given
        val eutranCid = 0x123456L // eNB=0x1234 (4660), sector=0x56 (86)

        // When
        val (enbId, sectorId) = CellIdParser.parseEutranCid(eutranCid)

        // Then
        assertThat(enbId).isEqualTo(0x1234)
        assertThat(sectorId).isEqualTo(0x56)
    }

    @Test
    fun `given a CID with zero sector when parsed then sector ID is zero`() {
        // Given
        val eutranCid = 0x100000L // eNB=0x1000, sector=0x00

        // When
        val (enbId, sectorId) = CellIdParser.parseEutranCid(eutranCid)

        // Then
        assertThat(enbId).isEqualTo(0x1000)
        assertThat(sectorId).isEqualTo(0)
    }

    @Test
    fun `given a CID with max sector when parsed then sector ID is 255`() {
        // Given
        val eutranCid = 0x10FFL // eNB=0x10, sector=0xFF

        // When
        val (enbId, sectorId) = CellIdParser.parseEutranCid(eutranCid)

        // Then
        assertThat(enbId).isEqualTo(0x10)
        assertThat(sectorId).isEqualTo(0xFF)
    }

    // --- buildEutranCid ---

    @Test
    fun `given eNB ID and sector ID when built then returns correct E-UTRAN CID`() {
        // Given
        val enbId = 0x1234
        val sectorId = 0x56

        // When
        val cid = CellIdParser.buildEutranCid(enbId, sectorId)

        // Then
        assertThat(cid).isEqualTo(0x123456L)
    }

    @Test
    fun `given parse and build are inverse operations when round-tripped then values match`() {
        // Given
        val originalCid = 0xABCDEFL

        // When
        val (enbId, sectorId) = CellIdParser.parseEutranCid(originalCid)
        val rebuiltCid = CellIdParser.buildEutranCid(enbId, sectorId)

        // Then
        assertThat(rebuiltCid).isEqualTo(originalCid)
    }

    // --- isValidEutranCid ---

    @Test
    fun `given a valid 28-bit CID when validated then returns true`() {
        // Given
        val cid = 0xFFFFFFL

        // When / Then
        assertThat(CellIdParser.isValidEutranCid(cid)).isTrue()
    }

    @Test
    fun `given the maximum 28-bit CID when validated then returns true`() {
        // Given
        val cid = 0xFFFFFFFL

        // When / Then
        assertThat(CellIdParser.isValidEutranCid(cid)).isTrue()
    }

    @Test
    fun `given zero CID when validated then returns true`() {
        assertThat(CellIdParser.isValidEutranCid(0L)).isTrue()
    }

    @Test
    fun `given a CID exceeding 28 bits when validated then returns false`() {
        // Given
        val cid = 0x10000000L

        // When / Then
        assertThat(CellIdParser.isValidEutranCid(cid)).isFalse()
    }

    @Test
    fun `given a negative CID when validated then returns false`() {
        assertThat(CellIdParser.isValidEutranCid(-1L)).isFalse()
    }

    // --- isValidNrCid ---

    @Test
    fun `given a valid 36-bit NR CID when validated then returns true`() {
        // Given
        val nci = 0xFFFFFFFFFL

        // When / Then
        assertThat(CellIdParser.isValidNrCid(nci)).isTrue()
    }

    @Test
    fun `given zero NR CID when validated then returns true`() {
        assertThat(CellIdParser.isValidNrCid(0L)).isTrue()
    }

    @Test
    fun `given a NR CID exceeding 36 bits when validated then returns false`() {
        // Given
        val nci = 0x1000000000L

        // When / Then
        assertThat(CellIdParser.isValidNrCid(nci)).isFalse()
    }

    @Test
    fun `given a negative NR CID when validated then returns false`() {
        assertThat(CellIdParser.isValidNrCid(-1L)).isFalse()
    }
}
