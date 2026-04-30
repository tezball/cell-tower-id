package com.celltowerid.android.util

import android.content.Context

class Preferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_RETENTION_MIGRATED_V1)) {
            prefs.edit()
                .putInt(KEY_RETENTION_DAYS, 14)
                .putBoolean(KEY_RETENTION_MIGRATED_V1, true)
                .apply()
        }
    }

    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, 14)
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

    var powerSaverEnabled: Boolean
        get() = prefs.getBoolean(KEY_POWER_SAVER, true)
        set(value) {
            prefs.edit().putBoolean(KEY_POWER_SAVER, value).apply()
        }

    // Tracks whether we've already shown the one-time battery-optimization
    // exemption prompt. Aggressive Doze on Xiaomi/OnePlus/Samsung silently
    // kills the foreground service overnight; the prompt deep-links to system
    // settings so users can opt the app out.
    var batteryOptPromptShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_OPT_PROMPT_SHOWN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BATTERY_OPT_PROMPT_SHOWN, value).apply()
        }

    // Persisted SAF tree URI the user picked as a durable backup destination.
    // null = unset (exports stay in app-scoped storage and are wiped on uninstall).
    var backupLocationUri: String?
        get() = prefs.getString(KEY_BACKUP_LOCATION_URI, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_BACKUP_LOCATION_URI)
                else putString(KEY_BACKUP_LOCATION_URI, value)
            }.apply()
        }

    companion object {
        private const val PREFS_NAME = "cellid_prefs"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_SCAN_ACTIVE = "scan_active"
        private const val KEY_SCAN_INTERVAL_MS = "scan_interval_ms"
        private const val KEY_POWER_SAVER = "power_saver_enabled"
        private const val KEY_RETENTION_MIGRATED_V1 = "retention_migrated_v1"
        private const val KEY_BATTERY_OPT_PROMPT_SHOWN = "battery_opt_prompt_shown"
        private const val KEY_BACKUP_LOCATION_URI = "backup_location_uri"
    }
}
