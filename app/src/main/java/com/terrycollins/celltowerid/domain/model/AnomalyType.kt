package com.terrycollins.celltowerid.domain.model

enum class AnomalyType(
    val displayName: String,
    val explanation: String,
    val drivingNote: String?
) {
    UNKNOWN_TOWER(
        "Unknown Tower",
        "This cell tower is not in the OpenCelliD database of known towers. It may be a newly deployed tower, a temporary site, or a rogue base station. If you see this repeatedly in the same location, it warrants investigation.",
        "Common when driving through areas not well-covered by the OpenCelliD database."
    ),
    SIGNAL_ANOMALY(
        "Signal Anomaly",
        "The signal strength from this tower is significantly stronger than the average for its operator in your area. IMSI catchers often broadcast at high power to force nearby phones to connect to them.",
        "When driving close to a tower, strong signals are normal. This alert is suppressed above walking speed but may still trigger near roadside towers."
    ),
    DOWNGRADE_2G(
        "2G Downgrade",
        "Your phone was forced to switch from a modern network (LTE/5G) to 2G (GSM). This is a classic IMSI catcher technique because 2G lacks mutual authentication, allowing the attacker to intercept calls and data. This is the most concerning alert type.",
        null
    ),
    LAC_TAC_CHANGE(
        "LAC/TAC Change",
        "The Location Area Code (LAC) or Tracking Area Code (TAC) changed unexpectedly on the same operator. IMSI catchers may use different area codes than legitimate towers. However, normal network handovers also cause area code changes.",
        "While driving, your phone frequently switches between coverage areas. This alert only fires if 3 or more rapid area code changes happen within 60 seconds, which can occur in areas with dense tower coverage."
    ),
    TRANSIENT_TOWER(
        "Transient Tower",
        "This tower appeared briefly then disappeared. Mobile IMSI catchers (e.g., mounted in vehicles) exhibit this pattern — they operate for a short time then move on. However, brief tower visibility can also have innocent explanations.",
        "While driving, you naturally pass through and out of tower coverage areas. A tower that appeared for a few minutes then vanished may simply be one you drove past. This is the most common false positive while driving."
    ),
    OPERATOR_MISMATCH(
        "Operator Mismatch",
        "This tower is broadcasting a network identity (MCC/MNC) that doesn't match any known carrier in your region. IMSI catchers sometimes use fabricated or mismatched operator codes. This alert is relatively rare and worth investigating.",
        null
    ),
    IMPOSSIBLE_MOVE(
        "Impossible Tower Move",
        "This tower's observed position is more than 20 km from its known location in the tower database. A legitimate tower doesn't move — if one appears to have relocated, it could be a cloned base station. This is the highest-severity alert.",
        "If the tower database has an inaccurate position for this tower, or your GPS had a drift, this alert may be a false positive. Check if the distance seems plausible for GPS error."
    );
}
