package com.celltowerid.android.service

import com.celltowerid.android.data.dao.MeasurementDao
import com.celltowerid.android.data.dao.TowerCacheDao
import com.celltowerid.android.data.entity.TowerCacheEntity
import com.celltowerid.android.domain.model.AnomalySeverity
import com.celltowerid.android.domain.model.AnomalyType
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class AnomalyDetectorTest {

    private lateinit var towerCacheDao: TowerCacheDao
    private lateinit var measurementDao: MeasurementDao
    private lateinit var detector: AnomalyDetector

    @Before
    fun setUp() {
        towerCacheDao = mockk()
        every { towerCacheDao.getCount() } returns 100
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns null
        every { towerCacheDao.findAnyByCidRange(any(), any(), any(), any(), any()) } returns null

        measurementDao = mockk()
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any())
        } returns 0
        every {
            measurementDao.countTowerObservationsInArea(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0

        detector = AnomalyDetector(towerCacheDao, measurementDao = measurementDao)
    }

    // --- DOWNGRADE_2G ---

    @Test
    fun `given LTE then GSM, when analyzing measurements, then returns DOWNGRADE_2G`() {
        detector.analyze(baseMeasurement(radio = RadioType.LTE, isRegistered = true))

        val anomalies = detector.analyze(baseMeasurement(radio = RadioType.GSM, isRegistered = true))

        val downgrade = anomalies.filter { it.type == AnomalyType.DOWNGRADE_2G }
        assertThat(downgrade).hasSize(1)
        assertThat(downgrade[0].severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given NR then GSM, when analyzing measurements, then returns DOWNGRADE_2G`() {
        detector.analyze(baseMeasurement(radio = RadioType.NR, isRegistered = true))

        val anomalies = detector.analyze(baseMeasurement(radio = RadioType.GSM, isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.DOWNGRADE_2G }).hasSize(1)
    }

    @Test
    fun `given GSM then GSM, when analyzing measurements, then no DOWNGRADE_2G`() {
        detector.analyze(baseMeasurement(radio = RadioType.GSM, isRegistered = true))

        val anomalies = detector.analyze(baseMeasurement(radio = RadioType.GSM, isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.DOWNGRADE_2G }).isEmpty()
    }

    // --- DOWNGRADE_3G ---

    @Test
    fun `given LTE then WCDMA, when analyzing measurements, then returns DOWNGRADE_3G`() {
        detector.analyze(baseMeasurement(radio = RadioType.LTE, isRegistered = true))

        val anomalies = detector.analyze(baseMeasurement(radio = RadioType.WCDMA, isRegistered = true))

        val downgrade = anomalies.filter { it.type == AnomalyType.DOWNGRADE_3G }
        assertThat(downgrade).hasSize(1)
        assertThat(downgrade[0].severity).isEqualTo(AnomalySeverity.MEDIUM)
    }

    @Test
    fun `given NR then CDMA, when analyzing measurements, then returns DOWNGRADE_3G`() {
        detector.analyze(baseMeasurement(radio = RadioType.NR, isRegistered = true))

        val anomalies = detector.analyze(baseMeasurement(radio = RadioType.CDMA, isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.DOWNGRADE_3G }).hasSize(1)
    }

    @Test
    fun `given WCDMA then WCDMA, when analyzing measurements, then no DOWNGRADE_3G`() {
        detector.analyze(baseMeasurement(radio = RadioType.WCDMA, isRegistered = true))

        val anomalies = detector.analyze(baseMeasurement(radio = RadioType.WCDMA, isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.DOWNGRADE_3G }).isEmpty()
    }

    @Test
    fun `given WCDMA then GSM, when analyzing measurements, then no DOWNGRADE_3G or 2G`() {
        // Downgrades only fire from LTE/NR; WCDMA->GSM is a normal handoff, not an anomaly.
        detector.analyze(baseMeasurement(radio = RadioType.WCDMA, isRegistered = true))

        val anomalies = detector.analyze(baseMeasurement(radio = RadioType.GSM, isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.DOWNGRADE_2G }).isEmpty()
        assertThat(anomalies.filter { it.type == AnomalyType.DOWNGRADE_3G }).isEmpty()
    }

    @Test
    fun `given LTE-WCDMA-LTE-WCDMA, when analyzing, then DOWNGRADE_3G fires twice (once per downgrade event)`() {
        detector.analyze(baseMeasurement(radio = RadioType.LTE, isRegistered = true))
        val first = detector.analyze(baseMeasurement(radio = RadioType.WCDMA, isRegistered = true))
        detector.analyze(baseMeasurement(radio = RadioType.LTE, isRegistered = true))
        val second = detector.analyze(baseMeasurement(radio = RadioType.WCDMA, isRegistered = true))

        assertThat(first.filter { it.type == AnomalyType.DOWNGRADE_3G }).hasSize(1)
        assertThat(second.filter { it.type == AnomalyType.DOWNGRADE_3G }).hasSize(1)
    }

    // --- SUSPICIOUS_PROXIMITY ---

    @Test
    fun `given stationary with TA 0 and moderate RSRP, when analyzing, then flags SUSPICIOUS_PROXIMITY`() {
        val m = baseMeasurement(isRegistered = true, speedMps = 0f)
            .copy(timingAdvance = 0, rsrp = -95)

        val anomalies = detector.analyze(m)

        val prox = anomalies.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }
        assertThat(prox).hasSize(1)
        assertThat(prox[0].severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given TA 0 but strong RSRP, when analyzing, then no SUSPICIOUS_PROXIMITY`() {
        // RSRP saturating near a real macro; the "moderate" window is -105..-85.
        val m = baseMeasurement(isRegistered = true, speedMps = 0f)
            .copy(timingAdvance = 0, rsrp = -60)

        val anomalies = detector.analyze(m)

        assertThat(anomalies.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }).isEmpty()
    }

    @Test
    fun `given TA 10 with moderate RSRP, when analyzing, then no SUSPICIOUS_PROXIMITY`() {
        val m = baseMeasurement(isRegistered = true, speedMps = 0f)
            .copy(timingAdvance = 10, rsrp = -95)

        val anomalies = detector.analyze(m)

        assertThat(anomalies.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }).isEmpty()
    }

    @Test
    fun `given driving speed with TA 0, when analyzing, then no SUSPICIOUS_PROXIMITY`() {
        // TA=0 next to a roadside tower is common while driving; gate out.
        val m = baseMeasurement(isRegistered = true, speedMps = 15f)
            .copy(timingAdvance = 0, rsrp = -95)

        val anomalies = detector.analyze(m)

        assertThat(anomalies.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }).isEmpty()
    }

    @Test
    fun `given TA null, when analyzing, then no SUSPICIOUS_PROXIMITY`() {
        val m = baseMeasurement(isRegistered = true, speedMps = 0f)
            .copy(timingAdvance = null, rsrp = -95)

        val anomalies = detector.analyze(m)

        assertThat(anomalies.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }).isEmpty()
    }

    @Test
    fun `given SUSPICIOUS_PROXIMITY hit twice for same tower, when analyzing, then only alerts once`() {
        val m = baseMeasurement(isRegistered = true, speedMps = 0f)
            .copy(timingAdvance = 0, rsrp = -95)

        val first = detector.analyze(m)
        val second = detector.analyze(m)

        assertThat(first.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }).hasSize(1)
        assertThat(second.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }).isEmpty()
    }

    // --- PCI_INSTABILITY ---

    @Test
    fun `given cached PCI differs from current measurement PCI, when analyzing, then flags PCI_INSTABILITY`() {
        val cached = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 12345; cid = 50331905L
            pci = 100
            latitude = 37.7749; longitude = -122.4194
        }
        every { towerCacheDao.findTower("LTE", 310, 260, 12345, 50331905L) } returns cached

        val m = baseMeasurement(isRegistered = true).copy(pciPsc = 200)
        val anomalies = detector.analyze(m)

        val pci = anomalies.filter { it.type == AnomalyType.PCI_INSTABILITY }
        assertThat(pci).hasSize(1)
        assertThat(pci[0].severity).isEqualTo(AnomalySeverity.MEDIUM)
    }

    @Test
    fun `given cached PCI matches measurement PCI, when analyzing, then no PCI_INSTABILITY`() {
        val cached = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 12345; cid = 50331905L
            pci = 214
            latitude = 37.7749; longitude = -122.4194
        }
        every { towerCacheDao.findTower("LTE", 310, 260, 12345, 50331905L) } returns cached

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_INSTABILITY }).isEmpty()
    }

    @Test
    fun `given no cached tower, when analyzing, then no PCI_INSTABILITY`() {
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns null

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_INSTABILITY }).isEmpty()
    }

    @Test
    fun `given cached tower with null PCI, when analyzing, then no PCI_INSTABILITY (first sighting)`() {
        val cached = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 12345; cid = 50331905L
            pci = null
            latitude = 37.7749; longitude = -122.4194
        }
        every { towerCacheDao.findTower("LTE", 310, 260, 12345, 50331905L) } returns cached

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true).copy(pciPsc = 200))

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_INSTABILITY }).isEmpty()
    }

    @Test
    fun `given measurement with null PCI, when analyzing, then no PCI_INSTABILITY`() {
        val cached = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 12345; cid = 50331905L
            pci = 100
            latitude = 37.7749; longitude = -122.4194
        }
        every { towerCacheDao.findTower("LTE", 310, 260, 12345, 50331905L) } returns cached

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true).copy(pciPsc = null))

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_INSTABILITY }).isEmpty()
    }

    @Test
    fun `given PCI instability same tower seen twice, when analyzing, then only alerts once`() {
        val cached = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 12345; cid = 50331905L
            pci = 100
            latitude = 37.7749; longitude = -122.4194
        }
        every { towerCacheDao.findTower("LTE", 310, 260, 12345, 50331905L) } returns cached

        val m = baseMeasurement(isRegistered = true).copy(pciPsc = 200)
        val first = detector.analyze(m)
        val second = detector.analyze(m)

        assertThat(first.filter { it.type == AnomalyType.PCI_INSTABILITY }).hasSize(1)
        assertThat(second.filter { it.type == AnomalyType.PCI_INSTABILITY }).isEmpty()
    }

    // --- LAC_TAC_CHANGE ---

    @Test
    fun `given TAC change while stationary, when analyzing, then returns LAC_TAC_CHANGE`() {
        detector.analyze(baseMeasurement(tacLac = 100, isRegistered = true))

        val anomalies = detector.analyze(baseMeasurement(tacLac = 200, isRegistered = true))

        val tac = anomalies.filter { it.type == AnomalyType.LAC_TAC_CHANGE }
        assertThat(tac).hasSize(1)
        assertThat(tac[0].severity).isEqualTo(AnomalySeverity.MEDIUM)
    }

    @Test
    fun `given TAC change with different operator, when analyzing, then no LAC_TAC_CHANGE`() {
        detector.analyze(baseMeasurement(mcc = 310, mnc = 260, tacLac = 100, isRegistered = true))

        val anomalies = detector.analyze(baseMeasurement(mcc = 310, mnc = 410, tacLac = 200, isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.LAC_TAC_CHANGE }).isEmpty()
    }

    // --- OPERATOR_MISMATCH ---

    @Test
    fun `given non-US MCC-MNC, when analyzing, then returns OPERATOR_MISMATCH`() {
        val anomalies = detector.analyze(baseMeasurement(mcc = 310, mnc = 999, isRegistered = true))

        val mm = anomalies.filter { it.type == AnomalyType.OPERATOR_MISMATCH }
        assertThat(mm).hasSize(1)
        assertThat(mm[0].severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given known US carrier, when analyzing, then no OPERATOR_MISMATCH`() {
        val anomalies = detector.analyze(baseMeasurement(mcc = 310, mnc = 260, isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.OPERATOR_MISMATCH }).isEmpty()
    }

    @Test
    fun `given non-US MCC, when analyzing, then no OPERATOR_MISMATCH`() {
        val anomalies = detector.analyze(baseMeasurement(mcc = 262, mnc = 1, isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.OPERATOR_MISMATCH }).isEmpty()
    }

    // --- SIGNAL_ANOMALY ---

    @Test
    fun `given signal 25dBm above average, when analyzing, then returns SIGNAL_ANOMALY`() {
        repeat(10) { detector.analyze(baseMeasurement(rsrp = -100, isRegistered = true)) }

        val anomalies = detector.analyze(baseMeasurement(rsrp = -70, isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.SIGNAL_ANOMALY }).hasSize(1)
    }

    @Test
    fun `given normal signal variation, when analyzing, then no SIGNAL_ANOMALY`() {
        repeat(10) { detector.analyze(baseMeasurement(rsrp = -90, isRegistered = false)) }

        val anomalies = detector.analyze(baseMeasurement(rsrp = -85, isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.SIGNAL_ANOMALY }).isEmpty()
    }

    // --- Threat scoring ---

    @Test
    fun `given multiple anomalies, when computing threat score, then sums weights correctly`() {
        val anomalies = listOf(
            anomalyOf(AnomalyType.SIGNAL_ANOMALY, AnomalySeverity.MEDIUM),    // +2
            anomalyOf(AnomalyType.DOWNGRADE_2G, AnomalySeverity.HIGH),        // +3
            anomalyOf(AnomalyType.OPERATOR_MISMATCH, AnomalySeverity.HIGH)    // +3
        )

        val score = detector.computeThreatScore(anomalies)

        assertThat(score).isEqualTo(8)
    }

    @Test
    fun `given DOWNGRADE_3G, SUSPICIOUS_PROXIMITY, PCI_INSTABILITY, when scoring, then uses expected weights`() {
        assertThat(detector.computeThreatScore(listOf(anomalyOf(AnomalyType.DOWNGRADE_3G, AnomalySeverity.MEDIUM))))
            .isEqualTo(2)
        assertThat(detector.computeThreatScore(listOf(anomalyOf(AnomalyType.SUSPICIOUS_PROXIMITY, AnomalySeverity.HIGH))))
            .isEqualTo(3)
        assertThat(detector.computeThreatScore(listOf(anomalyOf(AnomalyType.PCI_INSTABILITY, AnomalySeverity.MEDIUM))))
            .isEqualTo(2)
    }

    @Test
    fun `given SIGNAL_ANOMALY with different severities, when computing score, then weights by severity`() {
        assertThat(detector.computeThreatScore(listOf(anomalyOf(AnomalyType.SIGNAL_ANOMALY, AnomalySeverity.LOW)))).isEqualTo(1)
        assertThat(detector.computeThreatScore(listOf(anomalyOf(AnomalyType.SIGNAL_ANOMALY, AnomalySeverity.HIGH)))).isEqualTo(3)
    }

    // --- IMPOSSIBLE_MOVE ---

    @Test
    fun `given cached tower 1000km from current GPS, when analyzing, then flags IMPOSSIBLE_MOVE`() {
        val cached = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 12345; cid = 50331905L
            latitude = 53.3498; longitude = -6.2603
        }
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns cached

        val measurement = baseMeasurement(isRegistered = true).copy(
            latitude = 51.5074, longitude = -0.1278
        )

        val anomalies = detector.analyze(measurement)

        assertThat(anomalies.any { it.type == AnomalyType.IMPOSSIBLE_MOVE }).isTrue()
        assertThat(anomalies.first { it.type == AnomalyType.IMPOSSIBLE_MOVE }.severity)
            .isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given cached tower within 5km of current GPS, when analyzing, then no IMPOSSIBLE_MOVE`() {
        val cached = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 12345; cid = 50331905L
            latitude = 37.7749; longitude = -122.4194
        }
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns cached

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        assertThat(anomalies.any { it.type == AnomalyType.IMPOSSIBLE_MOVE }).isFalse()
    }

    @Test
    fun `given no cached tower, when analyzing, then no IMPOSSIBLE_MOVE`() {
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns null
        every { towerCacheDao.findAnyByCidRange(any(), any(), any(), any(), any()) } returns null

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        assertThat(anomalies.any { it.type == AnomalyType.IMPOSSIBLE_MOVE }).isFalse()
    }

    @Test
    fun `given exact CID missing but sister sector in cache far away, when analyzing LTE, then flags IMPOSSIBLE_MOVE via eNB fallback`() {
        val sister = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 99999; cid = 50331900L
            latitude = 53.3498; longitude = -6.2603
        }
        every { towerCacheDao.findTower(any(), any(), any(), any(), any()) } returns null
        every { towerCacheDao.findAnyByCidRange(any(), any(), any(), any(), any()) } returns sister

        val measurement = baseMeasurement(isRegistered = true).copy(
            latitude = 51.5074, longitude = -0.1278
        )

        val anomalies = detector.analyze(measurement)

        assertThat(anomalies.any { it.type == AnomalyType.IMPOSSIBLE_MOVE }).isTrue()
    }

    // --- POPUP_TOWER ---

    @Test
    fun `given prior coverage without this tower, when tower appears, then POPUP_TOWER fires`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.countTowerObservationsInArea(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        val popup = anomalies.filter { it.type == AnomalyType.POPUP_TOWER }
        assertThat(popup).hasSize(1)
        assertThat(popup[0].severity).isEqualTo(AnomalySeverity.MEDIUM)
        assertThat(popup[0].cellCid).isEqualTo(50331905L)
    }

    @Test
    fun `given no prior coverage in area, when new tower seen, then no POPUP_TOWER (cold-start gate)`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any())
        } returns 5
        every {
            measurementDao.countTowerObservationsInArea(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given prior measurements include this tower, when seen again, then no POPUP_TOWER`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.countTowerObservationsInArea(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 1

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given POPUP_TOWER fired this session, when same tower seen on next scan, then no duplicate alert`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.countTowerObservationsInArea(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0

        val first = detector.analyze(baseMeasurement(isRegistered = true))
        val second = detector.analyze(baseMeasurement(isRegistered = true))

        assertThat(first.filter { it.type == AnomalyType.POPUP_TOWER }).hasSize(1)
        assertThat(second.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given driving speed above threshold, when popup conditions met, then no POPUP_TOWER`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.countTowerObservationsInArea(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true, speedMps = 15f))

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given measurement with null cid, when checkPopupTower runs, then returns null`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.countTowerObservationsInArea(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0

        val m = baseMeasurement(isRegistered = true).copy(cid = null)
        val anomalies = detector.analyze(m)

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given unregistered measurement, when checkPopupTower runs, then returns null`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.countTowerObservationsInArea(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0

        val anomalies = detector.analyze(baseMeasurement(isRegistered = false))

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given exactly POPUP_MIN_PRIOR_MEASUREMENTS prior measurements without this tower, when popup conditions met, then POPUP_TOWER fires`() {
        // Boundary: priorCount == 20 should fire (predicate is `<`, not `<=`).
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any())
        } returns 20
        every {
            measurementDao.countTowerObservationsInArea(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).hasSize(1)
    }

    @Test
    fun `given measurementDao is null (legacy ctor), when analyzing, then POPUP_TOWER never fires`() {
        // Back-compat shim: the secondary constructor passes null for measurementDao,
        // so the popup detector silently no-ops. Existing callers and tests that
        // construct AnomalyDetector(towerCacheDao) keep working.
        val legacyDetector = AnomalyDetector(towerCacheDao)

        val anomalies = legacyDetector.analyze(baseMeasurement(isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given POPUP_TOWER threat weight, when computing score, then equals 2`() {
        val score = detector.computeThreatScore(
            listOf(anomalyOf(AnomalyType.POPUP_TOWER, AnomalySeverity.MEDIUM))
        )

        assertThat(score).isEqualTo(2)
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

    // --- Speed gate ---

    @Test
    fun `given a stationary measurement, when checkSignalAnomaly runs, then it flags strong signal`() {
        repeat(10) { detector.analyze(baseMeasurement(rsrp = -100, isRegistered = true)) }

        val anomalies = detector.analyze(baseMeasurement(rsrp = -70, isRegistered = true, speedMps = 0f))

        assertThat(anomalies.any { it.type == AnomalyType.SIGNAL_ANOMALY }).isTrue()
    }

    @Test
    fun `given a driving measurement at 15 m per s, when checkSignalAnomaly runs, then returns null`() {
        repeat(10) { detector.analyze(baseMeasurement(rsrp = -100, isRegistered = true)) }

        val anomalies = detector.analyze(baseMeasurement(rsrp = -70, isRegistered = true, speedMps = 15f))

        assertThat(anomalies.any { it.type == AnomalyType.SIGNAL_ANOMALY }).isFalse()
    }

    @Test
    fun `given two driving TAC flips, when analyzing, then no LAC_TAC_CHANGE anomaly`() {
        detector.analyze(baseMeasurement(tacLac = 100, isRegistered = true, speedMps = 15f))
        val first = detector.analyze(baseMeasurement(tacLac = 200, isRegistered = true, speedMps = 15f))
        val second = detector.analyze(baseMeasurement(tacLac = 300, isRegistered = true, speedMps = 15f))

        assertThat(first.any { it.type == AnomalyType.LAC_TAC_CHANGE }).isFalse()
        assertThat(second.any { it.type == AnomalyType.LAC_TAC_CHANGE }).isFalse()
    }

    @Test
    fun `given three driving TAC flips, when analyzing, then LAC_TAC_CHANGE fires`() {
        val now = System.currentTimeMillis()
        detector.analyze(baseMeasurement(tacLac = 100, isRegistered = true, speedMps = 15f, timestamp = now))
        detector.analyze(baseMeasurement(tacLac = 200, isRegistered = true, speedMps = 15f, timestamp = now + 1000))
        detector.analyze(baseMeasurement(tacLac = 300, isRegistered = true, speedMps = 15f, timestamp = now + 2000))

        val anomalies = detector.analyze(
            baseMeasurement(tacLac = 400, isRegistered = true, speedMps = 15f, timestamp = now + 3000)
        )

        assertThat(anomalies.any { it.type == AnomalyType.LAC_TAC_CHANGE }).isTrue()
    }

    @Test
    fun `given a 6 minute old transient at driving speed, when analyzing, then no TRANSIENT_TOWER anomaly`() {
        val now = System.currentTimeMillis()
        detector.analyze(baseMeasurement(cid = 111L, isRegistered = true, speedMps = 15f, timestamp = now))

        val anomalies = detector.analyze(
            baseMeasurement(cid = 222L, isRegistered = true, speedMps = 15f, timestamp = now + 6 * 60_000L)
        )

        assertThat(anomalies.any { it.type == AnomalyType.TRANSIENT_TOWER }).isFalse()
    }

    @Test
    fun `given stationary tower seen briefly then displaced after 6 minutes, when analyzing, then TRANSIENT_TOWER fires`() {
        // Given -- tower 111 appears briefly (10s) at a stationary speed.
        val now = System.currentTimeMillis()
        detector.analyze(baseMeasurement(cid = 111L, isRegistered = true, timestamp = now))
        detector.analyze(baseMeasurement(cid = 111L, isRegistered = true, timestamp = now + 10_000L))

        // When -- 6 minutes later (past the 5-min stationary window) we see a different tower.
        val anomalies = detector.analyze(
            baseMeasurement(cid = 222L, isRegistered = true, timestamp = now + 6 * 60_000L)
        )

        // Then -- tower 111 is flagged as transient with description noting the brief duration.
        val transient = requireNotNull(
            anomalies.firstOrNull { it.type == AnomalyType.TRANSIENT_TOWER }
        ) { "Expected TRANSIENT_TOWER anomaly to be present" }
        assertThat(transient.description).contains("appeared briefly")
        assertThat(transient.severity).isEqualTo(AnomalySeverity.MEDIUM)
    }

    // --- Signal anomaly identity gate ---

    @Test
    fun `given an unregistered neighbour cell with null cell identity, when checkSignalAnomaly runs, then returns null`() {
        repeat(10) { detector.analyze(baseMeasurement(rsrp = -100, isRegistered = true)) }

        val neighbour = baseMeasurement(rsrp = -60, isRegistered = false)
            .copy(mcc = null, mnc = null, tacLac = null, cid = null)
        val result = detector.checkSignalAnomaly(neighbour)

        assertThat(result).isNull()
    }

    // --- Helpers ---

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

    private fun anomalyOf(type: AnomalyType, severity: AnomalySeverity) =
        com.celltowerid.android.domain.model.AnomalyEvent(
            timestamp = System.currentTimeMillis(),
            type = type,
            severity = severity,
            description = "Test anomaly"
        )
}
