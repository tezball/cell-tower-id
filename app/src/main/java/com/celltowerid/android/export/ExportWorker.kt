package com.celltowerid.android.export

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.celltowerid.android.data.AppDatabase
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.repository.MeasurementRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_FORMAT = "export_format"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_PROGRESS = "progress"
        internal const val EXPORT_FILE_PREFIX = "cellid_export_"
        private val TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"

        fun buildRequest(format: ExportFormat, sessionId: Long? = null): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_FORMAT to format.name,
                KEY_SESSION_ID to (sessionId ?: -1L)
            )
            return OneTimeWorkRequestBuilder<ExportWorker>()
                .setInputData(data)
                .build()
        }

        // Pure entry point for unit tests. Returns the written file, or null if
        // there's nothing to export. Throws on I/O errors so the caller can map
        // to Result.failure().
        internal fun runExport(
            measurements: List<CellMeasurement>,
            format: ExportFormat,
            exportDir: File,
            timestamp: String
        ): File? {
            if (measurements.isEmpty()) return null
            exportDir.mkdirs()
            val outputFile = File(exportDir, "$EXPORT_FILE_PREFIX$timestamp.${format.extension}")
            when (format) {
                ExportFormat.CSV -> CsvExporter.exportToFile(measurements, outputFile)
                ExportFormat.GEOJSON -> GeoJsonExporter.exportToFile(measurements, outputFile)
                ExportFormat.KML -> KmlExporter.exportToFile(measurements, outputFile)
            }
            return outputFile
        }
    }

    override suspend fun doWork(): Result {
        val formatName = inputData.getString(KEY_FORMAT) ?: return Result.failure()
        val format = try {
            ExportFormat.valueOf(formatName)
        } catch (_: IllegalArgumentException) {
            return Result.failure()
        }
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)

        val db = AppDatabase.getInstance(applicationContext)
        val repo = MeasurementRepository(db.measurementDao())

        val measurements = if (sessionId > 0) {
            repo.getMeasurementsBySession(sessionId)
        } else {
            repo.getAllMeasurements()
        }

        setProgress(workDataOf(KEY_PROGRESS to 10))

        val timestamp = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US).format(Date())
        val exportDir = File(applicationContext.getExternalFilesDir(null), "exports")

        setProgress(workDataOf(KEY_PROGRESS to 30))

        val outputFile = runExport(measurements, format, exportDir, timestamp)
            ?: return Result.failure()

        setProgress(workDataOf(KEY_PROGRESS to 100))

        val outputData = workDataOf(KEY_OUTPUT_URI to outputFile.absolutePath)
        return Result.success(outputData)
    }
}
