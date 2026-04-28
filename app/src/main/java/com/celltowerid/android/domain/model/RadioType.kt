package com.celltowerid.android.domain.model

enum class RadioType {
    GSM, WCDMA, CDMA, LTE, NR, TDSCDMA, UNKNOWN;

    companion object {
        fun fromString(value: String): RadioType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
