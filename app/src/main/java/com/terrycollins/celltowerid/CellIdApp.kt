package com.terrycollins.celltowerid

import android.app.Application
import com.terrycollins.celltowerid.util.CrashReporter

class CellIdApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
