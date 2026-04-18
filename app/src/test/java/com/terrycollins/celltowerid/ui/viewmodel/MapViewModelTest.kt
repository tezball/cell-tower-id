package com.terrycollins.celltowerid.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.CellTower
import com.terrycollins.celltowerid.domain.model.RadioType
import com.terrycollins.celltowerid.repository.MeasurementRepository
import com.terrycollins.celltowerid.repository.TowerCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var measurementRepo: MeasurementRepository
    private lateinit var towerCacheRepo: TowerCacheRepository
    private lateinit var viewModel: MapViewModel

    private val lteMeasurement = CellMeasurement(
        timestamp = 1L,
        latitude = 37.0,
        longitude = -122.0,
        radio = RadioType.LTE,
        mcc = 310,
        mnc = 260,
        rsrp = -85,
        isRegistered = true,
        operatorName = "T-Mobile"
    )

    private val nrMeasurement = CellMeasurement(
        timestamp = 2L,
        latitude = 37.1,
        longitude = -122.1,
        radio = RadioType.NR,
        mcc = 310,
        mnc = 410,
        rsrp = -90,
        isRegistered = false,
        operatorName = "AT&T"
    )

    private val gsmMeasurement = CellMeasurement(
        timestamp = 3L,
        latitude = 37.2,
        longitude = -122.2,
        radio = RadioType.GSM,
        mcc = 310,
        mnc = 12,
        rssi = -75,
        isRegistered = false,
        operatorName = "Verizon"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        measurementRepo = mockk(relaxed = true)
        towerCacheRepo = mockk(relaxed = true)
        val mockApp = mockk<Application>(relaxed = true)
        viewModel = MapViewModel(mockApp, measurementRepo, towerCacheRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given no filters when applying filters then returns all measurements`() {
        val all = listOf(lteMeasurement, nrMeasurement, gsmMeasurement)

        val result = viewModel.applyFilters(all)

        assertThat(result).hasSize(3)
    }

    @Test
    fun `given LTE radio filter when applying filters then returns only LTE`() {
        viewModel.setRadioTypeFilter(RadioType.LTE)
        val all = listOf(lteMeasurement, nrMeasurement, gsmMeasurement)

        val result = viewModel.applyFilters(all)

        assertThat(result).hasSize(1)
        assertThat(result[0].radio).isEqualTo(RadioType.LTE)
    }

    @Test
    fun `given carrier filter when applying filters then returns matching carrier only`() {
        viewModel.setCarrierFilter("T-Mobile")
        val all = listOf(lteMeasurement, nrMeasurement, gsmMeasurement)

        val result = viewModel.applyFilters(all)

        assertThat(result).hasSize(1)
        assertThat(result[0].operatorName).isEqualTo("T-Mobile")
    }

    @Test
    fun `given both filters when applying then combines radio and carrier`() {
        viewModel.setRadioTypeFilter(RadioType.LTE)
        viewModel.setCarrierFilter("T-Mobile")
        val all = listOf(lteMeasurement, nrMeasurement, gsmMeasurement)

        val result = viewModel.applyFilters(all)

        assertThat(result).hasSize(1)
        assertThat(result[0].radio).isEqualTo(RadioType.LTE)
        assertThat(result[0].operatorName).isEqualTo("T-Mobile")
    }

    @Test
    fun `given null radio filter when clearing filter then returns all`() {
        viewModel.setRadioTypeFilter(RadioType.LTE)
        viewModel.setRadioTypeFilter(null)
        val all = listOf(lteMeasurement, nrMeasurement, gsmMeasurement)

        val result = viewModel.applyFilters(all)

        assertThat(result).hasSize(3)
    }

    @Test
    fun `given measurement with null mcc when carrier filter set then measurement excluded`() {
        viewModel.setCarrierFilter("T-Mobile")
        val noMcc = lteMeasurement.copy(mcc = null)
        val all = listOf(noMcc, nrMeasurement)

        val result = viewModel.applyFilters(all)

        assertThat(result).isEmpty()
    }

    @Test
    fun `given loadRecentMeasurements when called then measurements LiveData updated`() = runTest {
        val data = listOf(lteMeasurement, nrMeasurement)
        coEvery { measurementRepo.getRecentMeasurements(any()) } returns data

        viewModel.loadRecentMeasurements()

        assertThat(viewModel.measurements.value).hasSize(2)
    }

    @Test
    fun `given loadMeasurementsInArea when called then only measurements updated and towers untouched`() = runTest {
        val data = listOf(lteMeasurement)
        coEvery { measurementRepo.getMeasurementsInArea(any(), any(), any(), any()) } returns data

        viewModel.loadMeasurementsInArea(36.0, 38.0, -123.0, -121.0)

        assertThat(viewModel.measurements.value).hasSize(1)
        assertThat(viewModel.towers.value).isNull()
        coVerify(exactly = 0) { towerCacheRepo.getTowersInArea(any(), any(), any(), any()) }
    }

    @Test
    fun `given towers in cache when loadAllTowers called then towers LiveData emits all towers`() = runTest {
        val towers = listOf(
            CellTower(radio = RadioType.LTE, mcc = 272, mnc = 1, tacLac = 100, cid = 22501123L, latitude = 53.3, longitude = -6.2),
            CellTower(radio = RadioType.NR, mcc = 272, mnc = 2, tacLac = 200, cid = 42L, latitude = 53.4, longitude = -6.3)
        )
        coEvery { towerCacheRepo.getTowersInArea(-90.0, 90.0, -180.0, 180.0) } returns towers

        viewModel.loadAllTowers()

        assertThat(viewModel.towers.value).hasSize(2)
        assertThat(viewModel.towers.value).containsExactlyElementsIn(towers)
    }

    @Test
    fun `given auto-refresh started when delay elapses then loadAllTowers invoked`() = runTest {
        coEvery { measurementRepo.getRecentMeasurements(any()) } returns emptyList()
        coEvery { towerCacheRepo.getTowersInArea(-90.0, 90.0, -180.0, 180.0) } returns emptyList()

        viewModel.startAutoRefresh()
        advanceTimeBy(15_001)
        viewModel.stopAutoRefresh()

        coVerify(atLeast = 1) { towerCacheRepo.getTowersInArea(-90.0, 90.0, -180.0, 180.0) }
    }

    @Test
    fun `given initial state then LiveData defaults are correct`() {
        assertThat(viewModel.measurements.value).isNull()
        assertThat(viewModel.filterRadioType.value).isNull()
        assertThat(viewModel.filterCarrier.value).isNull()
    }
}
