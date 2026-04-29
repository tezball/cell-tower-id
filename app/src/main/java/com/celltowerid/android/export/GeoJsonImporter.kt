package com.celltowerid.android.export

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.google.gson.JsonSyntaxException
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.PushbackReader
import java.io.Reader

/**
 * Strict reverse of [GeoJsonExporter]. Accepts a top-level FeatureCollection
 * with Point features whose properties match our exported schema. Trees are
 * streamed via [JsonReader] so we never materialise the full document.
 */
object GeoJsonImporter {

    private const val FC_TYPE = "FeatureCollection"
    private const val FEATURE_TYPE = "Feature"
    private const val POINT_TYPE = "Point"

    fun import(reader: Reader): List<CellMeasurement> {
        val pr = PushbackReader(reader, 1)
        // Reject empty/whitespace-only documents up front so the caller gets
        // EMPTY_FILE rather than a JSON-parser MALFORMED.
        var c: Int
        do {
            c = pr.read()
            if (c == -1) {
                throw ImportException("GeoJSON file is empty", ImportException.Reason.EMPTY_FILE)
            }
        } while (c.toChar().isWhitespace())
        pr.unread(c)

        return JsonReader(pr).use { jr ->
            jr.strictness = Strictness.STRICT
            try {
                if (jr.peek() != JsonToken.BEGIN_OBJECT) {
                    throw ImportException(
                        "GeoJSON top level must be an object",
                        ImportException.Reason.UNRECOGNIZED_SCHEMA
                    )
                }
                parseFeatureCollection(jr)
            } catch (e: ImportException) {
                throw e
            } catch (e: JsonSyntaxException) {
                throw ImportException(
                    "Malformed GeoJSON: ${e.message}",
                    ImportException.Reason.MALFORMED,
                    e
                )
            } catch (e: IOException) {
                throw ImportException(
                    "Malformed GeoJSON: ${e.message}",
                    ImportException.Reason.MALFORMED,
                    e
                )
            } catch (e: IllegalStateException) {
                throw ImportException(
                    "Malformed GeoJSON: ${e.message}",
                    ImportException.Reason.MALFORMED,
                    e
                )
            } catch (e: NumberFormatException) {
                throw ImportException(
                    "Malformed GeoJSON number: ${e.message}",
                    ImportException.Reason.INVALID_VALUE,
                    e
                )
            }
        }
    }

    private fun parseFeatureCollection(jr: JsonReader): List<CellMeasurement> {
        var typeSeen: String? = null
        var measurements: List<CellMeasurement>? = null

        jr.beginObject()
        while (jr.hasNext()) {
            when (jr.nextName()) {
                "type" -> typeSeen = jr.nextString()
                "features" -> measurements = parseFeatures(jr)
                else -> jr.skipValue()
            }
        }
        jr.endObject()

        if (typeSeen != FC_TYPE) {
            throw ImportException(
                "Top-level type must be '$FC_TYPE', got '${typeSeen ?: "<missing>"}'",
                ImportException.Reason.UNRECOGNIZED_SCHEMA
            )
        }
        return measurements
            ?: throw ImportException(
                "Missing 'features' array",
                ImportException.Reason.UNRECOGNIZED_SCHEMA
            )
    }

    private fun parseFeatures(jr: JsonReader): List<CellMeasurement> {
        if (jr.peek() != JsonToken.BEGIN_ARRAY) {
            throw ImportException(
                "'features' must be an array",
                ImportException.Reason.UNRECOGNIZED_SCHEMA
            )
        }
        val results = ArrayList<CellMeasurement>()
        jr.beginArray()
        var index = 0
        while (jr.hasNext()) {
            if (results.size >= ImportLimits.MAX_ROWS) {
                throw ImportException(
                    "GeoJSON exceeds row cap of ${ImportLimits.MAX_ROWS}",
                    ImportException.Reason.TOO_MANY_ROWS
                )
            }
            results.add(parseFeature(jr, index))
            index++
        }
        jr.endArray()
        return results
    }

