package com.terrycollins.cellid.service

import com.terrycollins.cellid.data.dao.TowerCacheDao
import com.terrycollins.cellid.domain.model.AnomalyEvent
import com.terrycollins.cellid.domain.model.AnomalySeverity
import com.terrycollins.cellid.domain.model.AnomalyType
import com.terrycollins.cellid.domain.model.CellMeasurement
import com.terrycollins.cellid.domain.model.RadioType
import com.terrycollins.cellid.util.GeoDistance
import com.terrycollins.cellid.util.UsCarriers

class AnomalyDetector(
    private val towerCacheDao: TowerCacheDao,
    private val recentMeasurements: MutableList<CellMeasurement> = mutableListOf()
) {
    companion object {
        private const val MAX_RECENT = 100
        private const val SIGNAL_ANOMALY_THRESHOLD = 20 // dBm above average
        private const val TRANSIENT_WINDOW_MS = 5 * 60 * 1000L // 5 minutes
        private const val DRIVING_TRANSIENT_WINDOW_MS = 20L * 60_000L // 20 minutes
        private const val IMPOSSIBLE_MOVE_METERS = 20_000.0 // 20 km
        private const val DRIVING_SPEED_MPS = 3.0f // ~10 km/h; above walking
        private const val DRIVING_TAC_WINDOW_MS = 60_000L
        private const val DRIVING_TAC_MIN_FLIPS = 3
    }

    // Rolling history of recent TAC flips per operator (driving mode)
    private val tacFlipsByOperator = mutableMapOf<String, MutableList<Long>>()

    // Track recently seen towers for transient detection
    private val towerFirstSeen = mutableMapOf<String, Long>()
    private val towerLastSeen = mutableMapOf<String, Long>()
    private var lastRadioType: RadioType? = null
    private var lastTacLac: Int? = null
    private var lastMcc: Int? = null
    private var lastMnc: Int? = null

    // Deduplication: only alert once per tower/type combination
    private val alertedTowers = mutableSetOf<String>()
    private var cachedTowerCount: Int? = null

    fun analyze(measurement: CellMeasurement): List<AnomalyEvent> {
        val anomalies = mutableListOf<AnomalyEvent>()

        checkUnknownTower(measurement)?.let { anomalies.add(it) }
        checkImpossibleMove(measurement)?.let { anomalies.add(it) }
        checkSignalAnomaly(measurement)?.let { anomalies.add(it) }
        check2gDowngrade(measurement)?.let { anomalies.add(it) }
        checkLacTacChange(measurement)?.let { anomalies.add(it) }
        checkTransientTower(measurement)?.let { anomalies.add(it) }
        checkOperatorMismatch(measurement)?.let { anomalies.add(it) }

        // Update tracking state
        updateState(measurement)

        return anomalies
    }

    fun computeThreatScore(anomalies: List<AnomalyEvent>): Int {
        return anomalies.sumOf { anomaly ->
            when (anomaly.type) {
                AnomalyType.UNKNOWN_TOWER -> 2
                AnomalyType.SIGNAL_ANOMALY -> when (anomaly.severity) {
                    AnomalySeverity.HIGH -> 3
                    AnomalySeverity.MEDIUM -> 2
                    AnomalySeverity.LOW -> 1
                }
                AnomalyType.DOWNGRADE_2G -> 3
                AnomalyType.LAC_TAC_CHANGE -> 2
                AnomalyType.TRANSIENT_TOWER -> 2
                AnomalyType.OPERATOR_MISMATCH -> 3
                AnomalyType.IMPOSSIBLE_MOVE -> 6
            }
        }
    }

    fun getThreatLevel(score: Int): AnomalySeverity = when {
        score >= 6 -> AnomalySeverity.HIGH
        score >= 3 -> AnomalySeverity.MEDIUM
        else -> AnomalySeverity.LOW
    }

    internal fun checkUnknownTower(measurement: CellMeasurement): AnomalyEvent? {
        if (!measurement.isRegistered) return null
        val mcc = measurement.mcc ?: return null
        val mnc = measurement.mnc ?: return null
        val tacLac = measurement.tacLac ?: return null
        val cid = measurement.cid ?: return null

        // Skip if the tower cache is empty -- no baseline to compare against
        if (cachedTowerCount == null) {
            cachedTowerCount = towerCacheDao.getCount()
        }
        if (cachedTowerCount == 0) return null

        // Don't re-alert on the same tower
        val key = "unknown-${measurement.radio}-$mcc-$mnc-$tacLac-$cid"
        if (key in alertedTowers) return null

        val cached = towerCacheDao.findTower(
            measurement.radio.name,
            mcc,
            mnc,
            tacLac,
            cid
        )
        if (cached != null) return null

        alertedTowers.add(key)
        return AnomalyEvent(
            timestamp = measurement.timestamp,
            latitude = measurement.latitude,
            longitude = measurement.longitude,
            type = AnomalyType.UNKNOWN_TOWER,
            severity = AnomalySeverity.MEDIUM,
            description = "Registered to tower not in cache: ${measurement.radio} MCC=$mcc MNC=$mnc TAC/LAC=$tacLac CID=$cid",
            cellRadio = measurement.radio,
            cellMcc = mcc,
            cellMnc = mnc,
            cellTacLac = tacLac,
            cellCid = cid,
            cellPci = measurement.pciPsc,
            signalStrength = measurement.rsrp ?: measurement.rssi
        )
    }

    /**
     * Geographic-consistency check: if we have a cached known position for
     * this cell (exact CID match, or a sister sector sharing the same LTE
     * eNodeB ID) and the current GPS is impossibly far from it, flag a
     * HIGH-severity IMPOSSIBLE_MOVE — a real macro base station can't
     * teleport, so this is the strongest "cloned cell" signal we can get
     * from Android's public APIs.
     */
    internal fun checkImpossibleMove(measurement: CellMeasurement): AnomalyEvent? {
        if (!measurement.isRegistered) return null
        val mcc = measurement.mcc ?: return null
        val mnc = measurement.mnc ?: return null
        val tacLac = measurement.tacLac ?: return null
        val cid = measurement.cid ?: return null

        val key = "move-${measurement.radio}-$mcc-$mnc-$cid"
        if (key in alertedTowers) return null

        val exact = towerCacheDao.findTower(measurement.radio.name, mcc, mnc, tacLac, cid)
        val knownLat: Double?
        val knownLon: Double?
        val source: String
        if (exact?.latitude != null && exact.longitude != null) {
            knownLat = exact.latitude
            knownLon = exact.longitude
            source = "cached position"
        } else if (measurement.radio == RadioType.LTE) {
            val enbId = cid shr 8
            val minCid = enbId shl 8
            val maxCid = minCid + 255
            val sister = towerCacheDao.findAnyByCidRange(
                measurement.radio.name, mcc, mnc, minCid, maxCid
            )
            if (sister?.latitude != null && sister.longitude != null) {
                knownLat = sister.latitude
                knownLon = sister.longitude
                source = "sister sector of eNodeB $enbId"
            } else return null
        } else return null

        val distanceM = GeoDistance.haversineMeters(
            knownLat!!, knownLon!!, measurement.latitude, measurement.longitude
        )
        if (distanceM < IMPOSSIBLE_MOVE_METERS) return null

        alertedTowers.add(key)
        val km = (distanceM / 1000.0).toInt()
        return AnomalyEvent(
            timestamp = measurement.timestamp,
            latitude = measurement.latitude,
            longitude = measurement.longitude,
            type = AnomalyType.IMPOSSIBLE_MOVE,
            severity = AnomalySeverity.HIGH,
            description = "Cell ${measurement.radio} MCC=$mcc MNC=$mnc CID=$cid is $km km from its $source — tower appears to have moved, possible cloned base station.",
            cellRadio = measurement.radio,
            cellMcc = mcc,
            cellMnc = mnc,
            cellTacLac = tacLac,
            cellCid = cid,
            cellPci = measurement.pciPsc,
            signalStrength = measurement.rsrp ?: measurement.rssi
        )
    }

    internal fun checkSignalAnomaly(measurement: CellMeasurement): AnomalyEvent? {
        // 2.2 — require complete cell identity
        if (!measurement.isRegistered) return null
        if (measurement.mcc == null || measurement.mnc == null ||
            measurement.tacLac == null || measurement.cid == null) return null
        // 2.1 — speed gate: handover noise at driving speed
        if (measurement.speedMps != null && measurement.speedMps > DRIVING_SPEED_MPS) return null
        val currentRsrp = measurement.rsrp ?: return null
        val operatorKey = "${measurement.mcc}-${measurement.mnc}"

        val sameOperatorMeasurements = recentMeasurements.filter {
            "${it.mcc}-${it.mnc}" == operatorKey && it.rsrp != null
        }
        if (sameOperatorMeasurements.size < 5) return null

        val averageRsrp = sameOperatorMeasurements.mapNotNull { it.rsrp }.average()

        // RSRP is negative, so "stronger" means closer to 0.
        // A signal that is anomalously strong: currentRsrp - averageRsrp > threshold
        val difference = currentRsrp - averageRsrp
        if (difference <= SIGNAL_ANOMALY_THRESHOLD) return null

        val severity = when {
            difference > 35 -> AnomalySeverity.HIGH
            difference > 25 -> AnomalySeverity.MEDIUM
            else -> AnomalySeverity.LOW
        }

        return AnomalyEvent(
            timestamp = measurement.timestamp,
            latitude = measurement.latitude,
            longitude = measurement.longitude,
            type = AnomalyType.SIGNAL_ANOMALY,
            severity = severity,
            description = "Signal ${currentRsrp}dBm is ${difference.toInt()}dBm above average (${averageRsrp.toInt()}dBm) for operator $operatorKey",
            cellRadio = measurement.radio,
            cellMcc = measurement.mcc,
            cellMnc = measurement.mnc,
            cellTacLac = measurement.tacLac,
            cellCid = measurement.cid,
            cellPci = measurement.pciPsc,
            signalStrength = currentRsrp
        )
    }

    internal fun check2gDowngrade(measurement: CellMeasurement): AnomalyEvent? {
        val previous = lastRadioType ?: return null
        if (previous != RadioType.LTE && previous != RadioType.NR) return null
        if (measurement.radio != RadioType.GSM) return null
        if (!measurement.isRegistered) return null

        // Only alert once per downgrade event (until we go back to LTE/NR)
        val key = "2g-downgrade"
        if (key in alertedTowers) return null
        alertedTowers.add(key)

        return AnomalyEvent(
            timestamp = measurement.timestamp,
            latitude = measurement.latitude,
            longitude = measurement.longitude,
            type = AnomalyType.DOWNGRADE_2G,
            severity = AnomalySeverity.HIGH,
            description = "Downgraded from $previous to GSM (2G). Possible forced downgrade attack.",
            cellRadio = measurement.radio,
            cellMcc = measurement.mcc,
            cellMnc = measurement.mnc,
            cellTacLac = measurement.tacLac,
            cellCid = measurement.cid,
            cellPci = measurement.pciPsc,
            signalStrength = measurement.rssi
        )
    }

    internal fun checkLacTacChange(measurement: CellMeasurement): AnomalyEvent? {
        val previousTacLac = lastTacLac ?: return null
        val currentTacLac = measurement.tacLac ?: return null
        if (!measurement.isRegistered) return null
        if (previousTacLac == currentTacLac) return null

        // Only flag if same operator (not a handoff to different carrier)
        val sameOperator = measurement.mcc == lastMcc && measurement.mnc == lastMnc
        if (!sameOperator) return null

        // Driving mode: require 3 flips within 60s before firing. Normal
        // boundary ping-pong at driving speed is 1-2 flips, so ignore those.
        if (measurement.speedMps != null && measurement.speedMps > DRIVING_SPEED_MPS) {
            val opKey = "${measurement.mcc}-${measurement.mnc}"
            val now = measurement.timestamp
            val flips = tacFlipsByOperator.getOrPut(opKey) { mutableListOf() }
            flips.add(now)
            // Drop flips older than the window
            flips.removeAll { now - it > DRIVING_TAC_WINDOW_MS }
            if (flips.size < DRIVING_TAC_MIN_FLIPS) return null
        }

        // Only alert once per specific TAC change
        val key = "lac-$previousTacLac-$currentTacLac"
        if (key in alertedTowers) return null
        alertedTowers.add(key)

        return AnomalyEvent(
            timestamp = measurement.timestamp,
            latitude = measurement.latitude,
            longitude = measurement.longitude,
            type = AnomalyType.LAC_TAC_CHANGE,
            severity = AnomalySeverity.MEDIUM,
            description = "TAC/LAC changed from $previousTacLac to $currentTacLac on same operator (MCC=${measurement.mcc} MNC=${measurement.mnc})",
            cellRadio = measurement.radio,
            cellMcc = measurement.mcc,
            cellMnc = measurement.mnc,
            cellTacLac = currentTacLac,
            cellCid = measurement.cid,
            cellPci = measurement.pciPsc,
            signalStrength = measurement.rsrp ?: measurement.rssi
        )
    }

    internal fun checkTransientTower(measurement: CellMeasurement): AnomalyEvent? {
        val towerKey = towerKey(measurement) ?: return null
        val now = measurement.timestamp
        val window = if (measurement.speedMps != null && measurement.speedMps > DRIVING_SPEED_MPS) {
            DRIVING_TRANSIENT_WINDOW_MS
        } else {
            TRANSIENT_WINDOW_MS
        }

        // Clean up old entries beyond the transient window
        val expiredKeys = towerLastSeen.filter { (key, lastSeen) ->
            now - lastSeen > window && key != towerKey
        }.keys
        for (key in expiredKeys) {
            val firstSeen = towerFirstSeen[key] ?: continue
            val lastSeen = towerLastSeen[key] ?: continue
            val duration = lastSeen - firstSeen

            // If the tower was visible for less than the transient window, it's suspicious
            if (duration < window) {
                towerFirstSeen.remove(key)
                towerLastSeen.remove(key)

                return AnomalyEvent(
                    timestamp = now,
                    latitude = measurement.latitude,
                    longitude = measurement.longitude,
                    type = AnomalyType.TRANSIENT_TOWER,
                    severity = AnomalySeverity.MEDIUM,
                    description = "Tower $key appeared briefly (${duration / 1000}s) then disappeared. Possible mobile IMSI catcher.",
                    cellRadio = measurement.radio,
                    cellMcc = measurement.mcc,
                    cellMnc = measurement.mnc,
                    cellTacLac = measurement.tacLac,
                    cellCid = measurement.cid,
                    cellPci = measurement.pciPsc,
                    signalStrength = measurement.rsrp ?: measurement.rssi
                )
            }
            towerFirstSeen.remove(key)
            towerLastSeen.remove(key)
        }

        // Track current tower
        if (towerKey !in towerFirstSeen) {
            towerFirstSeen[towerKey] = now
        }
        towerLastSeen[towerKey] = now

        return null
    }

    internal fun checkOperatorMismatch(measurement: CellMeasurement): AnomalyEvent? {
        val mcc = measurement.mcc ?: return null
        val mnc = measurement.mnc ?: return null
        if (!measurement.isRegistered) return null

        // Only check US networks
        if (!UsCarriers.isUsNetwork(mcc)) return null
        if (UsCarriers.isKnownCarrier(mcc, mnc)) return null

        // Only alert once per unknown operator
        val key = "operator-$mcc-$mnc"
        if (key in alertedTowers) return null
        alertedTowers.add(key)

        return AnomalyEvent(
            timestamp = measurement.timestamp,
            latitude = measurement.latitude,
            longitude = measurement.longitude,
            type = AnomalyType.OPERATOR_MISMATCH,
            severity = AnomalySeverity.HIGH,
            description = "Registered to unknown US operator MCC=$mcc MNC=$mnc. Not in known carrier database.",
            cellRadio = measurement.radio,
            cellMcc = mcc,
            cellMnc = mnc,
            cellTacLac = measurement.tacLac,
            cellCid = measurement.cid,
            cellPci = measurement.pciPsc,
            signalStrength = measurement.rsrp ?: measurement.rssi
        )
    }

    private fun updateState(measurement: CellMeasurement) {
        if (measurement.isRegistered) {
            // Clear 2G downgrade flag when back on LTE/NR (allows re-detection)
            if (measurement.radio == RadioType.LTE || measurement.radio == RadioType.NR) {
                alertedTowers.remove("2g-downgrade")
            }
            lastRadioType = measurement.radio
            lastTacLac = measurement.tacLac
            lastMcc = measurement.mcc
            lastMnc = measurement.mnc
        }

        recentMeasurements.add(measurement)
        if (recentMeasurements.size > MAX_RECENT) {
            recentMeasurements.removeAt(0)
        }
    }

    private fun towerKey(measurement: CellMeasurement): String? {
        val mcc = measurement.mcc ?: return null
        val mnc = measurement.mnc ?: return null
        val tacLac = measurement.tacLac ?: return null
        val cid = measurement.cid ?: return null
        return "${measurement.radio}-$mcc-$mnc-$tacLac-$cid"
    }
}
