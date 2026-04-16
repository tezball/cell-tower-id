package com.terrycollins.celltowerid.domain.model

enum class AnomalySeverity(val displayName: String, val colorHex: String) {
    LOW("Low", "#FFD600"),
    MEDIUM("Medium", "#FF6D00"),
    HIGH("High", "#D50000");
}
