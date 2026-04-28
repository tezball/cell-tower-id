package com.celltowerid.android.service

import android.annotation.SuppressLint
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType

// Pure conversion of platform CellInfo objects into the app's domain model.
// Extracted from RealCellInfoProvider so the SDK_INT branching can be exercised
// under Robolectric without spinning up a full TelephonyManager mock.
internal object CellInfoConverter {

    fun convert(
        cellInfo: CellInfo,
        timestamp: Long,
        lat: Double,
        lon: Double,
        accuracy: Float?,
        speedMps: Float?
    ): CellMeasurement? {
        val isRegistered = cellInfo.isRegistered
        return when (cellInfo) {
            is CellInfoLte -> convertLte(cellInfo, timestamp, lat, lon, accuracy, isRegistered, speedMps)
            is CellInfoGsm -> convertGsm(cellInfo, timestamp, lat, lon, accuracy, isRegistered, speedMps)
            is CellInfoWcdma -> convertWcdma(cellInfo, timestamp, lat, lon, accuracy, isRegistered, speedMps)
            is CellInfoCdma -> convertCdma(cellInfo, timestamp, lat, lon, accuracy, isRegistered, speedMps)
            else -> convertByApiLevel(cellInfo, timestamp, lat, lon, accuracy, isRegistered, speedMps)
        }
    }

