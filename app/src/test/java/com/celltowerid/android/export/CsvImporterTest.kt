package com.celltowerid.android.export

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.StringReader

class CsvImporterTest {

    private fun makeMeasurement(
        timestamp: Long = 1700000000000L,
        operatorName: String? = "T-Mobile",
        rsrp: Int? = -85,
        cid: Long? = 67890L
    ): CellMeasurement = CellMeasurement(
        timestamp = timestamp,
        latitude = 37.7749,
        longitude = -122.4194,
        radio = RadioType.LTE,
        mcc = 310,
        mnc = 260,
        tacLac = 12345,
        cid = cid,
        pciPsc = 100,
        earfcnArfcn = 5230,
        band = 7,
        rsrp = rsrp,
        rsrq = -10,
        sinr = 15,
        rssi = -65,
        isRegistered = true,
        operatorName = operatorName
    )

    private fun importString(s: String): List<CellMeasurement> =
        CsvImporter.import(StringReader(s))

    @Test
    fun `given a freshly exported CSV, when importing, then yields the same measurements`() {
        // Given
        val original = listOf(
            makeMeasurement(timestamp = 1L, operatorName = "Verizon"),
            makeMeasurement(timestamp = 2L, operatorName = "T-Mobile"),
            makeMeasurement(timestamp = 3L, operatorName = null, rsrp = null)
        )
        val exported = CsvExporter.export(original)

        // When
        val imported = importString(exported)

        // Then
        assertThat(imported).hasSize(3)
        assertThat(imported[0].timestamp).isEqualTo(1L)
        assertThat(imported[0].operatorName).isEqualTo("Verizon")
        assertThat(imported[0].rsrp).isEqualTo(-85)
        assertThat(imported[2].operatorName).isNull()
        assertThat(imported[2].rsrp).isNull()
    }

    @Test
    fun `given CSV with UTF-8 BOM, when importing, then BOM is stripped before header check`() {
        // Given
        val csv = "﻿" + CsvExporter.HEADER + "\n" +
            "1,37.0,-122.0,LTE,,,,,,,,,,,,0,\n"

        // When
        val measurements = importString(csv)

        // Then
        assertThat(measurements).hasSize(1)
    }

