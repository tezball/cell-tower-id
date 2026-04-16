package com.terrycollins.celltowerid.export

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.terrycollins.celltowerid.data.AppDatabase
import com.terrycollins.celltowerid.repository.MeasurementRepository
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

        fun buildRequest(format: ExportFormat, sessionId: Long? = null): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_FORMAT to format.name,
                KEY_SESSION_ID to (sessionId ?: -1L)
            )
            return OneTimeWorkRequestBuilder<ExportWorker>()
                .setInputData(data)
                .build()
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

        if (measurements.isEmpty()) return Result.failure()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "cellid_export_$timestamp.${format.extension}"
        val exportDir = File(applicationContext.getExternalFilesDir(null), "exports")
        exportDir.mkdirs()
        val outputFile = File(exportDir, fileName)

        when (format) {
            ExportFormat.CSV -> CsvExporter.exportToFile(measurements, outputFile)
            ExportFormat.GEOJSON -> GeoJsonExporter.exportToFile(measurements, outputFile)
            ExportFormat.KML -> KmlExporter.exportToFile(measurements, outputFile)
        }

        val outputData = workDataOf(KEY_OUTPUT_URI to outputFile.absolutePath)
        return Result.success(outputData)
    }
}
