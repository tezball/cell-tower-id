package com.terrycollins.celltowerid.domain.model

data class CellMeasurement(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val gpsAccuracy: Float? = null,
    val radio: RadioType,
    val mcc: Int? = null,
    val mnc: Int? = null,
    val tacLac: Int? = null,
    val cid: Long? = null,
    val pciPsc: Int? = null,
    val earfcnArfcn: Int? = null,
    val bandwidth: Int? = null,
    val band: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val rssi: Int? = null,
    val sinr: Int? = null,
    val cqi: Int? = null,
    val timingAdvance: Int? = null,
    val signalLevel: Int? = null,
    val isRegistered: Boolean = false,
    val operatorName: String? = null,
    val speedMps: Float? = null
)
