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
        private const val PROXIMITY_MIN_RSRP = -95
        private const val PROXIMITY_MAX_RSRP = -85
        private const val PROXIMITY_HYSTERESIS_MS = 30_000L
        private const val PROXIMITY_SATURATION_RSRP = -75
        private const val POPUP_RADIUS_METERS = 2_000.0
        private const val POPUP_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        private const val POPUP_RECENT_WINDOW_MS = 6L * 60 * 60 * 1000 // 6 hours
        private const val POPUP_MIN_PRIOR_MEASUREMENTS = 20
        private const val POPUP_SIBLING_SUPPRESSION_THRESHOLD = 5
        private const val POPUP_BASELINE_MATURE_MS = 7L * 24 * 60 * 60 * 1000
        private const val POPUP_MIN_AREA_AGE_MS = 24L * 60 * 60 * 1000
        private const val PCI_COLLISION_RADIUS_METERS = 2_000.0
        private const val PCI_COLLISION_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        private const val PCI_COLLISION_DEDUPE_BUCKET_MS = 6L * 60 * 60 * 1000
        private const val PCI_COLLISION_MIN_RSRP = -115
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

    private fun buildAnomalyEvent(
        m: CellMeasurement,
        type: AnomalyType,
        severity: AnomalySeverity,
        description: String,
    ): AnomalyEvent = AnomalyEvent(
        timestamp = m.timestamp,
        latitude = m.latitude,
        longitude = m.longitude,
        type = type,
        severity = severity,
        description = description,
        cellRadio = m.radio,
        cellMcc = m.mcc,
        cellMnc = m.mnc,
        cellTacLac = m.tacLac,
        cellCid = m.cid,
        cellPci = m.pciPsc,
        signalStrength = m.rsrp ?: m.rssi,
        isRegistered = m.isRegistered,
        rsrp = m.rsrp,
        rsrq = m.rsrq,
        rssi = m.rssi,
        sinr = m.sinr,
        cqi = m.cqi,
        timingAdvance = m.timingAdvance,
        signalLevel = m.signalLevel,
        earfcnArfcn = m.earfcnArfcn,
        band = m.band,
        bandwidth = m.bandwidth,
        operatorName = m.operatorName,
        gpsAccuracy = m.gpsAccuracy,
    )

    fun analyze(measurement: CellMeasurement): List<AnomalyEvent> {
        val anomalies = mutableListOf<AnomalyEvent>()

        checkImpossibleMove(measurement)?.let { anomalies.add(it) }
        checkPciInstability(measurement)?.let { anomalies.add(it) }
        checkSignalAnomaly(measurement)?.let { anomalies.add(it) }
        checkRadioDowngrade(measurement)?.let { anomalies.add(it) }
        checkLacTacChange(measurement)?.let { anomalies.add(it) }
        checkTransientTower(measurement)?.let { anomalies.add(it) }
        checkPopupTower(measurement)?.let { anomalies.add(it) }
        checkPciCollision(measurement)?.let { anomalies.add(it) }
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
                AnomalyType.POPUP_TOWER -> 3
                AnomalyType.PCI_COLLISION -> 4
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
        return buildAnomalyEvent(
            m = measurement,
            type = AnomalyType.IMPOSSIBLE_MOVE,
            severity = AnomalySeverity.HIGH,
            description = "Cell ${measurement.radio} MCC=$mcc MNC=$mnc CID=$cid is $km km from its $source — tower appears to have moved, possible cloned base station.",
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
        return buildAnomalyEvent(
            m = measurement,
            type = AnomalyType.PCI_INSTABILITY,
            severity = AnomalySeverity.MEDIUM,
            description = "Cell ${measurement.radio} MCC=$mcc MNC=$mnc CID=$cid changed PCI from $cachedPci to $currentPci — possible cloned cell.",
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

        return buildAnomalyEvent(
            m = measurement,
            type = AnomalyType.SIGNAL_ANOMALY,
            severity = severity,
            description = "Signal ${currentRsrp}dBm is ${difference.toInt()}dBm above average (${averageRsrp.toInt()}dBm) for operator $operatorKey",
        )
    }

    /**
     * Flags forced transitions from a modern radio technology (LTE/NR) to an
     * older one (GSM = 2G, WCDMA/CDMA = 3G). WCDMA → GSM is treated as a
     * normal network handoff and is not flagged — only the step down from
     * LTE/NR is considered a potential downgrade attack. Cross-operator
     * fallback (the GSM/3G cell's MCC/MNC differ from the previous LTE/NR
     * cell's) is suppressed: that's roaming (e.g., Three IE → Vodafone IE
     * GSM in coverage holes), not an attack. Sophisticated IMSI catchers
     * spoof the home operator and still trigger; OPERATOR_MISMATCH is the
     * detector for unsophisticated cross-operator attacks.
     */
    internal fun checkRadioDowngrade(measurement: CellMeasurement): AnomalyEvent? {
        val previous = lastRadioType ?: return null
        if (previous != RadioType.LTE && previous != RadioType.NR) return null
        if (!measurement.isRegistered) return null
        if (measurement.mcc != lastMcc || measurement.mnc != lastMnc) return null

        return when (measurement.radio) {
            RadioType.GSM -> buildDowngradeEvent(
                measurement,
                AnomalyType.DOWNGRADE_2G,
                AnomalySeverity.HIGH,
                "2g-downgrade",
                "Downgraded from $previous to GSM (2G). Possible forced downgrade attack."
            )
            RadioType.WCDMA, RadioType.CDMA -> buildDowngradeEvent(
                measurement,
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
        type: AnomalyType,
        severity: AnomalySeverity,
        alertKey: String,
        description: String
    ): AnomalyEvent? {
        if (alertKey in alertedTowers) return null
        alertedTowers.add(alertKey)
        return buildAnomalyEvent(
            m = measurement,
            type = type,
            severity = severity,
            description = description,
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

        return buildAnomalyEvent(
            m = measurement,
            type = AnomalyType.LAC_TAC_CHANGE,
            severity = AnomalySeverity.MEDIUM,
            description = "TAC/LAC changed from $previousTacLac to $currentTacLac on same operator (MCC=${measurement.mcc} MNC=${measurement.mnc})",
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

                return buildAnomalyEvent(
                    m = measurement,
                    type = AnomalyType.TRANSIENT_TOWER,
                    severity = AnomalySeverity.MEDIUM,
                    description = "Tower $key appeared briefly (${duration / 1000}s) then disappeared. Possible mobile IMSI catcher.",
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

        return buildAnomalyEvent(
            m = measurement,
            type = AnomalyType.OPERATOR_MISMATCH,
            severity = AnomalySeverity.HIGH,
            description = "Registered to unknown US operator MCC=$mcc MNC=$mnc. Not in known carrier database.",
        )
    }

    /**
     * Timing Advance ≈ 0 indicates the serving cell is within ~550 m. At that
     * distance a real macro tower would saturate your RSRP near -60 dBm; a
     * portable IMSI catcher (car/backpack) typically radiates at more modest
     * power, producing moderate RSRP. Gated on stationary/walking speed
     * because driving past a roadside tower can briefly produce TA=0. The
     * speed gate carries 30 s of hysteresis: if any prior measurement in the
     * recent buffer was above driving speed within that window, we suppress
     * (a brake-light dip at a junction is not "stationary"). And we also
     * suppress when the same cell has previously been observed in this
     * session at saturation-grade RSRP (>= -75 dBm) — that means it's an
     * established close cell whose moderate-RSRP moments are signal
     * variability (multipath, body shadowing), not a portable transmitter.
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

        val hysteresisCutoff = measurement.timestamp - PROXIMITY_HYSTERESIS_MS
        val recentlyDriving = recentMeasurements.any { prior ->
            prior.timestamp >= hysteresisCutoff &&
                prior.speedMps != null && prior.speedMps > DRIVING_SPEED_MPS
        }
        if (recentlyDriving) return null

        val priorSaturation = recentMeasurements.any { prior ->
            prior.radio == measurement.radio &&
                prior.mcc == mcc && prior.mnc == mnc &&
                prior.tacLac == tacLac && prior.cid == cid &&
                (prior.rsrp ?: Int.MIN_VALUE) >= PROXIMITY_SATURATION_RSRP
        }
        if (priorSaturation) return null

        val key = "proximity-${measurement.radio}-$mcc-$mnc-$tacLac-$cid"
        if (key in alertedTowers) return null
        alertedTowers.add(key)

        return buildAnomalyEvent(
            m = measurement,
            type = AnomalyType.SUSPICIOUS_PROXIMITY,
            severity = AnomalySeverity.HIGH,
            description = "Timing Advance=$ta with RSRP=${rsrp}dBm while stationary. Real macro towers this close saturate signal; may indicate a portable IMSI catcher.",
        )
    }

    /**
     * Flags a tower that appears in a familiar area where it has not been
     * seen recently. Two firing cases:
     *   1. First appearance — no prior sighting of this cell anywhere in the
     *      bbox within the 7-day window.
     *   2. Reappearance after a gap — the most recent sighting in the bbox
     *      is older than POPUP_RECENT_WINDOW_MS (6 hours), so the tower has
     *      been "off" and is now back. This is the strongest popup signal:
     *      a tower being switched on and off in a familiar area is
     *      characteristic of a stationary IMSI catcher.
     *
     * Distinct from TRANSIENT_TOWER (which fires post-disappearance within
     * minutes). Speed-gated to avoid firing while driving into new areas.
     * Dedupe is bucketed on POPUP_RECENT_WINDOW_MS so each reappearance
     * after a fresh gap can re-fire, but a single continuous appearance
     * fires only once.
     */
    internal fun checkPopupTower(measurement: CellMeasurement): AnomalyEvent? {
        val dao = measurementDao ?: return null
        if (!measurement.isRegistered) return null
        val mcc = measurement.mcc ?: return null
        val mnc = measurement.mnc ?: return null
        val tacLac = measurement.tacLac ?: return null
        val cid = measurement.cid ?: return null
        if (measurement.speedMps != null && measurement.speedMps > DRIVING_SPEED_MPS) return null

        val bucket = measurement.timestamp / POPUP_RECENT_WINDOW_MS
        val key = "popup-${measurement.radio}-$mcc-$mnc-$tacLac-$cid-$bucket"
        if (key in alertedTowers) return null

        val deltaLat = POPUP_RADIUS_METERS / METERS_PER_DEGREE_LAT
        val deltaLon = POPUP_RADIUS_METERS /
            (METERS_PER_DEGREE_LAT * max(cos(Math.toRadians(measurement.latitude)), 1e-6))
        val minLat = measurement.latitude - deltaLat
        val maxLat = measurement.latitude + deltaLat
        val minLon = measurement.longitude - deltaLon
        val maxLon = measurement.longitude + deltaLon
        val sinceMs = measurement.timestamp - POPUP_WINDOW_MS
        val beforeMs = measurement.timestamp

        val priorCount = dao.countMeasurementsInArea(
            minLat, maxLat, minLon, maxLon, sinceMs, beforeMs
        )
        if (priorCount < POPUP_MIN_PRIOR_MEASUREMENTS) return null

        // Sibling-sector suppression (LTE/NR only): real macro eNBs add and lose
        // sectors as carrier aggregation, capacity tuning, and beam-steering
        // shift coverage around the same physical site. Treating each newly-
        // observed sector of an established eNB as a popup produces a
        // false-positive flood. UMTS/GSM CIDs aren't bit-packed this way, so
        // skip the gate for those radios.
        if (measurement.radio == RadioType.LTE || measurement.radio == RadioType.NR) {
            val enbId = cid shr 8
            val siblingCount = dao.countSiblingSectorsInArea(
                measurement.radio.name, mcc, mnc, tacLac, enbId, cid,
                minLat, maxLat, minLon, maxLon, sinceMs, beforeMs
            )
            if (siblingCount >= POPUP_SIBLING_SUPPRESSION_THRESHOLD) return null
        }

        val lastSightingMs = dao.findMostRecentTowerSighting(
            measurement.radio.name, mcc, mnc, tacLac, cid,
            minLat, maxLat, minLon, maxLon, sinceMs, beforeMs
        )
        val gapMs = if (lastSightingMs != null) measurement.timestamp - lastSightingMs else null
        if (gapMs != null && gapMs < POPUP_RECENT_WINDOW_MS) return null

        // Bootstrap window: until the bbox has at least POPUP_MIN_AREA_AGE_MS of
        // measurement history, suppress first-popup alerts entirely. Fresh
        // datasets produce a flood of "first time seen" sightings that aren't
        // really popups. Gap-reappearance still fires regardless of age.
        val firstSeenInArea = dao.findFirstMeasurementTimeInArea(minLat, maxLat, minLon, maxLon)
        val areaAgeMs = if (firstSeenInArea != null) measurement.timestamp - firstSeenInArea else 0L
        if (gapMs == null && areaAgeMs < POPUP_MIN_AREA_AGE_MS) return null

        // Bootstrap-aware severity: between 24h and 7d of baseline, first-popup
        // alerts fire as MEDIUM rather than HIGH — the bbox is established
        // enough to alert but not yet mature enough for full confidence. Above
        // 7d, full HIGH. Gap-reappearance always stays HIGH — "vanished then
        // returned" is the strongest popup signal regardless of dataset age.
        val severity = if (gapMs == null && areaAgeMs < POPUP_BASELINE_MATURE_MS) {
            AnomalySeverity.MEDIUM
        } else {
            AnomalySeverity.HIGH
        }

        alertedTowers.add(key)
        val description = if (gapMs == null) {
            "Tower ${measurement.radio} MCC=$mcc MNC=$mnc CID=$cid is visible here for the first time in the last 7 days; you've recorded $priorCount measurements in this area without seeing it."
        } else {
            "Tower ${measurement.radio} MCC=$mcc MNC=$mnc CID=$cid has reappeared after being absent for ${formatGap(gapMs)}; possible IMSI catcher being toggled on/off."
        }
        return buildAnomalyEvent(
            m = measurement,
            type = AnomalyType.POPUP_TOWER,
            severity = severity,
            description = description,
        )
    }

    /**
     * Flags Physical Cell ID (PCI) reuse — the cleanest fingerprint of a fake
     * cell that picked an arbitrary PCI without coordinating with the operator.
     * Real LTE/NR networks structurally avoid PCI collisions because they cause
     * interference and dropped handovers. Two firing branches:
     *   1. Collision — ≥ 2 distinct CIDs are observed broadcasting the same PCI
     *      in the bbox + 7-day window. This is the strongest signal: the
     *      legitimate operator wouldn't have allowed this clash.
     *   2. Reuse — exactly one prior CID used this PCI here, and it differs
     *      from the current measurement's CID. Real eNBs don't change PCI
     *      (PCI_INSTABILITY catches that case for the same identity); a
     *      different CID inheriting a familiar PCI suggests appropriation.
     *
     * Always HIGH severity: PCI collision is fingerprint-grade, not statistical
     * — suspicious even on a fresh dataset, no bootstrap demotion. Speed-gated
     * because driving past tightly-packed urban cells can briefly observe
     * legitimate PCI reuse outside the device's actual serving cell. Dedupe
     * bucketed on 6h so a continuous appearance fires once but a fresh re-
     * appearance after a gap can re-fire.
     */
    internal fun checkPciCollision(measurement: CellMeasurement): AnomalyEvent? {
        val dao = measurementDao ?: return null
        if (!measurement.isRegistered) return null
        val mcc = measurement.mcc ?: return null
        val mnc = measurement.mnc ?: return null
        val tacLac = measurement.tacLac ?: return null
        val cid = measurement.cid ?: return null
        val pci = measurement.pciPsc ?: return null
        if (measurement.speedMps != null && measurement.speedMps > DRIVING_SPEED_MPS) return null

        // Noise-floor gate (LTE/NR only — RSRP isn't reported on UMTS/GSM).
        // Cell-ID parsing at SNR < 0 is unreliable; PCI_COLLISION should only
        // run on observations strong enough that the CID/PCI pairing can be
        // trusted. Without this gate, neighbor-list scans of cells at -120 dBm
        // produced false-positive collisions in dense urban areas.
        val rsrp = measurement.rsrp
        if (rsrp != null && rsrp < PCI_COLLISION_MIN_RSRP) return null

        val bucket = measurement.timestamp / PCI_COLLISION_DEDUPE_BUCKET_MS
        val key = "pci-collision-${measurement.radio}-$mcc-$mnc-$pci-$bucket"
        if (key in alertedTowers) return null

        val deltaLat = PCI_COLLISION_RADIUS_METERS / METERS_PER_DEGREE_LAT
        val deltaLon = PCI_COLLISION_RADIUS_METERS /
            (METERS_PER_DEGREE_LAT * max(cos(Math.toRadians(measurement.latitude)), 1e-6))
        val minLat = measurement.latitude - deltaLat
        val maxLat = measurement.latitude + deltaLat
        val minLon = measurement.longitude - deltaLon
        val maxLon = measurement.longitude + deltaLon
        val sinceMs = measurement.timestamp - PCI_COLLISION_WINDOW_MS
        val beforeMs = measurement.timestamp

        val distinctCids = dao.countDistinctCidsForPci(
            measurement.radio.name, mcc, mnc, tacLac, pci,
            minLat, maxLat, minLon, maxLon, sinceMs, beforeMs
        )

        val isLteOrNr = measurement.radio == RadioType.LTE || measurement.radio == RadioType.NR

        // Sibling-eNB suppression (LTE/NR only): a single physical site can
        // legitimately reuse one PCI across multiple of its own sectors —
        // carrier aggregation, overlay layers, beam-steering. Confirm the
        // colliding CIDs span at least two distinct eNBs before firing.
        // UMTS/GSM/CDMA CIDs aren't bit-packed as eNB+sector, so skip the gate
        // and fall back to the raw CID count — same approach POPUP_TOWER takes.
        val collisionConfirmed = if (isLteOrNr && distinctCids >= 2) {
            dao.countDistinctEnbsForPci(
                measurement.radio.name, mcc, mnc, tacLac, pci,
                minLat, maxLat, minLon, maxLon, sinceMs, beforeMs
            ) >= 2
        } else {
            distinctCids >= 2
        }

        if (collisionConfirmed) {
            alertedTowers.add(key)
            return buildAnomalyEvent(
                m = measurement,
                type = AnomalyType.PCI_COLLISION,
                severity = AnomalySeverity.HIGH,
                description = "Cell ${measurement.radio} MCC=$mcc MNC=$mnc CID=$cid shares PCI=$pci with $distinctCids other cell identities in this area — real networks coordinate PCI to avoid collisions, possible fake cell.",
            )
        }

        val mostRecentCid = dao.findMostRecentCidForPci(
            measurement.radio.name, mcc, mnc, tacLac, pci,
            minLat, maxLat, minLon, maxLon, sinceMs, beforeMs
        )
        if (mostRecentCid != null && mostRecentCid != cid) {
            // Sibling-eNB suppression: a sector switch within the same physical
            // site (the device handing off between cells of the same eNB that
            // share a PCI) is not PCI hijacking. Skip for non-LTE/NR — same
            // reasoning as the collision branch.
            val sameEnb = isLteOrNr && (mostRecentCid shr 8) == (cid shr 8)
            if (!sameEnb) {
                alertedTowers.add(key)
                return buildAnomalyEvent(
                    m = measurement,
                    type = AnomalyType.PCI_COLLISION,
                    severity = AnomalySeverity.HIGH,
                    description = "Cell ${measurement.radio} MCC=$mcc MNC=$mnc CID=$cid is now broadcasting on PCI=$pci, which was last used in this area by a different cell (CID=$mostRecentCid) — possible PCI reuse by a fake cell.",
                )
            }
        }

        return null
    }

    private fun formatGap(gapMs: Long): String {
        val hours = gapMs / (60 * 60 * 1000)
        if (hours >= 24) return "${hours / 24}d ${hours % 24}h"
        if (hours >= 1) return "${hours}h"
        val minutes = gapMs / (60 * 1000)
        return "${minutes}m"
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
