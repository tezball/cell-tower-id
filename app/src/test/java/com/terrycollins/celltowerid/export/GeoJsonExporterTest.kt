package com.terrycollins.celltowerid.export

import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test

class GeoJsonExporterTest {

    private fun makeMeasurement(
        lat: Double = 37.7749,
        lon: Double = -122.4194,
        rsrp: Int? = -85
    ): CellMeasurement = CellMeasurement(
        timestamp = 1700000000000L,
        latitude = lat,
        longitude = lon,
        radio = RadioType.LTE,
        mcc = 310,
        mnc = 260,
        tacLac = 12345,
        cid = 67890L,
        pciPsc = 100,
        rsrp = rsrp,
        isRegistered = true,
        operatorName = "T-Mobile"
    )

    @Suppress("UNCHECKED_CAST")
    private fun parseJson(json: String): Map<String, Any> {
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return Gson().fromJson(json, type)
    }

    @Test
    fun `given list of measurements when exporting then valid GeoJSON with FeatureCollection`() {
        val measurements = listOf(makeMeasurement(), makeMeasurement())

        val json = GeoJsonExporter.export(measurements)
        val parsed = parseJson(json)

        assertThat(parsed["type"]).isEqualTo("FeatureCollection")
        @Suppress("UNCHECKED_CAST")
        val features = parsed["features"] as List<Map<String, Any>>
        assertThat(features).hasSize(2)
        assertThat(features[0]["type"]).isEqualTo("Feature")
    }

    @Test
    fun `given measurement when exporting then coordinates are lon-lat order`() {
        val m = makeMeasurement(lat = 40.0, lon = -74.0)

        val json = GeoJsonExporter.export(listOf(m))
        val parsed = parseJson(json)

        @Suppress("UNCHECKED_CAST")
        val features = parsed["features"] as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val geometry = features[0]["geometry"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val coordinates = geometry["coordinates"] as List<Double>

        // GeoJSON is [longitude, latitude]
        assertThat(coordinates[0]).isEqualTo(-74.0)
        assertThat(coordinates[1]).isEqualTo(40.0)
    }

    @Test
    fun `given measurement with signal when exporting then properties include signal_quality`() {
        val m = makeMeasurement(rsrp = -75) // EXCELLENT for LTE

        val json = GeoJsonExporter.export(listOf(m))
        val parsed = parseJson(json)

        @Suppress("UNCHECKED_CAST")
        val features = parsed["features"] as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val properties = features[0]["properties"] as Map<String, Any>

        assertThat(properties).containsKey("signal_quality")
        assertThat(properties["signal_quality"]).isEqualTo("EXCELLENT")
    }

    @Test
    fun `given measurement when exporting then properties contain radio type`() {
        val m = makeMeasurement()

        val json = GeoJsonExporter.export(listOf(m))
        val parsed = parseJson(json)

        @Suppress("UNCHECKED_CAST")
        val features = parsed["features"] as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val properties = features[0]["properties"] as Map<String, Any>

        assertThat(properties["radio"]).isEqualTo("LTE")
    }

    @Test
    fun `given empty list when exporting then returns valid empty FeatureCollection`() {
        val json = GeoJsonExporter.export(emptyList())
        val parsed = parseJson(json)

        assertThat(parsed["type"]).isEqualTo("FeatureCollection")
        @Suppress("UNCHECKED_CAST")
        val features = parsed["features"] as List<*>
        assertThat(features).isEmpty()
    }
}
