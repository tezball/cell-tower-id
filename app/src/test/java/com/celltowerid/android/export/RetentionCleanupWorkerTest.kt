package com.celltowerid.android.export

import com.celltowerid.android.repository.AnomalyRepository
import com.celltowerid.android.repository.MeasurementRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RetentionCleanupWorkerTest {

    @Test
    fun `given retention disabled, when run, then neither repo is called`() = runTest {
        // Given
        val measurementRepo = mockk<MeasurementRepository>()
        val anomalyRepo = mockk<AnomalyRepository>()

        // When
        RetentionCleanupWorker.run(
            retentionDays = 0,
            measurementRepo = measurementRepo,
            anomalyRepo = anomalyRepo,
            nowMs = 1_700_000_000_000L
        )

        // Then — confirmVerified (via strict mock alternative): if either were called, MockK would throw
        coVerify(exactly = 0) { measurementRepo.deleteOlderThan(any()) }
        coVerify(exactly = 0) { anomalyRepo.deleteOlderThan(any()) }
    }

    @Test
    fun `given negative retention, when run, then neither repo is called`() = runTest {
        // Given — guards against corrupt prefs returning a negative value
        val measurementRepo = mockk<MeasurementRepository>()
        val anomalyRepo = mockk<AnomalyRepository>()

        // When
        RetentionCleanupWorker.run(
            retentionDays = -1,
            measurementRepo = measurementRepo,
            anomalyRepo = anomalyRepo,
            nowMs = 1_700_000_000_000L
        )

        // Then
        coVerify(exactly = 0) { measurementRepo.deleteOlderThan(any()) }
        coVerify(exactly = 0) { anomalyRepo.deleteOlderThan(any()) }
    }

    @Test
    fun `given 14 day retention, when run, then deletes rows older than 14 days from both repos`() = runTest {
        // Given
        val now = 1_700_000_000_000L
        val expectedCutoff = now - 14L * 86_400_000L
        val measurementRepo = mockk<MeasurementRepository>()
        val anomalyRepo = mockk<AnomalyRepository>()
        coEvery { measurementRepo.deleteOlderThan(expectedCutoff) } returns 42
        coEvery { anomalyRepo.deleteOlderThan(expectedCutoff) } returns 7

        // When
        RetentionCleanupWorker.run(
            retentionDays = 14,
            measurementRepo = measurementRepo,
            anomalyRepo = anomalyRepo,
            nowMs = now
        )

        // Then
        coVerify(exactly = 1) { measurementRepo.deleteOlderThan(expectedCutoff) }
        coVerify(exactly = 1) { anomalyRepo.deleteOlderThan(expectedCutoff) }
    }

    @Test
    fun `given 1 day retention, when run, then cutoff is exactly one day before now`() = runTest {
        // Given
        val now = 1_700_000_000_000L
        val expectedCutoff = now - 86_400_000L
        val measurementRepo = mockk<MeasurementRepository>()
        val anomalyRepo = mockk<AnomalyRepository>()
        coEvery { measurementRepo.deleteOlderThan(expectedCutoff) } returns 0
        coEvery { anomalyRepo.deleteOlderThan(expectedCutoff) } returns 0

        // When
        RetentionCleanupWorker.run(
            retentionDays = 1,
            measurementRepo = measurementRepo,
            anomalyRepo = anomalyRepo,
            nowMs = now
        )

        // Then
        coVerify(exactly = 1) { measurementRepo.deleteOlderThan(expectedCutoff) }
        coVerify(exactly = 1) { anomalyRepo.deleteOlderThan(expectedCutoff) }
    }
}
