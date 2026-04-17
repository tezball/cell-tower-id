package com.terrycollins.celltowerid.util

import android.content.Context

class Preferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, 0)
        set(value) {
            prefs.edit().putInt(KEY_RETENTION_DAYS, value).apply()
        }

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()
        }

    // Whether a collection scan was running at the time the process was last
    // alive. Used by CollectionService to decide whether to resume after a
    // sticky-restart (null-intent onStartCommand) following process death.
    var isScanActive: Boolean
        get() = prefs.getBoolean(KEY_SCAN_ACTIVE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SCAN_ACTIVE, value).apply()
        }

    var scanIntervalMs: Long
        get() = prefs.getLong(KEY_SCAN_INTERVAL_MS, 0L)
        set(value) {
            prefs.edit().putLong(KEY_SCAN_INTERVAL_MS, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "cellid_prefs"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_SCAN_ACTIVE = "scan_active"
        private const val KEY_SCAN_INTERVAL_MS = "scan_interval_ms"
    }
}
