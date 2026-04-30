package com.celltowerid.android.ui

import android.content.Context
import android.content.Intent
import com.celltowerid.android.domain.model.AnomalyEvent

/**
 * Builds the [TowerDetailActivity] launch intent for an [AnomalyEvent].
 *
 * Centralized so the alerts list and the map popup populate the same set of
 * extras — otherwise the two call sites would drift over time and the detail
 * screen's "alert" card and signal tables would render differently depending
 * on where the user came from.
 */
object AnomalyIntentBuilder {

    fun build(context: Context, anomaly: AnomalyEvent): Intent {
        return Intent(context, TowerDetailActivity::class.java).apply {
            putExtra(TowerDetailActivity.EXTRA_RADIO, anomaly.cellRadio?.name ?: "UNKNOWN")
            putExtra(TowerDetailActivity.EXTRA_LATITUDE, anomaly.latitude ?: 0.0)
            putExtra(TowerDetailActivity.EXTRA_LONGITUDE, anomaly.longitude ?: 0.0)
            putExtra(TowerDetailActivity.EXTRA_TIMESTAMP, anomaly.timestamp)
            putExtra(TowerDetailActivity.EXTRA_IS_REGISTERED, anomaly.isRegistered)
            anomaly.cellMcc?.let { putExtra(TowerDetailActivity.EXTRA_MCC, it) }
            anomaly.cellMnc?.let { putExtra(TowerDetailActivity.EXTRA_MNC, it) }
            anomaly.cellTacLac?.let { putExtra(TowerDetailActivity.EXTRA_TAC_LAC, it) }
            anomaly.cellCid?.let { putExtra(TowerDetailActivity.EXTRA_CID, it) }
            anomaly.cellPci?.let { putExtra(TowerDetailActivity.EXTRA_PCI, it) }
            anomaly.earfcnArfcn?.let { putExtra(TowerDetailActivity.EXTRA_EARFCN, it) }
            anomaly.band?.let { putExtra(TowerDetailActivity.EXTRA_BAND, it) }
            anomaly.bandwidth?.let { putExtra(TowerDetailActivity.EXTRA_BANDWIDTH, it) }
            anomaly.rsrp?.let { putExtra(TowerDetailActivity.EXTRA_RSRP, it) }
            anomaly.rsrq?.let { putExtra(TowerDetailActivity.EXTRA_RSRQ, it) }
            anomaly.rssi?.let { putExtra(TowerDetailActivity.EXTRA_RSSI, it) }
            anomaly.sinr?.let { putExtra(TowerDetailActivity.EXTRA_SINR, it) }
            anomaly.cqi?.let { putExtra(TowerDetailActivity.EXTRA_CQI, it) }
            anomaly.timingAdvance?.let { putExtra(TowerDetailActivity.EXTRA_TA, it) }
            anomaly.signalLevel?.let { putExtra(TowerDetailActivity.EXTRA_SIGNAL_LEVEL, it) }
            anomaly.operatorName?.let { putExtra(TowerDetailActivity.EXTRA_OPERATOR, it) }
            anomaly.gpsAccuracy?.let { putExtra(TowerDetailActivity.EXTRA_GPS_ACCURACY, it) }
            putExtra(TowerDetailActivity.EXTRA_ALERT_TYPE, anomaly.type.name)
            putExtra(TowerDetailActivity.EXTRA_ALERT_SEVERITY, anomaly.severity.name)
            putExtra(TowerDetailActivity.EXTRA_ALERT_DESCRIPTION, anomaly.description)
        }
    }
}
