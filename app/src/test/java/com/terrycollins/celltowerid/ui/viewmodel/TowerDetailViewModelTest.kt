package com.terrycollins.celltowerid.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.terrycollins.celltowerid.data.dao.MeasurementDao
import com.terrycollins.celltowerid.data.entity.MeasurementEntity
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.CellTower
import com.terrycollins.celltowerid.domain.model.RadioType
import com.terrycollins.celltowerid.repository.TowerCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TowerDetailViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var measurementDao: MeasurementDao
    private lateinit var cacheRepo: TowerCacheRepository
    private lateinit var viewModel: TowerDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        measurementDao = mockk(relaxed = true)
        cacheRepo = mockk(relaxed = true)
        val mockApp = mockk<Application>(relaxed = true)
        // Pass the same test dispatcher for IO so withContext stays on the same scheduler
        // and advanceUntilIdle() drains it.
        viewModel = TowerDetailViewModel(mockApp, measurementDao, cacheRepo, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeMeasurement(
        timestamp: Long = 1000L,
        radio: RadioType = RadioType.LTE,
        mcc: Int? = 310,
        mnc: Int? = 260,
        tacLac: Int? = 12345,
        cid: Long? = 67890L,
        timingAdvance: Int? = null,
        rsrp: Int? = -85
    ) = CellMeasurement(
        timestamp = timestamp,
        latitude = 37.7749,
        longitude = -122.4194,
        radio = radio,
        mcc = mcc,
        mnc = mnc,
        tacLac = tacLac,
        cid = cid,
        rsrp = rsrp,
        timingAdvance = timingAdvance,
        isRegistered = true
    )

    private fun makeMeasurementEntity(
        timestamp: Long,
        rsrp: Int = -85,
        cid: Long = 67890L,
        latitude: Double = 37.7749,
        longitude: Double = -122.4194
    ): MeasurementEntity = MeasurementEntity().apply {
        this.timestamp = timestamp
        this.latitude = latitude
        this.longitude = longitude
        this.radio = "LTE"
        this.mcc = 310
        this.mnc = 260
        this.tacLac = 12345
        this.cid = cid
        this.rsrp = rsrp
        this.isRegistered = true
    }

    @Test
    fun `given measurement missing MCC, when loadHistory, then state is not posted`() = runTest {
        val current = makeMeasurement(mcc = null)

        viewModel.loadHistory(current)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isNull()
    }

    @Test
    fun `given measurement missing CID, when loadHistory, then state is not posted`() = runTest {
        val current = makeMeasurement(cid = null)

        viewModel.loadHistory(current)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isNull()
    }

    @Test
    fun `given cached tower with known coordinates, when loadHistory, then state uses cached lat lon`() = runTest {
        // Given
        every { measurementDao.getMeasurementsByCell(any(), any(), any(), any()) } returns emptyList()
        coEvery { cacheRepo.findTower(any(), any(), any(), any(), any()) } returns CellTower(
            radio = RadioType.LTE,
            mcc = 310, mnc = 260, tacLac = 12345, cid = 67890L,
            latitude = 40.7, longitude = -74.0,
            source = "ocid"
        )

        // When
        viewModel.loadHistory(makeMeasurement())
        advanceUntilIdle()

        // Then
        val state = requireNotNull(viewModel.state.value)
        assertThat(state.towerLat).isEqualTo(40.7)
        assertThat(state.towerLon).isEqualTo(-74.0)
        // Should NOT have called learnPosition (already cached).
        coVerify(exactly = 0) { cacheRepo.learnPosition(any()) }
    }

    @Test
    fun `given LTE current with timingAdvance, when loadHistory, then distanceMeters is computed via 78 dot 12 mult`() = runTest {
        // Given
        every { measurementDao.getMeasurementsByCell(any(), any(), any(), any()) } returns emptyList()
        coEvery { cacheRepo.findTower(any(), any(), any(), any(), any()) } returns null

        // When
        viewModel.loadHistory(makeMeasurement(timingAdvance = 4))
        advanceUntilIdle()

        // Then -- 4 * 78.12 = 312.48 m
        val state = requireNotNull(viewModel.state.value)
        assertThat(state.distanceMeters).isEqualTo(4 * 78.12)
    }

    @Test
    fun `given LTE with timingAdvance zero, when loadHistory, then distanceMeters is null (TA=0 is suspicious)`() = runTest {
        every { measurementDao.getMeasurementsByCell(any(), any(), any(), any()) } returns emptyList()
        coEvery { cacheRepo.findTower(any(), any(), any(), any(), any()) } returns null

        viewModel.loadHistory(makeMeasurement(timingAdvance = 0))
        advanceUntilIdle()

        val state = requireNotNull(viewModel.state.value)
        assertThat(state.distanceMeters).isNull()
    }

    @Test
    fun `given history limited to 50, when more than 50 measurements exist, then only 50 most recent are kept`() = runTest {
        // Given -- 60 distinct timestamps.
        val entities = (1L..60L).map { makeMeasurementEntity(timestamp = it * 1000) }
        every { measurementDao.getMeasurementsByCell(any(), any(), any(), any()) } returns entities
        coEvery { cacheRepo.findTower(any(), any(), any(), any(), any()) } returns null

        // When
        viewModel.loadHistory(makeMeasurement(timestamp = 999_999L))
        advanceUntilIdle()

        // Then
        val state = requireNotNull(viewModel.state.value)
        assertThat(state.history).hasSize(50)
        // Sorted desc by timestamp -- newest first.
        assertThat(state.history.first().timestamp).isEqualTo(60_000L)
    }

    @Test
    fun `given no cached tower and 5+ history points spread over 100m, when loadHistory, then learnPosition is called with estimated coords`() = runTest {
        // Given -- 5 history measurements ~100m apart so TowerLocator's 50m spread guard passes.
        val entities = (1..5).map {
            makeMeasurementEntity(
                timestamp = it.toLong() * 1000,
                rsrp = -70 - it,
                latitude = 37.7749 + it * 0.001,  // ~111m per 0.001 deg lat
                longitude = -122.4194
            )
        }
        every { measurementDao.getMeasurementsByCell(any(), any(), any(), any()) } returns entities
        coEvery { cacheRepo.findTower(any(), any(), any(), any(), any()) } returns null

        // When
        viewModel.loadHistory(makeMeasurement(timestamp = 999_999L))
        advanceUntilIdle()

        // Then -- learnPosition was called once with a "learned" CellTower record.
        coVerify(exactly = 1) {
            cacheRepo.learnPosition(match { tower ->
                tower.source == "learned" && tower.cid == 67890L && tower.samples == 6
            })
        }
    }

    @Test
    fun `given fewer than 5 history points and no cached tower, when loadHistory, then learnPosition is NOT called`() = runTest {
        // Given -- only 3 history points, plus current => 4 total < 5 threshold.
        val entities = (1..3).map { makeMeasurementEntity(timestamp = it.toLong() * 1000) }
        every { measurementDao.getMeasurementsByCell(any(), any(), any(), any()) } returns entities
        coEvery { cacheRepo.findTower(any(), any(), any(), any(), any()) } returns null

        // When
        viewModel.loadHistory(makeMeasurement())
        advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { cacheRepo.learnPosition(any()) }
    }
}
