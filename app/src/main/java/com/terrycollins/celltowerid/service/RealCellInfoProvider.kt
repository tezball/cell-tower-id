package com.terrycollins.celltowerid.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.util.AppLog

class RealCellInfoProvider(private val context: Context) : CellInfoProvider {

    companion object {
        private const val TAG = "CellTowerID.Provider"
    }

    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    override fun isAvailable(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        return hasPermission && hasTelephony
    }

    @SuppressLint("MissingPermission")
    override fun getCellMeasurements(
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float?,
        speedMps: Float?
    ): List<CellMeasurement> {
        // Catch Throwable: Xiaomi/MediaTek devices have been observed throwing
        // IllegalStateException and DeadObjectException-wrapping RuntimeException
        // from telephonyManager.allCellInfo. A single occurrence here used to
        // crash the whole collection cycle.
        val cellInfoList = try {
            telephonyManager.allCellInfo
        } catch (t: Throwable) {
            AppLog.w(TAG, "telephonyManager.allCellInfo failed", t)
            null
        } ?: return emptyList()

        val timestamp = System.currentTimeMillis()
        return cellInfoList.mapNotNull { cellInfo ->
            CellInfoConverter.convert(cellInfo, timestamp, latitude, longitude, gpsAccuracy, speedMps)
        }
    }
}
