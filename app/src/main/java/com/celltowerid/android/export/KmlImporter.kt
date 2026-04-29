package com.celltowerid.android.export

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * Strict reverse of [KmlExporter] (V2 format with `<ExtendedData>`).
 *
 * Each `<Placemark>` must carry an `<ExtendedData>` block holding the same
 * fields the exporter emits — the human-readable `<description>` is ignored
 * because it is lossy. Legacy KML files that lack `<ExtendedData>` are
 * rejected with [ImportException.Reason.UNRECOGNIZED_SCHEMA]; users are told
 * to re-export from a current build, or to import via CSV/GeoJSON instead.
 *
 * XML parsing disables DOCTYPE so external-entity (XXE) attacks and
 * billion-laughs amplification cannot be smuggled in via a malicious file.
 */
object KmlImporter {

    fun import(input: InputStream): List<CellMeasurement> {
        // Reject empty streams up front. ByteArrayInputStream / FileInputStream
        // both support mark/peek-style reads, so we just check the first byte.
        val firstByte = input.read()
        if (firstByte == -1) {
            throw ImportException("KML file is empty", ImportException.Reason.EMPTY_FILE)
        }
        val combined = java.io.SequenceInputStream(
            java.io.ByteArrayInputStream(byteArrayOf(firstByte.toByte())),
            input
        )

        val factory = secureFactoryOrThrow()
        val builder = try {
            factory.newDocumentBuilder()
        } catch (e: ParserConfigurationException) {
            throw ImportException(
                "KML parser unavailable: ${e.message}",
                ImportException.Reason.MALFORMED,
                e
            )
        }
        val doc = try {
            builder.parse(InputSource(combined))
        } catch (e: SAXException) {
            throw ImportException(
                "Malformed KML: ${e.message}",
                ImportException.Reason.MALFORMED,
                e
            )
        } catch (e: IOException) {
            throw ImportException(
                "Failed to read KML: ${e.message}",
                ImportException.Reason.MALFORMED,
                e
            )
        }

        val root = doc.documentElement
            ?: throw ImportException("KML missing root element", ImportException.Reason.UNRECOGNIZED_SCHEMA)
        if (root.localName?.lowercase() != "kml" && root.tagName.lowercase() != "kml") {
            throw ImportException(
                "Root element must be <kml>, got <${root.tagName}>",
                ImportException.Reason.UNRECOGNIZED_SCHEMA
            )
        }

        val placemarks = doc.getElementsByTagNameNS("*", "Placemark")
        val results = ArrayList<CellMeasurement>(placemarks.length)
        for (i in 0 until placemarks.length) {
            if (results.size >= ImportLimits.MAX_ROWS) {
                throw ImportException(
                    "KML exceeds row cap of ${ImportLimits.MAX_ROWS}",
                    ImportException.Reason.TOO_MANY_ROWS
                )
            }
            results.add(parsePlacemark(placemarks.item(i) as Element, i))
        }
        return results
    }

    private fun secureFactoryOrThrow(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        // Belt and braces: every flag below is a known XXE vector. Setting
        // disallow-doctype-decl alone is sufficient for parsers that honour
        // it; the rest are no-ops on those parsers and useful elsewhere.
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (e: ParserConfigurationException) {
            throw ImportException(
                "XML parser does not support disabling DOCTYPE",
                ImportException.Reason.MALFORMED,
                e
            )
        }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        factory.isNamespaceAware = true
        return factory
    }

    private fun parsePlacemark(placemark: Element, index: Int): CellMeasurement {
        val coords = extractCoordinates(placemark, index)
        val data = extractExtendedData(placemark, index)

        val timestampStr = data["timestamp"]
            ?: throw ImportException(
                "Placemark $index: missing 'timestamp' in ExtendedData",
                ImportException.Reason.INVALID_VALUE
            )
        val timestamp = timestampStr.toLongOrNull()
            ?: throw ImportException(
                "Placemark $index: invalid timestamp '$timestampStr'",
                ImportException.Reason.INVALID_VALUE
            )
        val radioStr = data["radio"]
            ?: throw ImportException(
                "Placemark $index: missing 'radio' in ExtendedData",
                ImportException.Reason.INVALID_VALUE
            )
        val radio = RadioType.entries.firstOrNull { it.name == radioStr }
            ?: throw ImportException(
                "Placemark $index: unknown radio '$radioStr'",
                ImportException.Reason.INVALID_VALUE
            )
        val isServing = parseBoolStrict(data["is_serving"], "is_serving", index)

        val (lon, lat) = coords
        if (lat.isNaN() || lat < -90.0 || lat > 90.0) {
            throw ImportException(
                "Placemark $index: lat $lat out of range [-90, 90]",
                ImportException.Reason.INVALID_VALUE
            )
        }
        if (lon.isNaN() || lon < -180.0 || lon > 180.0) {
            throw ImportException(
                "Placemark $index: lon $lon out of range [-180, 180]",
                ImportException.Reason.INVALID_VALUE
            )
        }

        val operator = data["operator"]?.also {
            if (it.length > ImportLimits.MAX_STRING_LEN) {
                throw ImportException(
                    "Placemark $index: operator longer than ${ImportLimits.MAX_STRING_LEN} chars",
                    ImportException.Reason.INVALID_VALUE
                )
            }
        }

        return CellMeasurement(
            timestamp = timestamp,
            latitude = lat,
            longitude = lon,
            radio = radio,
            mcc = parseIntOrNull(data["mcc"], "mcc", index),
            mnc = parseIntOrNull(data["mnc"], "mnc", index),
            tacLac = parseIntOrNull(data["tac"], "tac", index),
            cid = parseLongOrNull(data["cid"], "cid", index),
            pciPsc = parseIntOrNull(data["pci"], "pci", index),
            earfcnArfcn = parseIntOrNull(data["earfcn"], "earfcn", index),
            band = parseIntOrNull(data["band"], "band", index),
            rsrp = parseIntOrNull(data["rsrp"], "rsrp", index),
            rsrq = parseIntOrNull(data["rsrq"], "rsrq", index),
            sinr = parseIntOrNull(data["sinr"], "sinr", index),
            rssi = parseIntOrNull(data["rssi"], "rssi", index),
            isRegistered = isServing,
            operatorName = operator
        )
    }

