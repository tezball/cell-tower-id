package com.terrycollins.cellid.service

import com.terrycollins.cellid.data.dao.TowerCacheDao
import com.terrycollins.cellid.data.entity.TowerCacheEntity
import com.terrycollins.cellid.domain.model.AnomalySeverity
import com.terrycollins.cellid.domain.model.AnomalyType
import com.terrycollins.cellid.domain.model.CellMeasurement
import com.terrycollins.cellid.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class AnomalyDetectorTest {

    private lateinit var towerCacheDao: TowerCacheDao
    private lateinit var detector: AnomalyDetector

    @Before
    fun setUp() {
        towerCacheDao = mockk()
        every { towerCacheDao.getCount() } returns 100 // non-empty cache by default
        every { towerCacheDao.findAnyByCidRange(any(), any(), any(), any(), any()) } returns null
        detector = AnomalyDetector(towerCacheDao)
    }

    // --- UNKNOWN_TOWER checks ---

    @Test
    fun `given unknown tower, when analyzing measurement, then returns UNKNOWN_TOWER anomaly`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns null
        val measurement = baseMeasurement(isRegistered = true)

        // When
        val anomalies = detector.analyze(measurement)

        // Then
        assertThat(anomalies).hasSize(1)
        assertThat(anomalies[0].type).isEqualTo(AnomalyType.UNKNOWN_TOWER)
        assertThat(anomalies[0].severity).isEqualTo(AnomalySeverity.MEDIUM)
    }

    @Test
    fun `given unknown tower seen twice, when analyzing, then only alerts once`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns null
        val measurement = baseMeasurement(isRegistered = true)

        // When
        val first = detector.analyze(measurement)
        val second = detector.analyze(measurement)

        // Then
        assertThat(first.filter { it.type == AnomalyType.UNKNOWN_TOWER }).hasSize(1)
        assertThat(second.filter { it.type == AnomalyType.UNKNOWN_TOWER }).isEmpty()
    }

    @Test
    fun `given empty tower cache, when analyzing unknown tower, then returns no anomalies`() {
        // Given - empty cache means we have no baseline to compare
        every { towerCacheDao.getCount() } returns 0
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns null
        val freshDetector = AnomalyDetector(towerCacheDao)
        val measurement = baseMeasurement(isRegistered = true)

        // When
        val anomalies = freshDetector.analyze(measurement)

        // Then
        assertThat(anomalies.filter { it.type == AnomalyType.UNKNOWN_TOWER }).isEmpty()
    }

    @Test
    fun `given known tower, when analyzing measurement, then returns no anomalies`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        val measurement = baseMeasurement(isRegistered = true)

        // When
        val anomalies = detector.analyze(measurement)

        // Then
        assertThat(anomalies).isEmpty()
    }

    @Test
    fun `given unknown tower but not registered, when analyzing, then returns no anomalies`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns null
        val measurement = baseMeasurement(isRegistered = false)

        // When
        val anomalies = detector.analyze(measurement)

        // Then
        assertThat(anomalies.filter { it.type == AnomalyType.UNKNOWN_TOWER }).isEmpty()
    }

    // --- DOWNGRADE_2G checks ---

    @Test
    fun `given LTE then GSM, when analyzing measurements, then returns DOWNGRADE_2G`() {
        // Given - first establish LTE as last radio type
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        val lteMeasurement = baseMeasurement(radio = RadioType.LTE, isRegistered = true)
        detector.analyze(lteMeasurement)

        // When - now receive a GSM measurement
        val gsmMeasurement = baseMeasurement(radio = RadioType.GSM, isRegistered = true)
        val anomalies = detector.analyze(gsmMeasurement)

        // Then
        val downgradeAnomalies = anomalies.filter { it.type == AnomalyType.DOWNGRADE_2G }
        assertThat(downgradeAnomalies).hasSize(1)
        assertThat(downgradeAnomalies[0].severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given NR then GSM, when analyzing measurements, then returns DOWNGRADE_2G`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        val nrMeasurement = baseMeasurement(radio = RadioType.NR, isRegistered = true)
        detector.analyze(nrMeasurement)

        // When
        val gsmMeasurement = baseMeasurement(radio = RadioType.GSM, isRegistered = true)
        val anomalies = detector.analyze(gsmMeasurement)

        // Then
        assertThat(anomalies.filter { it.type == AnomalyType.DOWNGRADE_2G }).hasSize(1)
    }

    @Test
    fun `given GSM then GSM, when analyzing measurements, then no DOWNGRADE_2G`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        val gsm1 = baseMeasurement(radio = RadioType.GSM, isRegistered = true)
        detector.analyze(gsm1)

        // When
        val gsm2 = baseMeasurement(radio = RadioType.GSM, isRegistered = true)
        val anomalies = detector.analyze(gsm2)

        // Then
        assertThat(anomalies.filter { it.type == AnomalyType.DOWNGRADE_2G }).isEmpty()
    }

    // --- LAC_TAC_CHANGE checks ---

    @Test
    fun `given TAC change while stationary, when analyzing, then returns LAC_TAC_CHANGE`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        val first = baseMeasurement(tacLac = 100, isRegistered = true)
        detector.analyze(first)

        // When
        val second = baseMeasurement(tacLac = 200, isRegistered = true)
        val anomalies = detector.analyze(second)

        // Then
        val tacAnomalies = anomalies.filter { it.type == AnomalyType.LAC_TAC_CHANGE }
        assertThat(tacAnomalies).hasSize(1)
        assertThat(tacAnomalies[0].severity).isEqualTo(AnomalySeverity.MEDIUM)
    }

    @Test
    fun `given TAC change with different operator, when analyzing, then no LAC_TAC_CHANGE`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        val first = baseMeasurement(mcc = 310, mnc = 260, tacLac = 100, isRegistered = true)
        detector.analyze(first)

        // When - different operator
        val second = baseMeasurement(mcc = 310, mnc = 410, tacLac = 200, isRegistered = true)
        val anomalies = detector.analyze(second)

        // Then
        assertThat(anomalies.filter { it.type == AnomalyType.LAC_TAC_CHANGE }).isEmpty()
    }

    // --- OPERATOR_MISMATCH checks ---

    @Test
    fun `given non-US MCC-MNC, when analyzing, then returns OPERATOR_MISMATCH`() {
        // Given - US MCC but unknown MNC
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        val measurement = baseMeasurement(mcc = 310, mnc = 999, isRegistered = true)

        // When
        val anomalies = detector.analyze(measurement)

        // Then
        val mismatchAnomalies = anomalies.filter { it.type == AnomalyType.OPERATOR_MISMATCH }
        assertThat(mismatchAnomalies).hasSize(1)
        assertThat(mismatchAnomalies[0].severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given known US carrier, when analyzing, then no OPERATOR_MISMATCH`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        val measurement = baseMeasurement(mcc = 310, mnc = 260, isRegistered = true) // T-Mobile

        // When
        val anomalies = detector.analyze(measurement)

        // Then
        assertThat(anomalies.filter { it.type == AnomalyType.OPERATOR_MISMATCH }).isEmpty()
    }

    @Test
    fun `given non-US MCC, when analyzing, then no OPERATOR_MISMATCH`() {
        // Given - non-US MCC, skip check entirely
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        val measurement = baseMeasurement(mcc = 262, mnc = 1, isRegistered = true) // Germany

        // When
        val anomalies = detector.analyze(measurement)

        // Then
        assertThat(anomalies.filter { it.type == AnomalyType.OPERATOR_MISMATCH }).isEmpty()
    }

    // --- SIGNAL_ANOMALY checks ---

    @Test
    fun `given signal 25dBm above average, when analyzing, then returns SIGNAL_ANOMALY`() {
        // Given - build up history of weak signals
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        repeat(10) {
            detector.analyze(baseMeasurement(rsrp = -100, isRegistered = true))
        }

        // When - suddenly much stronger signal
        val strongMeasurement = baseMeasurement(rsrp = -70, isRegistered = true)
        val anomalies = detector.analyze(strongMeasurement)

        // Then - difference is 30dBm > 20dBm threshold
        val signalAnomalies = anomalies.filter { it.type == AnomalyType.SIGNAL_ANOMALY }
        assertThat(signalAnomalies).hasSize(1)
    }

    @Test
    fun `given normal signal variation, when analyzing, then no SIGNAL_ANOMALY`() {
        // Given - build up history
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        repeat(10) {
            detector.analyze(baseMeasurement(rsrp = -90, isRegistered = false))
        }

        // When - small variation
        val normalMeasurement = baseMeasurement(rsrp = -85, isRegistered = true)
        val anomalies = detector.analyze(normalMeasurement)

        // Then
        assertThat(anomalies.filter { it.type == AnomalyType.SIGNAL_ANOMALY }).isEmpty()
    }

    // --- Threat scoring ---

    @Test
    fun `given multiple anomalies, when computing threat score, then sums weights correctly`() {
        // Given
        val anomalies = listOf(
            anomalyOf(AnomalyType.UNKNOWN_TOWER, AnomalySeverity.MEDIUM),    // +2
            anomalyOf(AnomalyType.DOWNGRADE_2G, AnomalySeverity.HIGH),       // +3
            anomalyOf(AnomalyType.OPERATOR_MISMATCH, AnomalySeverity.HIGH)   // +3
        )

        // When
        val score = detector.computeThreatScore(anomalies)

        // Then
        assertThat(score).isEqualTo(8)
    }

    @Test
    fun `given SIGNAL_ANOMALY with different severities, when computing score, then weights by severity`() {
        // Given
        val lowSignal = anomalyOf(AnomalyType.SIGNAL_ANOMALY, AnomalySeverity.LOW)
        val highSignal = anomalyOf(AnomalyType.SIGNAL_ANOMALY, AnomalySeverity.HIGH)

        // When / Then
        assertThat(detector.computeThreatScore(listOf(lowSignal))).isEqualTo(1)
        assertThat(detector.computeThreatScore(listOf(highSignal))).isEqualTo(3)
    }

    // --- IMPOSSIBLE_MOVE checks ---

    @Test
    fun `given cached tower 1000km from current GPS, when analyzing, then flags IMPOSSIBLE_MOVE`() {
        // Given - cached tower at Dublin, current GPS near London
        val cached = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 12345; cid = 50331905L
            latitude = 53.3498; longitude = -6.2603 // Dublin
        }
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns cached
        val measurement = baseMeasurement(isRegistered = true).copy(
            latitude = 51.5074, longitude = -0.1278 // London ~463 km away
        )

        // When
        val anomalies = detector.analyze(measurement)

        // Then
        assertThat(anomalies.any { it.type == AnomalyType.IMPOSSIBLE_MOVE }).isTrue()
        val move = anomalies.first { it.type == AnomalyType.IMPOSSIBLE_MOVE }
        assertThat(move.severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given cached tower within 5km of current GPS, when analyzing, then no IMPOSSIBLE_MOVE`() {
        // Given
        val cached = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 12345; cid = 50331905L
            latitude = 37.7749; longitude = -122.4194
        }
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns cached
        val measurement = baseMeasurement(isRegistered = true) // same GPS

        // When
        val anomalies = detector.analyze(measurement)

        // Then
        assertThat(anomalies.any { it.type == AnomalyType.IMPOSSIBLE_MOVE }).isFalse()
    }

    @Test
    fun `given no cached tower, when analyzing, then no IMPOSSIBLE_MOVE`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns null
        every { towerCacheDao.findAnyByCidRange(any(), any(), any(), any(), any()) } returns null
        val measurement = baseMeasurement(isRegistered = true)

        // When
        val anomalies = detector.analyze(measurement)

        // Then
        assertThat(anomalies.any { it.type == AnomalyType.IMPOSSIBLE_MOVE }).isFalse()
    }

    @Test
    fun `given exact CID missing but sister sector in cache far away, when analyzing LTE, then flags IMPOSSIBLE_MOVE via eNB fallback`() {
        // Given - same eNodeB (top 20 bits), different sector; cached sister 1000km away
        val sister = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 99999; cid = 50331900L // same eNB, sector 0xFC
            latitude = 53.3498; longitude = -6.2603
        }
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns null
        every { towerCacheDao.findAnyByCidRange(any(), any(), any(), any(), any()) } returns sister
        val measurement = baseMeasurement(isRegistered = true).copy(
            latitude = 51.5074, longitude = -0.1278
        )

        // When
        val anomalies = detector.analyze(measurement)

        // Then
        assertThat(anomalies.any { it.type == AnomalyType.IMPOSSIBLE_MOVE }).isTrue()
    }

    // --- Threat level classification ---

    @Test
    fun `given score below 3, when getting threat level, then returns LOW`() {
        assertThat(detector.getThreatLevel(0)).isEqualTo(AnomalySeverity.LOW)
        assertThat(detector.getThreatLevel(2)).isEqualTo(AnomalySeverity.LOW)
    }

    @Test
    fun `given score 3 to 5, when getting threat level, then returns MEDIUM`() {
        assertThat(detector.getThreatLevel(3)).isEqualTo(AnomalySeverity.MEDIUM)
        assertThat(detector.getThreatLevel(5)).isEqualTo(AnomalySeverity.MEDIUM)
    }

    @Test
    fun `given score 6 or above, when getting threat level, then returns HIGH`() {
        assertThat(detector.getThreatLevel(6)).isEqualTo(AnomalySeverity.HIGH)
        assertThat(detector.getThreatLevel(10)).isEqualTo(AnomalySeverity.HIGH)
    }

    // --- Speed gate (2.1) ---

    @Test
    fun `given a stationary measurement, when checkSignalAnomaly runs, then it behaves as before`() {
        // Given - 10 weak stationary measurements
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        repeat(10) { detector.analyze(baseMeasurement(rsrp = -100, isRegistered = true)) }

        // When - stationary strong signal
        val anomalies = detector.analyze(baseMeasurement(rsrp = -70, isRegistered = true, speedMps = 0f))

        // Then
        assertThat(anomalies.any { it.type == AnomalyType.SIGNAL_ANOMALY }).isTrue()
    }

    @Test
    fun `given a driving measurement at 15 m per s, when checkSignalAnomaly runs, then returns null`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        repeat(10) { detector.analyze(baseMeasurement(rsrp = -100, isRegistered = true)) }

        // When
        val anomalies = detector.analyze(baseMeasurement(rsrp = -70, isRegistered = true, speedMps = 15f))

        // Then
        assertThat(anomalies.any { it.type == AnomalyType.SIGNAL_ANOMALY }).isFalse()
    }

    @Test
    fun `given two driving TAC flips, when analyzing, then no LAC_TAC_CHANGE anomaly`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        detector.analyze(baseMeasurement(tacLac = 100, isRegistered = true, speedMps = 15f))

        // When - first flip
        val first = detector.analyze(baseMeasurement(tacLac = 200, isRegistered = true, speedMps = 15f))
        // second flip
        val second = detector.analyze(baseMeasurement(tacLac = 300, isRegistered = true, speedMps = 15f))

        // Then
        assertThat(first.any { it.type == AnomalyType.LAC_TAC_CHANGE }).isFalse()
        assertThat(second.any { it.type == AnomalyType.LAC_TAC_CHANGE }).isFalse()
    }

    @Test
    fun `given three driving TAC flips, when analyzing, then LAC_TAC_CHANGE fires`() {
        // Given
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        val now = System.currentTimeMillis()
        detector.analyze(baseMeasurement(tacLac = 100, isRegistered = true, speedMps = 15f, timestamp = now))
        detector.analyze(baseMeasurement(tacLac = 200, isRegistered = true, speedMps = 15f, timestamp = now + 1000))
        detector.analyze(baseMeasurement(tacLac = 300, isRegistered = true, speedMps = 15f, timestamp = now + 2000))

        // When - 3rd flip (within 60s)
        val anomalies = detector.analyze(
            baseMeasurement(tacLac = 400, isRegistered = true, speedMps = 15f, timestamp = now + 3000)
        )

        // Then
        assertThat(anomalies.any { it.type == AnomalyType.LAC_TAC_CHANGE }).isTrue()
    }

    @Test
    fun `given a 6 minute old transient at driving speed, when analyzing, then no TRANSIENT_TOWER anomaly`() {
        // Given - tower seen 6 min ago then disappears; while driving transient window is 20 min
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        val now = System.currentTimeMillis()
        detector.analyze(baseMeasurement(cid = 111L, isRegistered = true, speedMps = 15f, timestamp = now))

        // When - new tower 6 min later, prev tower now "old"
        val anomalies = detector.analyze(
            baseMeasurement(cid = 222L, isRegistered = true, speedMps = 15f, timestamp = now + 6 * 60_000L)
        )

        // Then
        assertThat(anomalies.any { it.type == AnomalyType.TRANSIENT_TOWER }).isFalse()
    }

    // --- Signal anomaly identity gate (2.2) ---

    @Test
    fun `given an unregistered neighbour cell with null cell identity, when checkSignalAnomaly runs, then returns null`() {
        // Given - 10 registered measurements to build baseline
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns TowerCacheEntity()
        repeat(10) { detector.analyze(baseMeasurement(rsrp = -100, isRegistered = true)) }

        // When - unregistered neighbour with null identity and a very strong signal
        val neighbour = baseMeasurement(rsrp = -60, isRegistered = false)
            .copy(mcc = null, mnc = null, tacLac = null, cid = null)
        val result = detector.checkSignalAnomaly(neighbour)

        // Then
        assertThat(result).isNull()
    }

    // --- Helper functions ---

    private fun baseMeasurement(
        radio: RadioType = RadioType.LTE,
        mcc: Int? = 310,
        mnc: Int? = 260,
        tacLac: Int? = 12345,
        cid: Long? = 50331905L,
        rsrp: Int? = -90,
        isRegistered: Boolean = false,
        speedMps: Float? = null,
        timestamp: Long = System.currentTimeMillis()
    ): CellMeasurement = CellMeasurement(
        timestamp = timestamp,
        latitude = 37.7749,
        longitude = -122.4194,
        gpsAccuracy = 10f,
        radio = radio,
        mcc = mcc,
        mnc = mnc,
        tacLac = tacLac,
        cid = cid,
        pciPsc = 214,
        earfcnArfcn = 2050,
        rsrp = rsrp,
        rsrq = -10,
        sinr = 15,
        signalLevel = 3,
        isRegistered = isRegistered,
        operatorName = "T-Mobile",
        speedMps = speedMps
    )

    private fun anomalyOf(type: AnomalyType, severity: AnomalySeverity) = com.terrycollins.cellid.domain.model.AnomalyEvent(
        timestamp = System.currentTimeMillis(),
        type = type,
        severity = severity,
        description = "Test anomaly"
    )
}
