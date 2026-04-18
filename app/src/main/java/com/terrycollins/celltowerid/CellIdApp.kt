package com.terrycollins.celltowerid

import android.app.Application
import com.terrycollins.celltowerid.util.AppLog
import com.terrycollins.celltowerid.util.CrashReporter

class CellIdApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        CrashReporter.install(this)
        AppLog.d("CellIdApp", "process start, log file=${AppLog.logFile(this).absolutePath}")
    }
}
