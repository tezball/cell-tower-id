package com.terrycollins.celltowerid.domain.model

data class AnomalyEvent(
    val id: Long = 0,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val type: AnomalyType,
    val severity: AnomalySeverity,
    val description: String,
    val cellRadio: RadioType? = null,
    val cellMcc: Int? = null,
    val cellMnc: Int? = null,
    val cellTacLac: Int? = null,
    val cellCid: Long? = null,
    val cellPci: Int? = null,
    val signalStrength: Int? = null,
    val dismissed: Boolean = false
)
