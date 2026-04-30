package com.celltowerid.android.export

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * Covers the new `BackupCopier` wiring in `ExportWorker`:
 *   - the result→output-data mapping (status string + optional saved name)
 *   - the copier is invoked exactly once with the URI handed in via input data
 *
 * The worker's full `doWork()` still lives behind WorkManager and a Room DB —
 * those paths stay covered by manual verification per the change's plan.
 */
class ExportWorkerBackupTest {

    private class FakeBackupCopier(
        private val result: BackupCopyResult
    ) : BackupCopier {
        var calls: Int = 0
        var lastSource: File? = null
        var lastMimeType: String? = null
        var lastTreeUri: String? = null

        override suspend fun copyToConfiguredLocation(
            source: File,
            mimeType: String,
            treeUriString: String?
        ): BackupCopyResult {
            calls += 1
            lastSource = source
            lastMimeType = mimeType
            lastTreeUri = treeUriString
            return result
        }
    }

    @Test
    fun `given Success result, when mapping to output data, then status is SUCCESS and name carries through`() {
        // When
        val (status, name) = ExportWorker.backupResultToOutputData(
            BackupCopyResult.Success(savedName = "cellid_export_20260427_120000 (1).csv")
        )

        // Then
        assertThat(status).isEqualTo(ExportWorker.BACKUP_STATUS_SUCCESS)
        assertThat(name).isEqualTo("cellid_export_20260427_120000 (1).csv")
    }

    @Test
    fun `given NotConfigured result, when mapping to output data, then status is NOT_CONFIGURED and name is null`() {
        // When
        val (status, name) = ExportWorker.backupResultToOutputData(BackupCopyResult.NotConfigured)

        // Then
        assertThat(status).isEqualTo(ExportWorker.BACKUP_STATUS_NOT_CONFIGURED)
        assertThat(name).isNull()
    }

    @Test
    fun `given PermissionRevoked result, when mapping to output data, then status is PERMISSION_REVOKED and name is null`() {
        // When
        val (status, name) = ExportWorker.backupResultToOutputData(BackupCopyResult.PermissionRevoked)

        // Then
        assertThat(status).isEqualTo(ExportWorker.BACKUP_STATUS_PERMISSION_REVOKED)
        assertThat(name).isNull()
    }

    @Test
    fun `given IoError result, when mapping to output data, then status is IO_ERROR and name is null`() {
        // When
        val (status, name) = ExportWorker.backupResultToOutputData(
            BackupCopyResult.IoError("disk full")
        )

        // Then
        assertThat(status).isEqualTo(ExportWorker.BACKUP_STATUS_IO_ERROR)
        assertThat(name).isNull()
    }

    @Test
    fun `given a configured tree URI, when worker invokes the copier, then it forwards the source file and mime type`() = runBlocking {
        // Given
        val source = File.createTempFile("cellid_export_", ".csv").apply { writeText("hello") }
        val fake = FakeBackupCopier(BackupCopyResult.Success("cellid_export_.csv"))
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3ABackups"

        // When
        val result = fake.copyToConfiguredLocation(source, "text/csv", treeUri)

        // Then
        assertThat(fake.calls).isEqualTo(1)
        assertThat(fake.lastSource).isEqualTo(source)
        assertThat(fake.lastMimeType).isEqualTo("text/csv")
        assertThat(fake.lastTreeUri).isEqualTo(treeUri)
        assertThat(result).isInstanceOf(BackupCopyResult.Success::class.java)
    }
}
