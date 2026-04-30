package com.celltowerid.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeadingMathTest {

    @Test
    fun `given orientation 0 radians, when converted, then azimuth is 0 degrees`() {
        assertThat(HeadingMath.azimuthDegreesFromRadians(0.0)).isWithin(0.01).of(0.0)
    }

    @Test
    fun `given orientation pi over 2 radians, when converted, then azimuth is 90 degrees`() {
        assertThat(HeadingMath.azimuthDegreesFromRadians(Math.PI / 2)).isWithin(0.01).of(90.0)
    }

    @Test
    fun `given orientation negative pi over 2 radians, when converted, then azimuth is 270 degrees`() {
        // SensorManager.getOrientation reports azimuth in -pi..pi range.
        assertThat(HeadingMath.azimuthDegreesFromRadians(-Math.PI / 2)).isWithin(0.01).of(270.0)
    }

    @Test
    fun `given orientation pi radians, when converted, then azimuth is 180 degrees`() {
        assertThat(HeadingMath.azimuthDegreesFromRadians(Math.PI)).isWithin(0.01).of(180.0)
    }

    @Test
    fun `given prev 0 and next 10, when low-pass with alpha 0_5, then result is 5`() {
        // No wraparound — straightforward weighted average.
        assertThat(HeadingMath.lowPassAzimuth(0.0, 10.0, 0.5)).isWithin(0.01).of(5.0)
    }

    @Test
    fun `given prev 350 and next 10, when low-pass, then takes shortest arc through 0`() {
        // Wraparound: 350 → 10 is +20° going clockwise, not -340° going back.
        // With alpha=0.5 the smoothed value should sit at 0° (or 360°).
        val result = HeadingMath.lowPassAzimuth(350.0, 10.0, 0.5)

        // Allow either 0 or 360 representation; both are equivalent.
        val normalized = (result + 360.0) % 360.0
        assertThat(kotlin.math.min(normalized, 360.0 - normalized)).isWithin(0.01).of(0.0)
    }

    @Test
    fun `given prev 10 and next 350, when low-pass, then takes shortest arc through 0`() {
        // Reverse wraparound: 10 → 350 should smooth toward 0°, not 180°.
        val result = HeadingMath.lowPassAzimuth(10.0, 350.0, 0.5)

        val normalized = (result + 360.0) % 360.0
        assertThat(kotlin.math.min(normalized, 360.0 - normalized)).isWithin(0.01).of(0.0)
    }

    @Test
    fun `given identical prev and next, when low-pass, then result equals input`() {
        assertThat(HeadingMath.lowPassAzimuth(123.4, 123.4, 0.3)).isWithin(0.01).of(123.4)
    }

    @Test
    fun `given alpha 1, when low-pass, then result equals next`() {
        // Alpha=1 means "trust the new sample completely".
        assertThat(HeadingMath.lowPassAzimuth(45.0, 90.0, 1.0)).isWithin(0.01).of(90.0)
    }

    @Test
    fun `given alpha 0, when low-pass, then result equals prev`() {
        // Alpha=0 means "ignore new sample"; useful as a sanity check.
        assertThat(HeadingMath.lowPassAzimuth(45.0, 90.0, 0.0)).isWithin(0.01).of(45.0)
    }
}
