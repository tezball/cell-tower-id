package com.celltowerid.android.ui.viewmodel

import com.celltowerid.android.domain.model.AnomalyEvent

/**
 * Pure sort helper extracted from AnomalyViewModel so it can be unit-tested
 * without instantiating an AndroidViewModel.
 *
 * Orders by severity descending (HIGH -> MEDIUM -> LOW), then by timestamp
 * descending (newer first).
 */
object AnomalyViewModelSort {
    fun sortForDisplay(list: List<AnomalyEvent>): List<AnomalyEvent> =
        list.sortedWith(
            compareByDescending<AnomalyEvent> { it.severity.ordinal }
                .thenByDescending { it.timestamp }
        )
}
