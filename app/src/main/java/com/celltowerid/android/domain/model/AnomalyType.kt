package com.celltowerid.android.domain.model

enum class AnomalyType(
    val displayName: String,
    val explanation: String,
    val drivingNote: String?
) {
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
    DOWNGRADE_3G(
        "3G Downgrade",
        "Your phone was forced to switch from a modern network (LTE/5G) to 3G (WCDMA/CDMA). Forced downgrades to older radio technologies can be a step toward a 2G attack, or can indicate a rogue base station that only speaks 3G.",
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
        "This tower's observed position is more than 20 km from where you've seen it before. A legitimate tower doesn't move — if one appears to have relocated, it could be a cloned base station. This is the highest-severity alert.",
        "If you had a GPS drift or the earlier sighting was inaccurate, this alert may be a false positive. Check if the distance seems plausible for GPS error."
    ),
    SUSPICIOUS_PROXIMITY(
        "Suspicious Proximity",
        "A tower is reporting a Timing Advance close to zero (you're within roughly 550 m) but its signal strength is only moderate. A real macro tower that close would saturate your signal. Portable IMSI catchers carried nearby often produce this pattern.",
        "Driving past a roadside tower can briefly put you this close. This alert is suppressed above walking speed."
    ),
    PCI_INSTABILITY(
        "PCI Instability",
        "This cell has been observed broadcasting a different Physical Cell ID (PCI) than before. Real base stations do not change their PCI — a change can indicate a cloned cell or an IMSI catcher impersonating a known tower.",
        null
    ),
    POPUP_TOWER(
        "Popup Tower",
        "A tower has appeared in an area where you've collected enough prior measurements that you'd expect to have seen it already, but it has no recorded sightings here in the last 7 days. New towers are sometimes deployed legitimately by carriers, but a tower that suddenly appears in a familiar location can also be an IMSI catcher.",
        "While driving, you naturally enter areas with new tower coverage. This alert is suppressed above walking speed."
    );
}
