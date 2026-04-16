package com.terrycollins.celltowerid.util

import java.util.ArrayDeque

data class LogLine(
    val ts: Long,
    val level: String,
    val tag: String,
    val message: String
)

object AppLog {

    private const val MAX_LINES = 200
    private val buffer = ArrayDeque<LogLine>()

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

    fun clear() = synchronized(this) { buffer.clear() }

    private fun append(level: String, tag: String, msg: String) = synchronized(this) {
        buffer.addLast(LogLine(System.currentTimeMillis(), level, tag, msg))
        while (buffer.size > MAX_LINES) buffer.removeFirst()
    }

    private fun formatMessage(msg: String, t: Throwable?): String {
        if (t == null) return msg
        return "$msg: ${t.javaClass.simpleName}: ${t.message ?: ""}"
    }
}
