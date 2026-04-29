package com.celltowerid.android.export

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Reader

/**
 * Single entry point for restoring a backup file. Detects the format by file
 * extension, enforces a hard size cap, and dispatches to the format-specific
 * importer. Returns the parsed measurements plus the detected format so the
 * caller can report what they got.
 *
 * All errors surface as [ImportException]; partial imports are not produced.
 */
object BackupImporter {

    fun import(file: File): ImportResult {
        val format = detectFormat(file)
            ?: throw ImportException(
                "Unrecognized file extension '${file.extension}'. " +
                    "Expected one of: csv, geojson, json, kml.",
                ImportException.Reason.UNKNOWN_FORMAT
            )

        if (!file.exists() || !file.isFile) {
            throw ImportException(
                "Backup file not found: ${file.path}",
                ImportException.Reason.MALFORMED
            )
        }
        val length = file.length()
        if (length <= 0L) {
            throw ImportException(
                "Backup file is empty",
                ImportException.Reason.EMPTY_FILE
            )
        }
        if (length > ImportLimits.MAX_FILE_BYTES) {
            throw ImportException(
                "Backup file is ${length} bytes; max allowed is ${ImportLimits.MAX_FILE_BYTES}",
                ImportException.Reason.FILE_TOO_LARGE
            )
        }

        val measurements = try {
            file.inputStream().use { stream ->
                importStream(stream, format)
            }
        } catch (e: ImportException) {
            throw e
        } catch (e: IOException) {
            throw ImportException(
                "Failed to read backup file: ${e.message}",
                ImportException.Reason.MALFORMED,
                e
            )
        }

        return ImportResult(format = format, measurements = measurements)
    }

    /**
     * Visible for callers that already have a stream (e.g. content:// URIs)
     * and have validated the size out-of-band.
     */
    fun importStream(stream: InputStream, format: ExportFormat) =
        when (format) {
            ExportFormat.CSV -> CsvImporter.import(stream.reader(Charsets.UTF_8) as Reader)
            ExportFormat.GEOJSON -> GeoJsonImporter.import(stream.reader(Charsets.UTF_8) as Reader)
            ExportFormat.KML -> KmlImporter.import(stream)
        }

    fun detectFormat(file: File): ExportFormat? = when (file.extension.lowercase()) {
        "csv" -> ExportFormat.CSV
        "geojson", "json" -> ExportFormat.GEOJSON
        "kml" -> ExportFormat.KML
        else -> null
    }
}
