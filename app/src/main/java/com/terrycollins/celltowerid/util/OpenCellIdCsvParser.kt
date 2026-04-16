package com.terrycollins.celltowerid.util

import com.terrycollins.celltowerid.domain.model.CellTower
import com.terrycollins.celltowerid.domain.model.RadioType

object OpenCellIdCsvParser {

    private const val SOURCE = "opencellid"

    fun parseLine(line: String): CellTower? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val parts = trimmed.split(',')
        if (parts.size < 10) return null

        val radio = parseRadio(parts[0]) ?: return null
        val mcc = parts[1].toIntOrNull() ?: return null
        val mnc = parts[2].toIntOrNull() ?: return null
        val tacLac = parts[3].toIntOrNull() ?: return null
        val cid = parts[4].toLongOrNull() ?: return null
        val lon = parts[6].toDoubleOrNull() ?: return null
        val lat = parts[7].toDoubleOrNull() ?: return null
        val range = parts[8].toIntOrNull()
        val samples = parts[9].toIntOrNull()

        return CellTower(
            radio = radio,
            mcc = mcc,
            mnc = mnc,
            tacLac = tacLac,
            cid = cid,
            latitude = lat,
            longitude = lon,
            rangeMeters = range,
            samples = samples,
            source = SOURCE
        )
    }

    fun parseAll(lines: Sequence<String>): Sequence<CellTower> =
        lines.mapNotNull { parseLine(it) }

    private fun parseRadio(value: String): RadioType? = when (value.uppercase()) {
        "LTE" -> RadioType.LTE
        "GSM" -> RadioType.GSM
        "UMTS", "WCDMA" -> RadioType.WCDMA
        "NR", "5G" -> RadioType.NR
        "CDMA" -> RadioType.CDMA
        else -> null
    }
}
