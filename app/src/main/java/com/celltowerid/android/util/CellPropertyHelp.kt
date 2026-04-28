package com.celltowerid.android.util

/**
 * Plain-language explanations of cell-tower properties for users who
 * have never worked with mobile networks before. Each entry gives a
 * short "what is it?" plus, where it helps, a "what's normal?" so a
 * non-expert can judge whether a value looks ordinary or suspicious.
 */
object CellPropertyHelp {

    data class Help(val title: String, val body: String)

    enum class Key {
        TECHNOLOGY,
        MCC,
        MNC,
        TAC,
        LAC,
        E_UTRAN_CID,
        ENODEB_ID,
        SECTOR_ID,
        NR_NCI,
        CID_GENERIC,
        PCI,
        PSC,
        BSIC,
        EARFCN,
        NRARFCN,
        UARFCN,
        ARFCN,
        BAND,
        BANDWIDTH,
        OPERATOR,
        CARRIER,
        REGISTERED,
        SIGNAL_QUALITY,
        SIGNAL_LEVEL,
        RSRP,
        RSRQ,
        RSSI,
        SINR,
        CQI,
        TIMING_ADVANCE,
        EST_DISTANCE,
        TOWER_LOCATION,
        TOWER_LAT,
        TOWER_LON,
        YOUR_LAT,
        YOUR_LON,
        GPS_ACCURACY,
        OBSERVED_AT
    }

