package com.celltowerid.android.ui.dialog

import com.celltowerid.android.domain.model.RadioType

/**
 * Pure-logic parser/validator for the Add Tower dialog. Lives outside the
 * Fragment so it can be exercised without Robolectric.
 */
object ManualTowerInput {

    enum class Field { MCC, MNC, TAC_LAC, CID, LATITUDE, LONGITUDE, PCI }

    sealed class Result {
        data class Valid(
            val radio: RadioType,
            val mcc: Int,
            val mnc: Int,
            val tacLac: Int,
            val cid: Long,
            val latitude: Double,
            val longitude: Double,
            val pci: Int?
        ) : Result()

        data class Invalid(val errors: Map<Field, String>) : Result()
    }

    fun parse(
        radio: RadioType,
        mcc: String,
        mnc: String,
        tacLac: String,
        cid: String,
        latitude: String,
        longitude: String,
        pci: String?
    ): Result {
        val errors = mutableMapOf<Field, String>()

        val mccVal = mcc.trim().toIntOrNull()
        if (mccVal == null || mccVal !in 0..999) {
            errors[Field.MCC] = "MCC must be 0–999"
        }
        val mncVal = mnc.trim().toIntOrNull()
        if (mncVal == null || mncVal !in 0..999) {
            errors[Field.MNC] = "MNC must be 0–999"
        }
        val tacLacVal = tacLac.trim().toIntOrNull()
        if (tacLacVal == null || tacLacVal < 0) {
            errors[Field.TAC_LAC] = "TAC/LAC must be a non-negative integer"
        }
        val cidVal = cid.trim().toLongOrNull()
        if (cidVal == null || cidVal < 0) {
            errors[Field.CID] = "CID must be a non-negative integer"
        }
        val latVal = latitude.trim().toDoubleOrNull()
        if (latVal == null || latVal !in -90.0..90.0) {
            errors[Field.LATITUDE] = "Latitude must be between -90 and 90"
        }
        val lonVal = longitude.trim().toDoubleOrNull()
        if (lonVal == null || lonVal !in -180.0..180.0) {
            errors[Field.LONGITUDE] = "Longitude must be between -180 and 180"
        }
        val pciTrimmed = pci?.trim().orEmpty()
        val pciVal: Int? = if (pciTrimmed.isEmpty()) {
            null
        } else {
            val parsed = pciTrimmed.toIntOrNull()
            if (parsed == null || parsed < 0) {
                errors[Field.PCI] = "PCI must be a non-negative integer"
                null
            } else {
                parsed
            }
        }

        if (errors.isNotEmpty()) return Result.Invalid(errors)

        return Result.Valid(
            radio = radio,
            mcc = requireNotNull(mccVal) { "mcc null after validation" },
            mnc = requireNotNull(mncVal) { "mnc null after validation" },
            tacLac = requireNotNull(tacLacVal) { "tacLac null after validation" },
            cid = requireNotNull(cidVal) { "cid null after validation" },
            latitude = requireNotNull(latVal) { "lat null after validation" },
            longitude = requireNotNull(lonVal) { "lon null after validation" },
            pci = pciVal
        )
    }
}
