package com.celltowerid.android.export

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.util.SignalClassifier
import com.google.gson.GsonBuilder
import java.io.File

object GeoJsonExporter {

    fun export(measurements: List<CellMeasurement>): String {
        val features = measurements.map { m -> buildFeature(m) }

        val gson = GsonBuilder().setPrettyPrinting().create()
        val featureCollection = mapOf(
            "type" to "FeatureCollection",
            "features" to features
        )
        return gson.toJson(featureCollection)
    }

    fun exportToFile(measurements: List<CellMeasurement>, file: File) {
        file.writeText(export(measurements))
    }

    private fun buildFeature(m: CellMeasurement): Map<String, Any?> {
        return mapOf(
            "type" to "Feature",
            "geometry" to mapOf(
                "type" to "Point",
                "coordinates" to listOf(m.longitude, m.latitude) // GeoJSON is lon,lat
            ),
            "properties" to buildProperties(m)
        )
    }

    private fun buildProperties(m: CellMeasurement): Map<String, Any?> {
        return mapOf(
            "timestamp" to m.timestamp,
            "radio" to m.radio.name,
            "mcc" to m.mcc,
            "mnc" to m.mnc,
            "tac" to m.tacLac,
            "cid" to m.cid,
            "pci" to m.pciPsc,
            "earfcn" to m.earfcnArfcn,
            "band" to m.band,
            "rsrp" to m.rsrp,
            "rsrq" to m.rsrq,
            "sinr" to m.sinr,
            "rssi" to m.rssi,
            "is_serving" to m.isRegistered,
            "operator" to m.operatorName,
            "signal_quality" to SignalClassifier.classify(m).name
        ).filterValues { it != null }
    }
}
