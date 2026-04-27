package com.terrycollins.celltowerid.service

import com.terrycollins.celltowerid.util.Preferences

// Decides what a (re)started CollectionService should do based on persisted
// scan state and current permission grants. Isolated from the service so the
// decision logic can be unit-tested without Android plumbing.
object CollectionRestartPolicy {

    sealed class Decision {
        data class Resume(val intervalMs: Long) : Decision()
        object Stop : Decision()
        // Scan was active but the location permission has since been revoked.
        // Caller should clear the active flag and surface a notification so
        // the user knows scanning stopped silently.
        object StopAndNotifyPermissionLost : Decision()
    }

    fun decide(
        prefs: Preferences,
        defaultIntervalMs: Long,
        hasFineLocation: Boolean
    ): Decision {
        if (!hasFineLocation) {
            // If we'd previously been scanning, the stop is unexpected from the
            // user's POV -- surface it. Otherwise just stop quietly.
            return if (prefs.isScanActive) Decision.StopAndNotifyPermissionLost else Decision.Stop
        }
        if (!prefs.isScanActive) return Decision.Stop
        val interval = prefs.scanIntervalMs.takeIf { it > 0 } ?: defaultIntervalMs
        return Decision.Resume(interval)
    }
}
