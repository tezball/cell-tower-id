package com.celltowerid.android.util

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinIdentityTest {

    private fun makeCell(
        mcc: Int? = null,
        mnc: Int? = null,
        tac: Int? = null,
        cid: Long? = null,
        pci: Int? = null,
        earfcn: Int? = null
    ) = CellMeasurement(
        timestamp = 0L,
        latitude = 0.0,
        longitude = 0.0,
        radio = RadioType.LTE,
        mcc = mcc,
        mnc = mnc,
        tacLac = tac,
        cid = cid,
        pciPsc = pci,
        earfcnArfcn = earfcn
    )

    @Test
    fun `given full identity, when of, then returns real tuple unchanged`() {
        val cell = makeCell(mcc = 310, mnc = 260, tac = 12345, cid = 50331905L, pci = 321, earfcn = 2050)

        val tuple = requireNotNull(PinIdentity.of(cell))

        assertThat(tuple.mcc).isEqualTo(310)
        assertThat(tuple.mnc).isEqualTo(260)
        assertThat(tuple.tac).isEqualTo(12345)
        assertThat(tuple.cid).isEqualTo(50331905L)
    }

    @Test
    fun `given only PCI and EARFCN, when of, then returns sentinel MCC MNC TAC with negative synthetic CID`() {
        val cell = makeCell(pci = 321, earfcn = 2050)

        val tuple = requireNotNull(PinIdentity.of(cell))

        assertThat(tuple.mcc).isEqualTo(0)
        assertThat(tuple.mnc).isEqualTo(0)
        assertThat(tuple.tac).isEqualTo(0)
        assertThat(tuple.cid).isLessThan(0L)
    }

    @Test
    fun `given only PCI no EARFCN, when of, then returns a tuple with sentinel earfcn`() {
        val cell = makeCell(pci = 321)

        val tuple = requireNotNull(PinIdentity.of(cell))

        assertThat(tuple.cid).isLessThan(0L)
    }

    @Test
    fun `given no identity and no PCI, when of, then returns null`() {
        val cell = makeCell()

        val tuple = PinIdentity.of(cell)

        assertThat(tuple).isNull()
    }

    @Test
    fun `given partial identity missing CID but PCI present, when of, then falls back to PCI-based tuple`() {
        val cell = makeCell(mcc = 310, mnc = 260, tac = 12345, cid = null, pci = 321, earfcn = 2050)

        val tuple = requireNotNull(PinIdentity.of(cell))

        assertThat(tuple.mcc).isEqualTo(0)
        assertThat(tuple.cid).isLessThan(0L)
    }

    @Test
    fun `given two cells with same PCI and EARFCN, when of, then synthetic CIDs are equal`() {
        val a = makeCell(pci = 100, earfcn = 2050)
        val b = makeCell(pci = 100, earfcn = 2050)

        assertThat(requireNotNull(PinIdentity.of(a)).cid)
            .isEqualTo(requireNotNull(PinIdentity.of(b)).cid)
    }

    @Test
    fun `given two cells with different PCI, when of, then synthetic CIDs differ`() {
        val a = makeCell(pci = 100, earfcn = 2050)
        val b = makeCell(pci = 101, earfcn = 2050)

        assertThat(requireNotNull(PinIdentity.of(a)).cid)
            .isNotEqualTo(requireNotNull(PinIdentity.of(b)).cid)
    }

    @Test
    fun `given two cells with same PCI but different EARFCN, when of, then synthetic CIDs differ`() {
        val a = makeCell(pci = 100, earfcn = 2050)
        val b = makeCell(pci = 100, earfcn = 6200)

        assertThat(requireNotNull(PinIdentity.of(a)).cid)
            .isNotEqualTo(requireNotNull(PinIdentity.of(b)).cid)
    }

    @Test
    fun `given full identity, when keyOf, then returns radio-mcc-mnc-tac-cid`() {
        val cell = makeCell(mcc = 310, mnc = 260, tac = 12345, cid = 50331905L)

        val key = PinIdentity.keyOf(cell)

        assertThat(key).isEqualTo("LTE-310-260-12345-50331905")
    }

    @Test
    fun `given PCI-only cell, when keyOf, then matches a stored sentinel-based entity key`() {
        val cell = makeCell(pci = 321, earfcn = 2050)

        val tuple = requireNotNull(PinIdentity.of(cell))
        val fromCell = PinIdentity.keyOf(cell)
        val fromEntity = "LTE-${tuple.mcc}-${tuple.mnc}-${tuple.tac}-${tuple.cid}"

        assertThat(fromCell).isEqualTo(fromEntity)
    }

    @Test
    fun `given no identity, when keyOf, then returns null`() {
        val cell = makeCell()

        assertThat(PinIdentity.keyOf(cell)).isNull()
    }
}
