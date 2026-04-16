package com.terrycollins.cellid.util

object UsCarriers {
    data class Carrier(val mcc: Int, val mnc: Int, val name: String)

    val KNOWN_CARRIERS = listOf(
        Carrier(310, 410, "AT&T"),
        Carrier(310, 280, "AT&T"),
        Carrier(310, 150, "AT&T"),
        Carrier(311, 180, "AT&T"),
        Carrier(310, 260, "T-Mobile"),
        Carrier(310, 200, "T-Mobile"),
        Carrier(310, 250, "T-Mobile"),
        Carrier(310, 160, "T-Mobile"),
        Carrier(311, 490, "T-Mobile"),
        Carrier(311, 882, "T-Mobile"),
        Carrier(310, 12, "Verizon"),
        Carrier(311, 480, "Verizon"),
        Carrier(311, 481, "Verizon"),
        Carrier(310, 4, "Verizon"),
        Carrier(310, 890, "Verizon"),
        Carrier(311, 580, "US Cellular"),
        Carrier(311, 220, "US Cellular"),
        Carrier(312, 530, "Dish/Boost"),
        Carrier(310, 120, "Sprint (T-Mobile)"),
        Carrier(312, 190, "Sprint (T-Mobile)"),
        Carrier(311, 870, "T-Mobile"),
        Carrier(310, 310, "T-Mobile"),
        Carrier(310, 240, "T-Mobile"),
        Carrier(310, 660, "T-Mobile"),
        Carrier(311, 882, "T-Mobile")
    )

    fun isKnownCarrier(mcc: Int, mnc: Int): Boolean =
        KNOWN_CARRIERS.any { it.mcc == mcc && it.mnc == mnc }

    fun getCarrierName(mcc: Int, mnc: Int): String? =
        KNOWN_CARRIERS.find { it.mcc == mcc && it.mnc == mnc }?.name

    fun isUsNetwork(mcc: Int): Boolean = mcc in 310..316
}
