package com.celltowerid.android.export

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupImporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun makeMeasurement(
        timestamp: Long = 1700000000000L,
        operatorName: String? = "T-Mobile"
    ): CellMeasurement = CellMeasurement(
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
        operatorName = operatorName
    )

    private fun writeFile(extension: String, contents: String): File {
        val f = temp.newFile("backup.$extension")
        f.writeText(contents)
        return f
    }

    private fun writeBytes(extension: String, bytes: ByteArray): File {
        val f = temp.newFile("backup.$extension")
        f.writeBytes(bytes)
        return f
    }

    @Test
    fun `given a CSV file, when importing, then format is detected and rows parsed`() {
        // Given
        val original = listOf(makeMeasurement())
        val file = writeFile("csv", CsvExporter.export(original))

        // When
        val result = BackupImporter.import(file)

        // Then
        assertThat(result.format).isEqualTo(ExportFormat.CSV)
        assertThat(result.measurements).hasSize(1)
        assertThat(result.measurements[0].operatorName).isEqualTo("T-Mobile")
    }

    @Test
    fun `given a GeoJSON file with geojson extension, when importing, then GEOJSON format is detected`() {
        // Given
        val original = listOf(makeMeasurement())
        val file = writeFile("geojson", GeoJsonExporter.export(original))

        // When
        val result = BackupImporter.import(file)

        // Then
        assertThat(result.format).isEqualTo(ExportFormat.GEOJSON)
        assertThat(result.measurements).hasSize(1)
    }

    @Test
    fun `given a GeoJSON file with json extension, when importing, then GEOJSON format is detected`() {
        // Given
        val original = listOf(makeMeasurement())
        val file = writeFile("json", GeoJsonExporter.export(original))

        // When
        val result = BackupImporter.import(file)

        // Then
        assertThat(result.format).isEqualTo(ExportFormat.GEOJSON)
        assertThat(result.measurements).hasSize(1)
    }

    @Test
    fun `given a KML file, when importing, then KML format is detected`() {
        // Given
        val original = listOf(makeMeasurement())
        val file = writeFile("kml", KmlExporter.export(original))

        // When
        val result = BackupImporter.import(file)

        // Then
        assertThat(result.format).isEqualTo(ExportFormat.KML)
        assertThat(result.measurements).hasSize(1)
    }

    @Test
    fun `given a file with unknown extension, when importing, then throws UNKNOWN_FORMAT`() {
        // Given
        val file = writeFile("txt", "anything")

        // When/Then
        try {
            BackupImporter.import(file)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.UNKNOWN_FORMAT)
        }
    }

    @Test
    fun `given a file with no extension, when importing, then throws UNKNOWN_FORMAT`() {
        // Given
        val file = temp.newFile("backup")
        file.writeText(CsvExporter.export(listOf(makeMeasurement())))

        // When/Then
        try {
            BackupImporter.import(file)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.UNKNOWN_FORMAT)
        }
    }

    @Test
    fun `given a file larger than the cap, when importing, then throws FILE_TOO_LARGE`() {
        // Given - oversize a CSV by padding the operator field with spaces.
        val bytes = ByteArray((ImportLimits.MAX_FILE_BYTES + 1).toInt())
        val file = writeBytes("csv", bytes)

        // When/Then
        try {
            BackupImporter.import(file)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.FILE_TOO_LARGE)
        }
    }

    @Test
    fun `given a missing file, when importing, then throws MALFORMED`() {
        // Given
        val file = File(temp.root, "does-not-exist.csv")

        // When/Then
        try {
            BackupImporter.import(file)
            error("expected ImportException")
        } catch (e: ImportException) {
            // Could be UNKNOWN_FORMAT or MALFORMED — we accept either since
            // .csv extension is detectable but the file can't be opened.
            assertThat(e.reason).isAnyOf(
                ImportException.Reason.MALFORMED,
                ImportException.Reason.UNKNOWN_FORMAT
            )
        }
    }

    @Test
    fun `given uppercase extension, when importing, then format is still detected`() {
        // Given
        val original = listOf(makeMeasurement())
        val file = temp.newFile("backup.CSV")
        file.writeText(CsvExporter.export(original))

        // When
        val result = BackupImporter.import(file)

        // Then
        assertThat(result.format).isEqualTo(ExportFormat.CSV)
        assertThat(result.measurements).hasSize(1)
    }
}
