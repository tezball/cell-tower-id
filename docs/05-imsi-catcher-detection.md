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

### 1. Unknown Tower Detection
Cross-reference every observed cell (MCC-MNC-LAC/TAC-CID) against known databases:
- OpenCelliD
- FCC ASR
- Previously observed towers in CellID's local database

**Indicators:**
- Tower not in any database AND never previously observed at this location
- New tower appears suddenly and disappears quickly (transient)

**Caveats:** Databases are incomplete. New legitimate towers are deployed regularly. Use temporal/geographic correlation to reduce false positives.

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

## CellID Integration Approach

CellID can implement software-only detection using data from the Android CellInfo API:

### Feasible Without Root/SDR
1. **Unknown tower alerting** -- cross-reference against OpenCelliD + local history
2. **Signal strength anomaly detection** -- flag unusually strong unknown towers
3. **2G downgrade alerting** -- monitor RAT changes, alert on unexpected 2G fallback
4. **LAC/TAC change monitoring** -- track and alert on anomalous changes
5. **Tower history tracking** -- build longitudinal baseline of "normal" tower behavior per location
6. **Neighbor consistency** -- check if observed neighbors match expected patterns

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
| Not in OpenCelliD | +2 | Tower CID not found in database |
| Not in local history | +2 | Never seen at this location before |
| Abnormal signal strength | +1-3 | Signal >> expected for distance |
| 2G when LTE expected | +3 | Downgrade in known LTE area |
| LAC/TAC anomaly | +2 | Unexpected change while stationary |
| Transient appearance | +2 | Tower appears/disappears within minutes |
| Unusual operator | +3 | MCC/MNC doesn't match licensed operators |

**Threat levels:**
- 0-2: Normal (green)
- 3-5: Suspicious (yellow) -- log and monitor
- 6+: Alert (red) -- notify user immediately
