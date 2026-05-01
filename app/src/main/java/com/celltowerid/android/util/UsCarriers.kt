package com.celltowerid.android.util

object UsCarriers {
    data class Carrier(val mcc: Int, val mnc: Int, val name: String)

    val KNOWN_CARRIERS = listOf(
        // AT&T (and former Cingular / Cricket allocations now operated by AT&T)
        Carrier(310, 70, "AT&T"),
        Carrier(310, 90, "AT&T"),
        Carrier(310, 150, "AT&T"),
        Carrier(310, 170, "AT&T"),
        Carrier(310, 280, "AT&T"),
        Carrier(310, 380, "AT&T"),
        Carrier(310, 410, "AT&T"),
        Carrier(310, 560, "AT&T"),
        Carrier(310, 680, "AT&T"),
        Carrier(310, 980, "AT&T"),
        Carrier(311, 180, "AT&T"),

        // T-Mobile US (including legacy SunCom / VoiceStream / MetroPCS allocations)
        Carrier(310, 26, "T-Mobile"),
        Carrier(310, 160, "T-Mobile"),
        Carrier(310, 200, "T-Mobile"),
        Carrier(310, 210, "T-Mobile"),
        Carrier(310, 220, "T-Mobile"),
        Carrier(310, 230, "T-Mobile"),
        Carrier(310, 240, "T-Mobile"),
        Carrier(310, 250, "T-Mobile"),
        Carrier(310, 260, "T-Mobile"),
        Carrier(310, 270, "T-Mobile"),
        Carrier(310, 300, "T-Mobile"),
        Carrier(310, 310, "T-Mobile"),
        Carrier(310, 490, "T-Mobile"),
        Carrier(310, 660, "T-Mobile"),
        Carrier(310, 800, "T-Mobile"),
        Carrier(311, 490, "T-Mobile"),
        Carrier(311, 660, "T-Mobile"),
        Carrier(311, 870, "T-Mobile"),
        Carrier(311, 882, "T-Mobile"),

        // Verizon Wireless (including legacy Alltel / Cellco allocations)
        Carrier(310, 4, "Verizon"),
        Carrier(310, 5, "Verizon"),
        Carrier(310, 6, "Verizon"),
        Carrier(310, 10, "Verizon"),
        Carrier(310, 12, "Verizon"),
        Carrier(310, 13, "Verizon"),
        Carrier(310, 590, "Verizon"),
        Carrier(310, 890, "Verizon"),
        Carrier(310, 910, "Verizon"),
        Carrier(311, 110, "Verizon"),
        Carrier(311, 270, "Verizon"),
        Carrier(311, 271, "Verizon"),
        Carrier(311, 272, "Verizon"),
        Carrier(311, 273, "Verizon"),
        Carrier(311, 274, "Verizon"),
        Carrier(311, 275, "Verizon"),
        Carrier(311, 276, "Verizon"),
        Carrier(311, 277, "Verizon"),
        Carrier(311, 278, "Verizon"),
        Carrier(311, 279, "Verizon"),
        Carrier(311, 280, "Verizon"),
        Carrier(311, 281, "Verizon"),
        Carrier(311, 282, "Verizon"),
        Carrier(311, 283, "Verizon"),
        Carrier(311, 284, "Verizon"),
        Carrier(311, 285, "Verizon"),
        Carrier(311, 286, "Verizon"),
        Carrier(311, 287, "Verizon"),
        Carrier(311, 288, "Verizon"),
        Carrier(311, 289, "Verizon"),
        Carrier(311, 390, "Verizon"),
        Carrier(311, 480, "Verizon"),
        Carrier(311, 481, "Verizon"),
        Carrier(311, 482, "Verizon"),
        Carrier(311, 483, "Verizon"),
        Carrier(311, 484, "Verizon"),
        Carrier(311, 485, "Verizon"),
        Carrier(311, 486, "Verizon"),
        Carrier(311, 487, "Verizon"),
        Carrier(311, 488, "Verizon"),
        Carrier(311, 489, "Verizon"),

        // US Cellular
        Carrier(311, 220, "US Cellular"),
        Carrier(311, 221, "US Cellular"),
        Carrier(311, 222, "US Cellular"),
        Carrier(311, 223, "US Cellular"),
        Carrier(311, 224, "US Cellular"),
        Carrier(311, 225, "US Cellular"),
        Carrier(311, 226, "US Cellular"),
        Carrier(311, 227, "US Cellular"),
        Carrier(311, 228, "US Cellular"),
        Carrier(311, 229, "US Cellular"),
        Carrier(311, 580, "US Cellular"),

        // Sprint legacy (now operated by T-Mobile)
        Carrier(310, 120, "Sprint (T-Mobile)"),
        Carrier(311, 880, "Sprint (T-Mobile)"),
        Carrier(311, 881, "Sprint (T-Mobile)"),
        Carrier(311, 883, "Sprint (T-Mobile)"),
        Carrier(311, 884, "Sprint (T-Mobile)"),
        Carrier(311, 885, "Sprint (T-Mobile)"),
        Carrier(311, 886, "Sprint (T-Mobile)"),
        Carrier(311, 887, "Sprint (T-Mobile)"),
        Carrier(311, 888, "Sprint (T-Mobile)"),
        Carrier(311, 889, "Sprint (T-Mobile)"),
        Carrier(312, 190, "Sprint (T-Mobile)"),

        // Dish Wireless / Boost Mobile
        Carrier(312, 530, "Dish/Boost"),
        Carrier(313, 100, "Dish/Boost"),
    )

    fun isKnownCarrier(mcc: Int, mnc: Int): Boolean =
        KNOWN_CARRIERS.any { it.mcc == mcc && it.mnc == mnc }

    fun getCarrierName(mcc: Int, mnc: Int): String? =
        KNOWN_CARRIERS.find { it.mcc == mcc && it.mnc == mnc }?.name

    fun isUsNetwork(mcc: Int): Boolean = mcc in 310..316
}
