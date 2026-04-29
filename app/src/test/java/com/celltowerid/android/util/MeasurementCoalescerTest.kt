package com.celltowerid.android.util

import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MeasurementCoalescerTest {

    @Test
    fun `given current with null TA but history has TA, when coalesced, then TA filled from history`() {
        // The "tower from cells list" path opens the latest measurement; if that
        // scan dropped TA but a recent scan captured it, the tower-detail view
        // should still surface TA from history.
        val current = base(timingAdvance = null, rsrp = -100)
        val history = listOf(
            base(timingAdvance = 1, rsrp = -95, timestamp = 100L),
        )

        val merged = MeasurementCoalescer.coalesce(current, history)

        assertThat(merged.timingAdvance).isEqualTo(1)
        assertThat(merged.rsrp).isEqualTo(-100) // current wins when current is non-null
    }

    @Test
    fun `given current with non-null TA, when coalesced, then current TA preserved over history`() {
        val current = base(timingAdvance = 5)
        val history = listOf(base(timingAdvance = 1, timestamp = 100L))

        val merged = MeasurementCoalescer.coalesce(current, history)

        assertThat(merged.timingAdvance).isEqualTo(5)
    }

    @Test
    fun `given multiple history entries, when coalesced, then most recent non-null wins`() {
        val current = base(timingAdvance = null, rsrq = null)
        val history = listOf(
            base(timingAdvance = 9, rsrq = -10, timestamp = 300L),
            base(timingAdvance = 7, rsrq = -12, timestamp = 200L),
            base(timingAdvance = 1, rsrq = -15, timestamp = 100L),
        )

        val merged = MeasurementCoalescer.coalesce(current, history)

        assertThat(merged.timingAdvance).isEqualTo(9)
        assertThat(merged.rsrq).isEqualTo(-10)
    }

    @Test
    fun `given history with mixed nulls, when coalesced, then each field independently picks newest non-null`() {
        val current = base(timingAdvance = null, rsrq = null, sinr = null)
        val history = listOf(
            base(timingAdvance = null, rsrq = -10, sinr = null, timestamp = 300L),
            base(timingAdvance = 4, rsrq = null, sinr = null, timestamp = 200L),
            base(timingAdvance = null, rsrq = null, sinr = 8, timestamp = 100L),
        )

        val merged = MeasurementCoalescer.coalesce(current, history)

        assertThat(merged.timingAdvance).isEqualTo(4)
        assertThat(merged.rsrq).isEqualTo(-10)
        assertThat(merged.sinr).isEqualTo(8)
    }

    @Test
    fun `given empty history, when coalesced, then current returned unchanged`() {
        val current = base(timingAdvance = null, rsrp = -100)

        val merged = MeasurementCoalescer.coalesce(current, emptyList())

        assertThat(merged).isEqualTo(current)
    }

    @Test
    fun `given history sorted ascending by timestamp, when coalesced, then still picks newest non-null`() {
        // History order is intentionally varied; the coalescer should not assume input order.
        val current = base(timingAdvance = null)
        val history = listOf(
            base(timingAdvance = 1, timestamp = 100L),
            base(timingAdvance = 9, timestamp = 300L),
            base(timingAdvance = 5, timestamp = 200L),
        )

        val merged = MeasurementCoalescer.coalesce(current, history)

        assertThat(merged.timingAdvance).isEqualTo(9)
    }

    @Test
    fun `given identity fields differ in history, when coalesced, then identity from current is preserved`() {
        // History could contain stale identity (e.g. operatorName changed); we
        // never overwrite cell identity from history.
        val current = base(operatorName = null, mcc = 310, mnc = 260)
        val history = listOf(
            base(operatorName = "T-Mobile", mcc = 310, mnc = 260, timestamp = 100L),
        )

        val merged = MeasurementCoalescer.coalesce(current, history)

        assertThat(merged.operatorName).isEqualTo("T-Mobile")
        assertThat(merged.mcc).isEqualTo(310)
        assertThat(merged.mnc).isEqualTo(260)
    }

    @Test
    fun `given current and history both null on a field, when coalesced, then field stays null`() {
        val current = base(cqi = null)
        val history = listOf(base(cqi = null, timestamp = 100L))

        val merged = MeasurementCoalescer.coalesce(current, history)

        assertThat(merged.cqi).isNull()
    }

    private fun base(
        timestamp: Long = 1000L,
        timingAdvance: Int? = null,
        rsrp: Int? = null,
        rsrq: Int? = null,
        sinr: Int? = null,
        cqi: Int? = null,
        operatorName: String? = null,
        mcc: Int? = 310,
        mnc: Int? = 260,
    ): CellMeasurement = CellMeasurement(
        timestamp = timestamp,
        latitude = 37.7749,
        longitude = -122.4194,
        radio = RadioType.LTE,
        mcc = mcc,
        mnc = mnc,
        tacLac = 12345,
        cid = 50331905L,
        timingAdvance = timingAdvance,
        rsrp = rsrp,
        rsrq = rsrq,
        sinr = sinr,
        cqi = cqi,
        operatorName = operatorName,
        isRegistered = true,
    )
}