    private val entries: Map<Key, Help> = mapOf(
        Key.TECHNOLOGY to Help(
            "Radio technology",
            "The generation of mobile network this cell uses. GSM is 2G (voice/SMS, slow data), " +
            "WCDMA is 3G, LTE is 4G, and NR is 5G. If your phone is on LTE or NR you're on a " +
            "modern network; suddenly dropping to GSM in a 4G/5G area can be a sign of a forced " +
            "downgrade."
        ),
        Key.MCC to Help(
            "MCC — Mobile Country Code",
            "A 3-digit number identifying the country the network belongs to. For example 272 = " +
            "Ireland, 310 = USA, 234 = UK. If the MCC doesn't match the country you're in, the " +
            "cell is almost certainly not legitimate."
        ),
        Key.MNC to Help(
            "MNC — Mobile Network Code",
            "Identifies the carrier within the country. Combined with MCC it uniquely names an " +
            "operator (e.g. 272/5 = Three Ireland, 310/260 = T-Mobile US). An unfamiliar MNC " +
            "for a known country is worth a second look."
        ),
        Key.TAC to Help(
            "TAC — Tracking Area Code (LTE/5G)",
            "A group of cells the network uses to page your phone. When you move between tracking " +
            "areas your phone re-registers. A stable TAC is normal; rapidly changing TACs in one " +
            "spot (with the same carrier) can indicate a rogue base station."
        ),
        Key.LAC to Help(
            "LAC — Location Area Code (2G/3G)",
            "The 2G/3G equivalent of TAC — a group of cells under one registration area. Same " +
            "role: your phone re-registers when it crosses into a new LAC."
        ),
        Key.E_UTRAN_CID to Help(
            "E-UTRAN Cell ID",
            "A 28-bit number uniquely identifying an LTE cell within a carrier. It's composed of " +
            "the eNodeB ID (the physical site) in the top 20 bits and the Sector ID in the bottom " +
            "8 bits. Paired with MCC/MNC/TAC it globally identifies this exact cell."
        ),
        Key.ENODEB_ID to Help(
            "eNodeB ID",
            "The physical LTE base station (the tower or rooftop site). One eNodeB usually hosts " +
            "several sectors (cells). Two cells with the same eNodeB ID are at the same physical " +
            "location."
        ),
        Key.SECTOR_ID to Help(
            "Sector ID",
            "Which sector of the base station you're talking to. Most macro towers have 3 sectors " +
            "(0–2 or 1–3) pointing in different directions. Values higher than ~6 usually mean an " +
            "indoor small cell, a refarmed carrier, or an unusual deployment."
        ),
        Key.NR_NCI to Help(
            "NCI — NR Cell Identity (5G)",
            "The 5G equivalent of the LTE E-UTRAN CID: a globally unique (within the carrier) " +
            "identifier for a 5G cell, combined with the TAC and operator codes."
        ),
        Key.CID_GENERIC to Help(
            "Cell ID",
            "A number identifying this specific cell within the carrier. Together with MCC, MNC " +
            "and LAC/TAC it uniquely names the cell."
        ),
        Key.PCI to Help(
            "PCI — Physical Cell Identity",
            "A small number (0–503 for LTE, 0–1007 for 5G) the cell broadcasts so phones can tell " +
            "it apart from neighbors. It is not unique globally — operators reuse PCIs across a " +
            "country — so the same PCI showing up in two very different places is not unusual by " +
            "itself. Suspicious when a known PCI suddenly appears with a wrong MCC/MNC or CID."
        ),
        Key.PSC to Help(
            "PSC — Primary Scrambling Code (3G)",
            "The WCDMA equivalent of PCI. It's a code the cell uses to separate its signal from " +
            "neighbors. Like PCI, it's reused across the network and not globally unique."
        ),
        Key.BSIC to Help(
            "BSIC — Base Station Identity Code (2G)",
            "A short code a GSM cell transmits so nearby phones can distinguish it from neighbors " +
            "using the same frequency. It is not globally unique."
        ),
        Key.EARFCN to Help(
            "EARFCN — LTE channel number",
            "The radio channel the LTE cell transmits on. You can look up an EARFCN online to find " +
            "the frequency and LTE band. Different EARFCNs roughly mean different frequency layers " +
            "of the same carrier."
        ),
        Key.NRARFCN to Help(
            "NR-ARFCN — 5G channel number",
            "The radio channel a 5G cell uses. Low numbers are sub-6 GHz (what most phones see); " +
            "very high numbers are mmWave."
        ),
        Key.UARFCN to Help(
            "UARFCN — 3G channel number",
            "The radio channel for a WCDMA (3G) cell. Identifies which band and frequency the cell " +
            "uses."
        ),
        Key.ARFCN to Help(
            "ARFCN — 2G channel number",
            "The radio channel for a GSM (2G) cell. In the 900 band ARFCNs 1–124 are common; in " +
            "1800 the range is different."
        ),
        Key.BAND to Help(
            "Band",
            "The frequency band this cell uses. Low bands (600/700/800 MHz) travel far and " +
            "penetrate buildings well but carry less data; high bands (2100/2600/3500 MHz) are " +
            "faster but shorter range. Most urban towers use several bands at once."
        ),
        Key.BANDWIDTH to Help(
            "Bandwidth",
            "How wide the cell's radio channel is, in MHz. Wider = more capacity. Typical LTE: 5, " +
            "10, 15 or 20 MHz. 5G can go to 100 MHz on sub-6 and much more on mmWave."
        ),
        Key.OPERATOR to Help(
            "Operator (broadcast name)",
            "The human-readable name the cell broadcasts — e.g. \"Three\", \"Vodafone IE\". " +
            "Useful as a sanity check against the MCC/MNC. A mismatch between broadcast name and " +
            "expected carrier is worth investigating."
        ),
        Key.CARRIER to Help(
            "Carrier (looked up)",
            "The carrier name this app resolved from the MCC/MNC using its built-in list. It " +
            "should match the Operator the cell broadcasts and the SIM you're using."
        ),
        Key.REGISTERED to Help(
            "Registered",
            "\"Yes (Serving)\" means your phone is currently camped on this cell — it's the one " +
            "handling your calls and data. \"No (Neighbor)\" means your phone can hear it but " +
            "isn't using it. Only the serving cell directly affects your signal."
        ),
        Key.SIGNAL_QUALITY to Help(
            "Signal Quality",
            "A simple Excellent/Good/Fair/Poor label derived from the RSRP or RSSI value. Useful " +
            "at a glance but doesn't tell you the full story — check SINR for noise/interference."
        ),
        Key.SIGNAL_LEVEL to Help(
            "Signal Level (bars)",
            "The 0-to-4 value Android uses for the signal bars icon. It's derived from RSRP/RSSI " +
            "and capped at 4. Not precise — the raw dBm figures below are more informative."
        ),
        Key.RSRP to Help(
            "RSRP — Reference Signal Received Power (LTE/5G)",
            "The strength of the cell's reference signal in dBm. Rough guide: -80 dBm or better " +
            "is excellent, -90 good, -100 fair, -110 poor, -120 almost nothing. RSRP is negative, " +
            "so values closer to zero are stronger."
        ),
        Key.RSRQ to Help(
            "RSRQ — Reference Signal Received Quality (LTE)",
            "A quality metric that combines RSRP with interference from other cells, in dB. " +
            "-10 dB or better is good, -15 to -20 dB is poor. It tells you how clean the signal " +
            "is, not just how strong."
        ),
        Key.RSSI to Help(
            "RSSI — Received Signal Strength Indicator",
            "Total received power in the channel (in dBm), including your serving cell, neighbors " +
            "and noise. For 2G/3G this is the main strength number; for LTE prefer RSRP."
        ),
        Key.SINR to Help(
            "SINR — Signal to Interference + Noise Ratio",
            "How much stronger your cell's signal is than everything else on the same frequency, " +
            "in dB. Above 20 is excellent, 13-20 good, 0-13 fair, below 0 poor. Low SINR usually " +
            "means interference from other cells or Wi-Fi gear, not weak signal."
        ),
        Key.CQI to Help(
            "CQI — Channel Quality Indicator",
            "A 0-15 score the phone reports back to the cell, summarising how cleanly it can " +
            "receive data. Higher = better. It drives how fast the cell will send you data."
        ),
        Key.TIMING_ADVANCE to Help(
            "Timing Advance",
            "A value the LTE base station tells your phone so its uplink transmissions arrive on " +
            "time. Each unit represents roughly 78 metres of round-trip distance, so it's a " +
            "coarse distance-to-tower hint. Many phones report it as \"unavailable\"."
        ),
        Key.EST_DISTANCE to Help(
            "Estimated distance",
            "Rough distance to the base station, computed from Timing Advance (LTE ~78 m per unit). " +
            "Accurate to roughly a TA ring; not a precise GPS distance."
        ),
        Key.TOWER_LOCATION to Help(
            "Tower location",
            "Where the app thinks the tower physically sits. \"From database\" means it came from " +
            "an OpenCellID snapshot; \"Estimated from measurements\" means the app inferred it " +
            "from where you heard the cell most strongly."
        ),
        Key.TOWER_LAT to Help(
            "Tower latitude",
            "The north-south coordinate of the tower. Combined with the longitude it pins the " +
            "tower on the map."
        ),
        Key.TOWER_LON to Help(
            "Tower longitude",
            "The east-west coordinate of the tower. Combined with the latitude it pins the tower " +
            "on the map."
        ),
        Key.YOUR_LAT to Help(
            "Your latitude",
            "Your phone's north-south GPS coordinate when this measurement was recorded."
        ),
        Key.YOUR_LON to Help(
            "Your longitude",
            "Your phone's east-west GPS coordinate when this measurement was recorded."
        ),
        Key.GPS_ACCURACY to Help(
            "GPS accuracy",
            "The radius, in metres, within which the real GPS position is likely to lie. Under " +
            "10 m is good open-sky; 20-50 m is typical indoors or in urban canyons."
        ),
        Key.OBSERVED_AT to Help(
            "Observed at",
            "When this measurement was captured. Useful for correlating signal changes with " +
            "where you were and what you were doing."
        )
    )

    fun get(key: Key): Help? = entries[key]
}
