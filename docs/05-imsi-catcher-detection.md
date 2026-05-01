# IMSI Catcher Detection (Defensive Security)

## Purpose

This document covers techniques and tools for **detecting** rogue base stations (IMSI catchers / Stingrays) as a privacy protection measure. All approaches are passive/defensive.

---

## What is an IMSI Catcher?

An IMSI catcher (also called Stingray, fake base station, rogue BTS) is a device that impersonates a legitimate cell tower to:
1. Capture IMSI (permanent subscriber identity) from nearby phones
2. Track phone locations
3. Intercept calls/SMS (on 2G networks without mutual authentication)
4. Force protocol/encryption downgrades

---

## Detection Techniques

### 1. Self-Learned Tower Baselines
Cell Tower ID no longer ships any external tower database. Every cell observed is recorded to a local `tower_cache` row keyed on `(radio, mcc, mnc, tacLac, cid)`, storing lat/lon and PCI. Subsequent detectors (IMPOSSIBLE_MOVE, PCI_INSTABILITY) compare new sightings against this purely local, self-learned baseline.

**Why self-learned:** a seeded database is geographically biased (whoever compiled it), can be stale, and introduces licensing/attribution overhead for little practical benefit. The user's *own* observations over time become the ground truth for what's normal at the places they actually go.

### 2. Signal Strength Anomalies
IMSI catchers often transmit at higher power to force phones to connect.

**Indicators:**
- Sudden appearance of very strong signal from unknown tower
- Signal strength inconsistent with supposed distance (from database location)
- A cell that is significantly stronger than all other cells from the same operator

### 3. Forced 2G Downgrade Detection
The most common attack vector -- force phone from LTE/3G to 2G (GSM) where encryption is weak and mutual authentication doesn't exist.

**Indicators:**
- Phone drops from LTE/3G to 2G in an area with known LTE/3G coverage
- Broadband jamming on LTE/3G bands (elevated noise floor)
- Frequent RAT (Radio Access Technology) changes

**User Mitigation:** Lock phone to "LTE only" in settings (or `*#*#4636#*#*` on Android). Enable Lockdown Mode on iOS.

### 4. LAC/TAC Change Anomalies
Location Area Code (LAC) or Tracking Area Code (TAC) changes trigger location updates that reveal the phone's identity.

**Indicators:**
- Unexpected LAC/TAC changes while stationary
- Rapid LAC/TAC oscillation (ping-ponging)
- LAC/TAC values that don't match any known operator configuration for the area

### 5. Encryption Downgrade Detection (GSM)
GSM encryption algorithms:
- **A5/0:** No encryption (plaintext) -- RED FLAG
- **A5/1:** Weak stream cipher (broken, real-time cracking since 2009) -- suspicious
- **A5/2:** Deprecated/banned -- RED FLAG
- **A5/3 (KASUMI):** Strong, standard on modern networks -- expected

IMSI catchers often force A5/0 or A5/1 because they lack the operator's keys.

**Detection:** Requires baseband-level access (SnoopSnitch on Qualcomm) or SDR monitoring (gr-gsm).

### 6. TMSI Reallocation Analysis
TMSI (Temporary Mobile Subscriber Identity) should be used instead of permanent IMSI.

**Indicators of IMSI catcher:**
- Network requests IMSI directly instead of accepting TMSI
- No TMSI assigned at all
- Sequential/predictable TMSI patterns (poor randomization)
- High frequency of IMSI-type Identity Requests from a cell

### 7. Neighbor List Consistency
Legitimate towers broadcast lists of neighboring cells.

**Indicators:**
- Tower doesn't include correct neighbor lists
- Neighbors don't reciprocally list the suspicious tower
- Neighbor list is empty or very short

### 8. Cell Parameter Consistency
**Check against expected operator configuration:**
- PLMN (MCC/MNC) should match a licensed operator
- Frequency band should match operator's licensed spectrum
- System parameters (max TX power, RACH config, cell barring) should be consistent with operator norms

---

## Open Source Detection Projects

### SnoopSnitch (SRLabs)
- **Status:** Available (or was) on Google Play. Best technical capability.
- **Requires:** Rooted Qualcomm-based Android phone with baseband diag access
- **Detects:** SS7 attacks, fake base stations, encryption downgrades, silent SMS
- **How:** Hooks into Qualcomm's diagnostic interface (`/dev/diag`) for raw Layer 3 signaling
- **Limitation:** Compatible device list shrinking. Qualcomm increasingly locks down diag access.

### AIMSICD (Android IMSI-Catcher Detector)
- **Status:** Abandoned/stalled (~2016-2018). Never reached 1.0.
- **Repo:** `CellularPrivacy/Android-IMSI-Catcher-Detector`
- **Approach:** Monitored cell parameters, compared against OpenCelliD, flagged anomalies
- **Limitation:** Required root. High false positives. No meaningful LTE detection. Historical reference only.

### Crocodile Hunter (EFF Threat Lab)
- **Status:** Proof-of-concept / research tool
- **Repo:** `EFForg/crocodilehunter`
- **Approach:** SDR (USRP B200) + srsRAN to passively decode LTE broadcast channels (SIBs), compare against known-good database
- **Detects:** LTE IMSI catchers (e.g., Harris Hailstorm)
- **Limitation:** Requires USRP B200 (~$900+), CPU-intensive, complex setup

