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
import com.celltowerid.android.repository.SessionRepository
import java.io.File

/**
 * Restores a backup file in the background. The caller hands us a path to a
 * file already on local disk (the SAF picker hands back a content:// URI; the
 * UI is expected to copy that into the app's cache before enqueueing this
 * worker so we have predictable size/permissions semantics here).
 *
 * On success the parsed measurements are written under a fresh session named
 * after the source filename, so an import is just as easy to delete later as
 * any other session.
 */
class ImportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_FILE_PATH = "file_path"
        const val KEY_PROGRESS = "progress"
        const val KEY_IMPORTED_COUNT = "imported_count"
        const val KEY_FORMAT = "format"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_ERROR_REASON = "error_reason"
        const val KEY_ERROR_MESSAGE = "error_message"

        fun buildRequest(filePath: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(workDataOf(KEY_FILE_PATH to filePath))
                .build()

        /**
         * Pure entry point for unit tests. Parses the file, persists under a
         * new session, and returns the imported result. Throws [ImportException]
         * on any validation failure.
         */
        internal suspend fun runImport(
            file: File,
            sessionRepository: SessionRepository,
            measurementRepository: MeasurementRepository,
            sessionDescription: String = "Imported from ${file.name}"
        ): RunResult {
            val parsed = BackupImporter.import(file)
            val sessionId = sessionRepository.startSession(sessionDescription)
            measurementRepository.insertMeasurements(parsed.measurements, sessionId)
            sessionRepository.endSession(sessionId, parsed.measurements.size)
            return RunResult(
                format = parsed.format,
                sessionId = sessionId,
                measurements = parsed.measurements
            )
        }

        internal data class RunResult(
            val format: ExportFormat,
            val sessionId: Long,
            val measurements: List<CellMeasurement>
        )
    }

    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_FILE_PATH)
            ?: return reasonFailure(
                ImportException.Reason.UNKNOWN_FORMAT,
                "Missing file path"
            )
        val file = File(path)

        setProgress(workDataOf(KEY_PROGRESS to 10))

        val db = AppDatabase.getInstance(applicationContext)
        val sessionRepo = SessionRepository(db.sessionDao())
        val measurementRepo = MeasurementRepository(db.measurementDao())

        return try {
            val result = runImport(file, sessionRepo, measurementRepo)
            setProgress(workDataOf(KEY_PROGRESS to 100))
            Result.success(
                workDataOf(
                    KEY_IMPORTED_COUNT to result.measurements.size,
                    KEY_FORMAT to result.format.name,
                    KEY_SESSION_ID to result.sessionId
                )
            )
        } catch (e: ImportException) {
            reasonFailure(e.reason, e.message ?: "Import failed")
        }
    }

    private fun reasonFailure(reason: ImportException.Reason, message: String): Result =
        Result.failure(
            workDataOf(
                KEY_ERROR_REASON to reason.name,
                KEY_ERROR_MESSAGE to message
            )
        )
}
