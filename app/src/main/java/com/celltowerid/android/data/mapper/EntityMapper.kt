package com.celltowerid.android.data.mapper

import com.celltowerid.android.data.entity.AnomalyEntity
import com.celltowerid.android.data.entity.MeasurementEntity
import com.celltowerid.android.data.entity.TowerCacheEntity
import com.celltowerid.android.domain.model.AnomalyEvent
import com.celltowerid.android.domain.model.AnomalySeverity
import com.celltowerid.android.domain.model.AnomalyType
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.CellTower
import com.celltowerid.android.domain.model.RadioType

object EntityMapper {

    fun toEntity(m: CellMeasurement, sessionId: Long): MeasurementEntity {
        return MeasurementEntity().apply {
            timestamp = m.timestamp
            latitude = m.latitude
            longitude = m.longitude
            gpsAccuracy = m.gpsAccuracy
            radio = m.radio.name
            mcc = m.mcc
            mnc = m.mnc
            tacLac = m.tacLac
            cid = m.cid
            pciPsc = m.pciPsc
            earfcnArfcn = m.earfcnArfcn
            bandwidth = m.bandwidth
            band = m.band
            rsrp = m.rsrp
            rsrq = m.rsrq
            rssi = m.rssi
            sinr = m.sinr
            cqi = m.cqi
            timingAdvance = m.timingAdvance
            signalLevel = m.signalLevel
            isRegistered = m.isRegistered
            operatorName = m.operatorName
            this.sessionId = sessionId
            speedMps = m.speedMps
        }
    }

    fun toDomain(e: MeasurementEntity): CellMeasurement {
        return CellMeasurement(
            timestamp = e.timestamp,
            latitude = e.latitude,
            longitude = e.longitude,
            gpsAccuracy = e.gpsAccuracy,
            radio = RadioType.fromString(e.radio),
            mcc = e.mcc,
            mnc = e.mnc,
            tacLac = e.tacLac,
            cid = e.cid,
            pciPsc = e.pciPsc,
            earfcnArfcn = e.earfcnArfcn,
            bandwidth = e.bandwidth,
            band = e.band,
            rsrp = e.rsrp,
            rsrq = e.rsrq,
            rssi = e.rssi,
            sinr = e.sinr,
            cqi = e.cqi,
            timingAdvance = e.timingAdvance,
            signalLevel = e.signalLevel,
            isRegistered = e.isRegistered,
            operatorName = e.operatorName,
            speedMps = e.speedMps
        )
    }

    fun toEntity(a: AnomalyEvent, sessionId: Long?): AnomalyEntity {
        return AnomalyEntity().apply {
            timestamp = a.timestamp
            latitude = a.latitude
            longitude = a.longitude
            anomalyType = a.type.name
            severity = a.severity.name
            description = a.description
            cellRadio = a.cellRadio?.name
            cellMcc = a.cellMcc
            cellMnc = a.cellMnc
            cellTacLac = a.cellTacLac
            cellCid = a.cellCid
            cellPci = a.cellPci
            signalStrength = a.signalStrength
            isRegistered = a.isRegistered
            rsrp = a.rsrp
            rsrq = a.rsrq
            rssi = a.rssi
            sinr = a.sinr
            cqi = a.cqi
            timingAdvance = a.timingAdvance
            signalLevel = a.signalLevel
            earfcnArfcn = a.earfcnArfcn
            band = a.band
            bandwidth = a.bandwidth
            operatorName = a.operatorName
            gpsAccuracy = a.gpsAccuracy
            dismissed = a.dismissed
            this.sessionId = sessionId
        }
    }

    fun toDomain(e: AnomalyEntity): AnomalyEvent {
        return AnomalyEvent(
            id = e.id,
            timestamp = e.timestamp,
            latitude = e.latitude,
            longitude = e.longitude,
            type = AnomalyType.valueOf(e.anomalyType),
            severity = AnomalySeverity.valueOf(e.severity),
            description = e.description ?: "",
            cellRadio = e.cellRadio?.let { RadioType.fromString(it) },
            cellMcc = e.cellMcc,
            cellMnc = e.cellMnc,
            cellTacLac = e.cellTacLac,
            cellCid = e.cellCid,
            cellPci = e.cellPci,
            signalStrength = e.signalStrength,
            isRegistered = e.isRegistered,
            rsrp = e.rsrp,
            rsrq = e.rsrq,
            rssi = e.rssi,
            sinr = e.sinr,
            cqi = e.cqi,
            timingAdvance = e.timingAdvance,
            signalLevel = e.signalLevel,
            earfcnArfcn = e.earfcnArfcn,
            band = e.band,
            bandwidth = e.bandwidth,
            operatorName = e.operatorName,
            gpsAccuracy = e.gpsAccuracy,
            dismissed = e.dismissed
        )
    }

    fun toDomain(e: TowerCacheEntity): CellTower {
        return CellTower(
            radio = RadioType.fromString(e.radio),
            mcc = e.mcc,
            mnc = e.mnc,
            tacLac = e.tacLac,
            cid = e.cid,
            latitude = e.latitude,
            longitude = e.longitude,
            rangeMeters = e.rangeMeters,
            samples = e.samples,
            source = e.source,
            pci = e.pci,
            isPinned = e.isPinned
        )
    }

    fun toEntity(t: CellTower): TowerCacheEntity {
        return TowerCacheEntity().apply {
            radio = t.radio.name
            mcc = t.mcc
            mnc = t.mnc
            tacLac = t.tacLac
            cid = t.cid
            latitude = t.latitude
            longitude = t.longitude
            rangeMeters = t.rangeMeters
            samples = t.samples
            source = t.source
            lastUpdated = System.currentTimeMillis()
            pci = t.pci
            isPinned = t.isPinned
        }
    }
}
