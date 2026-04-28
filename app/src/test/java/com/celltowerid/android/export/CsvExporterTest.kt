package com.celltowerid.android.export

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvExporterTest {

    private fun makeLteMeasurement(
        timestamp: Long = 1700000000000L,
        lat: Double = 37.7749,
        lon: Double = -122.4194,
        mcc: Int? = 310,
        mnc: Int? = 260,
        tacLac: Int? = 12345,
        cid: Long? = 67890L,
        pciPsc: Int? = 100,
        earfcnArfcn: Int? = 5230,
        band: Int? = 7,
        rsrp: Int? = -85,
        rsrq: Int? = -10,
        sinr: Int? = 15,
        rssi: Int? = -65,
        isRegistered: Boolean = true,
        operatorName: String? = "T-Mobile"
    ): CellMeasurement = CellMeasurement(
        timestamp = timestamp,
        latitude = lat,
        longitude = lon,
        radio = RadioType.LTE,
        mcc = mcc,
        mnc = mnc,
        tacLac = tacLac,
        cid = cid,
        pciPsc = pciPsc,
        earfcnArfcn = earfcnArfcn,
        band = band,
        rsrp = rsrp,
        rsrq = rsrq,
        sinr = sinr,
        rssi = rssi,
        isRegistered = isRegistered,
        operatorName = operatorName
    )

    @Test
    fun `given list of measurements when exporting to CSV then output starts with BOM and header`() {
        val measurements = listOf(makeLteMeasurement())

        val csv = CsvExporter.export(measurements)

        assertThat(csv.first()).isEqualTo('\uFEFF')
        val firstLine = csv.removePrefix("\uFEFF").lines().first()
        assertThat(firstLine).isEqualTo(CsvExporter.HEADER)
    }

    @Test
    fun `given LTE measurement when exporting then row contains all fields`() {
        val m = makeLteMeasurement()

        val csv = CsvExporter.export(listOf(m))

        val lines = csv.trim().lines()
        assertThat(lines).hasSize(2) // header + 1 row
        val row = lines[1]
        assertThat(row).contains("1700000000000")
        assertThat(row).contains("37.7749")
        assertThat(row).contains("-122.4194")
        assertThat(row).contains("LTE")
        assertThat(row).contains("310")
        assertThat(row).contains("260")
        assertThat(row).contains("12345")
        assertThat(row).contains("67890")
        assertThat(row).contains("100")
        assertThat(row).contains("5230")
        assertThat(row).contains("7")
        assertThat(row).contains("-85")
        assertThat(row).contains("-10")
        assertThat(row).contains("15")
        assertThat(row).contains("-65")
        assertThat(row).contains(",1,") // is_serving = true -> 1
        assertThat(row).contains("T-Mobile")
    }

    @Test
    fun `given empty list when exporting then returns header only`() {
        val csv = CsvExporter.export(emptyList())

        val lines = csv.removePrefix("\uFEFF").trim().lines()
        assertThat(lines).hasSize(1)
        assertThat(lines[0]).isEqualTo(CsvExporter.HEADER)
    }

    @Test
    fun `given measurement with nulls when exporting then uses empty strings`() {
        val m = CellMeasurement(
            timestamp = 1700000000000L,
            latitude = 37.0,
            longitude = -122.0,
            radio = RadioType.LTE,
            mcc = null,
            mnc = null,
            tacLac = null,
            cid = null,
            pciPsc = null,
            earfcnArfcn = null,
            band = null,
            rsrp = null,
            rsrq = null,
            sinr = null,
            rssi = null,
            isRegistered = false,
            operatorName = null
        )

        val row = CsvExporter.buildCsvRow(m)

        // After LTE there should be consecutive commas for null fields
        // 17 fields: timestamp,lat,lon,radio,mcc,mnc,tac,cid,pci,earfcn,band,rsrp,rsrq,sinr,rssi,is_serving,operator
        assertThat(row).isEqualTo("1700000000000,37.0,-122.0,LTE,,,,,,,,,,,,0,")
    }

    @Test
    fun `given multiple measurements when exporting then all rows present`() {
        val measurements = listOf(
            makeLteMeasurement(timestamp = 1L),
            makeLteMeasurement(timestamp = 2L),
            makeLteMeasurement(timestamp = 3L)
        )

        val csv = CsvExporter.export(measurements)

        val lines = csv.removePrefix("\uFEFF").trim().lines()
        assertThat(lines).hasSize(4) // header + 3 rows
    }

    @Test
    fun `given operator name starting with equals, when exporting, then prefixed with single quote to defuse formula`() {
        val m = makeLteMeasurement(operatorName = "=2+2")

        val row = CsvExporter.buildCsvRow(m)

        assertThat(row.endsWith(",'=2+2")).isTrue()
    }

    @Test
    fun `given operator name with leading dash, when exporting, then prefixed with single quote`() {
        val m = makeLteMeasurement(operatorName = "-cmd")

        val row = CsvExporter.buildCsvRow(m)

        assertThat(row.endsWith(",'-cmd")).isTrue()
    }

    @Test
    fun `given operator name with leading at sign, when exporting, then prefixed with single quote`() {
        val m = makeLteMeasurement(operatorName = "@SUM(A1:A9)")

        val row = CsvExporter.buildCsvRow(m)

        assertThat(row.endsWith(",'@SUM(A1:A9)")).isTrue()
    }

    @Test
    fun `given operator name containing comma, when exporting, then field is double-quoted`() {
        val m = makeLteMeasurement(operatorName = "Acme, Inc")

        val row = CsvExporter.buildCsvRow(m)

        assertThat(row.endsWith(",\"Acme, Inc\"")).isTrue()
    }

    @Test
    fun `given operator name containing double quote, when exporting, then quote is escaped`() {
        val m = makeLteMeasurement(operatorName = "\"Sneaky\"")

        val row = CsvExporter.buildCsvRow(m)

        assertThat(row.endsWith(",\"\"\"Sneaky\"\"\"")).isTrue()
    }

    @Test
    fun `given operator name containing newline, when exporting, then field is double-quoted`() {
        val m = makeLteMeasurement(operatorName = "line1\nline2")

        val row = CsvExporter.buildCsvRow(m)

        assertThat(row.endsWith(",\"line1\nline2\"")).isTrue()
    }

    @Test
    fun `given normal operator name, when exporting, then no quoting is applied`() {
        val m = makeLteMeasurement(operatorName = "T-Mobile")

        val row = CsvExporter.buildCsvRow(m)

        // T-Mobile starts with 'T', not a formula trigger -- no defuse, no quote.
        assertThat(row.endsWith(",T-Mobile")).isTrue()
    }
}