### SeaGlass (University of Washington, 2017)
- **Type:** Research project (PETS 2017)
- **Approach:** Deployed Raspberry Pi + SDR sensors in rideshare vehicles. Passive longitudinal GSM monitoring + statistical anomaly detection.
- **Key Insight:** Passive citywide monitoring over time is more robust than simple database lookups

### SITCH (Sensor for IMSI-Catcher and Tracking Heuristics)
- **Developer:** Ash Wilson / Duo Security
- **Approach:** Raspberry Pi + SDR sensor nodes monitoring GSM towers
- **Status:** Open source on GitHub, development inactive

---

## Cell Tower ID Integration Approach

Cell Tower ID can implement software-only detection using data from the Android CellInfo API:

### Feasible Without Root/SDR
1. **Signal strength anomaly** -- flag cells whose RSRP is 20+ dBm above the operator's rolling average
2. **2G downgrade** -- alert when the phone drops from LTE/NR to GSM
3. **3G downgrade** -- alert when the phone drops from LTE/NR to WCDMA/CDMA
4. **LAC/TAC change** -- track and alert on anomalous area-code changes (speed-gated to reduce driving false positives)
5. **Transient tower** -- flag cells visible briefly then gone, a signature of mobile IMSI catchers
6. **Operator mismatch** -- MCC/MNC not on the known US carrier list
7. **Impossible move** -- cell's self-observed position jumped > 20 km; can't be the same macro tower
8. **Suspicious proximity** -- Timing Advance ≈ 0 (cell within ~550 m) with only moderate RSRP (-95 to -85 dBm) sustained for ≥ 60 s of continuous in-band observation while stationary, consistent with a portable IMSI catcher radiating at modest power. The 60 s sustained-window requirement filters body-shadowing dips (10-20 dB transient attenuation from the human torso) that would otherwise mimic the same RF signature on a single-sample trigger
9. **PCI instability** -- same cell identity reporting a different PCI/PSC than we've previously observed, consistent with a cloned cell
10. **Popup tower** -- a cell that appears in a familiar, well-mapped area where it has not been seen recently (never in the last 7 days, or absent for over 6 hours then back), characteristic of a stationary IMSI catcher being toggled on and off
11. **PCI collision** -- a Physical Cell ID (PCI) shared by two different cell identities in the same area, or a familiar PCI now hosted by a different cell identity, consistent with a fake cell that picked an arbitrary PCI without coordinating with the operator. The "same area" radius is dynamic: it defaults to 2 km in macro-cell-dominated environments but compresses to 500 m once the local area shows ≥ 50 distinct cell identities (a HetNet small-cell density signal), because legitimate PCI reuse at sub-2-km distances is mathematically necessary in dense small-cell deployments and the broader radius produces continuous false positives in places like the Dublin Docklands

### Requires Root (Qualcomm)
- Encryption algorithm detection (via `/dev/diag`)
- Silent SMS detection
- IMSI vs TMSI identity request monitoring

### Requires SDR Hardware
- Full broadcast channel decoding
- Encryption mode verification
- Deep protocol analysis
- Multi-band scanning

---

## Anomaly Scoring Model

Combine multiple weak signals into a composite threat score:

| Factor | Weight | Description |
|--------|--------|-------------|
| Abnormal signal strength (`SIGNAL_ANOMALY`) | +1-3 | Weighted by severity; signal 20-35 dBm above operator average |
| 2G downgrade (`DOWNGRADE_2G`) | +3 | LTE/NR → GSM |
| 3G downgrade (`DOWNGRADE_3G`) | +2 | LTE/NR → WCDMA/CDMA |
| LAC/TAC anomaly (`LAC_TAC_CHANGE`) | +2 | Unexpected area-code change |
| Transient appearance (`TRANSIENT_TOWER`) | +2 | Tower appears/disappears within minutes |
| Unusual operator (`OPERATOR_MISMATCH`) | +3 | MCC/MNC doesn't match licensed operators |
| Impossible move (`IMPOSSIBLE_MOVE`) | +6 | Cell position jumped > 20 km from self-observed baseline |
| Suspicious proximity (`SUSPICIOUS_PROXIMITY`) | +3 | TA ≈ 0 with moderate RSRP (-95..-85 dBm) sustained for ≥ 60 s while stationary |
| PCI instability (`PCI_INSTABILITY`) | +2 | Cell identity reporting a different PCI than before |
| Popup tower (`POPUP_TOWER`) | +3 | New or reappearing cell in a well-mapped area; HIGH severity for gap-reappearance, MEDIUM for first-time-in-area on immature baselines |
| PCI collision (`PCI_COLLISION`) | +4 | Same PCI broadcast by ≥ 2 different CIDs, or familiar PCI now on a new CID; evaluation radius adapts (2 km macro / 500 m HetNet) based on local CID density |

**Threat levels:**
- 0-2: Normal (green)
- 3-5: Suspicious (yellow) -- log and monitor
- 6+: Alert (red) -- notify user immediately
