package com.celltowerid.android.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Streams device-heading (compass azimuth) updates from the rotation-vector
 * sensor. Falls back to combined accelerometer + magnetic-field if the
 * rotation-vector sensor is missing (rare).
 *
 * Lifecycle: call [start] from `Activity.onResume` and [stop] from
 * `Activity.onPause` to avoid leaking listeners.
 *
 *   onAzimuth(azimuthDegrees: Float) is called on the main thread at the
 *   sensor's natural rate (typically 50–100 Hz; we don't throttle).
 *
 * The reported value is low-pass filtered to suppress jitter.
 */
class HeadingProvider(
    context: Context,
    private val onAzimuth: (Float) -> Unit,
    private val smoothingAlpha: Double = 0.15,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetic: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private var lastAzimuth: Double? = null

    val isAvailable: Boolean = rotationVector != null || (accelerometer != null && magnetic != null)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    emit(orientation[0].toDouble())
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                    hasGravity = true
                    fuseAccelMag()
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    hasGeomagnetic = true
                    fuseAccelMag()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private fun fuseAccelMag() {
        if (!hasGravity || !hasGeomagnetic) return
        if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) return
        SensorManager.getOrientation(rotationMatrix, orientation)
        emit(orientation[0].toDouble())
    }

    private fun emit(azimuthRadians: Double) {
        val azimuth = HeadingMath.azimuthDegreesFromRadians(azimuthRadians)
        val smoothed = lastAzimuth?.let { HeadingMath.lowPassAzimuth(it, azimuth, smoothingAlpha) }
            ?: azimuth
        lastAzimuth = smoothed
        onAzimuth(smoothed.toFloat())
    }

    fun start() {
        if (rotationVector != null) {
            sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_UI)
            return
        }
        if (accelerometer != null && magnetic != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetic, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
        hasGravity = false
        hasGeomagnetic = false
        lastAzimuth = null
    }
}
