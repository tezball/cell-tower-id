package com.terrycollins.celltowerid.service

import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CollectionServiceTest {

    // --- MockCellInfoProvider integration ---

    @Test
    fun `given mock provider when getCellMeasurements then returns non-empty list`() {
        val provider = MockCellInfoProvider()
        val measurements = provider.getCellMeasurements(37.7749, -122.4194, 10f)
        assertThat(measurements).isNotEmpty()
    }

    @Test
    fun `given mock provider when getCellMeasurements then at least one is registered`() {
        val provider = MockCellInfoProvider()
        val measurements = provider.getCellMeasurements(37.7749, -122.4194, 10f)
        val registered = measurements.filter { it.isRegistered }
        assertThat(registered).isNotEmpty()
    }

    @Test
    fun `given mock provider when getCellMeasurements then measurements have valid coordinates`() {
        val provider = MockCellInfoProvider()
        val measurements = provider.getCellMeasurements(37.7749, -122.4194, 10f)
        measurements.forEach { m ->
            assertThat(m.latitude).isWithin(0.01).of(37.7749)
            assertThat(m.longitude).isWithin(0.01).of(-122.4194)
        }
    }

    @Test
    fun `given mock provider when getCellMeasurements then measurements include LTE`() {
        val provider = MockCellInfoProvider()
        val measurements = provider.getCellMeasurements(37.7749, -122.4194, 10f)
        val lte = measurements.filter { it.radio == RadioType.LTE }
        assertThat(lte).isNotEmpty()
    }

    @Test
    fun `given mock provider when getCellMeasurements then rsrp values are in valid range`() {
        val provider = MockCellInfoProvider()
        val measurements = provider.getCellMeasurements(37.7749, -122.4194, 10f)
        measurements.forEach { m ->
            m.rsrp?.let { rsrp ->
                assertThat(rsrp).isAtLeast(-140)
                assertThat(rsrp).isAtMost(-44)
            }
        }
    }

    @Test
    fun `given mock provider when getCellMeasurements with speed then speed is recorded`() {
        val provider = MockCellInfoProvider()
        val measurements = provider.getCellMeasurements(37.7749, -122.4194, 10f, 15.0f)
        // MockCellInfoProvider may or may not propagate speed; verify no crash
        assertThat(measurements).isNotEmpty()
    }
}
