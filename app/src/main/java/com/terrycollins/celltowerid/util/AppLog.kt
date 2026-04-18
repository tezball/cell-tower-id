package com.terrycollins.celltowerid.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

data class LogLine(
    val ts: Long,
    val level: String,
    val tag: String,
    val message: String
)

object AppLog {

    private const val MAX_LINES = 200
    private const val MAX_FILE_BYTES: Long = 2L * 1024 * 1024
    private const val LOG_SUBDIR = "logs"
    private const val LOG_FILE_NAME = "app.log"
    private const val ROLLOVER_FILE_NAME = "app.log.1"

    private val buffer = ArrayDeque<LogLine>()
    private val lineFmt = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    @Volatile private var logFile: File? = null

    fun init(context: Context) {
        val dir = context.getExternalFilesDir(LOG_SUBDIR)
            ?: File(context.filesDir, LOG_SUBDIR)
        init(File(dir, LOG_FILE_NAME))
    }

    internal fun init(file: File) = synchronized(this) {
        try {
            file.parentFile?.mkdirs()
            logFile = file
        } catch (_: Throwable) {
            logFile = null
        }
    }

    fun logFile(context: Context): File {
        val dir = context.getExternalFilesDir(LOG_SUBDIR)
            ?: File(context.filesDir, LOG_SUBDIR)
        return File(dir, LOG_FILE_NAME)
    }

    fun d(tag: String, msg: String) {
        append("D", tag, msg)
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            // Unit tests without Robolectric -- Log is not mocked.
        }
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        append("E", tag, formatMessage(msg, t))
        try {
            if (t != null) android.util.Log.e(tag, msg, t) else android.util.Log.e(tag, msg)
        } catch (_: Throwable) {
            // Unit tests without Robolectric -- Log is not mocked.
        }
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        append("W", tag, formatMessage(msg, t))
        try {
            if (t != null) android.util.Log.w(tag, msg, t) else android.util.Log.w(tag, msg)
        } catch (_: Throwable) {
            // ignore in unit tests
        }
    }

    fun lines(): List<LogLine> = synchronized(this) { buffer.toList() }

    fun clear() = synchronized(this) {
        buffer.clear()
        try {
            logFile?.let { if (it.exists()) it.delete() }
            val rollover = logFile?.parentFile?.let { File(it, ROLLOVER_FILE_NAME) }
            rollover?.let { if (it.exists()) it.delete() }
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun append(level: String, tag: String, msg: String) = synchronized(this) {
        val ts = System.currentTimeMillis()
        buffer.addLast(LogLine(ts, level, tag, msg))
        while (buffer.size > MAX_LINES) buffer.removeFirst()
        writeToFile(ts, level, tag, msg)
    }

    private fun writeToFile(ts: Long, level: String, tag: String, msg: String) {
        val file = logFile ?: return
        try {
            if (file.exists() && file.length() >= MAX_FILE_BYTES) {
                val rollover = File(file.parentFile, ROLLOVER_FILE_NAME)
                if (rollover.exists()) rollover.delete()
                file.renameTo(rollover)
            }
            val stamp = (lineFmt.get() ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US))
                .format(Date(ts))
            file.appendText("$stamp $level/$tag: $msg\n")
        } catch (_: Throwable) {
            // Swallow — logging must never crash the app.
        }
    }

    private fun formatMessage(msg: String, t: Throwable?): String {
        if (t == null) return msg
        return "$msg: ${t.javaClass.simpleName}: ${t.message ?: ""}"
    }
}
