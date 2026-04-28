package com.celltowerid.android.domain.model

data class CellKey(
    val radio: RadioType,
    val mcc: Int,
    val mnc: Int,
    val tacLac: Int,
    val cid: Long
) {
    companion object {
        fun of(tower: CellTower): CellKey =
            CellKey(tower.radio, tower.mcc, tower.mnc, tower.tacLac, tower.cid)
    }
}