    private fun parseFeature(jr: JsonReader, index: Int): CellMeasurement {
        var typeSeen: String? = null
        var lat: Double? = null
        var lon: Double? = null
        var properties: FeatureProperties? = null

        jr.beginObject()
        while (jr.hasNext()) {
            when (jr.nextName()) {
                "type" -> typeSeen = jr.nextString()
                "geometry" -> {
                    val coords = parseGeometry(jr, index)
                    lon = coords.first
                    lat = coords.second
                }
                "properties" -> properties = parseProperties(jr, index)
                else -> jr.skipValue()
            }
        }
        jr.endObject()

        if (typeSeen != FEATURE_TYPE) {
            throw ImportException(
                "Feature $index: type must be '$FEATURE_TYPE'",
                ImportException.Reason.MALFORMED
            )
        }
        val props = properties
            ?: throw ImportException(
                "Feature $index: missing 'properties'",
                ImportException.Reason.MALFORMED
            )
        val resolvedLat = lat
            ?: throw ImportException(
                "Feature $index: missing geometry coordinates",
                ImportException.Reason.MALFORMED
            )
        val resolvedLon = lon!!

        if (resolvedLat.isNaN() || resolvedLat < -90.0 || resolvedLat > 90.0) {
            throw ImportException(
                "Feature $index: lat $resolvedLat out of range [-90, 90]",
                ImportException.Reason.INVALID_VALUE
            )
        }
        if (resolvedLon.isNaN() || resolvedLon < -180.0 || resolvedLon > 180.0) {
            throw ImportException(
                "Feature $index: lon $resolvedLon out of range [-180, 180]",
                ImportException.Reason.INVALID_VALUE
            )
        }

        return CellMeasurement(
            timestamp = props.timestamp
                ?: throw ImportException(
                    "Feature $index: missing 'timestamp'",
                    ImportException.Reason.INVALID_VALUE
                ),
            latitude = resolvedLat,
            longitude = resolvedLon,
            radio = props.radio
                ?: throw ImportException(
                    "Feature $index: missing 'radio'",
                    ImportException.Reason.INVALID_VALUE
                ),
            mcc = props.mcc,
            mnc = props.mnc,
            tacLac = props.tac,
            cid = props.cid,
            pciPsc = props.pci,
            earfcnArfcn = props.earfcn,
            band = props.band,
            rsrp = props.rsrp,
            rsrq = props.rsrq,
            sinr = props.sinr,
            rssi = props.rssi,
            isRegistered = props.isServing ?: false,
            operatorName = props.operator
        )
    }

    /** Returns (lon, lat). */
    private fun parseGeometry(jr: JsonReader, index: Int): Pair<Double, Double> {
        if (jr.peek() != JsonToken.BEGIN_OBJECT) {
            throw ImportException(
                "Feature $index: geometry must be an object",
                ImportException.Reason.MALFORMED
            )
        }
        var typeSeen: String? = null
        var coordinates: Pair<Double, Double>? = null
        jr.beginObject()
        while (jr.hasNext()) {
            when (jr.nextName()) {
                "type" -> typeSeen = jr.nextString()
                "coordinates" -> coordinates = parsePointCoordinates(jr, index)
                else -> jr.skipValue()
            }
        }
        jr.endObject()

        if (typeSeen != POINT_TYPE) {
            throw ImportException(
                "Feature $index: only Point geometry is supported, got '${typeSeen ?: "<missing>"}'",
                ImportException.Reason.MALFORMED
            )
        }
        return coordinates
            ?: throw ImportException(
                "Feature $index: geometry missing 'coordinates'",
                ImportException.Reason.MALFORMED
            )
    }

