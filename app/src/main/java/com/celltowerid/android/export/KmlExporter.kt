package com.celltowerid.android.export

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.SignalQuality
import com.celltowerid.android.util.SignalClassifier
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
        val name = xmlEscape("${m.radio.name} ${m.cid ?: "?"}")
        val desc = cdataEscape(buildDescription(m))

        sb.appendLine("  <Placemark>")
        sb.appendLine("    <name>$name</name>")
        sb.appendLine("    <description><![CDATA[$desc]]></description>")
        sb.appendLine("    <styleUrl>#style_${quality.name}</styleUrl>")
        sb.appendLine("    <Point><coordinates>${m.longitude},${m.latitude},0</coordinates></Point>")
        // Machine-readable mirror of the human description. KmlImporter parses
        // these <Data> entries; the description text is for the KML viewer only.
        appendExtendedData(sb, m)
        sb.appendLine("  </Placemark>")
    }

    private fun appendExtendedData(sb: StringBuilder, m: CellMeasurement) {
        sb.appendLine("    <ExtendedData>")
        appendData(sb, "timestamp", m.timestamp.toString())
        appendData(sb, "radio", m.radio.name)
        m.mcc?.let { appendData(sb, "mcc", it.toString()) }
        m.mnc?.let { appendData(sb, "mnc", it.toString()) }
        m.tacLac?.let { appendData(sb, "tac", it.toString()) }
        m.cid?.let { appendData(sb, "cid", it.toString()) }
        m.pciPsc?.let { appendData(sb, "pci", it.toString()) }
        m.earfcnArfcn?.let { appendData(sb, "earfcn", it.toString()) }
        m.band?.let { appendData(sb, "band", it.toString()) }
        m.rsrp?.let { appendData(sb, "rsrp", it.toString()) }
        m.rsrq?.let { appendData(sb, "rsrq", it.toString()) }
        m.sinr?.let { appendData(sb, "sinr", it.toString()) }
        m.rssi?.let { appendData(sb, "rssi", it.toString()) }
        appendData(sb, "is_serving", if (m.isRegistered) "1" else "0")
        m.operatorName?.let { appendData(sb, "operator", it) }
        sb.appendLine("    </ExtendedData>")
    }

    private fun appendData(sb: StringBuilder, name: String, value: String) {
        sb.append("      <Data name=\"")
            .append(xmlEscape(name))
            .append("\"><value>")
            .append(xmlEscape(value))
            .appendLine("</value></Data>")
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

    internal fun xmlEscape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    // CDATA terminators inside content break the parser. Splice the section so
    // ]]> becomes ]]]]><![CDATA[> -- the literal text survives intact.
    internal fun cdataEscape(s: String): String =
        s.replace("]]>", "]]]]><![CDATA[>")

    // KML uses AABBGGRR format (alpha, blue, green, red)
    internal fun hexToAbgr(hex: String): String {
        val clean = hex.removePrefix("#")
        val r = clean.substring(0, 2)
        val g = clean.substring(2, 4)
        val b = clean.substring(4, 6)
        return "ff${b}${g}${r}"
    }
}
