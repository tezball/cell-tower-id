package com.terrycollins.celltowerid.util

import java.util.Locale

object TowerInfoFormatter {

    fun formatTitle(radio: String, mcc: Int, mnc: Int): String {
        val carrier = UsCarriers.getCarrierName(mcc, mnc) ?: "$mcc/$mnc"
        return "$radio - $carrier"
    }

    fun formatIdentity(radio: String, cid: Long, tacLac: Int): String {
        val areaLabel = if (radio == "GSM" || radio == "WCDMA") "LAC" else "TAC"
        val base = "CID: $cid | $areaLabel: $tacLac"
        if (radio != "LTE") return base
        val enbId = cid shr 8
        val sectorId = cid and 0xFF
        return "$base | eNB: $enbId Sector: $sectorId"
    }

    fun formatLocation(latitude: Double, longitude: Double, rangeMeters: Int?): String {
        val coords = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
        return if (rangeMeters != null) "$coords (\u00B1${rangeMeters}m)" else coords
    }
}
