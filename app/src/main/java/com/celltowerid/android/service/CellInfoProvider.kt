package com.celltowerid.android.service

import com.celltowerid.android.domain.model.CellMeasurement

interface CellInfoProvider {
    fun getCellMeasurements(
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float?,
        speedMps: Float? = null
    ): List<CellMeasurement>
    fun isAvailable(): Boolean
}
