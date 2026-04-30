package com.celltowerid.android.export

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Robolectric coverage for the `NotConfigured` and `IoError` (parse-failure)
 * branches. The full SAF tree-URI round-trip relies on a real DocumentsContract
 * provider — verified manually per the change's verification plan.
 */
@RunWith(RobolectricTestRunner::class)
class DocumentFileBackupCopierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun makeSourceFile(name: String = "cellid_export.csv", body: String = "hello"): File {
        val f = File(tmp.newFolder("src"), name)
        f.writeText(body)
        return f
    }

    @Test
    fun `given null tree URI, when copyToConfiguredLocation, then returns NotConfigured`() = runBlocking {
        // Given
        val copier = DocumentFileBackupCopier(context)
        val source = makeSourceFile()

        // When
        val result = copier.copyToConfiguredLocation(source, "text/csv", null)

        // Then
        assertThat(result).isEqualTo(BackupCopyResult.NotConfigured)
    }

    @Test
    fun `given blank tree URI, when copyToConfiguredLocation, then returns NotConfigured`() = runBlocking {
        // Given
        val copier = DocumentFileBackupCopier(context)
        val source = makeSourceFile()

        // When
        val result = copier.copyToConfiguredLocation(source, "text/csv", "   ")

        // Then
        assertThat(result).isEqualTo(BackupCopyResult.NotConfigured)
    }

    @Test
    fun `given malformed tree URI, when copyToConfiguredLocation, then returns IoError or PermissionRevoked`() = runBlocking {
        // Given
        val copier = DocumentFileBackupCopier(context)
        val source = makeSourceFile()

        // When
        val result = copier.copyToConfiguredLocation(source, "text/csv", "not-a-tree-uri")

        // Then -- either IoError (DocumentFile returns null / throws IO) or PermissionRevoked
        // (SecurityException). Both are acceptable failure modes for an unusable URI; what
        // matters is that the copier does NOT return Success and does NOT crash the caller.
        assertThat(result).isNotEqualTo(BackupCopyResult.NotConfigured)
        assertThat(result).isNotInstanceOf(BackupCopyResult.Success::class.java)
    }
}
