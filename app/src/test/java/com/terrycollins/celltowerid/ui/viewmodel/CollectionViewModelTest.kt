package com.terrycollins.celltowerid.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.terrycollins.celltowerid.data.entity.SessionEntity
import com.terrycollins.celltowerid.repository.MeasurementRepository
import com.terrycollins.celltowerid.repository.SessionRepository
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
class CollectionViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var sessionRepo: SessionRepository
    private lateinit var measurementRepo: MeasurementRepository
    private lateinit var viewModel: CollectionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionRepo = mockk(relaxed = true)
        measurementRepo = mockk(relaxed = true)
        val mockApp = mockk<Application>(relaxed = true)
        viewModel = CollectionViewModel(mockApp, sessionRepo, measurementRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given initial state when created then isCollecting is false`() {
        assertThat(viewModel.isCollecting.value).isFalse()
    }

    @Test
    fun `given initial state when created then collection interval is 5000ms`() {
        assertThat(viewModel.collectionInterval.value).isEqualTo(5000L)
    }

    @Test
    fun `when setCollecting true then isCollecting updates`() {
        viewModel.setCollecting(true)
        assertThat(viewModel.isCollecting.value).isTrue()
    }

    @Test
    fun `when setInterval then collectionInterval updates`() {
        viewModel.setInterval(10000L)
        assertThat(viewModel.collectionInterval.value).isEqualTo(10000L)
    }

    @Test
    fun `when loadStats then totalMeasurements populated from repository`() = runTest {
        coEvery { measurementRepo.getMeasurementCount() } returns 42
        coEvery { sessionRepo.getAllSessions() } returns emptyList()

        viewModel.loadStats()

        assertThat(viewModel.totalMeasurements.value).isEqualTo(42)
    }

    @Test
    fun `when loadStats then sessions populated from repository`() = runTest {
        val sessions = listOf(SessionEntity(), SessionEntity())
        coEvery { sessionRepo.getAllSessions() } returns sessions
        coEvery { measurementRepo.getMeasurementCount() } returns 0

        viewModel.loadStats()

        assertThat(viewModel.sessions.value).hasSize(2)
    }
}
