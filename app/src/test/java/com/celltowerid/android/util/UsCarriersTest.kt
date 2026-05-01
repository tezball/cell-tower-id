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

    // --- whitelist integrity ---

    @Test
    fun `given carrier list, when scanning, then no duplicate MCC MNC pairs exist`() {
        val pairs = UsCarriers.KNOWN_CARRIERS.map { it.mcc to it.mnc }
        assertThat(pairs).containsNoDuplicates()
    }

    @Test
    fun `given carrier list, when checking MCC range, then every entry is in US 310-316`() {
        UsCarriers.KNOWN_CARRIERS.forEach { carrier ->
            assertThat(carrier.mcc).isIn(310..316)
        }
    }

    // --- expanded AT&T allocations ---

    @Test
    fun `given AT&T 310-070, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 70)).isTrue()
    }

    @Test
    fun `given AT&T 310-380, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 380)).isTrue()
    }

    @Test
    fun `given AT&T 310-680, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 680)).isTrue()
    }

    @Test
    fun `given AT&T 310-980, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 980)).isTrue()
    }

    // --- expanded T-Mobile allocations ---

    @Test
    fun `given T-Mobile 310-026 legacy, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 26)).isTrue()
    }

    @Test
    fun `given T-Mobile 310-210, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 210)).isTrue()
    }

    @Test
    fun `given T-Mobile 310-490, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 490)).isTrue()
    }

    // --- expanded Verizon allocations ---

    @Test
    fun `given Verizon 310-010, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 10)).isTrue()
    }

    @Test
    fun `given Verizon 310-013, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(310, 13)).isTrue()
    }

    @Test
    fun `given Verizon 311-110, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(311, 110)).isTrue()
    }

    @Test
    fun `given Verizon 311-270 LTE block, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(311, 270)).isTrue()
    }

    @Test
    fun `given Verizon 311-289 LTE block, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(311, 289)).isTrue()
    }

    @Test
    fun `given Verizon 311-485, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(311, 485)).isTrue()
    }

    // --- expanded Sprint legacy / Dish allocations ---

    @Test
    fun `given Sprint legacy 311-880, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(311, 880)).isTrue()
    }

    @Test
    fun `given Dish 313-100, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(313, 100)).isTrue()
    }

    // --- expanded US Cellular allocations ---

    @Test
    fun `given US Cellular 311-221, when checking, then returns true`() {
        assertThat(UsCarriers.isKnownCarrier(311, 221)).isTrue()
    }

    // --- guard: clearly unallocated MNCs still flagged ---

    @Test
    fun `given MCC 310 with implausible MNC 555, when checking, then returns false`() {
        assertThat(UsCarriers.isKnownCarrier(310, 555)).isFalse()
    }

    @Test
    fun `given MCC 312 with unallocated MNC 999, when checking, then returns false`() {
        assertThat(UsCarriers.isKnownCarrier(312, 999)).isFalse()
    }
}
