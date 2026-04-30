package com.celltowerid.android.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.ui.viewmodel.LocateViewModel.BearingSource
import com.celltowerid.android.util.LocateMath.Waypoint
import com.celltowerid.android.util.LocateMode
import org.junit.Test

class LocateViewModelTest {

    // --- appendWaypoint: configurable threshold for new vs running-average update ---

    @Test
    fun `given empty waypoint list, when appendWaypoint, then returns single waypoint`() {
        val result = LocateViewModel.appendWaypoint(
            current = emptyList(),
            next = Waypoint(lat = 53.3, lon = -6.3, rsrpDbm = -90),
            minDistanceM = 2.0,
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
        val result = LocateViewModel.appendWaypoint(current, next, minDistanceM = 2.0)

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
        val result = LocateViewModel.appendWaypoint(current, next, minDistanceM = 2.0)

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

        val result = LocateViewModel.appendWaypoint(listOf(first), next, minDistanceM = 2.0)

        assertThat(result).hasSize(1)
    }

    @Test
    fun `given driving threshold of 10m and step of 5m, when appendWaypoint, then averages into last waypoint`() {
        // At driving speeds we widen the threshold so we don't pile up waypoints
        // every 100ms within a single car-length.
        val first = Waypoint(lat = 53.30000, lon = -6.30000, rsrpDbm = -90)
        val next = Waypoint(lat = 53.30005, lon = -6.30000, rsrpDbm = -85)  // ~5m

        val result = LocateViewModel.appendWaypoint(listOf(first), next, minDistanceM = 10.0)

        assertThat(result).hasSize(1)
    }

    // --- effectiveMode: manual override always wins ---

    @Test
    fun `given no manual override, when computing effective mode, then matches auto mode`() {
        assertThat(LocateViewModel.effectiveMode(LocateMode.WALKING, null))
            .isEqualTo(LocateMode.WALKING)
        assertThat(LocateViewModel.effectiveMode(LocateMode.DRIVING, null))
            .isEqualTo(LocateMode.DRIVING)
    }

    @Test
    fun `given manual driving override and auto walking, when computing effective mode, then driving`() {
        assertThat(LocateViewModel.effectiveMode(LocateMode.WALKING, LocateMode.DRIVING))
            .isEqualTo(LocateMode.DRIVING)
    }

    @Test
    fun `given manual walking override and auto driving, when computing effective mode, then walking`() {
        // Even if the GPS says we're driving, manual walking wins.
        assertThat(LocateViewModel.effectiveMode(LocateMode.DRIVING, LocateMode.WALKING))
            .isEqualTo(LocateMode.WALKING)
    }

    // --- resolveBearing: gradient → tower-estimate fallback → stale → none ---

    @Test
    fun `given fresh gradient bearing, when resolving, then uses gradient and is fresh`() {
        val res = LocateViewModel.resolveBearing(
            gradientBearing = 45.0,
            estimatedTower = Pair(53.3, -6.2),
            currentLocation = Pair(53.3, -6.3),
            lastResolved = 90.0,
        )

        assertThat(res.bearing).isEqualTo(45.0)
        assertThat(res.source).isEqualTo(BearingSource.GRADIENT)
        assertThat(res.isFresh).isTrue()
    }

    @Test
    fun `given no gradient but estimated tower and current location, when resolving, then derives bearing to tower`() {
        // Tower is due east of me -> bearing should be ~90.
        val res = LocateViewModel.resolveBearing(
            gradientBearing = null,
            estimatedTower = Pair(53.0, -5.999),
            currentLocation = Pair(53.0, -6.0),
            lastResolved = null,
        )

        assertThat(res.bearing).isNotNull()
        assertThat(res.bearing!!).isWithin(2.0).of(90.0)
        assertThat(res.source).isEqualTo(BearingSource.TOWER_ESTIMATE)
        assertThat(res.isFresh).isTrue()
    }

    @Test
    fun `given no gradient and no tower estimate but last resolved, when resolving, then returns stale`() {
        val res = LocateViewModel.resolveBearing(
            gradientBearing = null,
            estimatedTower = null,
            currentLocation = Pair(53.3, -6.3),
            lastResolved = 123.0,
        )

        assertThat(res.bearing).isEqualTo(123.0)
        assertThat(res.source).isEqualTo(BearingSource.STALE)
        assertThat(res.isFresh).isFalse()
    }

    @Test
    fun `given nothing at all, when resolving, then returns NONE with null bearing`() {
        val res = LocateViewModel.resolveBearing(
            gradientBearing = null,
            estimatedTower = null,
            currentLocation = null,
            lastResolved = null,
        )

        assertThat(res.bearing).isNull()
        assertThat(res.source).isEqualTo(BearingSource.NONE)
        assertThat(res.isFresh).isFalse()
    }

    @Test
    fun `given tower estimate but no current location, when resolving, then falls back to stale or none`() {
        // Without a current position we can't derive bearing-to-tower.
        val res = LocateViewModel.resolveBearing(
            gradientBearing = null,
            estimatedTower = Pair(53.3, -6.2),
            currentLocation = null,
            lastResolved = null,
        )

        assertThat(res.bearing).isNull()
        assertThat(res.source).isEqualTo(BearingSource.NONE)
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
