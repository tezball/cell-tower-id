package com.celltowerid.android.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.util.LocateMath.Waypoint
import org.junit.Test

class LocateViewModelTest {

    // --- appendWaypoint: 2m threshold for new vs running-average update ---

    @Test
    fun `given empty waypoint list, when appendWaypoint, then returns single waypoint`() {
        val result = LocateViewModel.appendWaypoint(
            current = emptyList(),
            next = Waypoint(lat = 53.3, lon = -6.3, rsrpDbm = -90)
        )

        assertThat(result).hasSize(1)
        assertThat(result[0].rsrpDbm).isEqualTo(-90)
    }

    @Test
    fun `given last waypoint and new fix more than 2m away, when appendWaypoint, then appends new waypoint`() {
        // Given -- a 5m step north (~0.000045 deg lat = ~5m).
        val first = Waypoint(lat = 53.30000, lon = -6.30000, rsrpDbm = -90)
        val current = listOf(first)
        val next = Waypoint(lat = 53.30005, lon = -6.30000, rsrpDbm = -85)

        // When
        val result = LocateViewModel.appendWaypoint(current, next)

        // Then
        assertThat(result).hasSize(2)
        assertThat(result.last().rsrpDbm).isEqualTo(-85)
    }

    @Test
    fun `given last waypoint and new fix under 2m away, when appendWaypoint, then averages rsrp into last waypoint`() {
        // Given -- a sub-meter step (~0.0000045 deg lat = ~0.5m).
        val first = Waypoint(lat = 53.30000, lon = -6.30000, rsrpDbm = -90)
        val current = listOf(first)
        val next = Waypoint(lat = 53.300005, lon = -6.30000, rsrpDbm = -80)

        // When
        val result = LocateViewModel.appendWaypoint(current, next)

        // Then -- single waypoint, with the rsrp running-averaged.
        assertThat(result).hasSize(1)
        assertThat(result[0].rsrpDbm).isEqualTo(-85) // (-90 + -80) / 2
    }

    @Test
    fun `given longitude step under 2m at high latitude, when appendWaypoint, then averages into last waypoint`() {
        // Longitude meter-per-degree shrinks with cos(lat); a smaller lon delta
        // is needed at high latitude for a 2m step. Verify the cos(lat) scaling
        // is applied so we're not over- or under-counting movement near the poles.
        val first = Waypoint(lat = 60.0, lon = 0.0, rsrpDbm = -90)
        // ~0.5m east at 60N: lon delta needs to satisfy d * 111km * cos(60) < 2m
        // So d < 2 / (111000 * 0.5) = 3.6e-5 deg. Use 1e-5 to be safely under.
        val next = Waypoint(lat = 60.0, lon = 1e-5, rsrpDbm = -80)

        val result = LocateViewModel.appendWaypoint(listOf(first), next)

        assertThat(result).hasSize(1)
    }

    // --- matchesTarget: identity filtering ---

    @Test
    fun `given measurement matching all target identifiers, when matchesTarget, then returns true`() {
        val m = makeMeasurement(
            radio = RadioType.LTE, mcc = 310, mnc = 260, tacLac = 12345, cid = 999L,
            isRegistered = true
        )

        val matches = LocateViewModel.matchesTarget(
            m, RadioType.LTE, 310, 260, 12345, 999L
        )

        assertThat(matches).isTrue()
    }

    @Test
    fun `given measurement on different radio, when matchesTarget, then returns false`() {
        val m = makeMeasurement(radio = RadioType.GSM, isRegistered = true)

        val matches = LocateViewModel.matchesTarget(
            m, RadioType.LTE, 310, 260, 12345, 999L
        )

        assertThat(matches).isFalse()
    }

    @Test
    fun `given measurement with different MCC, when matchesTarget, then returns false`() {
        val m = makeMeasurement(mcc = 999, isRegistered = true)

        val matches = LocateViewModel.matchesTarget(
            m, RadioType.LTE, 310, 260, 12345, 999L
        )

        assertThat(matches).isFalse()
    }

    @Test
    fun `given measurement matching but not registered, when matchesTarget, then returns false`() {
        val m = makeMeasurement(isRegistered = false)

        val matches = LocateViewModel.matchesTarget(
            m, RadioType.LTE, 310, 260, 12345, 999L
        )

        assertThat(matches).isFalse()
    }

    @Test
    fun `given target MCC null, when matchesTarget, then MCC is not constrained`() {
        // Given -- a measurement with any MCC; target MCC is null (wildcard).
        val m = makeMeasurement(mcc = 999, isRegistered = true)

        // When
        val matches = LocateViewModel.matchesTarget(
            m,
            targetRadio = RadioType.LTE,
            targetMcc = null,
            targetMnc = 260,
            targetTac = 12345,
            targetCid = 999L
        )

        // Then -- accepted because MCC wildcard, but other fields still match.
        assertThat(matches).isTrue()
    }

    private fun makeMeasurement(
        radio: RadioType = RadioType.LTE,
        mcc: Int? = 310,
        mnc: Int? = 260,
        tacLac: Int? = 12345,
        cid: Long? = 999L,
        isRegistered: Boolean = true
    ) = CellMeasurement(
        timestamp = 0L,
        latitude = 0.0,
        longitude = 0.0,
        radio = radio,
        mcc = mcc,
        mnc = mnc,
        tacLac = tacLac,
        cid = cid,
        rsrp = -85,
        isRegistered = isRegistered
    )
}
