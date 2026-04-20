package com.terrycollins.celltowerid.repository

import com.terrycollins.celltowerid.data.dao.TowerCacheDao
import com.terrycollins.celltowerid.data.entity.TowerCacheEntity
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.CellTower
import com.terrycollins.celltowerid.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TowerCacheRepositoryTest {

    @Test
    fun `given a tower, when learnPosition, then dao insert is called with source learned`() = runTest {
        val dao = mockk<TowerCacheDao>()
        val captured = slot<TowerCacheEntity>()
        every { dao.insert(capture(captured)) } just Runs
        val repo = TowerCacheRepository(dao)

        val tower = CellTower(
            radio = RadioType.LTE,
            mcc = 310,
            mnc = 260,
            tacLac = 12345,
            cid = 50331905L,
            latitude = 37.7749,
            longitude = -122.4194,
            samples = 10,
            source = "ignored-will-be-overridden"
        )

        repo.learnPosition(tower)

        verify(exactly = 1) { dao.insert(any()) }
        assertThat(captured.captured.source).isEqualTo("learned")
        assertThat(captured.captured.latitude).isEqualTo(37.7749)
        assertThat(captured.captured.longitude).isEqualTo(-122.4194)
        assertThat(captured.captured.mcc).isEqualTo(310)
        assertThat(captured.captured.cid).isEqualTo(50331905L)
    }

    @Test
    fun `given a fully-identified measurement, when recordObservation, then upserts with source observed and pci`() = runTest {
        val dao = mockk<TowerCacheDao>()
        val captured = slot<TowerCacheEntity>()
        every { dao.insert(capture(captured)) } just Runs
        val repo = TowerCacheRepository(dao)

        val m = measurement(isRegistered = true, pciPsc = 321)

        repo.recordObservation(m)

        verify(exactly = 1) { dao.insert(any()) }
        assertThat(captured.captured.source).isEqualTo("observed")
        assertThat(captured.captured.radio).isEqualTo("LTE")
        assertThat(captured.captured.mcc).isEqualTo(310)
        assertThat(captured.captured.mnc).isEqualTo(260)
        assertThat(captured.captured.tacLac).isEqualTo(12345)
        assertThat(captured.captured.cid).isEqualTo(50331905L)
        assertThat(captured.captured.pci).isEqualTo(321)
        assertThat(captured.captured.latitude).isEqualTo(37.7749)
        assertThat(captured.captured.longitude).isEqualTo(-122.4194)
    }

    @Test
    fun `given an unregistered measurement, when recordObservation, then dao insert is not called`() = runTest {
        val dao = mockk<TowerCacheDao>(relaxed = true)
        val repo = TowerCacheRepository(dao)

        repo.recordObservation(measurement(isRegistered = false))

        verify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `given a measurement with null cell identity, when recordObservation, then dao insert is not called`() = runTest {
        val dao = mockk<TowerCacheDao>(relaxed = true)
        val repo = TowerCacheRepository(dao)

        repo.recordObservation(measurement(isRegistered = true).copy(cid = null))

        verify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `given a source, when deleteBySource, then dao deleteBySource is called with that source`() = runTest {
        val dao = mockk<TowerCacheDao>()
        every { dao.deleteBySource("opencellid") } returns 42
        val repo = TowerCacheRepository(dao)

        val deleted = repo.deleteBySource("opencellid")

        assertThat(deleted).isEqualTo(42)
        verify(exactly = 1) { dao.deleteBySource("opencellid") }
    }

    private fun measurement(
        isRegistered: Boolean,
        pciPsc: Int? = 214,
    ): CellMeasurement = CellMeasurement(
        timestamp = 1_000L,
        latitude = 37.7749,
        longitude = -122.4194,
        gpsAccuracy = 10f,
        radio = RadioType.LTE,
        mcc = 310,
        mnc = 260,
        tacLac = 12345,
        cid = 50331905L,
        pciPsc = pciPsc,
        earfcnArfcn = 2050,
        rsrp = -90,
        rsrq = -10,
        sinr = 15,
        signalLevel = 3,
        isRegistered = isRegistered,
        operatorName = "T-Mobile",
        speedMps = 0f
    )
}
