package com.celltowerid.android.export

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import java.io.PushbackReader
import java.io.Reader

/**
 * Strict reverse of [CsvExporter]. Accepts only headers in [SUPPORTED_HEADERS]
 * and rejects malformed rows; partial imports are not allowed.
 *
 * To add a new export schema version: ship the new header from CsvExporter,
 * keep the old one in [SUPPORTED_HEADERS], and branch on which header matched
 * inside [rowToMeasurement].
 */
object CsvImporter {

    private const val UTF8_BOM = '\uFEFF'

    private val SUPPORTED_HEADERS: Set<String> = setOf(CsvExporter.HEADER)

    // Mirror CsvExporter.FORMULA_TRIGGERS so we can undo the leading
    // single-quote defuse on round-trip.
    private val FORMULA_TRIGGERS = setOf('=', '+', '-', '@', '\t', '\r')

    private const val EXPECTED_FIELD_COUNT = 17

    // Cap the working buffer for any single record. CSV cells can technically
    // be arbitrary length, but our schema's only string field (operator name)
    // is bounded by MAX_STRING_LEN; allow some headroom for quoting overhead.
    private const val MAX_RECORD_CHARS = ImportLimits.MAX_STRING_LEN * 4

    fun import(reader: Reader): List<CellMeasurement> {
        val pr = PushbackReader(reader, 1)
        val header = readRecord(pr, recordIndex = 1)
            ?: throw ImportException("CSV file is empty", ImportException.Reason.EMPTY_FILE)

        // Strip UTF-8 BOM if present on the very first character of the first
        // header field. Reading via Reader gives us the BOM as U+FEFF.
        val normalisedHeader = if (header.isNotEmpty() && header[0].startsWith(UTF8_BOM.toString())) {
            listOf(header[0].substring(1)) + header.drop(1)
        } else {
            header
        }
        val headerLine = normalisedHeader.joinToString(",")
        if (headerLine !in SUPPORTED_HEADERS) {
            throw ImportException(
                "Unrecognized CSV header: $headerLine",
                ImportException.Reason.UNRECOGNIZED_SCHEMA
            )
        }

        val measurements = ArrayList<CellMeasurement>()
        var recordIndex = 1
        while (true) {
            recordIndex++
            val fields = readRecord(pr, recordIndex) ?: break
            // Skip blank trailing lines (a single empty field is what a bare
            // newline looks like in our parser).
            if (fields.size == 1 && fields[0].isEmpty()) continue
            if (measurements.size >= ImportLimits.MAX_ROWS) {
                throw ImportException(
                    "CSV exceeds row cap of ${ImportLimits.MAX_ROWS}",
                    ImportException.Reason.TOO_MANY_ROWS
                )
            }
            measurements.add(rowToMeasurement(fields, recordIndex))
        }
        return measurements
    }

    /**
     * Reads one RFC 4180 record (one logical row) and returns its fields, or
     * null at EOF. Quoted fields may contain commas and newlines; doubled
     * quotes inside a quoted field collapse to a single literal quote.
     */
    private fun readRecord(pr: PushbackReader, recordIndex: Int): List<String>? {
        val fields = ArrayList<String>(EXPECTED_FIELD_COUNT)
        val sb = StringBuilder()
        var inQuotes = false
        var quotedField = false
        var anyCharRead = false

        while (true) {
            val next = pr.read()
            if (next == -1) break
            anyCharRead = true
            val ch = next.toChar()

            if (sb.length > MAX_RECORD_CHARS) {
                throw ImportException(
                    "Record $recordIndex exceeds max length",
                    ImportException.Reason.MALFORMED
                )
            }

            if (inQuotes) {
                if (ch == '"') {
                    val peek = pr.read()
                    if (peek == '"'.code) {
                        sb.append('"')
                    } else {
                        inQuotes = false
                        if (peek != -1) pr.unread(peek)
                    }
                } else {
                    sb.append(ch)
                }
            } else {
                when (ch) {
                    ',' -> {
                        fields.add(sb.toString())
                        sb.setLength(0)
                        quotedField = false
                    }
                    '"' -> {
                        if (sb.isNotEmpty() || quotedField) {
                            throw ImportException(
                                "Record $recordIndex: stray quote in field",
                                ImportException.Reason.MALFORMED
                            )
                        }
                        inQuotes = true
                        quotedField = true
                    }
                    '\r' -> {
                        // CRLF: consume the LF if present.
                        val peek = pr.read()
                        if (peek != '\n'.code && peek != -1) pr.unread(peek)
                        fields.add(sb.toString())
                        return fields
                    }
                    '\n' -> {
                        fields.add(sb.toString())
                        return fields
                    }
                    else -> {
                        sb.append(ch)
                    }
                }
            }
        }

        if (inQuotes) {
            throw ImportException(
                "Record $recordIndex: unclosed quoted field at end of file",
                ImportException.Reason.MALFORMED
            )
        }
        if (!anyCharRead) return null
        fields.add(sb.toString())
        return fields
    }

