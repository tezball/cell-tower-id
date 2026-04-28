package com.celltowerid.android.ui.viewmodel

import com.celltowerid.android.domain.model.AnomalyEvent
import com.celltowerid.android.domain.model.AnomalySeverity
import com.celltowerid.android.domain.model.AnomalyType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnomalyViewModelSortTest {

    private fun event(ts: Long, sev: AnomalySeverity): AnomalyEvent =
        AnomalyEvent(
            timestamp = ts,
            type = AnomalyType.SIGNAL_ANOMALY,
            severity = sev,
            description = "t"
        )

    @Test
    fun `given mixed severities, when sortForDisplay, then HIGH first then MEDIUM then LOW`() {
        // Given
        val low = event(1_000L, AnomalySeverity.LOW)
        val med = event(2_000L, AnomalySeverity.MEDIUM)
        val high = event(500L, AnomalySeverity.HIGH)

        // When
        val result = AnomalyViewModelSort.sortForDisplay(listOf(low, med, high))

        // Then
        assertThat(result.map { it.severity })
            .containsExactly(AnomalySeverity.HIGH, AnomalySeverity.MEDIUM, AnomalySeverity.LOW)
            .inOrder()
    }

    @Test
    fun `given equal severities, when sortForDisplay, then newer timestamp first`() {
        // Given
        val older = event(1_000L, AnomalySeverity.HIGH)
        val newer = event(5_000L, AnomalySeverity.HIGH)
        val middle = event(3_000L, AnomalySeverity.HIGH)

        // When
        val result = AnomalyViewModelSort.sortForDisplay(listOf(older, newer, middle))

        // Then
        assertThat(result.map { it.timestamp }).containsExactly(5_000L, 3_000L, 1_000L).inOrder()
    }
}
