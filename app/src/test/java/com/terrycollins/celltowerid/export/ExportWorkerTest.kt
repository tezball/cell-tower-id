package com.terrycollins.celltowerid.export

import com.google.common.truth.Truth.assertThat
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.RadioType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportWorkerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun makeMeasurement(timestamp: Long = 1700000000000L) = CellMeasurement(
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

    @Test
    fun `given empty measurements list, when runExport, then returns null and writes no file`() {
        // Given
        val dir = tmp.newFolder("exports")

        // When
        val file = ExportWorker.runExport(
            measurements = emptyList(),
            format = ExportFormat.CSV,
            exportDir = dir,
            timestamp = "20260427_120000"
        )

        // Then
        assertThat(file).isNull()
        assertThat(dir.listFiles()).isEmpty()
    }

    @Test
    fun `given CSV format and measurements, when runExport, then file is written with cellid_export prefix and csv extension`() {
        // Given
        val dir = tmp.newFolder("exports")

        // When
        val file = requireNotNull(
            ExportWorker.runExport(
                measurements = listOf(makeMeasurement()),
                format = ExportFormat.CSV,
                exportDir = dir,
                timestamp = "20260427_120000"
            )
        )

        // Then
        assertThat(file.exists()).isTrue()
        assertThat(file.name).isEqualTo("cellid_export_20260427_120000.csv")
        assertThat(file.parentFile).isEqualTo(dir)
        // Body should include the BOM, CSV header and the measurement row.
        val body = file.readText()
        assertThat(body.first()).isEqualTo('\uFEFF')
        assertThat(body).contains("timestamp,lat,lon,radio")
        assertThat(body).contains("LTE")
        assertThat(body).contains("T-Mobile")
    }

    @Test
    fun `given GEOJSON format and measurements, when runExport, then file is written with geojson extension and FeatureCollection content`() {
        // Given
        val dir = tmp.newFolder("exports")

        // When
        val file = requireNotNull(
            ExportWorker.runExport(
                measurements = listOf(makeMeasurement()),
                format = ExportFormat.GEOJSON,
                exportDir = dir,
                timestamp = "20260427_120000"
            )
        )

        // Then
        assertThat(file.name).endsWith(".geojson")
        val body = file.readText()
        assertThat(body).contains("FeatureCollection")
    }

    @Test
    fun `given KML format and measurements, when runExport, then file is written with kml extension and Document content`() {
        // Given
        val dir = tmp.newFolder("exports")

        // When
        val file = requireNotNull(
            ExportWorker.runExport(
                measurements = listOf(makeMeasurement()),
                format = ExportFormat.KML,
                exportDir = dir,
                timestamp = "20260427_120000"
            )
        )

        // Then
        assertThat(file.name).endsWith(".kml")
        val body = file.readText()
        assertThat(body).contains("<Document>")
        assertThat(body).contains("<Placemark>")
    }

    @Test
    fun `given non-existent export dir, when runExport, then directory is created`() {
        // Given -- subdirectory doesn't exist yet.
        val parent = tmp.newFolder("parent")
        val dir = java.io.File(parent, "fresh_exports_dir")
        assertThat(dir.exists()).isFalse()

        // When
        val file = requireNotNull(
            ExportWorker.runExport(
                measurements = listOf(makeMeasurement()),
                format = ExportFormat.CSV,
                exportDir = dir,
                timestamp = "20260427_120000"
            )
        )

        // Then
        assertThat(dir.exists()).isTrue()
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun `given two exports with different timestamps, when runExport, then both files coexist`() {
        // Given
        val dir = tmp.newFolder("exports")

        // When
        val file1 = ExportWorker.runExport(
            listOf(makeMeasurement()), ExportFormat.CSV, dir, "20260427_120000"
        )
        val file2 = ExportWorker.runExport(
            listOf(makeMeasurement()), ExportFormat.CSV, dir, "20260427_120030"
        )

        // Then
        assertThat(requireNotNull(file1).exists()).isTrue()
        assertThat(requireNotNull(file2).exists()).isTrue()
        assertThat(file1.absolutePath).isNotEqualTo(file2.absolutePath)
        assertThat(requireNotNull(dir.listFiles()).size).isEqualTo(2)
    }
}
