package com.terrycollins.celltowerid.util

import com.terrycollins.celltowerid.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpenCellIdCsvParserTest {

    @Test
    fun `given a valid LTE row, when parsed, then returns a CellTower with mapped fields`() {
        // Given
        val line = "LTE,272,5,42300,237575,0,-8.3347,52.4677,2318,23,1,1475687630,1763894761,0"

        // When
        val tower = OpenCellIdCsvParser.parseLine(line)

        // Then
        assertThat(tower).isNotNull()
        assertThat(tower!!.radio).isEqualTo(RadioType.LTE)
        assertThat(tower.mcc).isEqualTo(272)
        assertThat(tower.mnc).isEqualTo(5)
        assertThat(tower.tacLac).isEqualTo(42300)
        assertThat(tower.cid).isEqualTo(237575L)
        assertThat(tower.longitude).isEqualTo(-8.3347)
        assertThat(tower.latitude).isEqualTo(52.4677)
        assertThat(tower.rangeMeters).isEqualTo(2318)
        assertThat(tower.samples).isEqualTo(23)
        assertThat(tower.source).isEqualTo("opencellid")
    }

    @Test
    fun `given a GSM row, when parsed, then radio type is GSM`() {
        // Given
        val line = "GSM,272,1,12345,67890,0,-8.1,53.0,1000,5,1,0,0,0"

        // When
        val tower = OpenCellIdCsvParser.parseLine(line)

        // Then
        assertThat(tower).isNotNull()
        assertThat(tower!!.radio).isEqualTo(RadioType.GSM)
    }

    @Test
    fun `given a line with too few columns, when parsed, then returns null`() {
        // Given
        val line = "LTE,272,5,42300"

        // When
        val tower = OpenCellIdCsvParser.parseLine(line)

        // Then
        assertThat(tower).isNull()
    }

    @Test
    fun `given a header or comment line, when parsed, then returns null`() {
        // Given
        val line = "radio,mcc,net,area,cell,unit,lon,lat,range,samples,changeable,created,updated,averageSignal"

        // When
        val tower = OpenCellIdCsvParser.parseLine(line)

        // Then
        assertThat(tower).isNull()
    }

    @Test
    fun `given a multiline CSV, when parseAll is called, then returns all valid towers`() {
        // Given
        val csv = """
            LTE,272,5,42300,237575,0,-8.3347,52.4677,2318,23,1,0,0,0
            LTE,272,5,42300,258818,0,-8.6133,52.6765,1259,24,1,0,0,0
            bogus,line
        """.trimIndent()

        // When
        val towers = OpenCellIdCsvParser.parseAll(csv.lineSequence()).toList()

        // Then
        assertThat(towers).hasSize(2)
        assertThat(towers[0].cid).isEqualTo(237575L)
        assertThat(towers[1].cid).isEqualTo(258818L)
    }
}
