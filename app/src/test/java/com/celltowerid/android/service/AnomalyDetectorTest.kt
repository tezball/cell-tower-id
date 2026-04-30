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
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 0
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns null
        every {
            measurementDao.countSiblingSectorsInArea(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()
            )
        } returns 0
        // 0L means "first measurement at epoch" — produces a huge area age, so by
        // default tests treat the bbox as a fully-mature baseline (>= 7 days). Tests
        // that exercise the bootstrap-severity logic override this to a recent time.
        every {
            measurementDao.findFirstMeasurementTimeInArea(any(), any(), any(), any())
        } returns 0L
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0
        every {
            measurementDao.countDistinctEnbsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0
        every {
            measurementDao.findMostRecentCidForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns null

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

    @Test
    fun `given LTE on operator A then GSM on operator B, when analyzing, then no DOWNGRADE_2G (roaming fallback)`() {
        // Real-world Three IE -> Vodafone IE 2G fallback: Three IE has no 2G of
        // its own, so when LTE coverage drops the device roams to a different
        // operator's GSM. The MCC/MNC change distinguishes this from an attack
        // (sophisticated IMSI catchers spoof the home operator).
        detector.analyze(baseMeasurement(radio = RadioType.LTE, mcc = 272, mnc = 5, isRegistered = true))

        val anomalies = detector.analyze(
            baseMeasurement(radio = RadioType.GSM, mcc = 272, mnc = 2, isRegistered = true)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.DOWNGRADE_2G }).isEmpty()
    }

    @Test
    fun `given LTE on operator A then WCDMA on operator B, when analyzing, then no DOWNGRADE_3G (roaming fallback)`() {
        detector.analyze(baseMeasurement(radio = RadioType.LTE, mcc = 272, mnc = 5, isRegistered = true))

        val anomalies = detector.analyze(
            baseMeasurement(radio = RadioType.WCDMA, mcc = 272, mnc = 2, isRegistered = true)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.DOWNGRADE_3G }).isEmpty()
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

    @Test
    fun `given TA 1 and RSRP below -95 floor, when analyzing, then no SUSPICIOUS_PROXIMITY`() {
        // Real-world Three IE Dublin: RSRP=-104..-105 with TA=1 stationary is the
        // small-cell-with-indoor-blocking signature, not a portable IMSI catcher.
        val m = baseMeasurement(isRegistered = true, speedMps = 0f)
            .copy(timingAdvance = 1, rsrp = -100)

        val anomalies = detector.analyze(m)

        assertThat(anomalies.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }).isEmpty()
    }

    @Test
    fun `given measurement at driving speed within 30s, when stationary TA 1 arrives, then no SUSPICIOUS_PROXIMITY`() {
        // Real-world driveby: 17 m/s on highway, brake to 2.9 m/s at a junction.
        // The 3.0 m/s static gate would fire on the brake-light dip; hysteresis
        // requires the prior 30 s to have been at walking speed.
        val baseMs = 1_000_000_000L
        detector.analyze(
            baseMeasurement(isRegistered = true, speedMps = 15f, timestamp = baseMs)
        )

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, speedMps = 0f, timestamp = baseMs + 20_000L)
                .copy(timingAdvance = 1, rsrp = -90)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }).isEmpty()
    }

    @Test
    fun `given measurement at driving speed 60s ago, when stationary TA 1 arrives, then SUSPICIOUS_PROXIMITY fires`() {
        // Hysteresis is exactly 30 s — beyond that, a prior driving sample no
        // longer suppresses, so genuinely-stationary observations still alert.
        val baseMs = 1_000_000_000L
        detector.analyze(
            baseMeasurement(isRegistered = true, speedMps = 15f, timestamp = baseMs)
        )

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, speedMps = 0f, timestamp = baseMs + 60_000L)
                .copy(timingAdvance = 1, rsrp = -90)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }).hasSize(1)
    }

    @Test
    fun `given prior saturation RSRP for same cell, when later TA 1 stationary in trigger range, then no SUSPICIOUS_PROXIMITY`() {
        // Real-world: a residential small cell whose RSRP varies between -66
        // (saturation) and -87 (body shadowing) as the user moves around it.
        // Once we've seen RSRP >= -75 for this cell in the recent buffer, treat
        // moderate-RSRP observations as signal variability, not a fake transmitter.
        val baseMs = 1_000_000_000L
        detector.analyze(
            baseMeasurement(isRegistered = true, speedMps = 0f, rsrp = -70, timestamp = baseMs)
        )

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, speedMps = 0f, rsrp = -90, timestamp = baseMs + 60_000L)
                .copy(timingAdvance = 1)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }).isEmpty()
    }

    @Test
    fun `given prior saturation RSRP for different cell, when current cell triggers, then SUSPICIOUS_PROXIMITY fires`() {
        // Saturation suppression is per-(radio,mcc,mnc,tacLac,cid). A strong
        // observation of cell A doesn't excuse a moderate-at-TA-1 observation
        // of cell B in the same area.
        val baseMs = 1_000_000_000L
        detector.analyze(
            baseMeasurement(
                isRegistered = true, speedMps = 0f, rsrp = -70, cid = 99999L, timestamp = baseMs
            )
        )

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, speedMps = 0f, rsrp = -90, timestamp = baseMs + 60_000L)
                .copy(timingAdvance = 1)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }).hasSize(1)
    }

    @Test
    fun `given a fully-populated measurement, when SUSPICIOUS_PROXIMITY fires, then the event carries every signal field`() {
        // Triggering measurement has values across the whole signal/identity surface
        // — assert that the AnomalyEvent forwards each one so the alert→tower-detail
        // navigation can show the same data the cells list does.
        val m = baseMeasurement(isRegistered = true, speedMps = 0f)
            .copy(
                timingAdvance = 1,
                rsrp = -95,
                rsrq = -12,
                rssi = -75,
                sinr = 3,
                cqi = 7,
                signalLevel = 2,
                earfcnArfcn = 6300,
                band = 66,
                bandwidth = 20000,
                gpsAccuracy = 8.5f,
                operatorName = "T-Mobile"
            )

        val event = detector.analyze(m).first { it.type == AnomalyType.SUSPICIOUS_PROXIMITY }

        assertThat(event.timingAdvance).isEqualTo(1)
        assertThat(event.rsrp).isEqualTo(-95)
        assertThat(event.rsrq).isEqualTo(-12)
        assertThat(event.rssi).isEqualTo(-75)
        assertThat(event.sinr).isEqualTo(3)
        assertThat(event.cqi).isEqualTo(7)
        assertThat(event.signalLevel).isEqualTo(2)
        assertThat(event.earfcnArfcn).isEqualTo(6300)
        assertThat(event.band).isEqualTo(66)
        assertThat(event.bandwidth).isEqualTo(20000)
        assertThat(event.gpsAccuracy).isEqualTo(8.5f)
        assertThat(event.operatorName).isEqualTo("T-Mobile")
        assertThat(event.isRegistered).isTrue()
    }

    @Test
    fun `given a fully-populated measurement, when OPERATOR_MISMATCH fires, then the event carries every signal field`() {
        // Spot-check a non-proximity detector to confirm the field-passthrough is
        // not specific to one detector — the helper feeds every detector.
        val m = baseMeasurement(mcc = 310, mnc = 999, isRegistered = true)
            .copy(
                timingAdvance = 4,
                rsrp = -88,
                rsrq = -9,
                sinr = 12,
                cqi = 10,
                signalLevel = 3,
                earfcnArfcn = 2050,
                band = 4,
                bandwidth = 15000,
                gpsAccuracy = 6.0f,
                operatorName = "Mystery"
            )

        val event = detector.analyze(m).first { it.type == AnomalyType.OPERATOR_MISMATCH }

        assertThat(event.timingAdvance).isEqualTo(4)
        assertThat(event.rsrp).isEqualTo(-88)
        assertThat(event.rsrq).isEqualTo(-9)
        assertThat(event.sinr).isEqualTo(12)
        assertThat(event.cqi).isEqualTo(10)
        assertThat(event.signalLevel).isEqualTo(3)
        assertThat(event.earfcnArfcn).isEqualTo(2050)
        assertThat(event.band).isEqualTo(4)
        assertThat(event.bandwidth).isEqualTo(15000)
        assertThat(event.gpsAccuracy).isEqualTo(6.0f)
        assertThat(event.operatorName).isEqualTo("Mystery")
        assertThat(event.isRegistered).isTrue()
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
    fun `given prior coverage without this tower, when tower appears, then POPUP_TOWER fires HIGH`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns null

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        val popup = anomalies.filter { it.type == AnomalyType.POPUP_TOWER }
        assertThat(popup).hasSize(1)
        assertThat(popup[0].severity).isEqualTo(AnomalySeverity.HIGH)
        assertThat(popup[0].cellCid).isEqualTo(50331905L)
        assertThat(popup[0].description).contains("first time")
    }

    @Test
    fun `given no prior coverage in area, when new tower seen, then no POPUP_TOWER (cold-start gate)`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 5
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns null

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given recent prior sighting in area, when tower seen, then no POPUP_TOWER (continuous coverage)`() {
        // Sighting 5 minutes ago — well within the 6h recent window. Established tower.
        val now = System.currentTimeMillis()
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns now - 5 * 60_000L

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true, timestamp = now))

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given last sighting older than 6 hours, when tower reappears, then POPUP_TOWER fires (gap reappearance)`() {
        // The user's scenario: tower was seen previously, then absent for ≥6h, now back.
        val now = System.currentTimeMillis()
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns now - 24 * 60 * 60_000L // 24 hours ago

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true, timestamp = now))

        val popup = anomalies.filter { it.type == AnomalyType.POPUP_TOWER }
        assertThat(popup).hasSize(1)
        assertThat(popup[0].severity).isEqualTo(AnomalySeverity.HIGH)
        assertThat(popup[0].description).contains("reappeared")
    }

    @Test
    fun `given last sighting at the 6 hour boundary, when tower reappears, then POPUP_TOWER fires`() {
        // Boundary: gap == POPUP_RECENT_WINDOW_MS should fire (predicate is `<`, not `<=`).
        val now = System.currentTimeMillis()
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns now - 6L * 60 * 60_000L

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true, timestamp = now))

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).hasSize(1)
    }

    @Test
    fun `given POPUP_TOWER fired this scan burst, when same tower seen on next scan, then no duplicate alert`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns null

        val now = System.currentTimeMillis()
        val first = detector.analyze(baseMeasurement(isRegistered = true, timestamp = now))
        val second = detector.analyze(
            baseMeasurement(isRegistered = true, timestamp = now + 10_000L)
        )

        assertThat(first.filter { it.type == AnomalyType.POPUP_TOWER }).hasSize(1)
        assertThat(second.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given driving speed above threshold, when popup conditions met, then no POPUP_TOWER`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns null

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true, speedMps = 15f))

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given measurement with null cid, when checkPopupTower runs, then returns null`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns null

        val m = baseMeasurement(isRegistered = true).copy(cid = null)
        val anomalies = detector.analyze(m)

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given unregistered measurement, when checkPopupTower runs, then returns null`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns null

        val anomalies = detector.analyze(baseMeasurement(isRegistered = false))

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given exactly POPUP_MIN_PRIOR_MEASUREMENTS prior measurements without this tower, when popup conditions met, then POPUP_TOWER fires`() {
        // Boundary: priorCount == 20 should fire (predicate is `<`, not `<=`).
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 20
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns null

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
    fun `given POPUP_TOWER threat weight, when computing score, then equals 3`() {
        val score = detector.computeThreatScore(
            listOf(anomalyOf(AnomalyType.POPUP_TOWER, AnomalySeverity.HIGH))
        )

        assertThat(score).isEqualTo(3)
    }

    // --- POPUP_TOWER sibling-eNB suppression ---

    @Test
    fun `given LTE eNB with established sibling sectors, when new sector appears, then POPUP_TOWER is suppressed`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.countSiblingSectorsInArea(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()
            )
        } returns 5

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, radio = RadioType.LTE)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given LTE eNB with sibling count below threshold, when new sector appears, then POPUP_TOWER fires`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.countSiblingSectorsInArea(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()
            )
        } returns 4

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, radio = RadioType.LTE)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).hasSize(1)
    }

    @Test
    fun `given UMTS cell with sibling-like CID, when new cell appears, then POPUP_TOWER still fires`() {
        // UMTS CID has no eNB-encoded structure; sibling gate is LTE/NR-only.
        // Even if the DAO would report siblings, the detector must skip the query.
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.countSiblingSectorsInArea(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()
            )
        } returns 100

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, radio = RadioType.WCDMA)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).hasSize(1)
    }

    @Test
    fun `given NR cell with established sibling sectors, when new sector appears, then POPUP_TOWER is suppressed`() {
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.countSiblingSectorsInArea(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()
            )
        } returns 10

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, radio = RadioType.NR)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    // --- POPUP_TOWER bootstrap-aware severity ---

    @Test
    fun `given area baseline less than 7 days old, when first popup fires, then severity is MEDIUM`() {
        val now = System.currentTimeMillis()
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findFirstMeasurementTimeInArea(any(), any(), any(), any())
        } returns now - 3L * 24 * 60 * 60 * 1000  // 3 days ago

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, timestamp = now)
        )

        val popup = anomalies.filter { it.type == AnomalyType.POPUP_TOWER }
        assertThat(popup).hasSize(1)
        assertThat(popup[0].severity).isEqualTo(AnomalySeverity.MEDIUM)
    }

    @Test
    fun `given area baseline at least 7 days old, when first popup fires, then severity is HIGH`() {
        val now = System.currentTimeMillis()
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findFirstMeasurementTimeInArea(any(), any(), any(), any())
        } returns now - 8L * 24 * 60 * 60 * 1000  // 8 days ago

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, timestamp = now)
        )

        val popup = anomalies.filter { it.type == AnomalyType.POPUP_TOWER }
        assertThat(popup).hasSize(1)
        assertThat(popup[0].severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given young area baseline, when gap-reappearance popup fires, then severity is HIGH`() {
        // Gap-reappearance branch represents "tower vanished and came back" — the
        // strongest popup signal. Severity must stay HIGH regardless of area age.
        val now = System.currentTimeMillis()
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns now - 24L * 60 * 60 * 1000  // 24h gap
        every {
            measurementDao.findFirstMeasurementTimeInArea(any(), any(), any(), any())
        } returns now - 3L * 24 * 60 * 60 * 1000  // 3 days ago — young

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, timestamp = now)
        )

        val popup = anomalies.filter { it.type == AnomalyType.POPUP_TOWER }
        assertThat(popup).hasSize(1)
        assertThat(popup[0].severity).isEqualTo(AnomalySeverity.HIGH)
    }

    // --- POPUP_TOWER bootstrap window ---

    @Test
    fun `given area baseline less than 24 hours old, when first popup would fire, then it is suppressed`() {
        // Fresh dataset: every cell looks like a "first time" sighting. Suppress
        // first-popup alerts entirely until the bbox has 24h of baseline.
        val now = System.currentTimeMillis()
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findFirstMeasurementTimeInArea(any(), any(), any(), any())
        } returns now - 6L * 60 * 60 * 1000  // 6 hours ago

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, timestamp = now)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.POPUP_TOWER }).isEmpty()
    }

    @Test
    fun `given area baseline less than 24 hours old, when gap-reappearance popup fires, then it still fires`() {
        // The gap-reappearance branch bypasses the bootstrap window: a tower that
        // was seen, vanished, and came back is a meaningful signal even on a
        // fresh dataset.
        val now = System.currentTimeMillis()
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findMostRecentTowerSighting(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns now - 12L * 60 * 60 * 1000  // 12h gap
        every {
            measurementDao.findFirstMeasurementTimeInArea(any(), any(), any(), any())
        } returns now - 6L * 60 * 60 * 1000  // 6 hours ago — well inside bootstrap window

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, timestamp = now)
        )

        val popup = anomalies.filter { it.type == AnomalyType.POPUP_TOWER }
        assertThat(popup).hasSize(1)
        assertThat(popup[0].severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given area baseline 25 hours old, when first popup fires, then severity is MEDIUM`() {
        // Boundary just past the bootstrap window: gate releases, but baseline is
        // still well under 7d so severity is MEDIUM not HIGH.
        val now = System.currentTimeMillis()
        every {
            measurementDao.countMeasurementsInArea(any(), any(), any(), any(), any(), any())
        } returns 30
        every {
            measurementDao.findFirstMeasurementTimeInArea(any(), any(), any(), any())
        } returns now - 25L * 60 * 60 * 1000  // 25 hours ago

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, timestamp = now)
        )

        val popup = anomalies.filter { it.type == AnomalyType.POPUP_TOWER }
        assertThat(popup).hasSize(1)
        assertThat(popup[0].severity).isEqualTo(AnomalySeverity.MEDIUM)
    }

    // --- PCI_COLLISION ---

    @Test
    fun `given two distinct CIDs sharing a PCI in the bbox, when measurement arrives, then PCI_COLLISION fires HIGH`() {
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2
        every {
            measurementDao.countDistinctEnbsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        val collision = anomalies.filter { it.type == AnomalyType.PCI_COLLISION }
        assertThat(collision).hasSize(1)
        assertThat(collision[0].severity).isEqualTo(AnomalySeverity.HIGH)
        assertThat(collision[0].description).contains("shares PCI")
    }

    @Test
    fun `given two CIDs sharing PCI but same eNB, when LTE measurement arrives, then PCI_COLLISION suppressed`() {
        // Real-world Dublin example: two CIDs (sectors 2 and 32 of eNB 4709)
        // sharing PCI=173 — same physical site, not a fake cell.
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2
        every {
            measurementDao.countDistinctEnbsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 1

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, radio = RadioType.LTE)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_COLLISION }).isEmpty()
    }

    @Test
    fun `given two CIDs sharing PCI on different eNBs, when LTE measurement arrives, then PCI_COLLISION fires HIGH`() {
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2
        every {
            measurementDao.countDistinctEnbsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, radio = RadioType.LTE)
        )

        val collision = anomalies.filter { it.type == AnomalyType.PCI_COLLISION }
        assertThat(collision).hasSize(1)
        assertThat(collision[0].severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given reuse-branch CID is sibling of current CID on same eNB, when LTE measurement arrives, then no alert`() {
        // current cid 1_205_536L (eNB 4709 sector 32); prior cid 1_205_506L
        // (eNB 4709 sector 2). 1_205_536 shr 8 == 1_205_506 shr 8 == 4709.
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 1
        every {
            measurementDao.findMostRecentCidForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 1_205_506L

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, radio = RadioType.LTE, cid = 1_205_536L)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_COLLISION }).isEmpty()
    }

    @Test
    fun `given UMTS cell with two CIDs sharing PCI, when measurement arrives, then PCI_COLLISION fires HIGH (no eNB gate for non-LTE-NR)`() {
        // For UMTS the CID has no eNB-encoded structure, so the sibling-eNB
        // gate must be skipped — even if countDistinctEnbsForPci would return 1
        // (which it won't be queried for), the collision must still fire.
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2
        every {
            measurementDao.countDistinctEnbsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 1

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, radio = RadioType.WCDMA)
        )

        val collision = anomalies.filter { it.type == AnomalyType.PCI_COLLISION }
        assertThat(collision).hasSize(1)
        assertThat(collision[0].severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given a single CID per PCI in the bbox, when measurement arrives, then no PCI_COLLISION`() {
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 1
        every {
            measurementDao.findMostRecentCidForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 50331905L  // same as default cid in baseMeasurement

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_COLLISION }).isEmpty()
    }

    @Test
    fun `given a familiar PCI now hosted by a new CID, when measurement arrives, then PCI_COLLISION fires HIGH (reuse branch)`() {
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 1
        every {
            measurementDao.findMostRecentCidForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 99999999L  // different from default cid 50331905L

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true))

        val collision = anomalies.filter { it.type == AnomalyType.PCI_COLLISION }
        assertThat(collision).hasSize(1)
        assertThat(collision[0].severity).isEqualTo(AnomalySeverity.HIGH)
        assertThat(collision[0].description).contains("PCI reuse")
    }

    @Test
    fun `given driving speed above threshold, when collision conditions met, then no PCI_COLLISION`() {
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true, speedMps = 15f))

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_COLLISION }).isEmpty()
    }

    @Test
    fun `given measurement with null pciPsc, when checkPciCollision runs, then returns null`() {
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2

        val m = baseMeasurement(isRegistered = true).copy(pciPsc = null)
        val anomalies = detector.analyze(m)

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_COLLISION }).isEmpty()
    }

    @Test
    fun `given PCI_COLLISION fires once, when same PCI seen 5 minutes later, then no duplicate alert`() {
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2
        every {
            measurementDao.countDistinctEnbsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2

        // Fixed timestamp so the +5min delta stays in the same 6h dedupe bucket
        // regardless of wall-clock remainder; same baseMs as the gap-roll test below.
        val baseMs = 1_000_000_000L
        val first = detector.analyze(baseMeasurement(isRegistered = true, timestamp = baseMs))
        val second = detector.analyze(
            baseMeasurement(isRegistered = true, timestamp = baseMs + 5L * 60_000L)
        )

        assertThat(first.filter { it.type == AnomalyType.PCI_COLLISION }).hasSize(1)
        assertThat(second.filter { it.type == AnomalyType.PCI_COLLISION }).isEmpty()
    }

    @Test
    fun `given gap longer than 6 hours after PCI_COLLISION, when same PCI seen again, then re-fires`() {
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2
        every {
            measurementDao.countDistinctEnbsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2

        // Use a timestamp aligned to the bucket boundary so + 7h definitely
        // crosses into the next bucket regardless of wall-clock remainder.
        val baseMs = 1_000_000_000L
        val first = detector.analyze(baseMeasurement(isRegistered = true, timestamp = baseMs))
        val second = detector.analyze(
            baseMeasurement(isRegistered = true, timestamp = baseMs + 7L * 60 * 60_000L)
        )

        assertThat(first.filter { it.type == AnomalyType.PCI_COLLISION }).hasSize(1)
        assertThat(second.filter { it.type == AnomalyType.PCI_COLLISION }).hasSize(1)
    }

    @Test
    fun `given LTE measurement at noise-floor RSRP, when collision conditions met, then no PCI_COLLISION`() {
        // Real-world eir Dublin: a neighbor-list scan at RSRP=-120 dBm produced a
        // false-positive collision. Cell-ID parsing at noise floor isn't
        // reliable, so the detector now requires RSRP >= -115 dBm to fire.
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2
        every {
            measurementDao.countDistinctEnbsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, radio = RadioType.LTE, rsrp = -120)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_COLLISION }).isEmpty()
    }

    @Test
    fun `given LTE measurement at -115 dBm, when collision conditions met, then PCI_COLLISION fires`() {
        // -115 dBm is exactly the gate; the floor is inclusive so this still
        // qualifies. Anything above the noise floor remains in scope.
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2
        every {
            measurementDao.countDistinctEnbsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, radio = RadioType.LTE, rsrp = -115)
        )

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_COLLISION }).hasSize(1)
    }

    @Test
    fun `given UMTS measurement with null rsrp, when collision conditions met, then PCI_COLLISION fires (RSRP gate skipped)`() {
        // RSRP is LTE/NR-only. UMTS/GSM measurements have null rsrp; the noise-
        // floor gate only applies when rsrp is non-null.
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2

        val m = baseMeasurement(isRegistered = true, radio = RadioType.WCDMA, rsrp = null)
        val anomalies = detector.analyze(m)

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_COLLISION }).hasSize(1)
    }

    @Test
    fun `given prior CID with same PCI in different TAC, when measurement arrives, then no PCI_COLLISION`() {
        // Real operators reuse PCI across TAC boundaries by design — the DAO
        // queries are now scoped to the current measurement's tacLac, so a
        // prior cell with PCI=334 in TAC 40072 doesn't collide with a current
        // cell with PCI=334 in TAC 40071. This is the alert-30 case.
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 0  // no prior CIDs in same TAC
        every {
            measurementDao.findMostRecentCidForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns null  // no prior CID in same TAC

        val anomalies = detector.analyze(baseMeasurement(isRegistered = true, tacLac = 40071))

        assertThat(anomalies.filter { it.type == AnomalyType.PCI_COLLISION }).isEmpty()
    }

    @Test
    fun `given two CIDs sharing PCI in same TAC, when LTE measurement arrives, then PCI_COLLISION fires HIGH`() {
        // Sanity: TAC-aware scoping does not break legitimate same-TAC collisions.
        every {
            measurementDao.countDistinctCidsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2
        every {
            measurementDao.countDistinctEnbsForPci(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns 2

        val anomalies = detector.analyze(
            baseMeasurement(isRegistered = true, radio = RadioType.LTE, tacLac = 40071)
        )

        val collision = anomalies.filter { it.type == AnomalyType.PCI_COLLISION }
        assertThat(collision).hasSize(1)
        assertThat(collision[0].severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given PCI_COLLISION threat weight, when computing score, then equals 4`() {
        val score = detector.computeThreatScore(
            listOf(anomalyOf(AnomalyType.PCI_COLLISION, AnomalySeverity.HIGH))
        )
        assertThat(score).isEqualTo(4)
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