    /** Returns (lon, lat) from the first <coordinates> within this Placemark. */
    private fun extractCoordinates(placemark: Element, index: Int): Pair<Double, Double> {
        val coordsList = placemark.getElementsByTagNameNS("*", "coordinates")
        if (coordsList.length == 0) {
            throw ImportException(
                "Placemark $index: missing <coordinates>",
                ImportException.Reason.MALFORMED
            )
        }
        val raw = coordsList.item(0).textContent?.trim().orEmpty()
        if (raw.isEmpty()) {
            throw ImportException(
                "Placemark $index: empty <coordinates>",
                ImportException.Reason.MALFORMED
            )
        }
        // KML coordinates are "lon,lat[,alt]". Whitespace separates multiple
        // tuples for line/polygon geometries; we only care about the first.
        val firstTuple = raw.split(Regex("\\s+")).first()
        val parts = firstTuple.split(',')
        if (parts.size < 2) {
            throw ImportException(
                "Placemark $index: <coordinates> must be 'lon,lat[,alt]'",
                ImportException.Reason.MALFORMED
            )
        }
        val lon = parts[0].toDoubleOrNull()
            ?: throw ImportException(
                "Placemark $index: invalid lon '${parts[0]}'",
                ImportException.Reason.INVALID_VALUE
            )
        val lat = parts[1].toDoubleOrNull()
            ?: throw ImportException(
                "Placemark $index: invalid lat '${parts[1]}'",
                ImportException.Reason.INVALID_VALUE
            )
        return lon to lat
    }

    private fun extractExtendedData(placemark: Element, index: Int): Map<String, String> {
        val list = placemark.getElementsByTagNameNS("*", "ExtendedData")
        if (list.length == 0) {
            throw ImportException(
                "Placemark $index: missing <ExtendedData>; cannot import a KML file " +
                    "lacking machine-readable data. Re-export from a current build, " +
                    "or import via CSV or GeoJSON instead.",
                ImportException.Reason.UNRECOGNIZED_SCHEMA
            )
        }
        val ext = list.item(0) as Element
        val data = HashMap<String, String>()
        val children = ext.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val el = node as Element
            if (el.localName?.lowercase() != "data" && el.tagName.lowercase() != "data") continue
            val name = el.getAttribute("name")
            if (name.isNullOrEmpty()) continue
            val valueNodes = el.getElementsByTagNameNS("*", "value")
            if (valueNodes.length == 0) continue
            val value = valueNodes.item(0).textContent ?: ""
            data[name] = value
        }
        return data
    }

    private fun parseIntOrNull(s: String?, field: String, index: Int): Int? {
        if (s.isNullOrEmpty()) return null
        return s.toIntOrNull() ?: throw ImportException(
            "Placemark $index: invalid $field '$s'",
            ImportException.Reason.INVALID_VALUE
        )
    }

    private fun parseLongOrNull(s: String?, field: String, index: Int): Long? {
        if (s.isNullOrEmpty()) return null
        return s.toLongOrNull() ?: throw ImportException(
            "Placemark $index: invalid $field '$s'",
            ImportException.Reason.INVALID_VALUE
        )
    }

    private fun parseBoolStrict(s: String?, field: String, index: Int): Boolean = when (s) {
        "1", "true" -> true
        "0", "false" -> false
        null -> throw ImportException(
            "Placemark $index: missing '$field'",
            ImportException.Reason.INVALID_VALUE
        )
        else -> throw ImportException(
            "Placemark $index: invalid $field '$s'",
            ImportException.Reason.INVALID_VALUE
        )
    }
}
