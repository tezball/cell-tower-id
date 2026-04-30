package com.celltowerid.android.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Result of an attempt to copy a freshly-written export file into the user's
 * chosen SAF backup folder. The local copy in app-scoped storage is written
 * before the backup attempt, so even on failure the export itself succeeded —
 * only the durable mirror is missing.
 */
sealed class BackupCopyResult {
    data class Success(val savedName: String) : BackupCopyResult()
    object NotConfigured : BackupCopyResult()
    object PermissionRevoked : BackupCopyResult()
    data class IoError(val message: String) : BackupCopyResult()
}

/**
 * Copies an export file into a user-picked SAF tree, if one is configured.
 * Stays out of [ExportWorker] so the worker stays unit-testable with a fake.
 */
interface BackupCopier {
    suspend fun copyToConfiguredLocation(
        source: File,
        mimeType: String,
        treeUriString: String?
    ): BackupCopyResult
}

class DocumentFileBackupCopier(private val context: Context) : BackupCopier {

    override suspend fun copyToConfiguredLocation(
        source: File,
        mimeType: String,
        treeUriString: String?
    ): BackupCopyResult = withContext(Dispatchers.IO) {
        if (treeUriString.isNullOrBlank()) return@withContext BackupCopyResult.NotConfigured

        try {
            val treeUri = Uri.parse(treeUriString)
            val tree = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext BackupCopyResult.IoError("Backup folder not reachable")

            val doc = tree.createFile(mimeType, source.name)
                ?: return@withContext BackupCopyResult.IoError("Could not create file in backup folder")

            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } ?: return@withContext BackupCopyResult.IoError("Could not open backup file for writing")

            // createFile may auto-suffix on conflict (e.g. "cellid_export (1).csv");
            // surface the actual name so the success snackbar is honest.
            BackupCopyResult.Success(doc.name ?: source.name)
        } catch (_: SecurityException) {
            BackupCopyResult.PermissionRevoked
        } catch (e: IOException) {
            BackupCopyResult.IoError(e.message ?: "I/O error")
        } catch (e: IllegalArgumentException) {
            // Thrown by DocumentsContract for malformed tree URIs.
            BackupCopyResult.IoError(e.message ?: "Invalid backup URI")
        }
    }
}