    @Test
    fun `given empty input, when importing, then throws EMPTY_FILE`() {
        try {
            importString("")
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.EMPTY_FILE)
        }
    }

    @Test
    fun `given CSV with wrong header, when importing, then throws UNRECOGNIZED_SCHEMA`() {
        // Given
        val csv = "lat,lon,timestamp\n37.0,-122.0,1\n"

        // When/Then
        try {
            importString(csv)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.UNRECOGNIZED_SCHEMA)
        }
    }

    @Test
    fun `given CSV row with wrong number of fields, when importing, then throws MALFORMED`() {
        // Given
        val csv = CsvExporter.HEADER + "\n1,37.0\n"

        // When/Then
        try {
            importString(csv)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.MALFORMED)
        }
    }

    @Test
    fun `given CSV row with unknown radio enum, when importing, then throws INVALID_VALUE`() {
        // Given - "FAKE" is not a valid RadioType
        val csv = CsvExporter.HEADER + "\n" +
            "1,37.0,-122.0,FAKE,,,,,,,,,,,,0,\n"

        // When/Then
        try {
            importString(csv)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.INVALID_VALUE)
        }
    }

    @Test
    fun `given CSV row with lat out of range, when importing, then throws INVALID_VALUE`() {
        // Given
        val csv = CsvExporter.HEADER + "\n" +
            "1,91.0,-122.0,LTE,,,,,,,,,,,,0,\n"

        // When/Then
        try {
            importString(csv)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.INVALID_VALUE)
        }
    }

    @Test
    fun `given CSV row with lon out of range, when importing, then throws INVALID_VALUE`() {
        // Given
        val csv = CsvExporter.HEADER + "\n" +
            "1,37.0,200.0,LTE,,,,,,,,,,,,0,\n"

        // When/Then
        try {
            importString(csv)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.INVALID_VALUE)
        }
    }

    @Test
    fun `given CSV with non-numeric mcc, when importing, then throws INVALID_VALUE`() {
        // Given - build via the exporter then poison the mcc field so the
        // field count is exactly correct.
        val original = makeMeasurement()
        val csv = CsvExporter.export(listOf(original))
        val poisoned = csv.replace(",310,260,", ",abc,260,")

        // When/Then
        try {
            importString(poisoned)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.INVALID_VALUE)
        }
    }

    @Test
    fun `given operator field with formula defuse prefix, when importing, then leading single quote is stripped`() {
        // Given - exporter would have written 'foo for original "=foo"
        val original = makeMeasurement(operatorName = "=2+2")
        val csv = CsvExporter.export(listOf(original))

        // When
        val imported = importString(csv)

        // Then
        assertThat(imported[0].operatorName).isEqualTo("=2+2")
    }

    @Test
    fun `given operator name with comma, when round-tripping, then unquoting recovers the original`() {
        // Given
        val original = makeMeasurement(operatorName = "Acme, Inc")
        val csv = CsvExporter.export(listOf(original))

        // When
        val imported = importString(csv)

        // Then
        assertThat(imported[0].operatorName).isEqualTo("Acme, Inc")
    }

    @Test
    fun `given operator name with embedded double quote, when round-tripping, then unescaping recovers the original`() {
        // Given
        val original = makeMeasurement(operatorName = "\"Sneaky\"")
        val csv = CsvExporter.export(listOf(original))

        // When
        val imported = importString(csv)

        // Then
        assertThat(imported[0].operatorName).isEqualTo("\"Sneaky\"")
    }

    @Test
    fun `given operator name with newline, when round-tripping, then newline is preserved`() {
        // Given
        val original = makeMeasurement(operatorName = "line1\nline2")
        val csv = CsvExporter.export(listOf(original))

        // When
        val imported = importString(csv)

        // Then
        assertThat(imported[0].operatorName).isEqualTo("line1\nline2")
    }

    @Test
    fun `given CSV with unclosed quoted field, when importing, then throws MALFORMED`() {
        // Given
        val csv = CsvExporter.HEADER + "\n" +
            "1,37.0,-122.0,LTE,,,,,,,,,,,,0,\"unclosed\n"

        // When/Then
        try {
            importString(csv)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.MALFORMED)
        }
    }

    @Test
    fun `given header only with no rows, when importing, then returns empty list`() {
        // Given
        val csv = CsvExporter.HEADER + "\n"

        // When
        val imported = importString(csv)

        // Then
        assertThat(imported).isEmpty()
    }

    @Test
    fun `given CSV with blank trailing lines, when importing, then those lines are ignored`() {
        // Given
        val csv = CsvExporter.HEADER + "\n" +
            "1,37.0,-122.0,LTE,,,,,,,,,,,,0,\n" +
            "\n\n"

        // When
        val imported = importString(csv)

        // Then
        assertThat(imported).hasSize(1)
    }

    @Test
    fun `given is_serving 0 and 1, when importing, then maps to false and true`() {
        // Given
        val csv = CsvExporter.HEADER + "\n" +
            "1,37.0,-122.0,LTE,,,,,,,,,,,,0,\n" +
            "2,37.0,-122.0,LTE,,,,,,,,,,,,1,\n"

        // When
        val imported = importString(csv)

        // Then
        assertThat(imported[0].isRegistered).isFalse()
        assertThat(imported[1].isRegistered).isTrue()
    }

    @Test
    fun `given is_serving with bogus value, when importing, then throws INVALID_VALUE`() {
        // Given
        val csv = CsvExporter.HEADER + "\n" +
            "1,37.0,-122.0,LTE,,,,,,,,,,,,maybe,\n"

        // When/Then
        try {
            importString(csv)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.INVALID_VALUE)
        }
    }

    @Test
    fun `given operator string longer than the cap, when importing, then throws INVALID_VALUE`() {
        // Given
        val long = "x".repeat(ImportLimits.MAX_STRING_LEN + 1)
        val csv = CsvExporter.HEADER + "\n" +
            "1,37.0,-122.0,LTE,,,,,,,,,,,,0,$long\n"

        // When/Then
        try {
            importString(csv)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.INVALID_VALUE)
        }
    }
}
