package com.celltowerid.android.ui

import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.ui.dialog.ManualTowerInput
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ManualTowerInputTest {

    @Test
    fun `given all valid fields, when parse, then returns Valid with parsed values`() {
        val result = ManualTowerInput.parse(
            radio = RadioType.LTE,
            mcc = "310",
            mnc = "260",
            tacLac = "12345",
            cid = "50331905",
            latitude = "37.7749",
            longitude = "-122.4194",
            pci = "321"
        )

        assertThat(result).isInstanceOf(ManualTowerInput.Result.Valid::class.java)
        val valid = result as ManualTowerInput.Result.Valid
        assertThat(valid.radio).isEqualTo(RadioType.LTE)
        assertThat(valid.mcc).isEqualTo(310)
        assertThat(valid.mnc).isEqualTo(260)
        assertThat(valid.tacLac).isEqualTo(12345)
        assertThat(valid.cid).isEqualTo(50331905L)
        assertThat(valid.latitude).isEqualTo(37.7749)
        assertThat(valid.longitude).isEqualTo(-122.4194)
        assertThat(valid.pci).isEqualTo(321)
    }

    @Test
    fun `given blank pci, when parse, then pci is null and result is Valid`() {
        val result = ManualTowerInput.parse(
            radio = RadioType.NR,
            mcc = "310",
            mnc = "260",
            tacLac = "1",
            cid = "2",
            latitude = "1.0",
            longitude = "2.0",
            pci = ""
        )

        assertThat(result).isInstanceOf(ManualTowerInput.Result.Valid::class.java)
        assertThat((result as ManualTowerInput.Result.Valid).pci).isNull()
    }

    @Test
    fun `given non-numeric mcc, when parse, then Invalid with mcc error`() {
        val result = ManualTowerInput.parse(
            radio = RadioType.LTE,
            mcc = "abc",
            mnc = "260",
            tacLac = "1",
            cid = "1",
            latitude = "0",
            longitude = "0",
            pci = null
        )

        assertThat(result).isInstanceOf(ManualTowerInput.Result.Invalid::class.java)
        val errors = (result as ManualTowerInput.Result.Invalid).errors
        assertThat(errors).containsKey(ManualTowerInput.Field.MCC)
    }

    @Test
    fun `given mcc out of range, when parse, then Invalid with mcc error`() {
        val result = ManualTowerInput.parse(
            radio = RadioType.LTE,
            mcc = "1000",
            mnc = "260",
            tacLac = "1",
            cid = "1",
            latitude = "0",
            longitude = "0",
            pci = null
        )

        assertThat(result).isInstanceOf(ManualTowerInput.Result.Invalid::class.java)
        val errors = (result as ManualTowerInput.Result.Invalid).errors
        assertThat(errors).containsKey(ManualTowerInput.Field.MCC)
    }

    @Test
    fun `given latitude out of range, when parse, then Invalid with latitude error`() {
        val result = ManualTowerInput.parse(
            radio = RadioType.LTE,
            mcc = "310",
            mnc = "260",
            tacLac = "1",
            cid = "1",
            latitude = "95.0",
            longitude = "0",
            pci = null
        )

        assertThat(result).isInstanceOf(ManualTowerInput.Result.Invalid::class.java)
        val errors = (result as ManualTowerInput.Result.Invalid).errors
        assertThat(errors).containsKey(ManualTowerInput.Field.LATITUDE)
    }

    @Test
    fun `given longitude out of range, when parse, then Invalid with longitude error`() {
        val result = ManualTowerInput.parse(
            radio = RadioType.LTE,
            mcc = "310",
            mnc = "260",
            tacLac = "1",
            cid = "1",
            latitude = "0",
            longitude = "181.0",
            pci = null
        )

        assertThat(result).isInstanceOf(ManualTowerInput.Result.Invalid::class.java)
        val errors = (result as ManualTowerInput.Result.Invalid).errors
        assertThat(errors).containsKey(ManualTowerInput.Field.LONGITUDE)
    }

    @Test
    fun `given negative cid, when parse, then Invalid with cid error`() {
        val result = ManualTowerInput.parse(
            radio = RadioType.LTE,
            mcc = "310",
            mnc = "260",
            tacLac = "1",
            cid = "-1",
            latitude = "0",
            longitude = "0",
            pci = null
        )

        assertThat(result).isInstanceOf(ManualTowerInput.Result.Invalid::class.java)
        val errors = (result as ManualTowerInput.Result.Invalid).errors
        assertThat(errors).containsKey(ManualTowerInput.Field.CID)
    }

    @Test
    fun `given multiple bad fields, when parse, then Invalid with all errors`() {
        val result = ManualTowerInput.parse(
            radio = RadioType.LTE,
            mcc = "",
            mnc = "",
            tacLac = "",
            cid = "",
            latitude = "",
            longitude = "",
            pci = null
        )

        val errors = (result as ManualTowerInput.Result.Invalid).errors
        assertThat(errors.keys).containsAtLeast(
            ManualTowerInput.Field.MCC,
            ManualTowerInput.Field.MNC,
            ManualTowerInput.Field.TAC_LAC,
            ManualTowerInput.Field.CID,
            ManualTowerInput.Field.LATITUDE,
            ManualTowerInput.Field.LONGITUDE
        )
    }
}
