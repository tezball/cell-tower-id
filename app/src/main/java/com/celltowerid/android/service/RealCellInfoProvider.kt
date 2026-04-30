package com.celltowerid.android.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.util.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class RealCellInfoProvider(private val context: Context) : CellInfoProvider {

    companion object {
        private const val TAG = "CellTowerID.Provider"
    }

    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private val executor by lazy { Executors.newSingleThreadExecutor() }

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

        return convertAll(cellInfoList, latitude, longitude, gpsAccuracy, speedMps)
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCellMeasurementsFresh(
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float?,
        speedMps: Float?,
        timeoutMs: Long,
    ): List<CellMeasurement> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return getCellMeasurements(latitude, longitude, gpsAccuracy, speedMps)
        }
        val fresh = withTimeoutOrNull(timeoutMs) {
            try {
                requestFreshCellInfo()
            } catch (t: Throwable) {
                AppLog.w(TAG, "requestCellInfoUpdate failed", t)
                null
            }
        }
        if (fresh == null) {
            return getCellMeasurements(latitude, longitude, gpsAccuracy, speedMps)
        }
        return convertAll(fresh, latitude, longitude, gpsAccuracy, speedMps)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private suspend fun requestFreshCellInfo(): List<CellInfo> =
        suspendCancellableCoroutine { cont ->
            val callback = object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(activeCellInfo: MutableList<CellInfo>) {
                    if (cont.isActive) cont.resume(activeCellInfo)
                }

                override fun onError(errorCode: Int, detail: Throwable?) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            }
            try {
                telephonyManager.requestCellInfoUpdate(executor, callback)
            } catch (t: Throwable) {
                if (cont.isActive) cont.resume(emptyList())
            }
        }

    private fun convertAll(
        list: List<CellInfo>,
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float?,
        speedMps: Float?,
    ): List<CellMeasurement> {
        val timestamp = System.currentTimeMillis()
        return list.mapNotNull { info ->
            CellInfoConverter.convert(info, timestamp, latitude, longitude, gpsAccuracy, speedMps)
        }
    }
}
