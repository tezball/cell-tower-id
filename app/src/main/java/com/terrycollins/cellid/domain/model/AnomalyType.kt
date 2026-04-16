package com.terrycollins.cellid.domain.model

enum class AnomalyType(val displayName: String) {
    UNKNOWN_TOWER("Unknown Tower"),
    SIGNAL_ANOMALY("Signal Anomaly"),
    DOWNGRADE_2G("2G Downgrade"),
    LAC_TAC_CHANGE("LAC/TAC Change"),
    TRANSIENT_TOWER("Transient Tower"),
    OPERATOR_MISMATCH("Operator Mismatch"),
    IMPOSSIBLE_MOVE("Impossible Tower Move");
}
