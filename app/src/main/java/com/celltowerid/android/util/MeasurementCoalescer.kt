package com.celltowerid.android.util

import com.celltowerid.android.domain.model.CellMeasurement

/**
 * Fills nullable fields on a "current" measurement with the most recent
 * non-null value from history. Cell identity (radio, MCC/MNC/TAC/CID/PCI),
 * position, and timestamp always come from `current` — only signal/radio-config
 * fields are coalesced. The motivation: device cellular stacks drop fields
 * intermittently (TA, CQI, RSRQ, etc.), so the latest scan for a tower may
 * have NULLs that a recent scan captured. Surfacing those NULLs as empty
 * rows on the tower-detail screen makes the cells-list and alerts-list
 * entry paths look inconsistent for the same tower.
 */
object MeasurementCoalescer {

    fun coalesce(current: CellMeasurement, history: List<CellMeasurement>): CellMeasurement {
        if (history.isEmpty()) return current

        val newestFirst = history.sortedByDescending { it.timestamp }

        return current.copy(
            rsrp = current.rsrp ?: newestFirst.firstNonNull { it.rsrp },
            rsrq = current.rsrq ?: newestFirst.firstNonNull { it.rsrq },
            rssi = current.rssi ?: newestFirst.firstNonNull { it.rssi },
            sinr = current.sinr ?: newestFirst.firstNonNull { it.sinr },
            cqi = current.cqi ?: newestFirst.firstNonNull { it.cqi },
            timingAdvance = current.timingAdvance ?: newestFirst.firstNonNull { it.timingAdvance },
            signalLevel = current.signalLevel ?: newestFirst.firstNonNull { it.signalLevel },
            earfcnArfcn = current.earfcnArfcn ?: newestFirst.firstNonNull { it.earfcnArfcn },
            band = current.band ?: newestFirst.firstNonNull { it.band },
            bandwidth = current.bandwidth ?: newestFirst.firstNonNull { it.bandwidth },
            operatorName = current.operatorName ?: newestFirst.firstNonNull { it.operatorName },
            gpsAccuracy = current.gpsAccuracy ?: newestFirst.firstNonNull { it.gpsAccuracy },
        )
    }

    private inline fun <T : Any> List<CellMeasurement>.firstNonNull(
        selector: (CellMeasurement) -> T?
    ): T? {
        for (m in this) selector(m)?.let { return it }
        return null
    }
}
