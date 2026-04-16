package com.terrycollins.cellid.util

import com.terrycollins.cellid.domain.model.CellTower
import com.terrycollins.cellid.domain.model.RadioType

/**
 * Collapses multiple LTE sector dots that belong to the same physical
 * eNodeB (identified by mcc/mnc and cid shr 8) into a single synthetic
 * CellTower located at the arithmetic mean of the group's lat/lon. Non-LTE
 * towers pass through unchanged.
 *
 * Pure utility so it can be unit-tested without any Android dependencies.
 */
object TowerDedup {

    fun collapseLteByEnb(towers: List<CellTower>): List<CellTower> {
        val result = mutableListOf<CellTower>()
        val lteGroups = linkedMapOf<Triple<Int, Int, Long>, MutableList<CellTower>>()

        for (t in towers) {
            if (t.radio == RadioType.LTE) {
                val key = Triple(t.mcc, t.mnc, t.cid shr 8)
                lteGroups.getOrPut(key) { mutableListOf() }.add(t)
            } else {
                result.add(t)
            }
        }

        for ((_, group) in lteGroups) {
            if (group.size == 1) {
                result.add(group[0])
                continue
            }
            val withCoords = group.filter { it.latitude != null && it.longitude != null }
            val first = group[0]
            if (withCoords.isEmpty()) {
                result.add(first)
                continue
            }
            val avgLat = withCoords.sumOf { it.latitude!! } / withCoords.size
            val avgLon = withCoords.sumOf { it.longitude!! } / withCoords.size
            result.add(first.copy(latitude = avgLat, longitude = avgLon))
        }

        return result
    }
}
