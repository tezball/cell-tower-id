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

    companion object {
        private const val PREFS_NAME = "cellid_prefs"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
