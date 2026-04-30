package com.celltowerid.android.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.celltowerid.android.domain.model.AnomalyEvent
import com.celltowerid.android.domain.model.AnomalySeverity
import com.celltowerid.android.domain.model.AnomalyType
import com.celltowerid.android.domain.model.CellKey
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.CellTower
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.repository.AnomalyRepository
import com.celltowerid.android.repository.MeasurementRepository
import com.celltowerid.android.repository.TowerCacheRepository
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
    private lateinit var anomalyRepo: AnomalyRepository
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
        anomalyRepo = mockk(relaxed = true)
        val mockApp = mockk<Application>(relaxed = true)
        viewModel = MapViewModel(mockApp, measurementRepo, towerCacheRepo, anomalyRepo)
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

    @Test
    fun `given unpinTower called, when invoked, then repo unpinTower invoked and loadAllTowers triggered`() = runTest {
        coEvery { towerCacheRepo.getTowersInArea(-90.0, 90.0, -180.0, 180.0) } returns emptyList()

        viewModel.unpinTower(RadioType.LTE, 310, 260, 100, 555L)

        coVerify(exactly = 1) { towerCacheRepo.unpinTower(RadioType.LTE, 310, 260, 100, 555L) }
        coVerify(atLeast = 1) { towerCacheRepo.getTowersInArea(-90.0, 90.0, -180.0, 180.0) }
    }

    @Test
    fun `given best readings present, when loadAllTowers called, then bestReadings LiveData emits map keyed by cell tuple`() = runTest {
        val key = CellKey(RadioType.LTE, 310, 260, 100, 50331905L)
        val expected = mapOf(key to lteMeasurement)
        coEvery { towerCacheRepo.getTowersInArea(-90.0, 90.0, -180.0, 180.0) } returns emptyList()
        coEvery {
            measurementRepo.getBestMeasurementsByCellInArea(-90.0, 90.0, -180.0, 180.0)
        } returns expected

        viewModel.loadAllTowers()

        assertThat(viewModel.bestReadings.value).isEqualTo(expected)
        coVerify { measurementRepo.getBestMeasurementsByCellInArea(-90.0, 90.0, -180.0, 180.0) }
    }

    @Test
    fun `given empty repo, when loadAllTowers called, then bestReadings LiveData emits empty map`() = runTest {
        coEvery { towerCacheRepo.getTowersInArea(-90.0, 90.0, -180.0, 180.0) } returns emptyList()
        coEvery {
            measurementRepo.getBestMeasurementsByCellInArea(any(), any(), any(), any())
        } returns emptyMap()

        viewModel.loadAllTowers()

        assertThat(viewModel.bestReadings.value).isEmpty()
    }

    @Test
    fun `given LTE radio filter, when towers loaded, then filteredTowers emits only LTE towers`() = runTest {
        val towers = listOf(
            CellTower(radio = RadioType.LTE, mcc = 310, mnc = 260, tacLac = 1, cid = 100, latitude = 0.0, longitude = 0.0),
            CellTower(radio = RadioType.NR, mcc = 310, mnc = 260, tacLac = 1, cid = 200, latitude = 0.0, longitude = 0.0),
            CellTower(radio = RadioType.GSM, mcc = 310, mnc = 12, tacLac = 1, cid = 300, latitude = 0.0, longitude = 0.0)
        )
        coEvery { towerCacheRepo.getTowersInArea(any(), any(), any(), any()) } returns towers
        coEvery { measurementRepo.getBestMeasurementsByCellInArea(any(), any(), any(), any()) } returns emptyMap()

        viewModel.loadAllTowers()
        viewModel.setRadioTypeFilter(RadioType.LTE)

        val filtered = viewModel.filteredTowers.value!!
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].radio).isEqualTo(RadioType.LTE)
    }

    @Test
    fun `given carrier filter, when towers loaded, then filteredTowers excludes other carriers`() = runTest {
        val towers = listOf(
            CellTower(radio = RadioType.LTE, mcc = 310, mnc = 260, tacLac = 1, cid = 100, latitude = 0.0, longitude = 0.0), // T-Mobile
            CellTower(radio = RadioType.LTE, mcc = 310, mnc = 410, tacLac = 1, cid = 200, latitude = 0.0, longitude = 0.0)  // AT&T
        )
        coEvery { towerCacheRepo.getTowersInArea(any(), any(), any(), any()) } returns towers
        coEvery { measurementRepo.getBestMeasurementsByCellInArea(any(), any(), any(), any()) } returns emptyMap()

        viewModel.loadAllTowers()
        viewModel.setCarrierFilter("T-Mobile")

        val filtered = viewModel.filteredTowers.value!!
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].mnc).isEqualTo(260)
    }

    @Test
    fun `given filters cleared, when filter set to null, then filteredTowers emits all loaded towers`() = runTest {
        val towers = listOf(
            CellTower(radio = RadioType.LTE, mcc = 310, mnc = 260, tacLac = 1, cid = 100, latitude = 0.0, longitude = 0.0),
            CellTower(radio = RadioType.NR, mcc = 310, mnc = 260, tacLac = 1, cid = 200, latitude = 0.0, longitude = 0.0)
        )
        coEvery { towerCacheRepo.getTowersInArea(any(), any(), any(), any()) } returns towers
        coEvery { measurementRepo.getBestMeasurementsByCellInArea(any(), any(), any(), any()) } returns emptyMap()

        viewModel.loadAllTowers()
        viewModel.setRadioTypeFilter(RadioType.LTE)
        viewModel.setRadioTypeFilter(null)

        assertThat(viewModel.filteredTowers.value).hasSize(2)
    }

    @Test
    fun `given no towers loaded, when filter changes, then filteredTowers emits empty list`() {
        viewModel.setRadioTypeFilter(RadioType.LTE)
        assertThat(viewModel.filteredTowers.value).isEmpty()
    }

    // -- Alert filter --

    private fun anomalyFor(
        tower: CellTower,
        severity: AnomalySeverity = AnomalySeverity.MEDIUM,
        timestamp: Long = 1L
    ): AnomalyEvent = AnomalyEvent(
        timestamp = timestamp,
        type = AnomalyType.SIGNAL_ANOMALY,
        severity = severity,
        description = "test",
        cellRadio = tower.radio,
        cellMcc = tower.mcc,
        cellMnc = tower.mnc,
        cellTacLac = tower.tacLac,
        cellCid = tower.cid
    )

    @Test
    fun `given empty severity filter, when applying tower filters, then all towers retained`() = runTest {
        val gsm = CellTower(RadioType.GSM, 310, 12, 1, 100, latitude = 0.0, longitude = 0.0)
        val nr = CellTower(RadioType.NR, 310, 410, 1, 200, latitude = 0.0, longitude = 0.0)
        coEvery { towerCacheRepo.getTowersInArea(any(), any(), any(), any()) } returns listOf(gsm, nr)
        coEvery { measurementRepo.getBestMeasurementsByCellInArea(any(), any(), any(), any()) } returns emptyMap()
        coEvery { anomalyRepo.getUndismissedAnomalies() } returns listOf(anomalyFor(gsm, AnomalySeverity.HIGH))

        viewModel.loadAllTowers()

        assertThat(viewModel.filteredTowers.value).hasSize(2)
    }

    @Test
    fun `given HIGH severity filter, when applying, then only towers with HIGH alerts retained`() = runTest {
        val highTower = CellTower(RadioType.GSM, 310, 12, 1, 100, latitude = 0.0, longitude = 0.0)
        val mediumTower = CellTower(RadioType.NR, 310, 410, 1, 200, latitude = 0.0, longitude = 0.0)
        val noAlertTower = CellTower(RadioType.WCDMA, 310, 12, 1, 300, latitude = 0.0, longitude = 0.0)
        coEvery { towerCacheRepo.getTowersInArea(any(), any(), any(), any()) } returns
            listOf(highTower, mediumTower, noAlertTower)
        coEvery { measurementRepo.getBestMeasurementsByCellInArea(any(), any(), any(), any()) } returns emptyMap()
        coEvery { anomalyRepo.getUndismissedAnomalies() } returns listOf(
            anomalyFor(highTower, AnomalySeverity.HIGH),
            anomalyFor(mediumTower, AnomalySeverity.MEDIUM)
        )

        viewModel.loadAllTowers()
        viewModel.setAlertSeverityFilter(setOf(AnomalySeverity.HIGH))

        val filtered = viewModel.filteredTowers.value!!
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].cid).isEqualTo(100L)
    }

    @Test
    fun `given HIGH and MEDIUM filter, when applying, then towers with HIGH or MEDIUM retained`() = runTest {
        val highTower = CellTower(RadioType.GSM, 310, 12, 1, 100, latitude = 0.0, longitude = 0.0)
        val mediumTower = CellTower(RadioType.NR, 310, 410, 1, 200, latitude = 0.0, longitude = 0.0)
        val lowTower = CellTower(RadioType.WCDMA, 310, 12, 1, 300, latitude = 0.0, longitude = 0.0)
        coEvery { towerCacheRepo.getTowersInArea(any(), any(), any(), any()) } returns
            listOf(highTower, mediumTower, lowTower)
        coEvery { measurementRepo.getBestMeasurementsByCellInArea(any(), any(), any(), any()) } returns emptyMap()
        coEvery { anomalyRepo.getUndismissedAnomalies() } returns listOf(
            anomalyFor(highTower, AnomalySeverity.HIGH),
            anomalyFor(mediumTower, AnomalySeverity.MEDIUM),
            anomalyFor(lowTower, AnomalySeverity.LOW)
        )

        viewModel.loadAllTowers()
        viewModel.setAlertSeverityFilter(setOf(AnomalySeverity.HIGH, AnomalySeverity.MEDIUM))

        val filtered = viewModel.filteredTowers.value!!
        assertThat(filtered).hasSize(2)
        assertThat(filtered.map { it.cid }).containsExactly(100L, 200L)
    }

    @Test
    fun `given alert filter and radio filter, when applying, then both compose`() = runTest {
        val gsmHigh = CellTower(RadioType.GSM, 310, 12, 1, 100, latitude = 0.0, longitude = 0.0)
        val nrHigh = CellTower(RadioType.NR, 310, 410, 1, 200, latitude = 0.0, longitude = 0.0)
        coEvery { towerCacheRepo.getTowersInArea(any(), any(), any(), any()) } returns listOf(gsmHigh, nrHigh)
        coEvery { measurementRepo.getBestMeasurementsByCellInArea(any(), any(), any(), any()) } returns emptyMap()
        coEvery { anomalyRepo.getUndismissedAnomalies() } returns listOf(
            anomalyFor(gsmHigh, AnomalySeverity.HIGH),
            anomalyFor(nrHigh, AnomalySeverity.HIGH)
        )

        viewModel.loadAllTowers()
        viewModel.setAlertSeverityFilter(setOf(AnomalySeverity.HIGH))
        viewModel.setRadioTypeFilter(RadioType.NR)

        val filtered = viewModel.filteredTowers.value!!
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].radio).isEqualTo(RadioType.NR)
    }

    @Test
    fun `given alert filter set before towers loaded, when towers and alerts arrive, then filter applied with no race`() = runTest {
        val highTower = CellTower(RadioType.GSM, 310, 12, 1, 100, latitude = 0.0, longitude = 0.0)
        val noAlertTower = CellTower(RadioType.WCDMA, 310, 12, 1, 300, latitude = 0.0, longitude = 0.0)
        coEvery { towerCacheRepo.getTowersInArea(any(), any(), any(), any()) } returns
            listOf(highTower, noAlertTower)
        coEvery { measurementRepo.getBestMeasurementsByCellInArea(any(), any(), any(), any()) } returns emptyMap()
        coEvery { anomalyRepo.getUndismissedAnomalies() } returns listOf(
            anomalyFor(highTower, AnomalySeverity.HIGH)
        )

        viewModel.setAlertSeverityFilter(setOf(AnomalySeverity.HIGH))
        viewModel.loadAllTowers()

        val filtered = viewModel.filteredTowers.value!!
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].cid).isEqualTo(100L)
    }

    @Test
    fun `given LTE alert on a sector, when filtering, then any sector of the same eNB is retained`() = runTest {
        val enbBase = 87_895L shl 8
        val sector1 = CellTower(RadioType.LTE, 310, 260, 100, enbBase or 1L,
            latitude = 0.0, longitude = 0.0)
        val sector2 = CellTower(RadioType.LTE, 310, 260, 100, enbBase or 2L,
            latitude = 0.0, longitude = 0.0)
        coEvery { towerCacheRepo.getTowersInArea(any(), any(), any(), any()) } returns listOf(sector1, sector2)
        coEvery { measurementRepo.getBestMeasurementsByCellInArea(any(), any(), any(), any()) } returns emptyMap()
        // Alert lives on sector 1 only.
        coEvery { anomalyRepo.getUndismissedAnomalies() } returns listOf(
            anomalyFor(sector1, AnomalySeverity.HIGH)
        )

        viewModel.loadAllTowers()
        viewModel.setAlertSeverityFilter(setOf(AnomalySeverity.HIGH))

        // Both sectors retained — they collapse to the same eNB on the map and the alert
        // attribution should follow.
        assertThat(viewModel.filteredTowers.value).hasSize(2)
    }

    @Test
    fun `given alerts loaded, when loadAllTowers called, then alertIndex LiveData populated`() = runTest {
        val gsm = CellTower(RadioType.GSM, 310, 12, 1, 100, latitude = 0.0, longitude = 0.0)
        coEvery { towerCacheRepo.getTowersInArea(any(), any(), any(), any()) } returns listOf(gsm)
        coEvery { measurementRepo.getBestMeasurementsByCellInArea(any(), any(), any(), any()) } returns emptyMap()
        coEvery { anomalyRepo.getUndismissedAnomalies() } returns listOf(
            anomalyFor(gsm, AnomalySeverity.HIGH)
        )

        viewModel.loadAllTowers()

        val index = viewModel.alertIndex.value!!
        assertThat(index.nonLte).isNotEmpty()
    }

    @Test
    fun `given initial state, then alertSeverityFilter is empty set`() {
        assertThat(viewModel.alertSeverityFilter.value).isEmpty()
    }
}
