package com.terrycollins.celltowerid.util

object CellIdParser {
    fun parseEutranCid(eutranCid: Long): Pair<Int, Int> {
        val enbId = (eutranCid shr 8).toInt()
        val sectorId = (eutranCid and 0xFF).toInt()
        return Pair(enbId, sectorId)
    }

    fun buildEutranCid(enbId: Int, sectorId: Int): Long {
        return (enbId.toLong() shl 8) or (sectorId.toLong() and 0xFF)
    }

    fun isValidEutranCid(cid: Long): Boolean {
        return cid in 0..0xFFFFFFF // 28-bit
    }

    fun isValidNrCid(nci: Long): Boolean {
        return nci in 0..0xFFFFFFFFF // 36-bit
    }
}
