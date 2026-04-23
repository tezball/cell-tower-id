package com.terrycollins.celltowerid.util

import com.terrycollins.celltowerid.domain.model.CellMeasurement

/**
 * Canonical pinning identity for a [CellMeasurement].
 *
 * Android's `getAllCellInfo()` typically only exposes full identity
 * (MCC/MNC/TAC/CID) for the serving cell. Neighbor cells often report
 * only PCI + EARFCN + signal strength. To let users pin those neighbors
 * anyway, we fall back to a synthetic tuple keyed on PCI (and EARFCN
 * when available) with sentinel values for the network-identity fields.
 *
 * Sentinels: MCC=0, MNC=0, TAC=0, and CID is a negative value derived
 * from PCI and EARFCN. Real CIDs are always non-negative, so the sign
 * bit alone distinguishes synthetic from real entries.
 */
object PinIdentity {

    data class Tuple(val mcc: Int, val mnc: Int, val tac: Int, val cid: Long)

    fun of(cell: CellMeasurement): Tuple? {
        val mcc = cell.mcc
        val mnc = cell.mnc
        val tac = cell.tacLac
        val cid = cell.cid
        if (mcc != null && mnc != null && tac != null && cid != null) {
            return Tuple(mcc, mnc, tac, cid)
        }

        val pci = cell.pciPsc ?: return null
        val earfcn = cell.earfcnArfcn ?: 0
        // PCI is 9 bits (0-503), EARFCN up to 18 bits (<= 262143).
        // pci * 1_000_000 + earfcn is unique per (pci, earfcn) pair and
        // fits well within Long. Negating guarantees no collision with
        // real CIDs, which are always non-negative.
        val synthCid = -(pci.toLong() * 1_000_000L + earfcn.toLong())
        return Tuple(mcc = 0, mnc = 0, tac = 0, cid = synthCid)
    }

    fun keyOf(cell: CellMeasurement): String? {
        val t = of(cell) ?: return null
        return "${cell.radio}-${t.mcc}-${t.mnc}-${t.tac}-${t.cid}"
    }
}
