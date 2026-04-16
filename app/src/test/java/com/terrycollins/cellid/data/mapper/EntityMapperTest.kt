package com.terrycollins.cellid.data.mapper

import com.terrycollins.cellid.data.entity.AnomalyEntity
import com.terrycollins.cellid.data.entity.MeasurementEntity
import com.terrycollins.cellid.data.entity.TowerCacheEntity
import com.terrycollins.cellid.domain.model.AnomalyEvent
import com.terrycollins.cellid.domain.model.AnomalySeverity
import com.terrycollins.cellid.domain.model.AnomalyType
import com.terrycollins.cellid.domain.model.CellMeasurement
import com.terrycollins.cellid.domain.model.CellTower
import com.terrycollins.cellid.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntityMapperTest {

    // --- CellMeasurement <-> MeasurementEntity ---

    @Test
    fun `given a CellMeasurement when mapped to entity then all fields are preserved`() {
        val measurement = CellMeasurement(
            timestamp = 1000L,
            latitude = 37.7749,
            longitude = -122.4194,
            gpsAccuracy = 10.5f,
            radio = RadioType.LTE,
            mcc = 310,
            mnc = 260,
            tacLac = 12345,
            cid = 50331905L,
            pciPsc = 214,
            earfcnArfcn = 2050,
            bandwidth = 15000,
            band = 4,
            rsrp = -85,
            rsrq = -10,
            rssi = -55,
            sinr = 18,
            cqi = 12,
            timingAdvance = 2,
            signalLevel = 3,
            isRegistered = true,
            operatorName = "T-Mobile"
        )

        val entity = EntityMapper.toEntity(measurement, sessionId = 42L)

        assertThat(entity.timestamp).isEqualTo(1000L)
        assertThat(entity.latitude).isEqualTo(37.7749)
        assertThat(entity.longitude).isEqualTo(-122.4194)
        assertThat(entity.gpsAccuracy).isEqualTo(10.5f)
        assertThat(entity.radio).isEqualTo("LTE")
        assertThat(entity.mcc).isEqualTo(310)
        assertThat(entity.mnc).isEqualTo(260)
        assertThat(entity.tacLac).isEqualTo(12345)
        assertThat(entity.cid).isEqualTo(50331905L)
        assertThat(entity.pciPsc).isEqualTo(214)
        assertThat(entity.earfcnArfcn).isEqualTo(2050)
        assertThat(entity.bandwidth).isEqualTo(15000)
        assertThat(entity.band).isEqualTo(4)
        assertThat(entity.rsrp).isEqualTo(-85)
        assertThat(entity.rsrq).isEqualTo(-10)
        assertThat(entity.rssi).isEqualTo(-55)
        assertThat(entity.sinr).isEqualTo(18)
        assertThat(entity.cqi).isEqualTo(12)
        assertThat(entity.timingAdvance).isEqualTo(2)
        assertThat(entity.signalLevel).isEqualTo(3)
        assertThat(entity.isRegistered).isTrue()
        assertThat(entity.operatorName).isEqualTo("T-Mobile")
        assertThat(entity.sessionId).isEqualTo(42L)
    }

    @Test
    fun `given a MeasurementEntity when mapped to domain then all fields are preserved`() {
        val entity = MeasurementEntity().apply {
            id = 1
            timestamp = 2000L
            latitude = 40.7128
            longitude = -74.0060
            gpsAccuracy = 5.0f
            radio = "NR"
            mcc = 311
            mnc = 480
            tacLac = 9999
            cid = 268435457L
            pciPsc = 502
            earfcnArfcn = 627264
            bandwidth = null
            band = 71
            rsrp = -92
            rsrq = -11
            rssi = null
            sinr = 14
            cqi = null
            timingAdvance = null
            signalLevel = 2
            isRegistered = false
            operatorName = "Verizon"
            sessionId = 7L
        }

        val domain = EntityMapper.toDomain(entity)

        assertThat(domain.timestamp).isEqualTo(2000L)
        assertThat(domain.latitude).isEqualTo(40.7128)
        assertThat(domain.longitude).isEqualTo(-74.0060)
        assertThat(domain.gpsAccuracy).isEqualTo(5.0f)
        assertThat(domain.radio).isEqualTo(RadioType.NR)
        assertThat(domain.mcc).isEqualTo(311)
        assertThat(domain.mnc).isEqualTo(480)
        assertThat(domain.tacLac).isEqualTo(9999)
        assertThat(domain.cid).isEqualTo(268435457L)
        assertThat(domain.pciPsc).isEqualTo(502)
        assertThat(domain.earfcnArfcn).isEqualTo(627264)
        assertThat(domain.bandwidth).isNull()
        assertThat(domain.band).isEqualTo(71)
        assertThat(domain.rsrp).isEqualTo(-92)
        assertThat(domain.rsrq).isEqualTo(-11)
        assertThat(domain.rssi).isNull()
        assertThat(domain.sinr).isEqualTo(14)
        assertThat(domain.cqi).isNull()
        assertThat(domain.timingAdvance).isNull()
        assertThat(domain.signalLevel).isEqualTo(2)
        assertThat(domain.isRegistered).isFalse()
        assertThat(domain.operatorName).isEqualTo("Verizon")
    }

    @Test
    fun `given a CellMeasurement when round-tripped through entity then values match`() {
        val original = CellMeasurement(
            timestamp = 5000L,
            latitude = 51.5074,
            longitude = -0.1278,
            gpsAccuracy = null,
            radio = RadioType.GSM,
            mcc = 234,
            mnc = 15,
            tacLac = 501,
            cid = 4120L,
            rssi = -95,
            signalLevel = 1,
            isRegistered = true,
            operatorName = "Vodafone"
        )

        val roundTripped = EntityMapper.toDomain(EntityMapper.toEntity(original, 1L))

        assertThat(roundTripped.timestamp).isEqualTo(original.timestamp)
        assertThat(roundTripped.latitude).isEqualTo(original.latitude)
        assertThat(roundTripped.longitude).isEqualTo(original.longitude)
        assertThat(roundTripped.radio).isEqualTo(original.radio)
        assertThat(roundTripped.mcc).isEqualTo(original.mcc)
        assertThat(roundTripped.mnc).isEqualTo(original.mnc)
        assertThat(roundTripped.tacLac).isEqualTo(original.tacLac)
        assertThat(roundTripped.cid).isEqualTo(original.cid)
        assertThat(roundTripped.rssi).isEqualTo(original.rssi)
        assertThat(roundTripped.isRegistered).isEqualTo(original.isRegistered)
        assertThat(roundTripped.operatorName).isEqualTo(original.operatorName)
    }

    @Test
    fun `given an unknown radio string when mapped to domain then radio is UNKNOWN`() {
        val entity = MeasurementEntity().apply {
            radio = "WIMAX"
            timestamp = 1000L
        }

        val domain = EntityMapper.toDomain(entity)

        assertThat(domain.radio).isEqualTo(RadioType.UNKNOWN)
    }

    // --- AnomalyEvent <-> AnomalyEntity ---

    @Test
    fun `given an AnomalyEvent when mapped to entity then all fields are preserved`() {
        val event = AnomalyEvent(
            timestamp = 3000L,
            latitude = 37.7749,
            longitude = -122.4194,
            type = AnomalyType.UNKNOWN_TOWER,
            severity = AnomalySeverity.MEDIUM,
            description = "Unknown tower detected",
            cellRadio = RadioType.LTE,
            cellMcc = 310,
            cellMnc = 260,
            cellTacLac = 12345,
            cellCid = 50331905L,
            cellPci = 214,
            signalStrength = -85,
            dismissed = false
        )

        val entity = EntityMapper.toEntity(event, sessionId = 10L)

        assertThat(entity.timestamp).isEqualTo(3000L)
        assertThat(entity.latitude).isEqualTo(37.7749)
        assertThat(entity.longitude).isEqualTo(-122.4194)
        assertThat(entity.anomalyType).isEqualTo("UNKNOWN_TOWER")
        assertThat(entity.severity).isEqualTo("MEDIUM")
        assertThat(entity.description).isEqualTo("Unknown tower detected")
        assertThat(entity.cellRadio).isEqualTo("LTE")
        assertThat(entity.cellMcc).isEqualTo(310)
        assertThat(entity.cellMnc).isEqualTo(260)
        assertThat(entity.cellTacLac).isEqualTo(12345)
        assertThat(entity.cellCid).isEqualTo(50331905L)
        assertThat(entity.cellPci).isEqualTo(214)
        assertThat(entity.signalStrength).isEqualTo(-85)
        assertThat(entity.dismissed).isFalse()
        assertThat(entity.sessionId).isEqualTo(10L)
    }

    @Test
    fun `given an AnomalyEntity when mapped to domain then all fields are preserved`() {
        val entity = AnomalyEntity().apply {
            id = 5
            timestamp = 4000L
            latitude = 40.7128
            longitude = -74.0060
            anomalyType = "DOWNGRADE_2G"
            severity = "HIGH"
            description = "Downgraded from LTE to GSM"
            cellRadio = "GSM"
            cellMcc = 310
            cellMnc = 260
            cellTacLac = 501
            cellCid = 4120L
            cellPci = null
            signalStrength = -95
            dismissed = true
            sessionId = 3L
        }

        val domain = EntityMapper.toDomain(entity)

        assertThat(domain.id).isEqualTo(5)
        assertThat(domain.timestamp).isEqualTo(4000L)
        assertThat(domain.latitude).isEqualTo(40.7128)
        assertThat(domain.longitude).isEqualTo(-74.0060)
        assertThat(domain.type).isEqualTo(AnomalyType.DOWNGRADE_2G)
        assertThat(domain.severity).isEqualTo(AnomalySeverity.HIGH)
        assertThat(domain.description).isEqualTo("Downgraded from LTE to GSM")
        assertThat(domain.cellRadio).isEqualTo(RadioType.GSM)
        assertThat(domain.cellMcc).isEqualTo(310)
        assertThat(domain.cellMnc).isEqualTo(260)
        assertThat(domain.cellTacLac).isEqualTo(501)
        assertThat(domain.cellCid).isEqualTo(4120L)
        assertThat(domain.cellPci).isNull()
        assertThat(domain.signalStrength).isEqualTo(-95)
        assertThat(domain.dismissed).isTrue()
    }

    @Test
    fun `given an AnomalyEvent with null sessionId when mapped to entity then sessionId is null`() {
        val event = AnomalyEvent(
            timestamp = 1000L,
            type = AnomalyType.SIGNAL_ANOMALY,
            severity = AnomalySeverity.LOW,
            description = "Signal spike"
        )

        val entity = EntityMapper.toEntity(event, sessionId = null)

        assertThat(entity.sessionId).isNull()
    }

    // --- CellTower <-> TowerCacheEntity ---

    @Test
    fun `given a CellTower when mapped to entity then all fields are preserved`() {
        val tower = CellTower(
            radio = RadioType.LTE,
            mcc = 310,
            mnc = 260,
            tacLac = 12345,
            cid = 50331905L,
            latitude = 37.7749,
            longitude = -122.4194,
            rangeMeters = 500,
            samples = 42,
            source = "OpenCelliD"
        )

        val entity = EntityMapper.toEntity(tower)

        assertThat(entity.radio).isEqualTo("LTE")
        assertThat(entity.mcc).isEqualTo(310)
        assertThat(entity.mnc).isEqualTo(260)
        assertThat(entity.tacLac).isEqualTo(12345)
        assertThat(entity.cid).isEqualTo(50331905L)
        assertThat(entity.latitude).isEqualTo(37.7749)
        assertThat(entity.longitude).isEqualTo(-122.4194)
        assertThat(entity.rangeMeters).isEqualTo(500)
        assertThat(entity.samples).isEqualTo(42)
        assertThat(entity.source).isEqualTo("OpenCelliD")
        assertThat(entity.lastUpdated).isNotNull()
    }

    @Test
    fun `given a TowerCacheEntity when mapped to domain then all fields are preserved`() {
        val entity = TowerCacheEntity().apply {
            id = 99
            radio = "WCDMA"
            mcc = 234
            mnc = 15
            tacLac = 1000
            cid = 65536L
            latitude = 51.5074
            longitude = -0.1278
            rangeMeters = 1200
            samples = 100
            source = "MLS"
            lastUpdated = 9999L
        }

        val domain = EntityMapper.toDomain(entity)

        assertThat(domain.radio).isEqualTo(RadioType.WCDMA)
        assertThat(domain.mcc).isEqualTo(234)
        assertThat(domain.mnc).isEqualTo(15)
        assertThat(domain.tacLac).isEqualTo(1000)
        assertThat(domain.cid).isEqualTo(65536L)
        assertThat(domain.latitude).isEqualTo(51.5074)
        assertThat(domain.longitude).isEqualTo(-0.1278)
        assertThat(domain.rangeMeters).isEqualTo(1200)
        assertThat(domain.samples).isEqualTo(100)
        assertThat(domain.source).isEqualTo("MLS")
    }

    @Test
    fun `given a CellTower with null location when round-tripped then nulls are preserved`() {
        val original = CellTower(
            radio = RadioType.NR,
            mcc = 310,
            mnc = 260,
            tacLac = 99999,
            cid = 268435457L,
            latitude = null,
            longitude = null,
            rangeMeters = null,
            samples = null,
            source = null
        )

        val roundTripped = EntityMapper.toDomain(EntityMapper.toEntity(original))

        assertThat(roundTripped.radio).isEqualTo(original.radio)
        assertThat(roundTripped.mcc).isEqualTo(original.mcc)
        assertThat(roundTripped.mnc).isEqualTo(original.mnc)
        assertThat(roundTripped.tacLac).isEqualTo(original.tacLac)
        assertThat(roundTripped.cid).isEqualTo(original.cid)
        assertThat(roundTripped.latitude).isNull()
        assertThat(roundTripped.longitude).isNull()
        assertThat(roundTripped.rangeMeters).isNull()
        assertThat(roundTripped.samples).isNull()
        assertThat(roundTripped.source).isNull()
    }
}
