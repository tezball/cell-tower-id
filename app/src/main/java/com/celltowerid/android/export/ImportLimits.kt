package com.celltowerid.android.export

internal object ImportLimits {
    // Defensive caps so a hostile or corrupt file cannot exhaust memory or time.
    const val MAX_FILE_BYTES: Long = 100L * 1024L * 1024L
    const val MAX_ROWS: Int = 1_000_000
    const val MAX_STRING_LEN: Int = 1024
}
