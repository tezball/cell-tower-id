package com.terrycollins.celltowerid.export

import com.terrycollins.celltowerid.domain.model.CellMeasurement
import java.io.File

object CsvExporter {
    internal const val HEADER =
        "timestamp,lat,lon,radio,mcc,mnc,tac,cid,pci,earfcn,band,rsrp,rsrq,sinr,rssi,is_serving,operator"

    private const val UTF8_BOM = "\uFEFF"

    // Excel/Sheets execute leading =, +, -, @ and treat tab/CR as cell separators.
    private val FORMULA_TRIGGERS = setOf('=', '+', '-', '@', '\t', '\r')

    fun export(measurements: List<CellMeasurement>): String {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        sb.appendLine(HEADER)
        for (m in measurements) {
            sb.appendLine(buildCsvRow(m))
        }
        return sb.toString()
    }

    fun exportToFile(measurements: List<CellMeasurement>, file: File) {
        file.bufferedWriter().use { writer ->
            writer.write(UTF8_BOM)
            writer.appendLine(HEADER)
            for (m in measurements) {
                writer.appendLine(buildCsvRow(m))
            }
        }
    }

    internal fun buildCsvRow(m: CellMeasurement): String {
        return listOf(
            m.timestamp.toString(),
            m.latitude.toString(),
            m.longitude.toString(),
            m.radio.name,
            (m.mcc ?: "").toString(),
            (m.mnc ?: "").toString(),
            (m.tacLac ?: "").toString(),
            (m.cid ?: "").toString(),
            (m.pciPsc ?: "").toString(),
            (m.earfcnArfcn ?: "").toString(),
            (m.band ?: "").toString(),
            (m.rsrp ?: "").toString(),
            (m.rsrq ?: "").toString(),
            (m.sinr ?: "").toString(),
            (m.rssi ?: "").toString(),
            if (m.isRegistered) "1" else "0",
            csvField(m.operatorName)
        ).joinToString(",")
    }

    internal fun csvField(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        // Defuse formula injection: prefix a single quote so spreadsheet apps
        // treat the cell as a literal string instead of executing it.
        val defused = if (value.first() in FORMULA_TRIGGERS) "'$value" else value
        // RFC 4180 quoting for any field containing the delimiter, quote, or newline.
        val needsQuoting = defused.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) {
            "\"" + defused.replace("\"", "\"\"") + "\""
        } else {
            defused
        }
    }
}