    // Field order from CsvExporter.HEADER:
    //  0 timestamp  1 lat  2 lon  3 radio  4 mcc  5 mnc  6 tac  7 cid  8 pci
    //  9 earfcn  10 band  11 rsrp  12 rsrq  13 sinr  14 rssi  15 is_serving  16 operator
    private fun rowToMeasurement(fields: List<String>, recordIndex: Int): CellMeasurement {
        if (fields.size != EXPECTED_FIELD_COUNT) {
            throw ImportException(
                "Record $recordIndex: expected $EXPECTED_FIELD_COUNT fields, got ${fields.size}",
                ImportException.Reason.MALFORMED
            )
        }

        val timestamp = parseLongRequired(fields[0], "timestamp", recordIndex)
        val lat = parseDoubleRequired(fields[1], "lat", recordIndex)
        if (lat.isNaN() || lat < -90.0 || lat > 90.0) {
            throw ImportException(
                "Record $recordIndex: lat $lat out of range [-90, 90]",
                ImportException.Reason.INVALID_VALUE
            )
        }
        val lon = parseDoubleRequired(fields[2], "lon", recordIndex)
        if (lon.isNaN() || lon < -180.0 || lon > 180.0) {
            throw ImportException(
                "Record $recordIndex: lon $lon out of range [-180, 180]",
                ImportException.Reason.INVALID_VALUE
            )
        }
        val radio = parseRadio(fields[3], recordIndex)
        val mcc = parseIntOrNull(fields[4], "mcc", recordIndex)
        val mnc = parseIntOrNull(fields[5], "mnc", recordIndex)
        val tacLac = parseIntOrNull(fields[6], "tac", recordIndex)
        val cid = parseLongOrNull(fields[7], "cid", recordIndex)
        val pci = parseIntOrNull(fields[8], "pci", recordIndex)
        val earfcn = parseIntOrNull(fields[9], "earfcn", recordIndex)
        val band = parseIntOrNull(fields[10], "band", recordIndex)
        val rsrp = parseIntOrNull(fields[11], "rsrp", recordIndex)
        val rsrq = parseIntOrNull(fields[12], "rsrq", recordIndex)
        val sinr = parseIntOrNull(fields[13], "sinr", recordIndex)
        val rssi = parseIntOrNull(fields[14], "rssi", recordIndex)
        val isServing = parseBoolStrict(fields[15], "is_serving", recordIndex)

        val operatorRaw = fields[16]
        val operator = if (operatorRaw.isEmpty()) {
            null
        } else {
            if (operatorRaw.length > ImportLimits.MAX_STRING_LEN) {
                throw ImportException(
                    "Record $recordIndex: operator longer than ${ImportLimits.MAX_STRING_LEN} chars",
                    ImportException.Reason.INVALID_VALUE
                )
            }
            stripDefuse(operatorRaw)
        }

        return CellMeasurement(
            timestamp = timestamp,
            latitude = lat,
            longitude = lon,
            radio = radio,
            mcc = mcc,
            mnc = mnc,
            tacLac = tacLac,
            cid = cid,
            pciPsc = pci,
            earfcnArfcn = earfcn,
            band = band,
            rsrp = rsrp,
            rsrq = rsrq,
            sinr = sinr,
            rssi = rssi,
            isRegistered = isServing,
            operatorName = operator
        )
    }

    private fun parseRadio(s: String, recordIndex: Int): RadioType {
        // Strict: only canonical names from RadioType.entries. UNKNOWN is
        // accepted because the exporter can emit it.
        return RadioType.entries.firstOrNull { it.name == s }
            ?: throw ImportException(
                "Record $recordIndex: unknown radio '$s'",
                ImportException.Reason.INVALID_VALUE
            )
    }

    private fun parseLongRequired(s: String, field: String, recordIndex: Int): Long =
        s.takeIf { it.isNotEmpty() }?.toLongOrNull()
            ?: throw ImportException(
                "Record $recordIndex: '$s' is not a valid $field",
                ImportException.Reason.INVALID_VALUE
            )

    private fun parseDoubleRequired(s: String, field: String, recordIndex: Int): Double =
        s.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            ?: throw ImportException(
                "Record $recordIndex: '$s' is not a valid $field",
                ImportException.Reason.INVALID_VALUE
            )

    private fun parseIntOrNull(s: String, field: String, recordIndex: Int): Int? {
        if (s.isEmpty()) return null
        return s.toIntOrNull() ?: throw ImportException(
            "Record $recordIndex: '$s' is not a valid $field",
            ImportException.Reason.INVALID_VALUE
        )
    }

    private fun parseLongOrNull(s: String, field: String, recordIndex: Int): Long? {
        if (s.isEmpty()) return null
        return s.toLongOrNull() ?: throw ImportException(
            "Record $recordIndex: '$s' is not a valid $field",
            ImportException.Reason.INVALID_VALUE
        )
    }

    private fun parseBoolStrict(s: String, field: String, recordIndex: Int): Boolean = when (s) {
        "1", "true" -> true
        "0", "false" -> false
        else -> throw ImportException(
            "Record $recordIndex: '$s' is not a valid $field (expected 0 or 1)",
            ImportException.Reason.INVALID_VALUE
        )
    }

    // Undo CsvExporter's formula-injection defuse so a round-trip returns the
    // original value. Strip the leading apostrophe only when it sits in front
    // of one of the trigger characters the exporter would have defused.
    internal fun stripDefuse(s: String): String =
        if (s.length >= 2 && s[0] == '\'' && s[1] in FORMULA_TRIGGERS) {
            s.substring(1)
        } else {
            s
        }
}
