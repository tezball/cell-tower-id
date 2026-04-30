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
import com.celltowerid.android.util.Preferences
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
        const val KEY_BACKUP_LOCATION_URI = "backup_location_uri"
        const val KEY_BACKUP_STATUS = "backup_status"
        const val KEY_BACKUP_NAME = "backup_name"

        const val BACKUP_STATUS_SUCCESS = "SUCCESS"
        const val BACKUP_STATUS_NOT_CONFIGURED = "NOT_CONFIGURED"
        const val BACKUP_STATUS_PERMISSION_REVOKED = "PERMISSION_REVOKED"
        const val BACKUP_STATUS_IO_ERROR = "IO_ERROR"

        internal const val EXPORT_FILE_PREFIX = "cellid_export_"
        private val TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"

        // Test seam: lets unit tests swap in a FakeBackupCopier without
        // standing up DocumentsContract / SAF infrastructure.
        @Volatile
        internal var backupCopierFactory: (Context) -> BackupCopier = { DocumentFileBackupCopier(it) }

        fun buildRequest(
            context: Context,
            format: ExportFormat,
            sessionId: Long? = null
        ): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_FORMAT to format.name,
                KEY_SESSION_ID to (sessionId ?: -1L),
                KEY_BACKUP_LOCATION_URI to Preferences(context).backupLocationUri
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

        // Pure mapper from a BackupCopyResult to the (status, name) pair the
        // worker stuffs into its output data. Tested directly.
        internal fun backupResultToOutputData(result: BackupCopyResult): Pair<String, String?> = when (result) {
            is BackupCopyResult.Success -> BACKUP_STATUS_SUCCESS to result.savedName
            is BackupCopyResult.NotConfigured -> BACKUP_STATUS_NOT_CONFIGURED to null
            is BackupCopyResult.PermissionRevoked -> BACKUP_STATUS_PERMISSION_REVOKED to null
            is BackupCopyResult.IoError -> BACKUP_STATUS_IO_ERROR to null
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
        val backupTreeUri = inputData.getString(KEY_BACKUP_LOCATION_URI)

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

        // Best-effort copy to user-picked SAF backup folder. A failure here does
        // NOT fail the export — local copy already succeeded and is what the
        // Share intent serves via FileProvider.
        val backupResult = backupCopierFactory(applicationContext)
            .copyToConfiguredLocation(outputFile, format.mimeType, backupTreeUri)
        val (backupStatus, backupName) = backupResultToOutputData(backupResult)

        setProgress(workDataOf(KEY_PROGRESS to 100))

        val outputData = workDataOf(
            KEY_OUTPUT_URI to outputFile.absolutePath,
            KEY_BACKUP_STATUS to backupStatus,
            KEY_BACKUP_NAME to backupName
        )
        return Result.success(outputData)
    }
}
