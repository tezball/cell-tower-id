package com.terrycollins.celltowerid.util

import com.terrycollins.celltowerid.domain.model.CellMeasurement

/**
 * Weighted-centroid estimate of a tower's position from a list of
 * geolocated RSRP samples. Stronger signal = closer = higher weight.
 */
object TowerLocator {

    private const val MIN_SPREAD_METERS = 50.0

    fun estimate(measurements: List<CellMeasurement>): Pair<Double, Double>? {
        if (measurements.size < 2) return null

        val lats = measurements.map { it.latitude }
        val lons = measurements.map { it.longitude }
        val latSpread = (lats.max() - lats.min()) * 111_000.0
        val lonSpread = (lons.max() - lons.min()) * 111_000.0 *
            Math.cos(Math.toRadians(lats.average()))
        if (latSpread < MIN_SPREAD_METERS && lonSpread < MIN_SPREAD_METERS) return null

        var weightedLat = 0.0
        var weightedLon = 0.0
        var totalWeight = 0.0
        for (m in measurements) {
            val rsrp = m.rsrp ?: continue
            val weight = Math.pow(10.0, (rsrp + 120.0) / 20.0)
            weightedLat += m.latitude * weight
            weightedLon += m.longitude * weight
            totalWeight += weight
        }
        if (totalWeight == 0.0) return null
        return Pair(weightedLat / totalWeight, weightedLon / totalWeight)
    }
}
