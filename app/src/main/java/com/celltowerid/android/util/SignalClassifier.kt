package com.celltowerid.android.util

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.domain.model.SignalQuality

object SignalClassifier {
    fun classifyLteRsrp(rsrp: Int): SignalQuality = when {
        rsrp >= -80 -> SignalQuality.EXCELLENT
        rsrp >= -90 -> SignalQuality.GOOD
        rsrp >= -100 -> SignalQuality.FAIR
        rsrp >= -110 -> SignalQuality.POOR
        rsrp >= -120 -> SignalQuality.VERY_POOR
        else -> SignalQuality.NO_SIGNAL
    }

    fun classifyLteSinr(sinr: Int): SignalQuality = when {
        sinr >= 20 -> SignalQuality.EXCELLENT
        sinr >= 13 -> SignalQuality.GOOD
        sinr >= 0 -> SignalQuality.FAIR
        else -> SignalQuality.POOR
    }

    fun classifyNrSsRsrp(ssRsrp: Int): SignalQuality = when {
        ssRsrp >= -80 -> SignalQuality.EXCELLENT
        ssRsrp >= -90 -> SignalQuality.GOOD
        ssRsrp >= -100 -> SignalQuality.FAIR
        ssRsrp >= -110 -> SignalQuality.POOR
        ssRsrp >= -120 -> SignalQuality.VERY_POOR
        else -> SignalQuality.NO_SIGNAL
    }

    fun classifyGsmRssi(rssi: Int): SignalQuality = when {
        rssi >= -70 -> SignalQuality.EXCELLENT
        rssi >= -85 -> SignalQuality.GOOD
        rssi >= -100 -> SignalQuality.FAIR
        rssi >= -110 -> SignalQuality.POOR
        else -> SignalQuality.NO_SIGNAL
    }

    fun classify(measurement: CellMeasurement): SignalQuality {
        return when (measurement.radio) {
            RadioType.LTE -> measurement.rsrp?.let { classifyLteRsrp(it) } ?: SignalQuality.NO_SIGNAL
            RadioType.NR -> measurement.rsrp?.let { classifyNrSsRsrp(it) } ?: SignalQuality.NO_SIGNAL
            RadioType.GSM -> measurement.rssi?.let { classifyGsmRssi(it) } ?: SignalQuality.NO_SIGNAL
            else -> measurement.rssi?.let { classifyGsmRssi(it) } ?: SignalQuality.NO_SIGNAL
        }
    }

    fun getColorForRsrp(rsrp: Int): String = classifyLteRsrp(rsrp).colorHex
}
