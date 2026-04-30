package com.celltowerid.android.util

import com.celltowerid.android.domain.model.AnomalyEvent
import com.celltowerid.android.domain.model.AnomalySeverity
import com.celltowerid.android.domain.model.CellKey
import com.celltowerid.android.domain.model.CellTower
import com.celltowerid.android.domain.model.RadioType

/**
 * Indexes a list of alerts so the map can quickly look up "is there an alert
 * for this marker?" Maintains two maps because LTE markers are eNB-collapsed
 * (mcc, mnc, cid shr 8) but alerts live at the full sector CID — keeping a
 * separate eNB-keyed map lets a marker representing one sector still surface
 * an alert that originated on a different sector of the same eNB.
 *
 * On collisions within a bucket, picks the alert with the highest severity
 * (HIGH > MEDIUM > LOW), then the most recent timestamp.
 */
object AlertIndexer {

    data class Index(
        val nonLte: Map<CellKey, AnomalyEvent>,
        val lteByEnb: Map<Triple<Int, Int, Long>, AnomalyEvent>
    )

    fun index(events: List<AnomalyEvent>): Index {
        val nonLte = mutableMapOf<CellKey, AnomalyEvent>()
        val lteByEnb = mutableMapOf<Triple<Int, Int, Long>, AnomalyEvent>()

        for (e in events) {
            val radio = e.cellRadio ?: continue
            val mcc = e.cellMcc ?: continue
            val mnc = e.cellMnc ?: continue
            val tacLac = e.cellTacLac ?: continue
            val cid = e.cellCid ?: continue

            if (radio == RadioType.LTE) {
                val k = Triple(mcc, mnc, cid shr 8)
                val existing = lteByEnb[k]
                if (existing == null || prefers(e, existing)) {
                    lteByEnb[k] = e
                }
            } else {
                val k = CellKey(radio, mcc, mnc, tacLac, cid)
                val existing = nonLte[k]
                if (existing == null || prefers(e, existing)) {
                    nonLte[k] = e
                }
            }
        }

        return Index(nonLte, lteByEnb)
    }

    fun lookup(index: Index, tower: CellTower): AnomalyEvent? {
        return if (tower.radio == RadioType.LTE) {
            index.lteByEnb[Triple(tower.mcc, tower.mnc, tower.cid shr 8)]
        } else {
            index.nonLte[CellKey.of(tower)]
        }
    }

    private fun prefers(candidate: AnomalyEvent, existing: AnomalyEvent): Boolean {
        val rankCmp = severityRank(candidate.severity).compareTo(severityRank(existing.severity))
        if (rankCmp != 0) return rankCmp > 0
        return candidate.timestamp > existing.timestamp
    }

    private fun severityRank(s: AnomalySeverity): Int = when (s) {
        AnomalySeverity.HIGH -> 2
        AnomalySeverity.MEDIUM -> 1
        AnomalySeverity.LOW -> 0
    }
}
