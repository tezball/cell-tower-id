package com.celltowerid.android.service

import com.celltowerid.android.domain.model.CellMeasurement

interface CellInfoProvider {
    fun getCellMeasurements(
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float?,
        speedMps: Float? = null
    ): List<CellMeasurement>

    /**
     * Like [getCellMeasurements], but on API 29+ requests a fresh modem
     * read via `TelephonyManager.requestCellInfoUpdate` rather than
     * returning whatever the platform last cached. Used by the Locate
     * feature in driving mode where the polling interval (≤ 250 ms) is
     * shorter than the platform's default cell-info refresh cadence.
     */
    suspend fun getCellMeasurementsFresh(
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float?,
        speedMps: Float? = null,
        timeoutMs: Long = 1500L,
    ): List<CellMeasurement> = getCellMeasurements(latitude, longitude, gpsAccuracy, speedMps)

    fun isAvailable(): Boolean
}
