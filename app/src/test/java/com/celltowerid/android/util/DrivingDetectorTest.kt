package com.celltowerid.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DrivingDetectorTest {

    @Test
    fun `given fresh detector, when no samples, then mode is WALKING`() {
        val d = DrivingDetector()
        assertThat(d.mode).isEqualTo(LocateMode.WALKING)
    }

    @Test
    fun `given speed sustained above promote threshold for full dwell, when updated, then promotes to DRIVING`() {
        val d = DrivingDetector()

        // Given walking, then 10 m/s sustained from t=0 through t=5000.
        d.update(speedMps = 10f, speedAccuracyMps = 0.5f, timestampMs = 0L)
        // 4.999 s in -- not yet enough
        d.update(speedMps = 10f, speedAccuracyMps = 0.5f, timestampMs = 4_999L)
        assertThat(d.mode).isEqualTo(LocateMode.WALKING)

        // 5.000 s in -- promotion fires
        d.update(speedMps = 10f, speedAccuracyMps = 0.5f, timestampMs = 5_000L)
        assertThat(d.mode).isEqualTo(LocateMode.DRIVING)
    }

    @Test
    fun `given driving, when speed sustained below demote threshold for full dwell, then demotes to WALKING`() {
        val d = DrivingDetector()
        // Promote first.
        d.update(10f, 0.5f, 0L)
        d.update(10f, 0.5f, 5_000L)
        assertThat(d.mode).isEqualTo(LocateMode.DRIVING)

        // Now sustained 1 m/s -- 9.999 s still not enough
        d.update(1f, 0.5f, 5_001L)
        d.update(1f, 0.5f, 15_000L)
        assertThat(d.mode).isEqualTo(LocateMode.DRIVING)

        // 10 s exactly -- demote
        d.update(1f, 0.5f, 15_001L)
        assertThat(d.mode).isEqualTo(LocateMode.WALKING)
    }

    @Test
    fun `given driving, when brief stoplight then resumes speed, then stays DRIVING`() {
        val d = DrivingDetector()
        d.update(10f, 0.5f, 0L)
        d.update(10f, 0.5f, 5_000L)
        assertThat(d.mode).isEqualTo(LocateMode.DRIVING)

        // 5 s of crawling at 1 m/s -- short of the 10 s demote dwell
        d.update(1f, 0.5f, 6_000L)
        d.update(1f, 0.5f, 11_000L)
        assertThat(d.mode).isEqualTo(LocateMode.DRIVING)

        // Back to highway -- candidate transition resets
        d.update(15f, 0.5f, 12_000L)
        assertThat(d.mode).isEqualTo(LocateMode.DRIVING)
    }

    @Test
    fun `given walking, when speed swings above threshold then drops, then no promotion`() {
        val d = DrivingDetector()
        d.update(10f, 0.5f, 0L)
        d.update(10f, 0.5f, 4_000L)
        // Drop back to walking pace -- candidate cleared
        d.update(0.5f, 0.5f, 4_500L)
        // Back up, but timer started fresh -- 4 s is not 5 s
        d.update(10f, 0.5f, 8_500L)

        assertThat(d.mode).isEqualTo(LocateMode.WALKING)
    }

    @Test
    fun `given high speed accuracy uncertainty, when updated, then sample is ignored`() {
        val d = DrivingDetector()
        // Bogus 30 m/s sample with terrible 5 m/s accuracy: don't trust it.
        d.update(speedMps = 30f, speedAccuracyMps = 5f, timestampMs = 0L)
        d.update(speedMps = 30f, speedAccuracyMps = 5f, timestampMs = 10_000L)

        assertThat(d.mode).isEqualTo(LocateMode.WALKING)
    }

    @Test
    fun `given null speed sample, when updated, then no state change`() {
        val d = DrivingDetector()
        d.update(null, null, 0L)
        d.update(null, null, 30_000L)
        assertThat(d.mode).isEqualTo(LocateMode.WALKING)

        // Promote, then null sample shouldn't demote.
        d.update(10f, 0.5f, 30_001L)
        d.update(10f, 0.5f, 35_001L)
        assertThat(d.mode).isEqualTo(LocateMode.DRIVING)
        d.update(null, null, 50_000L)
        assertThat(d.mode).isEqualTo(LocateMode.DRIVING)
    }

    @Test
    fun `given null speed accuracy on min-sdk-24 device, when updated, then sample is trusted`() {
        // Older devices (API 24-25) lack hasSpeedAccuracy(); we shouldn't refuse
        // every sample on those phones.
        val d = DrivingDetector()
        d.update(speedMps = 10f, speedAccuracyMps = null, timestampMs = 0L)
        d.update(speedMps = 10f, speedAccuracyMps = null, timestampMs = 5_000L)

        assertThat(d.mode).isEqualTo(LocateMode.DRIVING)
    }

    @Test
    fun `given speed in hysteresis band 2 to 5, when updated, then no state change`() {
        // Walking detector: 4 m/s is below promote threshold, no promotion.
        val d1 = DrivingDetector()
        d1.update(4f, 0.5f, 0L)
        d1.update(4f, 0.5f, 60_000L)
        assertThat(d1.mode).isEqualTo(LocateMode.WALKING)

        // Driving detector: 4 m/s is above demote threshold, no demotion.
        val d2 = DrivingDetector()
        d2.update(10f, 0.5f, 0L)
        d2.update(10f, 0.5f, 5_000L)
        d2.update(4f, 0.5f, 5_001L)
        d2.update(4f, 0.5f, 60_000L)
        assertThat(d2.mode).isEqualTo(LocateMode.DRIVING)
    }
}