    /** GeoJSON Point coordinates are [longitude, latitude] (with optional altitude). */
    private fun parsePointCoordinates(jr: JsonReader, index: Int): Pair<Double, Double> {
        if (jr.peek() != JsonToken.BEGIN_ARRAY) {
            throw ImportException(
                "Feature $index: coordinates must be an array",
                ImportException.Reason.MALFORMED
            )
        }
        jr.beginArray()
        if (!jr.hasNext()) throw ImportException(
            "Feature $index: coordinates empty",
            ImportException.Reason.MALFORMED
        )
        val lon = jr.nextDouble()
        if (!jr.hasNext()) throw ImportException(
            "Feature $index: coordinates missing latitude",
            ImportException.Reason.MALFORMED
        )
        val lat = jr.nextDouble()
        // Skip optional altitude / extras.
        while (jr.hasNext()) jr.skipValue()
        jr.endArray()
        return lon to lat
    }

    private data class FeatureProperties(
        val timestamp: Long?,
        val radio: RadioType?,
        val mcc: Int?,
        val mnc: Int?,
        val tac: Int?,
        val cid: Long?,
        val pci: Int?,
        val earfcn: Int?,
        val band: Int?,
        val rsrp: Int?,
        val rsrq: Int?,
        val sinr: Int?,
        val rssi: Int?,
        val isServing: Boolean?,
        val operator: String?
    )

    private fun parseProperties(jr: JsonReader, index: Int): FeatureProperties {
        if (jr.peek() != JsonToken.BEGIN_OBJECT) {
            throw ImportException(
                "Feature $index: properties must be an object",
                ImportException.Reason.MALFORMED
            )
        }
        var timestamp: Long? = null
        var radio: RadioType? = null
        var mcc: Int? = null
        var mnc: Int? = null
        var tac: Int? = null
        var cid: Long? = null
        var pci: Int? = null
        var earfcn: Int? = null
        var band: Int? = null
        var rsrp: Int? = null
        var rsrq: Int? = null
        var sinr: Int? = null
        var rssi: Int? = null
        var isServing: Boolean? = null
        var operator: String? = null

        jr.beginObject()
        while (jr.hasNext()) {
            val key = jr.nextName()
            if (jr.peek() == JsonToken.NULL) {
                jr.nextNull()
                continue
            }
            when (key) {
                "timestamp" -> timestamp = jr.nextLong()
                "radio" -> radio = parseRadioStrict(jr.nextString(), index)
                "mcc" -> mcc = jr.nextInt()
                "mnc" -> mnc = jr.nextInt()
                "tac" -> tac = jr.nextInt()
                "cid" -> cid = jr.nextLong()
                "pci" -> pci = jr.nextInt()
                "earfcn" -> earfcn = jr.nextInt()
                "band" -> band = jr.nextInt()
                "rsrp" -> rsrp = jr.nextInt()
                "rsrq" -> rsrq = jr.nextInt()
                "sinr" -> sinr = jr.nextInt()
                "rssi" -> rssi = jr.nextInt()
                "is_serving" -> isServing = jr.nextBoolean()
                "operator" -> {
                    val s = jr.nextString()
                    if (s.length > ImportLimits.MAX_STRING_LEN) {
                        throw ImportException(
                            "Feature $index: operator longer than ${ImportLimits.MAX_STRING_LEN} chars",
                            ImportException.Reason.INVALID_VALUE
                        )
                    }
                    operator = s
                }
                // Ignore exporter-derived or future fields.
                else -> jr.skipValue()
            }
        }
        jr.endObject()

        return FeatureProperties(
            timestamp, radio, mcc, mnc, tac, cid, pci, earfcn, band,
            rsrp, rsrq, sinr, rssi, isServing, operator
        )
    }

    private fun parseRadioStrict(value: String, index: Int): RadioType {
        return RadioType.entries.firstOrNull { it.name == value }
            ?: throw ImportException(
                "Feature $index: unknown radio '$value'",
                ImportException.Reason.INVALID_VALUE
            )
    }
}
