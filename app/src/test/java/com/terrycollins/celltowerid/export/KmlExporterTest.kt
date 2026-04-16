package com.terrycollins.celltowerid.export

import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.RadioType
import com.terrycollins.celltowerid.domain.model.SignalQuality
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KmlExporterTest {

    private fun makeMeasurement(
        lat: Double = 37.7749,
        lon: Double = -122.4194,
        rsrp: Int? = -75, // EXCELLENT for LTE
        cid: Long? = 67890L
    ): CellMeasurement = CellMeasurement(
        timestamp = 1700000000000L,
        latitude = lat,
        longitude = lon,
        radio = RadioType.LTE,
        mcc = 310,
        mnc = 260,
        tacLac = 12345,
        cid = cid,
        rsrp = rsrp,
        rsrq = -10,
        sinr = 15,
        isRegistered = true,
        operatorName = "T-Mobile"
    )

    @Test
    fun `given measurements when exporting then valid KML document`() {
        val kml = KmlExporter.export(listOf(makeMeasurement()))

        assertThat(kml).contains("""<?xml version="1.0" encoding="UTF-8"?>""")
        assertThat(kml).contains("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        assertThat(kml).contains("<Document>")
        assertThat(kml).contains("</Document>")
        assertThat(kml).contains("</kml>")
        assertThat(kml).contains("<name>Cell Tower ID Export</name>")
    }

    @Test
    fun `given measurement when exporting then placemark has correct coordinates`() {
        val m = makeMeasurement(lat = 40.7128, lon = -74.0060)

        val kml = KmlExporter.export(listOf(m))

        // KML coordinates are lon,lat,altitude
        assertThat(kml).contains("<coordinates>-74.006,40.7128,0</coordinates>")
    }

    @Test
    fun `given measurement with good signal then style matches EXCELLENT`() {
        val m = makeMeasurement(rsrp = -75) // EXCELLENT

        val kml = KmlExporter.export(listOf(m))

        assertThat(kml).contains("<styleUrl>#style_EXCELLENT</styleUrl>")
    }

    @Test
    fun `given measurement with poor signal then style matches POOR`() {
        val m = makeMeasurement(rsrp = -105) // POOR

        val kml = KmlExporter.export(listOf(m))

        assertThat(kml).contains("<styleUrl>#style_POOR</styleUrl>")
    }

    @Test
    fun `given hex color when converting to ABGR then bytes are reversed`() {
        // #00C853 -> R=00, G=C8, B=53 -> ABGR = ff53C800
        val result = KmlExporter.hexToAbgr("#00C853")
        assertThat(result).isEqualTo("ff53C800")
    }

    @Test
    fun `given hex without hash when converting to ABGR then still works`() {
        val result = KmlExporter.hexToAbgr("FF6D00")
        assertThat(result).isEqualTo("ff006DFF")
    }

    @Test
    fun `given all signal qualities when exporting then all styles defined`() {
        val kml = KmlExporter.export(listOf(makeMeasurement()))

        for (quality in SignalQuality.entries) {
            assertThat(kml).contains("""<Style id="style_${quality.name}">""")
        }
    }

    @Test
    fun `given measurement when exporting then placemark contains description with radio`() {
        val kml = KmlExporter.export(listOf(makeMeasurement()))

        assertThat(kml).contains("Radio: LTE")
    }

    @Test
    fun `given measurement with cid when exporting then placemark name contains cid`() {
        val m = makeMeasurement(cid = 99999L)

        val kml = KmlExporter.export(listOf(m))

        assertThat(kml).contains("<name>LTE 99999</name>")
    }

    @Test
    fun `given measurement without cid when exporting then placemark name uses question mark`() {
        val m = makeMeasurement(cid = null)

        val kml = KmlExporter.export(listOf(m))

        assertThat(kml).contains("<name>LTE ?</name>")
    }
}
