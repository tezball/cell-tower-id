package com.terrycollins.celltowerid.service

import com.terrycollins.celltowerid.util.Preferences

// Decides what a restarted CollectionService should do when Android redelivers
// a null intent after process death (START_STICKY behavior). Isolated from the
// service so the decision logic can be unit-tested without Android plumbing.
object CollectionRestartPolicy {

    sealed class Decision {
        data class Resume(val intervalMs: Long) : Decision()
        object Stop : Decision()
    }

    fun decide(prefs: Preferences, defaultIntervalMs: Long): Decision {
        if (!prefs.isScanActive) return Decision.Stop
        val interval = prefs.scanIntervalMs.takeIf { it > 0 } ?: defaultIntervalMs
        return Decision.Resume(interval)
    }
}
