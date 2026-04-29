package com.celltowerid.android.export

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream

class KmlImporterTest {

    private fun makeMeasurement(
        timestamp: Long = 1700000000000L,
        operatorName: String? = "T-Mobile",
        rsrp: Int? = -85,
        radio: RadioType = RadioType.LTE
    ): CellMeasurement = CellMeasurement(
        timestamp = timestamp,
        latitude = 37.7749,
        longitude = -122.4194,
        radio = radio,
        mcc = 310,
        mnc = 260,
        tacLac = 12345,
        cid = 67890L,
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

    private fun import(s: String): List<CellMeasurement> =
        KmlImporter.import(ByteArrayInputStream(s.toByteArray(Charsets.UTF_8)))

    @Test
    fun `given a freshly exported KML, when importing, then yields the same measurements`() {
        // Given
        val original = listOf(
            makeMeasurement(timestamp = 1L, operatorName = "Verizon"),
            makeMeasurement(timestamp = 2L, operatorName = null, rsrp = null)
        )

        // When
        val imported = import(KmlExporter.export(original))

        // Then
        assertThat(imported).hasSize(2)
        assertThat(imported[0].timestamp).isEqualTo(1L)
        assertThat(imported[0].operatorName).isEqualTo("Verizon")
        assertThat(imported[0].latitude).isEqualTo(37.7749)
        assertThat(imported[0].longitude).isEqualTo(-122.4194)
        assertThat(imported[1].rsrp).isNull()
        assertThat(imported[1].operatorName).isNull()
    }

    @Test
    fun `given KML with NR radio, when round-tripping, then radio is preserved`() {
        val original = listOf(makeMeasurement(radio = RadioType.NR))
        val imported = import(KmlExporter.export(original))
        assertThat(imported[0].radio).isEqualTo(RadioType.NR)
    }

    @Test
    fun `given empty input, when importing, then throws EMPTY_FILE`() {
        try {
            import("")
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.EMPTY_FILE)
        }
    }

    @Test
    fun `given KML missing ExtendedData on every placemark, when importing, then throws UNRECOGNIZED_SCHEMA`() {
        // Given - a legacy V1 KML without ExtendedData. We can't synthesise a
        // timestamp from this, so the importer must reject it.
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>LTE 1</name>
                  <Point><coordinates>-122.0,37.0,0</coordinates></Point>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        // When/Then
        try {
            import(kml)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.UNRECOGNIZED_SCHEMA)
        }
    }

    @Test
    fun `given KML with unknown radio in ExtendedData, when importing, then throws INVALID_VALUE`() {
        // Given
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <Point><coordinates>-122.0,37.0,0</coordinates></Point>
                  <ExtendedData>
                    <Data name="timestamp"><value>1</value></Data>
                    <Data name="radio"><value>FAKE</value></Data>
                    <Data name="is_serving"><value>1</value></Data>
                  </ExtendedData>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        // When/Then
        try {
            import(kml)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.INVALID_VALUE)
        }
    }

    @Test
    fun `given KML with lat out of range, when importing, then throws INVALID_VALUE`() {
        // Given
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <Point><coordinates>0,95,0</coordinates></Point>
                  <ExtendedData>
                    <Data name="timestamp"><value>1</value></Data>
                    <Data name="radio"><value>LTE</value></Data>
                    <Data name="is_serving"><value>1</value></Data>
                  </ExtendedData>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        // When/Then
        try {
            import(kml)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.INVALID_VALUE)
        }
    }

    @Test
    fun `given KML referencing an external entity, when importing, then DOCTYPE is rejected`() {
        // Given - classic XXE attempt: DOCTYPE with file:// entity reference.
        val kml = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>&xxe;</name>
                  <Point><coordinates>0,0,0</coordinates></Point>
                  <ExtendedData>
                    <Data name="timestamp"><value>1</value></Data>
                    <Data name="radio"><value>LTE</value></Data>
                    <Data name="is_serving"><value>1</value></Data>
                  </ExtendedData>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        // When/Then - parser must throw rather than expand &xxe;.
        try {
            import(kml)
            error("expected ImportException due to DOCTYPE rejection")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.MALFORMED)
        }
    }

    @Test
    fun `given KML with no placemarks, when importing, then returns empty list`() {
        // Given
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>Empty</name>
              </Document>
            </kml>
        """.trimIndent()

        // When
        val imported = import(kml)

        // Then
        assertThat(imported).isEmpty()
    }

    @Test
    fun `given KML with placemark missing coordinates, when importing, then throws MALFORMED`() {
        // Given
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <ExtendedData>
                    <Data name="timestamp"><value>1</value></Data>
                    <Data name="radio"><value>LTE</value></Data>
                    <Data name="is_serving"><value>1</value></Data>
                  </ExtendedData>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        // When/Then
        try {
            import(kml)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.MALFORMED)
        }
    }

    @Test
    fun `given operator name with XML special chars, when round-tripping, then is preserved`() {
        // Given
        val original = listOf(makeMeasurement(operatorName = "AT&T <Wireless>"))

        // When
        val imported = import(KmlExporter.export(original))

        // Then
        assertThat(imported[0].operatorName).isEqualTo("AT&T <Wireless>")
    }

    @Test
    fun `given malformed XML, when importing, then throws MALFORMED`() {
        // Given - missing closing tag
        val kml = """
            <?xml version="1.0"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
        """.trimIndent()

        // When/Then
        try {
            import(kml)
            error("expected ImportException")
        } catch (e: ImportException) {
            assertThat(e.reason).isEqualTo(ImportException.Reason.MALFORMED)
        }
    }
}
