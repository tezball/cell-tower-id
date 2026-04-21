package com.terrycollins.celltowerid.util

/**
 * Single-shot LiveData payload. Observers call [getContentIfNotHandled] so the
 * same event is not re-delivered after config changes. [peekContent] reads
 * without consuming (useful for tests).
 */
class Event<out T>(private val content: T) {

    private var handled = false

    fun getContentIfNotHandled(): T? =
        if (handled) null else { handled = true; content }

    fun peekContent(): T = content
}
