package com.celltowerid.android.service

import com.celltowerid.android.data.dao.MeasurementDao
import com.celltowerid.android.data.dao.TowerCacheDao
import com.celltowerid.android.domain.model.AnomalyEvent
import com.celltowerid.android.domain.model.AnomalySeverity
import com.celltowerid.android.domain.model.AnomalyType
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.util.GeoDistance
import com.celltowerid.android.util.UsCarriers
import kotlin.math.cos
import kotlin.math.max

class AnomalyDetector(
    private val towerCacheDao: TowerCacheDao,
    private val measurementDao: MeasurementDao? = null,
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
        private const val MAX_ALERTED_TOWERS = 500
        private const val MAX_TRACKED_TOWERS = 200
        private const val PROXIMITY_MAX_TA = 1
        private const val PROXIMITY_MIN_RSRP = -105
        private const val PROXIMITY_MAX_RSRP = -85
        private const val POPUP_RADIUS_METERS = 2_000.0
        private const val POPUP_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        private const val POPUP_MIN_PRIOR_MEASUREMENTS = 20
        private const val METERS_PER_DEGREE_LAT = 111_320.0
    }

    private val tacFlipsByOperator = mutableMapOf<String, MutableList<Long>>()

    private val towerFirstSeen = mutableMapOf<String, Long>()
    private val towerLastSeen = mutableMapOf<String, Long>()
    private var lastRadioType: RadioType? = null
    private var lastTacLac: Int? = null
    private var lastMcc: Int? = null
    private var lastMnc: Int? = null

    private val alertedTowers = object : LinkedHashSet<String>() {
        override fun add(element: String): Boolean =
            super.add(element).also {
                if (size > MAX_ALERTED_TOWERS) iterator().apply { next(); remove() }
            }
    }

    fun analyze(measurement: CellMeasurement): List<AnomalyEvent> {
        val anomalies = mutableListOf<AnomalyEvent>()

        checkImpossibleMove(measurement)?.let { anomalies.add(it) }
        checkPciInstability(measurement)?.let { anomalies.add(it) }
        checkSignalAnomaly(measurement)?.let { anomalies.add(it) }
        checkRadioDowngrade(measurement)?.let { anomalies.add(it) }
        checkLacTacChange(measurement)?.let { anomalies.add(it) }
        checkTransientTower(measurement)?.let { anomalies.add(it) }
        checkPopupTower(measurement)?.let { anomalies.add(it) }
        checkOperatorMismatch(measurement)?.let { anomalies.add(it) }
        checkSuspiciousProximity(measurement)?.let { anomalies.add(it) }

        updateState(measurement)

        return anomalies
    }

    fun computeThreatScore(anomalies: List<AnomalyEvent>): Int {
        return anomalies.sumOf { anomaly ->
            when (anomaly.type) {
                AnomalyType.SIGNAL_ANOMALY -> when (anomaly.severity) {
                    AnomalySeverity.HIGH -> 3
                    AnomalySeverity.MEDIUM -> 2
                    AnomalySeverity.LOW -> 1
                }
                AnomalyType.DOWNGRADE_2G -> 3
                AnomalyType.DOWNGRADE_3G -> 2
                AnomalyType.LAC_TAC_CHANGE -> 2
                AnomalyType.TRANSIENT_TOWER -> 2
                AnomalyType.OPERATOR_MISMATCH -> 3
                AnomalyType.IMPOSSIBLE_MOVE -> 6
                AnomalyType.SUSPICIOUS_PROXIMITY -> 3
                AnomalyType.PCI_INSTABILITY -> 2
                AnomalyType.POPUP_TOWER -> 2
            }
        }
    }

    fun getThreatLevel(score: Int): AnomalySeverity = when {
        score >= 6 -> AnomalySeverity.HIGH
        score >= 3 -> AnomalySeverity.MEDIUM
        else -> AnomalySeverity.LOW
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

    /**
     * PCI instability: the same cell identity (mcc, mnc, tacLac, cid) is
     * reporting a different physical cell id than what we previously recorded.
     * Real base stations do not change their PCI/PSC. A mismatch suggests a
     * cloned cell or an IMSI catcher impersonating a known tower.
     */
    internal fun checkPciInstability(measurement: CellMeasurement): AnomalyEvent? {
        if (!measurement.isRegistered) return null
        val mcc = measurement.mcc ?: return null
        val mnc = measurement.mnc ?: return null
        val tacLac = measurement.tacLac ?: return null
        val cid = measurement.cid ?: return null
        val currentPci = measurement.pciPsc ?: return null

        val key = "pci-${measurement.radio}-$mcc-$mnc-$tacLac-$cid"
        if (key in alertedTowers) return null

        val cached = towerCacheDao.findTower(measurement.radio.name, mcc, mnc, tacLac, cid)
        val cachedPci = cached?.pci ?: return null
        if (cachedPci == currentPci) return null

        alertedTowers.add(key)
        return AnomalyEvent(
            timestamp = measurement.timestamp,
            latitude = measurement.latitude,
            longitude = measurement.longitude,
            type = AnomalyType.PCI_INSTABILITY,
            severity = AnomalySeverity.MEDIUM,
            description = "Cell ${measurement.radio} MCC=$mcc MNC=$mnc CID=$cid changed PCI from $cachedPci to $currentPci — possible cloned cell.",
            cellRadio = measurement.radio,
            cellMcc = mcc,
            cellMnc = mnc,
            cellTacLac = tacLac,
            cellCid = cid,
            cellPci = currentPci,
            signalStrength = measurement.rsrp ?: measurement.rssi
        )
    }

    internal fun checkSignalAnomaly(measurement: CellMeasurement): AnomalyEvent? {
        if (!measurement.isRegistered) return null
        if (measurement.mcc == null || measurement.mnc == null ||
            measurement.tacLac == null || measurement.cid == null) return null
        if (measurement.speedMps != null && measurement.speedMps > DRIVING_SPEED_MPS) return null
        val currentRsrp = measurement.rsrp ?: return null
        val operatorKey = "${measurement.mcc}-${measurement.mnc}"

        val sameOperatorMeasurements = recentMeasurements.filter {
            "${it.mcc}-${it.mnc}" == operatorKey && it.rsrp != null
        }
        if (sameOperatorMeasurements.size < 5) return null

        val averageRsrp = sameOperatorMeasurements.mapNotNull { it.rsrp }.average()

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

    /**
     * Flags forced transitions from a modern radio technology (LTE/NR) to an
     * older one (GSM = 2G, WCDMA/CDMA = 3G). WCDMA → GSM is treated as a
     * normal network handoff and is not flagged — only the step down from
     * LTE/NR is considered a potential downgrade attack.
     */
    internal fun checkRadioDowngrade(measurement: CellMeasurement): AnomalyEvent? {
        val previous = lastRadioType ?: return null
        if (previous != RadioType.LTE && previous != RadioType.NR) return null
        if (!measurement.isRegistered) return null

        return when (measurement.radio) {
            RadioType.GSM -> buildDowngradeEvent(
                measurement,
                previous,
                AnomalyType.DOWNGRADE_2G,
                AnomalySeverity.HIGH,
                "2g-downgrade",
                "Downgraded from $previous to GSM (2G). Possible forced downgrade attack."
            )
            RadioType.WCDMA, RadioType.CDMA -> buildDowngradeEvent(
                measurement,
                previous,
                AnomalyType.DOWNGRADE_3G,
                AnomalySeverity.MEDIUM,
                "3g-downgrade",
                "Downgraded from $previous to ${measurement.radio} (3G). Forced downgrades can precede a 2G attack."
            )
            else -> null
        }
    }

    private fun buildDowngradeEvent(
        measurement: CellMeasurement,
        previous: RadioType,
        type: AnomalyType,
        severity: AnomalySeverity,
        alertKey: String,
        description: String
    ): AnomalyEvent? {
        if (alertKey in alertedTowers) return null
        alertedTowers.add(alertKey)
        return AnomalyEvent(
            timestamp = measurement.timestamp,
            latitude = measurement.latitude,
            longitude = measurement.longitude,
            type = type,
            severity = severity,
            description = description,
            cellRadio = measurement.radio,
            cellMcc = measurement.mcc,
            cellMnc = measurement.mnc,
            cellTacLac = measurement.tacLac,
            cellCid = measurement.cid,
            cellPci = measurement.pciPsc,
            signalStrength = measurement.rsrp ?: measurement.rssi
        )
    }

    internal fun checkLacTacChange(measurement: CellMeasurement): AnomalyEvent? {
        val previousTacLac = lastTacLac ?: return null
        val currentTacLac = measurement.tacLac ?: return null
        if (!measurement.isRegistered) return null
        if (previousTacLac == currentTacLac) return null

        val sameOperator = measurement.mcc == lastMcc && measurement.mnc == lastMnc
        if (!sameOperator) return null

        if (measurement.speedMps != null && measurement.speedMps > DRIVING_SPEED_MPS) {
            val opKey = "${measurement.mcc}-${measurement.mnc}"
            val now = measurement.timestamp
            val flips = tacFlipsByOperator.getOrPut(opKey) { mutableListOf() }
            flips.add(now)
            flips.removeAll { now - it > DRIVING_TAC_WINDOW_MS }
            if (flips.isEmpty()) tacFlipsByOperator.remove(opKey)
            if (flips.size < DRIVING_TAC_MIN_FLIPS) return null
        }

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

        val expiredKeys = towerLastSeen.filter { (key, lastSeen) ->
            now - lastSeen > window && key != towerKey
        }.keys
        for (key in expiredKeys) {
            val firstSeen = towerFirstSeen[key] ?: continue
            val lastSeen = towerLastSeen[key] ?: continue
            val duration = lastSeen - firstSeen

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

        if (towerKey !in towerFirstSeen) {
            towerFirstSeen[towerKey] = now
        }
        towerLastSeen[towerKey] = now

        if (towerLastSeen.size > MAX_TRACKED_TOWERS) {
            val oldestKey = towerLastSeen.minByOrNull { it.value }?.key
            if (oldestKey != null) {
                towerFirstSeen.remove(oldestKey)
                towerLastSeen.remove(oldestKey)
            }
        }

        return null
    }

    internal fun checkOperatorMismatch(measurement: CellMeasurement): AnomalyEvent? {
        val mcc = measurement.mcc ?: return null
        val mnc = measurement.mnc ?: return null
        if (!measurement.isRegistered) return null

        if (!UsCarriers.isUsNetwork(mcc)) return null
        if (UsCarriers.isKnownCarrier(mcc, mnc)) return null

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

    /**
     * Timing Advance ≈ 0 indicates the serving cell is within ~550 m. At that
     * distance a real macro tower would saturate your RSRP near -60 dBm; a
     * portable IMSI catcher (car/backpack) typically radiates at more modest
     * power, producing moderate RSRP. Gated on stationary/walking speed
     * because driving past a roadside tower can briefly produce TA=0.
     */
    internal fun checkSuspiciousProximity(measurement: CellMeasurement): AnomalyEvent? {
        if (!measurement.isRegistered) return null
        val mcc = measurement.mcc ?: return null
        val mnc = measurement.mnc ?: return null
        val tacLac = measurement.tacLac ?: return null
        val cid = measurement.cid ?: return null
        val ta = measurement.timingAdvance ?: return null
        val rsrp = measurement.rsrp ?: return null
        if (measurement.speedMps != null && measurement.speedMps > DRIVING_SPEED_MPS) return null
        if (ta > PROXIMITY_MAX_TA) return null
        if (rsrp !in PROXIMITY_MIN_RSRP..PROXIMITY_MAX_RSRP) return null

        val key = "proximity-${measurement.radio}-$mcc-$mnc-$tacLac-$cid"
        if (key in alertedTowers) return null
        alertedTowers.add(key)

        return AnomalyEvent(
            timestamp = measurement.timestamp,
            latitude = measurement.latitude,
            longitude = measurement.longitude,
            type = AnomalyType.SUSPICIOUS_PROXIMITY,
            severity = AnomalySeverity.HIGH,
            description = "Timing Advance=$ta with RSRP=${rsrp}dBm while stationary. Real macro towers this close saturate signal; may indicate a portable IMSI catcher.",
            cellRadio = measurement.radio,
            cellMcc = mcc,
            cellMnc = mnc,
            cellTacLac = tacLac,
            cellCid = cid,
            cellPci = measurement.pciPsc,
            signalStrength = rsrp
        )
    }

    /**
     * Flags the first observation of a tower in an area where the user has
     * substantial prior coverage but no historical sighting of this exact
     * cell. Distinct from TRANSIENT_TOWER (which fires after a brief tower
     * disappears) — POPUP_TOWER fires on first appearance, when the user
     * "should have" seen this tower already given how long they've been
     * collecting nearby. Speed-gated so driving into a new neighborhood
     * doesn't fire it.
     */
    internal fun checkPopupTower(measurement: CellMeasurement): AnomalyEvent? {
        val dao = measurementDao ?: return null
        if (!measurement.isRegistered) return null
        val mcc = measurement.mcc ?: return null
        val mnc = measurement.mnc ?: return null
        val tacLac = measurement.tacLac ?: return null
        val cid = measurement.cid ?: return null
        if (measurement.speedMps != null && measurement.speedMps > DRIVING_SPEED_MPS) return null

        val key = "popup-${measurement.radio}-$mcc-$mnc-$tacLac-$cid"
        if (key in alertedTowers) return null

        val deltaLat = POPUP_RADIUS_METERS / METERS_PER_DEGREE_LAT
        val deltaLon = POPUP_RADIUS_METERS /
            (METERS_PER_DEGREE_LAT * max(cos(Math.toRadians(measurement.latitude)), 1e-6))
        val minLat = measurement.latitude - deltaLat
        val maxLat = measurement.latitude + deltaLat
        val minLon = measurement.longitude - deltaLon
        val maxLon = measurement.longitude + deltaLon
        val sinceMs = measurement.timestamp - POPUP_WINDOW_MS

        val priorCount = dao.countMeasurementsInArea(minLat, maxLat, minLon, maxLon, sinceMs)
        if (priorCount < POPUP_MIN_PRIOR_MEASUREMENTS) return null

        val priorSightings = dao.countTowerObservationsInArea(
            measurement.radio.name, mcc, mnc, tacLac, cid,
            minLat, maxLat, minLon, maxLon, sinceMs
        )
        if (priorSightings > 0) return null

        alertedTowers.add(key)
        return AnomalyEvent(
            timestamp = measurement.timestamp,
            latitude = measurement.latitude,
            longitude = measurement.longitude,
            type = AnomalyType.POPUP_TOWER,
            severity = AnomalySeverity.MEDIUM,
            description = "Tower ${measurement.radio} MCC=$mcc MNC=$mnc CID=$cid is visible here for the first time in the last 7 days; you've recorded $priorCount measurements in this area without seeing it.",
            cellRadio = measurement.radio,
            cellMcc = mcc,
            cellMnc = mnc,
            cellTacLac = tacLac,
            cellCid = cid,
            cellPci = measurement.pciPsc,
            signalStrength = measurement.rsrp ?: measurement.rssi
        )
    }

    private fun updateState(measurement: CellMeasurement) {
        if (measurement.isRegistered) {
            if (measurement.radio == RadioType.LTE || measurement.radio == RadioType.NR) {
                alertedTowers.remove("2g-downgrade")
                alertedTowers.remove("3g-downgrade")
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
