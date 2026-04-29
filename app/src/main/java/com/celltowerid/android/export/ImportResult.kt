package com.celltowerid.android.export

import com.celltowerid.android.domain.model.CellMeasurement

data class ImportResult(
    val format: ExportFormat,
    val measurements: List<CellMeasurement>
)
