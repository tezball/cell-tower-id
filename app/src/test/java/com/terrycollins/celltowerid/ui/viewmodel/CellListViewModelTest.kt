package com.terrycollins.celltowerid.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.RadioType
import androidx.lifecycle.MutableLiveData
import com.terrycollins.celltowerid.data.entity.TowerCacheEntity
import com.terrycollins.celltowerid.repository.MeasurementRepository
import com.terrycollins.celltowerid.repository.TowerCacheRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
    private lateinit var towerCacheRepo: TowerCacheRepository
    private lateinit var pinnedLive: MutableLiveData<List<TowerCacheEntity>>
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

    private val noopPinnedObserver = Observer<Set<String>> { }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        measurementRepo = mockk(relaxed = true)
        towerCacheRepo = mockk(relaxed = true)
        pinnedLive = MutableLiveData(emptyList())
        every { towerCacheRepo.getPinnedTowerEntitiesLive() } returns pinnedLive
        val mockApp = mockk<Application>(relaxed = true)
        viewModel = CellListViewModel(mockApp, measurementRepo, towerCacheRepo)
        // LiveData.map is lazy — attach a no-op observer so the transform runs
        viewModel.pinnedTowerKeys.observeForever(noopPinnedObserver)
    }

    @After
    fun tearDown() {
        viewModel.pinnedTowerKeys.removeObserver(noopPinnedObserver)
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
    fun `given fresh measurements when loadRecentCells then fetches from repository`() = runTest {
        val measurements = listOf(makeMeasurement())
        coEvery { measurementRepo.getMeasurementsSince(any()) } returns measurements
        coEvery { towerCacheRepo.getPinnedTowerEntities() } returns emptyList()

        viewModel.loadRecentCells()

        assertThat(viewModel.currentCells.value).isNotEmpty()
    }

    @Test
    fun `when loadRecentCells, then queries measurements since now minus 15 second freshness window`() = runTest {
        coEvery { measurementRepo.getMeasurementsSince(any()) } returns emptyList()
        coEvery { towerCacheRepo.getPinnedTowerEntities() } returns emptyList()
        val cutoffSlot = slot<Long>()

        val before = System.currentTimeMillis()
        viewModel.loadRecentCells()
        val after = System.currentTimeMillis()

        coVerify { measurementRepo.getMeasurementsSince(capture(cutoffSlot)) }
        assertThat(cutoffSlot.captured).isAtLeast(before - 15_000)
        assertThat(cutoffSlot.captured).isAtMost(after - 15_000)
    }

    @Test
    fun `given only stale measurements in repo, when loadRecentCells, then list is empty`() = runTest {
        // The DAO filters by cutoff, so getMeasurementsSince returns only fresh rows.
        // If everything is stale, it returns an empty list.
        coEvery { measurementRepo.getMeasurementsSince(any()) } returns emptyList()
        coEvery { towerCacheRepo.getPinnedTowerEntities() } returns emptyList()

        viewModel.loadRecentCells()

        assertThat(viewModel.currentCells.value).isEmpty()
    }

    @Test
    fun `given pinned tower not in fresh measurements, when loadRecentCells, then appears as out-of-range stub with null signal`() = runTest {
        coEvery { measurementRepo.getMeasurementsSince(any()) } returns emptyList()
        val pinnedEntity = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 100; cid = 777L
            isPinned = true
            latitude = 37.7749; longitude = -122.4194; pci = 42; lastUpdated = 5000L
        }
        coEvery { towerCacheRepo.getPinnedTowerEntities() } returns listOf(pinnedEntity)

        viewModel.loadRecentCells()

        val result = viewModel.currentCells.value!!
        assertThat(result).hasSize(1)
        val stub = result[0]
        assertThat(stub.cid).isEqualTo(777L)
        assertThat(stub.radio).isEqualTo(RadioType.LTE)
        assertThat(stub.mcc).isEqualTo(310)
        assertThat(stub.mnc).isEqualTo(260)
        assertThat(stub.tacLac).isEqualTo(100)
        assertThat(stub.pciPsc).isEqualTo(42)
        assertThat(stub.rsrp).isNull()
        assertThat(stub.rssi).isNull()
        assertThat(stub.isRegistered).isFalse()
    }

    @Test
    fun `given pinned tower present in fresh measurements, when loadRecentCells, then fresh measurement shown and no duplicate stub`() = runTest {
        val freshCell = makeMeasurement(cid = 777L, rsrp = -80, isRegistered = true)
        coEvery { measurementRepo.getMeasurementsSince(any()) } returns listOf(freshCell)
        val pinnedEntity = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 100; cid = 777L
            isPinned = true
        }
        coEvery { towerCacheRepo.getPinnedTowerEntities() } returns listOf(pinnedEntity)

        viewModel.loadRecentCells()

        val result = viewModel.currentCells.value!!
        assertThat(result).hasSize(1)
        assertThat(result[0].rsrp).isEqualTo(-80)
        assertThat(result[0].isRegistered).isTrue()
    }

    @Test
    fun `given pinned entity with unparseable radio, when loadRecentCells, then entity is skipped`() = runTest {
        coEvery { measurementRepo.getMeasurementsSince(any()) } returns emptyList()
        val bogus = TowerCacheEntity().apply {
            radio = "BOGUS"; mcc = 310; mnc = 260; tacLac = 100; cid = 1L
            isPinned = true
        }
        coEvery { towerCacheRepo.getPinnedTowerEntities() } returns listOf(bogus)

        viewModel.loadRecentCells()

        assertThat(viewModel.currentCells.value).isEmpty()
    }

    @Test
    fun `given togglePin on unpinned cell, when pin completes, then loadRecentCells is invoked`() = runTest {
        coEvery {
            towerCacheRepo.pinTower(any(), any(), any(), any(), any(), any(), any(), any())
        } returns true
        coEvery { measurementRepo.getMeasurementsSince(any()) } returns emptyList()
        coEvery { towerCacheRepo.getPinnedTowerEntities() } returns emptyList()
        val cell = makeMeasurement(cid = 50331905L, isRegistered = true)

        viewModel.togglePin(cell)

        coVerify(atLeast = 1) { measurementRepo.getMeasurementsSince(any()) }
    }

    @Test
    fun `given togglePin on pinned cell, when unpin completes, then loadRecentCells is invoked`() = runTest {
        coEvery { measurementRepo.getMeasurementsSince(any()) } returns emptyList()
        coEvery { towerCacheRepo.getPinnedTowerEntities() } returns emptyList()
        val cell = makeMeasurement(cid = 888L, isRegistered = true)
        val pinnedEntity = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 100; cid = 888L; isPinned = true
        }
        pinnedLive.value = listOf(pinnedEntity)

        viewModel.togglePin(cell)

        coVerify(atLeast = 1) { measurementRepo.getMeasurementsSince(any()) }
    }

    @Test
    fun `given fully-identified cell not pinned, when togglePin, then repo pinTower called with measurement coords as fallback`() = runTest {
        coEvery {
            towerCacheRepo.pinTower(any(), any(), any(), any(), any(), any(), any(), any())
        } returns true
        val cell = makeMeasurement(cid = 50331905L, isRegistered = true)

        viewModel.togglePin(cell)

        coVerify(exactly = 1) {
            towerCacheRepo.pinTower(
                RadioType.LTE, 310, 260, 100, 50331905L,
                37.7749, -122.4194, null
            )
        }
    }

    @Test
    fun `given already-pinned cell, when togglePin, then repo unpinTower called`() = runTest {
        val cell = makeMeasurement(cid = 777L, isRegistered = true)
        val pinnedEntity = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 100; cid = 777L; isPinned = true
        }
        pinnedLive.value = listOf(pinnedEntity)

        viewModel.togglePin(cell)

        coVerify(exactly = 1) { towerCacheRepo.unpinTower(RadioType.LTE, 310, 260, 100, 777L) }
        coVerify(exactly = 0) { towerCacheRepo.pinTower(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given cell with null cid, when togglePin, then snackbar emitted and pinTower not called`() = runTest {
        val cell = makeMeasurement().copy(cid = null)

        viewModel.togglePin(cell)

        coVerify(exactly = 0) { towerCacheRepo.pinTower(any(), any(), any(), any(), any(), any(), any(), any()) }
        val event = viewModel.pinSnackbar.value
        assertThat(event).isNotNull()
        assertThat(event!!.peekContent()).contains("incomplete")
    }

    @Test
    fun `given unpinned cells in repo, when pinnedTowerKeys observed, then emits keys derived from repo live data`() {
        val pinnedEntity = TowerCacheEntity().apply {
            radio = "LTE"; mcc = 310; mnc = 260; tacLac = 100; cid = 777L; isPinned = true
        }
        pinnedLive.value = listOf(pinnedEntity)

        val keys = viewModel.pinnedTowerKeys.value
        assertThat(keys).containsExactly("LTE-310-260-100-777")
    }
}
