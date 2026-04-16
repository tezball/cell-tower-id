package com.terrycollins.celltowerid.domain.model

data class CellTower(
    val radio: RadioType,
    val mcc: Int,
    val mnc: Int,
    val tacLac: Int,
    val cid: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rangeMeters: Int? = null,
    val samples: Int? = null,
    val source: String? = null
) {
    val enbId: Int? get() = if (radio == RadioType.LTE) (cid shr 8).toInt() else null
    val sectorId: Int? get() = if (radio == RadioType.LTE) (cid and 0xFF).toInt() else null
}
