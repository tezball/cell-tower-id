package com.celltowerid.android.util

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.CellTower

/**
 * Picks the lat/lon to render a tower's dot at on the map. Prefers the
 * exact location of the strongest reading observed for the tower so the
 * dot reflects where the user actually saw the cell, not a synthesized
 * estimate of where the physical tower might sit. Falls back to the
 * cached tower position (e.g. for pinned stubs that have no readings yet).
 *
 * Returns null when neither a reading nor a cached position is available.
 */
object TowerMarkerPosition {
    fun pick(tower: CellTower, best: CellMeasurement?): Pair<Double, Double>? {
        if (best != null) return best.latitude to best.longitude
        val lat = tower.latitude ?: return null
        val lon = tower.longitude ?: return null
        return lat to lon
    }
}
