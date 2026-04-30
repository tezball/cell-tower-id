package com.celltowerid.android.util

import com.celltowerid.android.domain.model.AnomalyEvent
import com.celltowerid.android.domain.model.AnomalySeverity
import com.celltowerid.android.domain.model.AnomalyType
import com.celltowerid.android.domain.model.CellTower
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlertIndexerTest {

    private fun anomaly(
        type: AnomalyType = AnomalyType.SIGNAL_ANOMALY,
        severity: AnomalySeverity = AnomalySeverity.MEDIUM,
        timestamp: Long = 1_000L,
        radio: RadioType? = RadioType.LTE,
        mcc: Int? = 310,
        mnc: Int? = 260,
        tacLac: Int? = 100,
        cid: Long? = 50331905L
    ): AnomalyEvent = AnomalyEvent(
        timestamp = timestamp,
        type = type,
        severity = severity,
        description = "test",
        cellRadio = radio,
        cellMcc = mcc,
        cellMnc = mnc,
        cellTacLac = tacLac,
        cellCid = cid
    )

    private fun lteTower(
        mcc: Int = 310,
        mnc: Int = 260,
        tacLac: Int = 100,
        cid: Long = 50331905L
    ) = CellTower(
        radio = RadioType.LTE, mcc = mcc, mnc = mnc, tacLac = tacLac, cid = cid,
        latitude = 0.0, longitude = 0.0
    )

    private fun gsmTower(cid: Long = 1234L) = CellTower(
        radio = RadioType.GSM, mcc = 310, mnc = 260, tacLac = 100, cid = cid,
        latitude = 0.0, longitude = 0.0
    )

    @Test
    fun `given empty event list, when indexing, then both maps empty`() {
        val index = AlertIndexer.index(emptyList())

        assertThat(index.nonLte).isEmpty()
        assertThat(index.lteByEnb).isEmpty()
    }

    @Test
    fun `given anomaly with null cell key, when indexing, then it is skipped`() {
        val orphan = anomaly(radio = null, mcc = null, mnc = null, tacLac = null, cid = null)

        val index = AlertIndexer.index(listOf(orphan))

        assertThat(index.nonLte).isEmpty()
        assertThat(index.lteByEnb).isEmpty()
    }

    @Test
    fun `given two non-LTE alerts on same cell with different severity, when indexing, then highest severity wins`() {
        val low = anomaly(severity = AnomalySeverity.LOW, timestamp = 2_000L,
            radio = RadioType.GSM, cid = 1234L)
        val high = anomaly(severity = AnomalySeverity.HIGH, timestamp = 1_000L,
            radio = RadioType.GSM, cid = 1234L)

        val index = AlertIndexer.index(listOf(low, high))
        val result = AlertIndexer.lookup(index, gsmTower(cid = 1234L))

        assertThat(result).isNotNull()
        assertThat(result!!.severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given two non-LTE alerts on same cell with equal severity, when indexing, then most recent wins`() {
        val older = anomaly(severity = AnomalySeverity.MEDIUM, timestamp = 1_000L,
            radio = RadioType.GSM, cid = 1234L,
            type = AnomalyType.SIGNAL_ANOMALY)
        val newer = anomaly(severity = AnomalySeverity.MEDIUM, timestamp = 5_000L,
            radio = RadioType.GSM, cid = 1234L,
            type = AnomalyType.LAC_TAC_CHANGE)

        val index = AlertIndexer.index(listOf(older, newer))
        val result = AlertIndexer.lookup(index, gsmTower(cid = 1234L))

        assertThat(result).isNotNull()
        assertThat(result!!.timestamp).isEqualTo(5_000L)
        assertThat(result.type).isEqualTo(AnomalyType.LAC_TAC_CHANGE)
    }

    @Test
    fun `given LTE alerts on different sectors of one eNB, when looking up, then any sector returns the eNB-bucket alert`() {
        val enbBase = 87_895L shl 8
        val sector1Alert = anomaly(severity = AnomalySeverity.HIGH, cid = enbBase or 1L)

        val index = AlertIndexer.index(listOf(sector1Alert))

        // Looking up by a *different* sector of the same eNB should still return the alert.
        val sector2Tower = lteTower(cid = enbBase or 2L)
        val result = AlertIndexer.lookup(index, sector2Tower)

        assertThat(result).isNotNull()
        assertThat(result!!.severity).isEqualTo(AnomalySeverity.HIGH)
    }

    @Test
    fun `given LTE alerts on multiple sectors of one eNB, when indexing, then highest-severity wins for the bucket`() {
        val enbBase = 87_895L shl 8
        val low = anomaly(severity = AnomalySeverity.LOW, cid = enbBase or 1L)
        val high = anomaly(severity = AnomalySeverity.HIGH, cid = enbBase or 2L)
        val medium = anomaly(severity = AnomalySeverity.MEDIUM, cid = enbBase or 3L)

        val index = AlertIndexer.index(listOf(low, high, medium))
        val result = AlertIndexer.lookup(index, lteTower(cid = enbBase or 1L))

        assertThat(result).isNotNull()
        assertThat(result!!.severity).isEqualTo(AnomalySeverity.HIGH)
        assertThat(result.cellCid).isEqualTo(enbBase or 2L)
    }

    @Test
    fun `given LTE alerts on different eNBs, when looking up by one eNB, then only that bucket is returned`() {
        val enbA = 1_000L shl 8
        val enbB = 2_000L shl 8
        val alertA = anomaly(severity = AnomalySeverity.HIGH, cid = enbA or 1L)
        val alertB = anomaly(severity = AnomalySeverity.LOW, cid = enbB or 1L)

        val index = AlertIndexer.index(listOf(alertA, alertB))

        val resultA = AlertIndexer.lookup(index, lteTower(cid = enbA or 5L))
        val resultB = AlertIndexer.lookup(index, lteTower(cid = enbB or 7L))

        assertThat(resultA?.severity).isEqualTo(AnomalySeverity.HIGH)
        assertThat(resultB?.severity).isEqualTo(AnomalySeverity.LOW)
    }

    @Test
    fun `given non-LTE alert, when looking up by tower with different cid, then returns null`() {
        val alert = anomaly(radio = RadioType.GSM, cid = 1234L)

        val index = AlertIndexer.index(listOf(alert))
        val result = AlertIndexer.lookup(index, gsmTower(cid = 9999L))

        assertThat(result).isNull()
    }

    @Test
    fun `given LTE alert with cid only differing in low 8 bits, when looking up GSM tower with same numeric cid, then returns null`() {
        val alert = anomaly(radio = RadioType.LTE, cid = 50331905L)

        val index = AlertIndexer.index(listOf(alert))
        // Same numeric cid, but radio is GSM — must not collide with the LTE eNB bucket.
        val result = AlertIndexer.lookup(index, gsmTower(cid = 50331905L))

        assertThat(result).isNull()
    }

    @Test
    fun `given alert and matching tower, when looking up, then alert returned`() {
        val alert = anomaly(radio = RadioType.NR, cid = 42L, mcc = 310, mnc = 410, tacLac = 200)
        val tower = CellTower(
            radio = RadioType.NR, mcc = 310, mnc = 410, tacLac = 200, cid = 42L,
            latitude = 0.0, longitude = 0.0
        )

        val index = AlertIndexer.index(listOf(alert))
        val result = AlertIndexer.lookup(index, tower)

        assertThat(result).isNotNull()
        assertThat(result!!.cellCid).isEqualTo(42L)
    }

    @Test
    fun `given indexed alerts, when calling severityRank, then HIGH greater than MEDIUM greater than LOW`() {
        // Tie-break ordering used by the indexer must be HIGH > MEDIUM > LOW.
        val low = anomaly(severity = AnomalySeverity.LOW, timestamp = 9_999L,
            radio = RadioType.GSM, cid = 1L)
        val medium = anomaly(severity = AnomalySeverity.MEDIUM, timestamp = 1L,
            radio = RadioType.GSM, cid = 1L)
        val high = anomaly(severity = AnomalySeverity.HIGH, timestamp = 1L,
            radio = RadioType.GSM, cid = 1L)

        val index = AlertIndexer.index(listOf(low, medium, high))
        val result = AlertIndexer.lookup(index, gsmTower(cid = 1L))

        // HIGH wins despite the much-newer LOW timestamp.
        assertThat(result?.severity).isEqualTo(AnomalySeverity.HIGH)
    }
}
