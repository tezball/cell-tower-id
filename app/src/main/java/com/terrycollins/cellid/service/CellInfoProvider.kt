package com.terrycollins.cellid.service

import com.terrycollins.cellid.domain.model.CellMeasurement

interface CellInfoProvider {
    fun getCellMeasurements(
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float?,
        speedMps: Float? = null
    ): List<CellMeasurement>
    fun isAvailable(): Boolean
}