    internal fun convertLte(
        cellInfo: CellInfoLte,
        timestamp: Long,
        lat: Double,
        lon: Double,
        accuracy: Float?,
        isRegistered: Boolean,
        speedMps: Float?
    ): CellMeasurement {
        val identity = cellInfo.cellIdentity
        val signal = cellInfo.cellSignalStrength

        val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            identity.mccString?.toIntOrNull()
        } else {
            @Suppress("DEPRECATION")
            identity.mcc.takeIfAvailable()
        }
        val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            identity.mncString?.toIntOrNull()
        } else {
            @Suppress("DEPRECATION")
            identity.mnc.takeIfAvailable()
        }

        return CellMeasurement(
            timestamp = timestamp,
            latitude = lat,
            longitude = lon,
            gpsAccuracy = accuracy,
            radio = RadioType.LTE,
            mcc = mcc,
            mnc = mnc,
            tacLac = identity.tac.takeIfAvailable(),
            cid = identity.ci.toLong().takeIfAvailable(),
            pciPsc = identity.pci.takeIfAvailable(),
            earfcnArfcn = identity.earfcn.takeIfAvailable(),
            bandwidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                identity.bandwidth.takeIfAvailable()
            } else null,
            rsrp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                signal.rsrp.takeIfAvailable()
            } else null,
            rsrq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                signal.rsrq.takeIfAvailable()
            } else null,
            rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                signal.rssi.takeIfAvailable()
            } else null,
            sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                signal.rssnr.takeIfAvailable()
            } else null,
            cqi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                signal.cqi.takeIfAvailable()
            } else null,
            timingAdvance = signal.timingAdvance.takeIfAvailable(),
            signalLevel = signal.level,
            isRegistered = isRegistered,
            operatorName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                identity.operatorAlphaLong?.toString()
            } else null,
            speedMps = speedMps
        )
    }

    internal fun convertGsm(
        cellInfo: CellInfoGsm,
        timestamp: Long,
        lat: Double,
        lon: Double,
        accuracy: Float?,
        isRegistered: Boolean,
        speedMps: Float?
    ): CellMeasurement {
        val identity = cellInfo.cellIdentity
        val signal = cellInfo.cellSignalStrength

        val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            identity.mccString?.toIntOrNull()
        } else {
            @Suppress("DEPRECATION")
            identity.mcc.takeIfAvailable()
        }
        val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            identity.mncString?.toIntOrNull()
        } else {
            @Suppress("DEPRECATION")
            identity.mnc.takeIfAvailable()
        }

        return CellMeasurement(
            timestamp = timestamp,
            latitude = lat,
            longitude = lon,
            gpsAccuracy = accuracy,
            radio = RadioType.GSM,
            mcc = mcc,
            mnc = mnc,
            tacLac = identity.lac.takeIfAvailable(),
            cid = identity.cid.toLong().takeIfAvailable(),
            pciPsc = identity.psc.takeIfAvailable(),
            earfcnArfcn = identity.arfcn.takeIfAvailable(),
            rssi = signal.dbm.takeIfAvailable(),
            timingAdvance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                signal.timingAdvance.takeIfAvailable()
            } else null,
            signalLevel = signal.level,
            isRegistered = isRegistered,
            operatorName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                identity.operatorAlphaLong?.toString()
            } else null,
            speedMps = speedMps
        )
    }

    internal fun convertWcdma(
        cellInfo: CellInfoWcdma,
        timestamp: Long,
        lat: Double,
        lon: Double,
        accuracy: Float?,
        isRegistered: Boolean,
        speedMps: Float?
    ): CellMeasurement {
        val identity = cellInfo.cellIdentity
        val signal = cellInfo.cellSignalStrength

        val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            identity.mccString?.toIntOrNull()
        } else {
            @Suppress("DEPRECATION")
            identity.mcc.takeIfAvailable()
        }
        val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            identity.mncString?.toIntOrNull()
        } else {
            @Suppress("DEPRECATION")
            identity.mnc.takeIfAvailable()
        }

        return CellMeasurement(
            timestamp = timestamp,
            latitude = lat,
            longitude = lon,
            gpsAccuracy = accuracy,
            radio = RadioType.WCDMA,
            mcc = mcc,
            mnc = mnc,
            tacLac = identity.lac.takeIfAvailable(),
            cid = identity.cid.toLong().takeIfAvailable(),
            pciPsc = identity.psc.takeIfAvailable(),
            earfcnArfcn = identity.uarfcn.takeIfAvailable(),
            rssi = signal.dbm.takeIfAvailable(),
            signalLevel = signal.level,
            isRegistered = isRegistered,
            operatorName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                identity.operatorAlphaLong?.toString()
            } else null,
            speedMps = speedMps
        )
    }

    internal fun convertCdma(
        cellInfo: CellInfoCdma,
        timestamp: Long,
        lat: Double,
        lon: Double,
        accuracy: Float?,
        isRegistered: Boolean,
        speedMps: Float?
    ): CellMeasurement {
        val identity = cellInfo.cellIdentity
        val signal = cellInfo.cellSignalStrength

        return CellMeasurement(
            timestamp = timestamp,
            latitude = lat,
            longitude = lon,
            gpsAccuracy = accuracy,
            radio = RadioType.CDMA,
            tacLac = identity.networkId.takeIfAvailable(),
            cid = identity.basestationId.toLong().takeIfAvailable(),
            rssi = signal.cdmaDbm.takeIfAvailable(),
            signalLevel = signal.level,
            isRegistered = isRegistered,
            speedMps = speedMps
        )
    }

    internal fun convertByApiLevel(
        cellInfo: CellInfo,
        timestamp: Long,
        lat: Double,
        lon: Double,
        accuracy: Float?,
        isRegistered: Boolean,
        speedMps: Float?
    ): CellMeasurement? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // CellInfoNr is available API 29+
            val nrClass = try {
                Class.forName("android.telephony.CellInfoNr")
            } catch (_: ClassNotFoundException) {
                null
            }
            if (nrClass != null && nrClass.isInstance(cellInfo)) {
                return convertNr(cellInfo, timestamp, lat, lon, accuracy, isRegistered, speedMps)
            }

            // CellInfoTdscdma is available API 29+
            val tdscdmaClass = try {
                Class.forName("android.telephony.CellInfoTdscdma")
            } catch (_: ClassNotFoundException) {
                null
            }
            if (tdscdmaClass != null && tdscdmaClass.isInstance(cellInfo)) {
                return convertTdscdma(cellInfo, timestamp, lat, lon, accuracy, isRegistered, speedMps)
            }
        }
        return null
    }

    @SuppressLint("NewApi")
    internal fun convertNr(
        cellInfo: CellInfo,
        timestamp: Long,
        lat: Double,
        lon: Double,
        accuracy: Float?,
        isRegistered: Boolean,
        speedMps: Float?
    ): CellMeasurement? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val nrInfo = cellInfo as android.telephony.CellInfoNr
        val identity = nrInfo.cellIdentity as android.telephony.CellIdentityNr
        val signal = nrInfo.cellSignalStrength as android.telephony.CellSignalStrengthNr

        return CellMeasurement(
            timestamp = timestamp,
            latitude = lat,
            longitude = lon,
            gpsAccuracy = accuracy,
            radio = RadioType.NR,
            mcc = identity.mccString?.toIntOrNull(),
            mnc = identity.mncString?.toIntOrNull(),
            tacLac = identity.tac.takeIfAvailable(),
            cid = identity.nci.takeIfAvailable(),
            pciPsc = identity.pci.takeIfAvailable(),
            earfcnArfcn = identity.nrarfcn.takeIfAvailable(),
            rsrp = signal.ssRsrp.takeIfAvailable(),
            rsrq = signal.ssRsrq.takeIfAvailable(),
            sinr = signal.ssSinr.takeIfAvailable(),
            signalLevel = signal.level,
            isRegistered = isRegistered,
            operatorName = identity.operatorAlphaLong?.toString(),
            speedMps = speedMps
        )
    }

    @SuppressLint("NewApi")
    internal fun convertTdscdma(
        cellInfo: CellInfo,
        timestamp: Long,
        lat: Double,
        lon: Double,
        accuracy: Float?,
        isRegistered: Boolean,
        speedMps: Float?
    ): CellMeasurement? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val tdscdmaInfo = cellInfo as android.telephony.CellInfoTdscdma
        val identity = tdscdmaInfo.cellIdentity as android.telephony.CellIdentityTdscdma
        val signal = tdscdmaInfo.cellSignalStrength as android.telephony.CellSignalStrengthTdscdma

        return CellMeasurement(
            timestamp = timestamp,
            latitude = lat,
            longitude = lon,
            gpsAccuracy = accuracy,
            radio = RadioType.TDSCDMA,
            mcc = identity.mccString?.toIntOrNull(),
            mnc = identity.mncString?.toIntOrNull(),
            tacLac = identity.lac.takeIfAvailable(),
            cid = identity.cid.toLong().takeIfAvailable(),
            rssi = signal.dbm.takeIfAvailable(),
            signalLevel = signal.level,
            isRegistered = isRegistered,
            operatorName = identity.operatorAlphaLong?.toString(),
            speedMps = speedMps
        )
    }

    private fun Int.takeIfAvailable(): Int? =
        if (this == Int.MAX_VALUE || this == CellInfo.UNAVAILABLE) null else this

    private fun Long.takeIfAvailable(): Long? =
        if (this == Long.MAX_VALUE || this == CellInfo.UNAVAILABLE.toLong()) null else this
}
