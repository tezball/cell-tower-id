package com.terrycollins.celltowerid.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.RadioType
import com.terrycollins.celltowerid.repository.MeasurementRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CellListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var measurementRepo: MeasurementRepository
    private lateinit var viewModel: CellListViewModel

    private fun makeMeasurement(
        radio: RadioType = RadioType.LTE,
        mcc: Int = 310,
        mnc: Int = 260,
        tacLac: Int = 100,
        cid: Long = 50331905L,
        rsrp: Int? = -90,
        isRegistered: Boolean = false,
        timestamp: Long = System.currentTimeMillis()
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
        isRegistered = isRegistered
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        measurementRepo = mockk(relaxed = true)
        val mockApp = mockk<Application>(relaxed = true)
        viewModel = CellListViewModel(mockApp, measurementRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given measurements when updateCells then deduplicates by cell identity`() {
        val older = makeMeasurement(cid = 1L, rsrp = -100, timestamp = 1000L)
        val newer = makeMeasurement(cid = 1L, rsrp = -80, timestamp = 2000L)

        viewModel.updateCells(listOf(older, newer))

        val result = viewModel.currentCells.value!!
        assertThat(result).hasSize(1)
        assertThat(result[0].rsrp).isEqualTo(-80)
    }

    @Test
    fun `given measurements when updateCells then sorts registered first`() {
        val neighbor = makeMeasurement(cid = 1L, rsrp = -70, isRegistered = false)
        val serving = makeMeasurement(cid = 2L, rsrp = -90, isRegistered = true)

        viewModel.updateCells(listOf(neighbor, serving))

        val result = viewModel.currentCells.value!!
        assertThat(result[0].isRegistered).isTrue()
        assertThat(result[1].isRegistered).isFalse()
    }

    @Test
    fun `given measurements when updateCells then sorts by signal strength within same registration status`() {
        val weak = makeMeasurement(cid = 1L, rsrp = -110)
        val strong = makeMeasurement(cid = 2L, rsrp = -70)

        viewModel.updateCells(listOf(weak, strong))

        val result = viewModel.currentCells.value!!
        assertThat(result[0].rsrp).isEqualTo(-70)
        assertThat(result[1].rsrp).isEqualTo(-110)
    }

    @Test
    fun `given LTE filter when updateCells then only LTE measurements shown`() {
        val lte = makeMeasurement(radio = RadioType.LTE, cid = 1L)
        val gsm = makeMeasurement(radio = RadioType.GSM, cid = 2L)

        viewModel.setRadioTypeFilter(RadioType.LTE)
        viewModel.updateCells(listOf(lte, gsm))

        val result = viewModel.currentCells.value!!
        assertThat(result).hasSize(1)
        assertThat(result[0].radio).isEqualTo(RadioType.LTE)
    }

    @Test
    fun `given null filter when updateCells then all radio types shown`() {
        val lte = makeMeasurement(radio = RadioType.LTE, cid = 1L)
        val gsm = makeMeasurement(radio = RadioType.GSM, cid = 2L)

        viewModel.setRadioTypeFilter(null)
        viewModel.updateCells(listOf(lte, gsm))

        assertThat(viewModel.currentCells.value).hasSize(2)
    }

    @Test
    fun `given empty list when updateCells then currentCells is empty`() {
        viewModel.updateCells(emptyList())
        assertThat(viewModel.currentCells.value).isEmpty()
    }

    @Test
    fun `given measurements when loadRecentCells then fetches from repository`() = runTest {
        val measurements = listOf(makeMeasurement())
        coEvery { measurementRepo.getRecentMeasurements(100) } returns measurements

        viewModel.loadRecentCells()

        assertThat(viewModel.currentCells.value).isNotEmpty()
    }
}
