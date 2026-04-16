package com.terrycollins.cellid.domain.model

enum class SignalQuality(val label: String, val colorHex: String) {
    EXCELLENT("Excellent", "#00C853"),
    GOOD("Good", "#64DD17"),
    FAIR("Fair", "#FFD600"),
    POOR("Poor", "#FF6D00"),
    VERY_POOR("Very Poor", "#D50000"),
    NO_SIGNAL("No Signal", "#424242");
}
