package com.celltowerid.android.export

class ImportException(
    message: String,
    val reason: Reason,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class Reason {
        EMPTY_FILE,
        FILE_TOO_LARGE,
        TOO_MANY_ROWS,
        UNKNOWN_FORMAT,
        UNRECOGNIZED_SCHEMA,
        MALFORMED,
        INVALID_VALUE
    }
}
