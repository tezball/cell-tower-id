package com.terrycollins.cellid.export

import com.terrycollins.cellid.domain.model.CellMeasurement
import java.io.File

object CsvExporter {
    internal const val HEADER =
        "timestamp,lat,lon,radio,mcc,mnc,tac,cid,pci,earfcn,band,rsrp,rsrq,sinr,rssi,is_serving,operator"

    fun export(measurements: List<CellMeasurement>): String {
        val sb = StringBuilder()
        sb.appendLine(HEADER)
        for (m in measurements) {
            sb.appendLine(buildCsvRow(m))
        }
        return sb.toString()
    }

    fun exportToFile(measurements: List<CellMeasurement>, file: File) {
        file.bufferedWriter().use { writer ->
            writer.appendLine(HEADER)
            for (m in measurements) {
                writer.appendLine(buildCsvRow(m))
            }
        }
    }

    internal fun buildCsvRow(m: CellMeasurement): String {
        return listOf(
            m.timestamp,
            m.latitude,
            m.longitude,
            m.radio.name,
            m.mcc ?: "",
            m.mnc ?: "",
            m.tacLac ?: "",
            m.cid ?: "",
            m.pciPsc ?: "",
            m.earfcnArfcn ?: "",
            m.band ?: "",
            m.rsrp ?: "",
            m.rsrq ?: "",
            m.sinr ?: "",
            m.rssi ?: "",
            if (m.isRegistered) 1 else 0,
            m.operatorName ?: ""
        ).joinToString(",")
    }
}
