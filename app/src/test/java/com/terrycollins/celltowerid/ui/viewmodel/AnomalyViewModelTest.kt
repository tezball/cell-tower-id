package com.terrycollins.celltowerid.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.terrycollins.celltowerid.domain.model.AnomalyEvent
import com.terrycollins.celltowerid.domain.model.AnomalySeverity
import com.terrycollins.celltowerid.domain.model.AnomalyType
import com.terrycollins.celltowerid.repository.AnomalyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnomalyViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var anomalyRepo: AnomalyRepository
    private lateinit var viewModel: AnomalyViewModel

    private fun makeAnomaly(id: Long = 1L, dismissed: Boolean = false): AnomalyEvent {
        return AnomalyEvent(
            id = id,
            timestamp = System.currentTimeMillis(),
            type = AnomalyType.SIGNAL_ANOMALY,
            severity = AnomalySeverity.HIGH,
            description = "Signal anomaly detected",
            dismissed = dismissed
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        anomalyRepo = mockk(relaxed = true)
        val mockApp = mockk<Application>(relaxed = true)
        viewModel = AnomalyViewModel(mockApp, anomalyRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given undismissed anomalies when loadAnomalies then anomalies LiveData populated`() = runTest {
        val events = listOf(makeAnomaly(1L), makeAnomaly(2L))
        coEvery { anomalyRepo.getUndismissedAnomalies() } returns events

        viewModel.loadAnomalies()

        assertThat(viewModel.anomalies.value).hasSize(2)
        assertThat(viewModel.undismissedCount.value).isEqualTo(2)
    }

    @Test
    fun `given no anomalies when loadAnomalies then LiveData is empty`() = runTest {
        coEvery { anomalyRepo.getUndismissedAnomalies() } returns emptyList()

        viewModel.loadAnomalies()

        assertThat(viewModel.anomalies.value).isEmpty()
        assertThat(viewModel.undismissedCount.value).isEqualTo(0)
    }

    @Test
    fun `given anomalies when loadAllAnomalies then all anomalies loaded`() = runTest {
        val events = listOf(
            makeAnomaly(1L, dismissed = false),
            makeAnomaly(2L, dismissed = true),
            makeAnomaly(3L, dismissed = false)
        )
        coEvery { anomalyRepo.getAllAnomalies() } returns events

        viewModel.loadAllAnomalies()

        assertThat(viewModel.anomalies.value).hasSize(3)
    }

    @Test
    fun `given anomalies when dismissAll then dao dismissAll called and reloaded`() = runTest {
        val events = listOf(makeAnomaly(1L), makeAnomaly(2L))
        coEvery { anomalyRepo.getUndismissedAnomalies() } returns events andThen emptyList()
        coEvery { anomalyRepo.dismissAll() } just runs

        viewModel.loadAnomalies()
        assertThat(viewModel.undismissedCount.value).isEqualTo(2)

        viewModel.dismissAll()

        coVerify { anomalyRepo.dismissAll() }
        assertThat(viewModel.anomalies.value).isEmpty()
        assertThat(viewModel.undismissedCount.value).isEqualTo(0)
    }

    @Test
    fun `given initial state then undismissedCount is zero`() {
        assertThat(viewModel.undismissedCount.value).isEqualTo(0)
    }
}
