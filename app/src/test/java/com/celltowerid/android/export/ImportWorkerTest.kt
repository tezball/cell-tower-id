package com.celltowerid.android.export

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.repository.MeasurementRepository
import com.celltowerid.android.repository.SessionRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImportWorkerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun makeMeasurement(timestamp: Long = 1700000000000L): CellMeasurement = CellMeasurement(
        timestamp = timestamp,
        latitude = 37.7749,
        longitude = -122.4194,
        radio = RadioType.LTE,
        mcc = 310,
        mnc = 260,
        tacLac = 12345,
        cid = 67890L,
        rsrp = -85,
        rsrq = -10,
        sinr = 15,
        isRegistered = true,
        operatorName = "T-Mobile"
    )

    private fun writeFile(extension: String, contents: String): File {
        val f = temp.newFile("backup.$extension")
        f.writeText(contents)
        return f
    }

    @Test
    fun `given a valid CSV backup, when runImport, then a session is created and measurements are inserted`() = runTest {
        // Given
        val original = listOf(makeMeasurement(timestamp = 1L), makeMeasurement(timestamp = 2L))
        val file = writeFile("csv", CsvExporter.export(original))
        val sessionRepo = mockk<SessionRepository>()
        val measurementRepo = mockk<MeasurementRepository>(relaxed = true)
        coEvery { sessionRepo.startSession(any()) } returns 42L
        coEvery { sessionRepo.endSession(any(), any()) } returns Unit

        // When
        val result = ImportWorker.runImport(file, sessionRepo, measurementRepo)

        // Then
        assertThat(result.format).isEqualTo(ExportFormat.CSV)
        assertThat(result.sessionId).isEqualTo(42L)
        assertThat(result.measurements).hasSize(2)
        coVerify { sessionRepo.startSession("Imported from ${file.name}") }
        coVerify { measurementRepo.insertMeasurements(any(), 42L) }
        coVerify { sessionRepo.endSession(42L, 2) }
    }

    @Test
    fun `given a malformed backup, when runImport, then exception bubbles up and no session work is finalised`() = runTest {
        // Given
        val file = writeFile("csv", "not,a,valid,csv\n")
        val sessionRepo = mockk<SessionRepository>(relaxed = true)
        val measurementRepo = mockk<MeasurementRepository>(relaxed = true)

        // When/Then
        try {
            ImportWorker.runImport(file, sessionRepo, measurementRepo)
            error("expected ImportException")
        } catch (e: ImportException) {
            // Failure is surfaced before any DB writes.
            assertThat(e.reason).isEqualTo(ImportException.Reason.UNRECOGNIZED_SCHEMA)
            coVerify(exactly = 0) { sessionRepo.startSession(any()) }
            coVerify(exactly = 0) { measurementRepo.insertMeasurements(any(), any()) }
            coVerify(exactly = 0) { sessionRepo.endSession(any(), any()) }
        }
    }

    @Test
    fun `given a GeoJSON backup, when runImport, then format is reported as GEOJSON`() = runTest {
        // Given
        val original = listOf(makeMeasurement())
        val file = writeFile("geojson", GeoJsonExporter.export(original))
        val sessionRepo = mockk<SessionRepository>()
        val measurementRepo = mockk<MeasurementRepository>(relaxed = true)
        coEvery { sessionRepo.startSession(any()) } returns 7L
        coEvery { sessionRepo.endSession(any(), any()) } returns Unit

        // When
        val result = ImportWorker.runImport(file, sessionRepo, measurementRepo)

        // Then
        assertThat(result.format).isEqualTo(ExportFormat.GEOJSON)
        assertThat(result.sessionId).isEqualTo(7L)
    }

    @Test
    fun `given a KML backup, when runImport, then format is reported as KML`() = runTest {
        // Given
        val original = listOf(makeMeasurement())
        val file = writeFile("kml", KmlExporter.export(original))
        val sessionRepo = mockk<SessionRepository>()
        val measurementRepo = mockk<MeasurementRepository>(relaxed = true)
        coEvery { sessionRepo.startSession(any()) } returns 9L
        coEvery { sessionRepo.endSession(any(), any()) } returns Unit

        // When
        val result = ImportWorker.runImport(file, sessionRepo, measurementRepo)

        // Then
        assertThat(result.format).isEqualTo(ExportFormat.KML)
    }
}
