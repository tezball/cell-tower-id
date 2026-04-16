package com.terrycollins.celltowerid.service

import com.terrycollins.celltowerid.domain.model.CellMeasurement

interface CellInfoProvider {
    fun getCellMeasurements(
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float?,
        speedMps: Float? = null
    ): List<CellMeasurement>
    fun isAvailable(): Boolean
}
