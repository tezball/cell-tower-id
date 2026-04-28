package com.terrycollins.celltowerid.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TowerInfoFormatterTest {

    @Test
    fun `given known US carrier, when formatting title, then shows radio and carrier name`() {
        // When
        val title = TowerInfoFormatter.formatTitle("LTE", mcc = 310, mnc = 410)

        // Then
        assertThat(title).isEqualTo("LTE - AT&T")
    }

    @Test
    fun `given unknown MCC and MNC, when formatting title, then falls back to numeric code`() {
        // When
        val title = TowerInfoFormatter.formatTitle("NR", mcc = 999, mnc = 42)

        // Then
        assertThat(title).isEqualTo("NR - 999/42")
    }

    @Test
    fun `given LTE tower, when formatting identity, then includes eNB and sector derived from CID`() {
        // Given - cid shr 8 = 87895, cid and 0xFF = 3
        val cid = (87_895L shl 8) or 3L

        // When
        val identity = TowerInfoFormatter.formatIdentity("LTE", cid = cid, tacLac = 41002)

        // Then
        assertThat(identity).isEqualTo("CID: 22501123 | TAC: 41002 | eNB: 87895 Sector: 3")
    }

    @Test
    fun `given non-LTE tower, when formatting identity, then omits eNB and sector`() {
        // When
        val identity = TowerInfoFormatter.formatIdentity("GSM", cid = 12345L, tacLac = 678)

        // Then
        assertThat(identity).isEqualTo("CID: 12345 | LAC: 678")
    }

    @Test
    fun `given NR tower, when formatting identity, then uses TAC label and omits eNB`() {
        // When
        val identity = TowerInfoFormatter.formatIdentity("NR", cid = 9_876_543L, tacLac = 1024)

        // Then
        assertThat(identity).isEqualTo("CID: 9876543 | TAC: 1024")
    }

    @Test
    fun `given tower without range, when formatting location, then omits range suffix`() {
        // When
        val location = TowerInfoFormatter.formatLocation(40.123456, -74.567890, rangeMeters = null)

        // Then
        assertThat(location).isEqualTo("40.123456, -74.567890")
    }

    @Test
    fun `given tower with range, when formatting location, then includes plus-minus range in meters`() {
        // When
        val location = TowerInfoFormatter.formatLocation(40.123456, -74.567890, rangeMeters = 500)

        // Then
        assertThat(location).isEqualTo("40.123456, -74.567890 (\u00B1500m)")
    }

    @Test
    fun `given coordinates with many decimals, when formatting location, then rounds to six decimals`() {
        // When
        val location = TowerInfoFormatter.formatLocation(40.12345678, -74.98765432, rangeMeters = null)

        // Then
        assertThat(location).isEqualTo("40.123457, -74.987654")
    }

    @Test
    fun `given rsrp -82 and timestamp 14 minutes ago, when formatBestReading, then returns dBm and minutes-ago`() {
        // Given
        val now = 1_700_000_000_000L
        val ts = now - 14 * 60_000L

        // When
        val text = TowerInfoFormatter.formatBestReading(rsrp = -82, rssi = null, timestampMs = ts, nowMs = now)

        // Then
        assertThat(text).isEqualTo("Best: -82 dBm · 14m ago")
    }

    @Test
    fun `given GSM reading with null rsrp and rssi -75, when formatBestReading, then falls back to RSSI value`() {
        // Given
        val now = 1_700_000_000_000L
        val ts = now - 30_000L

        // When
        val text = TowerInfoFormatter.formatBestReading(rsrp = null, rssi = -75, timestampMs = ts, nowMs = now)

        // Then
        assertThat(text).isEqualTo("Best: -75 dBm · just now")
    }

    @Test
    fun `given null rsrp and null rssi, when formatBestReading, then returns null`() {
        // When
        val text = TowerInfoFormatter.formatBestReading(rsrp = null, rssi = null, timestampMs = 0L, nowMs = 0L)

        // Then
        assertThat(text).isNull()
    }

    @Test
    fun `given timestamp 3 hours ago, when formatBestReading, then uses hours-ago suffix`() {
        // Given
        val now = 1_700_000_000_000L
        val ts = now - 3 * 60 * 60_000L

        // When
        val text = TowerInfoFormatter.formatBestReading(rsrp = -90, rssi = null, timestampMs = ts, nowMs = now)

        // Then
        assertThat(text).isEqualTo("Best: -90 dBm · 3h ago")
    }

    @Test
    fun `given timestamp 2 days ago, when formatBestReading, then uses days-ago suffix`() {
        // Given
        val now = 1_700_000_000_000L
        val ts = now - 2 * 24 * 60 * 60_000L

        // When
        val text = TowerInfoFormatter.formatBestReading(rsrp = -100, rssi = null, timestampMs = ts, nowMs = now)

        // Then
        assertThat(text).isEqualTo("Best: -100 dBm · 2d ago")
    }
}
