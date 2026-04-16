package com.terrycollins.celltowerid.export

import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.SignalQuality
import com.terrycollins.celltowerid.util.SignalClassifier
import java.io.File

object KmlExporter {

    fun export(measurements: List<CellMeasurement>): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        sb.appendLine("<Document>")
        sb.appendLine("<name>Cell Tower ID Export</name>")
        sb.appendLine("<description>Cell tower measurements exported from Cell Tower ID</description>")

        appendStyles(sb)

        for (m in measurements) {
            appendPlacemark(sb, m)
        }

        sb.appendLine("</Document>")
        sb.appendLine("</kml>")
        return sb.toString()
    }

    fun exportToFile(measurements: List<CellMeasurement>, file: File) {
        file.writeText(export(measurements))
    }

    private fun appendStyles(sb: StringBuilder) {
        for (quality in SignalQuality.entries) {
            val abgr = hexToAbgr(quality.colorHex)
            sb.appendLine("""  <Style id="style_${quality.name}">""")
            sb.appendLine("""    <IconStyle><color>$abgr</color></IconStyle>""")
            sb.appendLine("""  </Style>""")
        }
    }

    private fun appendPlacemark(sb: StringBuilder, m: CellMeasurement) {
        val quality = SignalClassifier.classify(m)
        val name = "${m.radio.name} ${m.cid ?: "?"}"
        val desc = buildDescription(m)

        sb.appendLine("  <Placemark>")
        sb.appendLine("    <name>$name</name>")
        sb.appendLine("    <description><![CDATA[$desc]]></description>")
        sb.appendLine("    <styleUrl>#style_${quality.name}</styleUrl>")
        sb.appendLine("    <Point><coordinates>${m.longitude},${m.latitude},0</coordinates></Point>")
        sb.appendLine("  </Placemark>")
    }

    private fun buildDescription(m: CellMeasurement): String {
        val parts = mutableListOf<String>()
        parts.add("Radio: ${m.radio.name}")
        m.mcc?.let { mcc -> m.mnc?.let { mnc -> parts.add("MCC/MNC: $mcc/$mnc") } }
        m.tacLac?.let { parts.add("TAC/LAC: $it") }
        m.cid?.let { parts.add("CID: $it") }
        m.rsrp?.let { parts.add("RSRP: $it dBm") }
        m.rsrq?.let { parts.add("RSRQ: $it dB") }
        m.sinr?.let { parts.add("SINR: $it dB") }
        m.operatorName?.let { parts.add("Operator: $it") }
        parts.add("Serving: ${if (m.isRegistered) "Yes" else "No"}")
        return parts.joinToString("<br/>")
    }

    // KML uses AABBGGRR format (alpha, blue, green, red)
    internal fun hexToAbgr(hex: String): String {
        val clean = hex.removePrefix("#")
        val r = clean.substring(0, 2)
        val g = clean.substring(2, 4)
        val b = clean.substring(4, 6)
        return "ff${b}${g}${r}"
    }
}
