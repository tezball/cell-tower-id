package com.terrycollins.celltowerid.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashReporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var crashDir: File

    @Before
    fun setUp() {
        crashDir = tempFolder.newFolder("crashes")
        AppLog.clear()
    }

    @After
    fun tearDown() {
        AppLog.clear()
    }

    @Test
    fun `given a throwable, when persistCrash is called, then writes a file containing the stack trace`() {
        // Given
        val throwable = IllegalStateException("startForeground never called")

        // When
        CrashReporter.persistCrash(crashDir, throwable, thread = Thread.currentThread())

        // Then
        val files = crashDir.listFiles().orEmpty()
        assertThat(files).hasLength(1)
        val contents = files.single().readText()
        assertThat(contents).contains("IllegalStateException")
        assertThat(contents).contains("startForeground never called")
    }

    @Test
    fun `given recent AppLog lines, when persistCrash is called, then the dump includes those log lines`() {
        // Given
        AppLog.e("CollectionService", "onCreate finished")
        AppLog.e("CollectionService", "startCollection called")
        val throwable = RuntimeException("boom")

        // When
        CrashReporter.persistCrash(crashDir, throwable, thread = Thread.currentThread())

        // Then
        val contents = crashDir.listFiles()!!.single().readText()
        assertThat(contents).contains("onCreate finished")
        assertThat(contents).contains("startCollection called")
    }

    @Test
    fun `given multiple crashes, when persistCrash is called for each, then all are written with unique filenames`() {
        // When
        CrashReporter.persistCrash(crashDir, RuntimeException("first"), Thread.currentThread())
        CrashReporter.persistCrash(crashDir, RuntimeException("second"), Thread.currentThread())
        CrashReporter.persistCrash(crashDir, RuntimeException("third"), Thread.currentThread())

        // Then
        val files = crashDir.listFiles().orEmpty()
        assertThat(files).hasLength(3)
        assertThat(files.map { it.name }.toSet()).hasSize(3)
    }

    @Test
    fun `given many crashes beyond cap, when persistCrash is called, then oldest crashes are pruned`() {
        // Given — create more crash files than the retention cap.
        repeat(CrashReporter.MAX_RETAINED_CRASHES + 3) { i ->
            CrashReporter.persistCrash(crashDir, RuntimeException("crash $i"), Thread.currentThread())
            // Small delay to ensure distinct lastModified stamps on fast FS.
            Thread.sleep(5)
        }

        // Then
        val files = crashDir.listFiles().orEmpty()
        assertThat(files.size).isAtMost(CrashReporter.MAX_RETAINED_CRASHES)
    }

    @Test
    fun `given crashes exist, when readAll called, then returns contents newest-first`() {
        // Given
        CrashReporter.persistCrash(crashDir, RuntimeException("older"), Thread.currentThread())
        Thread.sleep(10)
        CrashReporter.persistCrash(crashDir, RuntimeException("newer"), Thread.currentThread())

        // When
        val reports = CrashReporter.readAll(crashDir)

        // Then
        assertThat(reports).hasSize(2)
        assertThat(reports[0].body).contains("newer")
        assertThat(reports[1].body).contains("older")
    }

    @Test
    fun `given no crashes, when readAll called, then returns empty list`() {
        // When
        val reports = CrashReporter.readAll(crashDir)

        // Then
        assertThat(reports).isEmpty()
    }

    @Test
    fun `given crashes exist, when clearAll called, then directory is empty`() {
        // Given
        CrashReporter.persistCrash(crashDir, RuntimeException("one"), Thread.currentThread())
        CrashReporter.persistCrash(crashDir, RuntimeException("two"), Thread.currentThread())

        // When
        CrashReporter.clearAll(crashDir)

        // Then
        assertThat(crashDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `given an installed handler, when an uncaught exception propagates, then persistCrash is invoked and prior handler is delegated to`() {
        // Given
        val captured = mutableListOf<Throwable>()
        val prior = Thread.UncaughtExceptionHandler { _, e -> captured.add(e) }
        val installed = CrashReporter.wrap(prior, crashDir)

        // When
        val t = Thread.currentThread()
        installed.uncaughtException(t, ArithmeticException("div by zero"))

        // Then
        val files = crashDir.listFiles().orEmpty()
        assertThat(files).hasLength(1)
        assertThat(captured).hasSize(1)
        assertThat(captured[0]).isInstanceOf(ArithmeticException::class.java)
    }
}
