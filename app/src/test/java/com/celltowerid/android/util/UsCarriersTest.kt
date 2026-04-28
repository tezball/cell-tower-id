package com.celltowerid.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UsCarriersTest {

    // --- isKnownCarrier ---

    @Test
    fun `given AT&T MCC MNC when checking then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 410)).isTrue()
    }

    @Test
    fun `given T-Mobile MCC MNC when checking then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 260)).isTrue()
    }

    @Test
    fun `given Verizon MCC MNC when checking then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 12)).isTrue()
    }

    @Test
    fun `given unknown MCC MNC when checking then returns false`() {
        assertThat(UsCarriers.isKnownCarrier(999, 99)).isFalse()
    }

    @Test
    fun `given valid MCC but wrong MNC when checking then returns false`() {
        assertThat(UsCarriers.isKnownCarrier(310, 999)).isFalse()
    }

    // --- getCarrierName ---

    @Test
    fun `given AT&T MCC MNC when getting name then returns AT&T`() {
        assertThat(UsCarriers.getCarrierName(310, 410)).isEqualTo("AT&T")
    }

    @Test
    fun `given T-Mobile MCC MNC when getting name then returns T-Mobile`() {
        assertThat(UsCarriers.getCarrierName(310, 260)).isEqualTo("T-Mobile")
    }

    @Test
    fun `given Verizon MCC MNC when getting name then returns Verizon`() {
        assertThat(UsCarriers.getCarrierName(311, 480)).isEqualTo("Verizon")
    }

    @Test
    fun `given US Cellular MCC MNC when getting name then returns US Cellular`() {
        assertThat(UsCarriers.getCarrierName(311, 580)).isEqualTo("US Cellular")
    }

    @Test
    fun `given unknown MCC MNC when getting name then returns null`() {
        assertThat(UsCarriers.getCarrierName(999, 99)).isNull()
    }

    // --- isUsNetwork ---

    @Test
    fun `given MCC 310 when checking US network then returns true`() {
        assertThat(UsCarriers.isUsNetwork(310)).isTrue()
    }

    @Test
    fun `given MCC 311 when checking US network then returns true`() {
        assertThat(UsCarriers.isUsNetwork(311)).isTrue()
    }

    @Test
    fun `given MCC 316 when checking US network then returns true`() {
        assertThat(UsCarriers.isUsNetwork(316)).isTrue()
    }

    @Test
    fun `given MCC 234 for UK when checking US network then returns false`() {
        assertThat(UsCarriers.isUsNetwork(234)).isFalse()
    }

    @Test
    fun `given MCC 309 when checking US network then returns false`() {
        assertThat(UsCarriers.isUsNetwork(309)).isFalse()
    }

    @Test
    fun `given MCC 317 when checking US network then returns false`() {
        assertThat(UsCarriers.isUsNetwork(317)).isFalse()
    }
}
