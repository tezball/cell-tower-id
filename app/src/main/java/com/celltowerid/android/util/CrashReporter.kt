package com.celltowerid.android.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// On-device crash persistence: when the JVM default uncaught-exception
// handler fires, we write the stack trace plus the recent AppLog buffer to
// a file under the app's files/ dir before delegating to the system handler
// (which kills the process). On the next launch the user can view these
// from the debug screen.
object CrashReporter {

    const val MAX_RETAINED_CRASHES = 10
    private const val CRASH_DIR = "crashes"
    private val FILE_STAMP = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    data class CrashReport(
        val file: File,
        val timestampMs: Long,
        val body: String
    )

    fun install(context: Context) {
        val dir = crashDir(context)
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(wrap(prior, dir))
    }

    fun wrap(
        prior: Thread.UncaughtExceptionHandler?,
        crashDir: File
    ): Thread.UncaughtExceptionHandler {
        return Thread.UncaughtExceptionHandler { thread, throwable ->
            try {
                persistCrash(crashDir, throwable, thread)
            } catch (_: Throwable) {
                // Never rethrow from an uncaught handler — swallow persistence errors.
            }
            prior?.uncaughtException(thread, throwable)
        }
    }

    fun persistCrash(crashDir: File, throwable: Throwable, thread: Thread) {
        if (!crashDir.exists()) crashDir.mkdirs()

        val stamp = FILE_STAMP.format(Date())
        val file = uniqueFile(crashDir, "crash_$stamp")
        file.writeText(formatReport(throwable, thread))
        pruneOldest(crashDir)
    }

    fun readAll(crashDir: File): List<CrashReport> {
        if (!crashDir.exists()) return emptyList()
        return crashDir.listFiles()
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map {
                CrashReport(
                    file = it,
                    timestampMs = it.lastModified(),
                    body = it.readText()
                )
            }
    }

    fun clearAll(crashDir: File) {
        crashDir.listFiles()?.forEach { it.delete() }
    }

    fun crashDir(context: Context): File =
        File(context.applicationContext.filesDir, CRASH_DIR)

    private fun formatReport(throwable: Throwable, thread: Thread): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("=== Cell Tower ID crash report ===")
        pw.println("time: ${Date()}")
        pw.println("thread: ${thread.name}")
        pw.println()
        pw.println("=== stack trace ===")
        throwable.printStackTrace(pw)
        pw.println()
        pw.println("=== recent log (${AppLog.lines().size} lines) ===")
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        AppLog.lines().forEach { line ->
            pw.println("${fmt.format(Date(line.ts))} ${line.level}/${line.tag}: ${line.message}")
        }
        pw.flush()
        return sw.toString()
    }

    private fun uniqueFile(dir: File, baseName: String): File {
        var candidate = File(dir, "$baseName.txt")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(dir, "${baseName}_$suffix.txt")
            suffix++
        }
        return candidate
    }

    private fun pruneOldest(dir: File) {
        val files = dir.listFiles().orEmpty()
        if (files.size <= MAX_RETAINED_CRASHES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_RETAINED_CRASHES)
            .forEach { it.delete() }
    }
}
