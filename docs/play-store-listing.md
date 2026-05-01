# Cell Tower ID — Play Store Listing

## App Name (30 char max)
Cell Tower ID

## Short Description (80 char max)
Map cell towers, track signal strength, and detect IMSI catchers on your device.

## Full Description (4000 char max)

Cell Tower ID is a powerful, privacy-first tool for mapping cell towers, tracking signal strength, and detecting potential IMSI catchers — all from your Android device.

**Map Cell Towers**
See cell towers around you plotted on an interactive map, color-coded by signal strength. Filter by network type: LTE, 5G NR, GSM, and WCDMA. Tap any tower for detailed technical information including Cell ID, TAC/LAC, MCC/MNC, signal metrics (RSRP, RSRQ, SINR), and carrier identification.

**Track Signal Strength**
Start a collection session to continuously log cell tower observations as you move. Monitor how signal strength changes across locations and identify coverage gaps. Use the built-in tower locator to walk or drive toward a specific cell tower using real-time signal strength feedback. The locator includes a compass-driven radar arrow that responds as you turn the phone, plus an auto-detected driving mode that pulses the signal four times a second when you're moving at vehicle speeds.

**Detect IMSI Catchers**
Cell Tower ID passively monitors for signs of rogue base stations (IMSI catchers / Stingrays) using eleven detection heuristics:
• Abnormally strong signals suggesting proximity spoofing
• Forced 2G downgrades (a classic interception technique)
• Forced 3G downgrades
• Transient towers that appear and disappear within minutes
• Impossible tower location jumps (cached observations vs current GPS)
• PCI instability (known cell reporting a different physical cell id)
• Unexpected LAC/TAC changes
• Suspicious proximity (Timing Advance 0 with moderate RSRP while stationary)
• Operator/carrier mismatches
• Popup towers (new or reappearing cells in an otherwise well-mapped area)
• PCI collisions (same PCI broadcast by 2+ different cells, or PCI repurposed)

Anomalies are scored by severity (High / Medium / Low) and presented as actionable alerts.

**Your Data, Your Device**
All data is stored locally on your device. Nothing is ever transmitted to any server. There is no cloud sync, no analytics, and no tracking. You control when collection starts, when it stops, and when data is deleted.

**Export Your Data**
Export your measurements in CSV, GeoJSON, or KML format for analysis in external tools, GIS software, or Google Earth.

**Key Features**
• Interactive cell tower map color-coded by signal strength
• Real-time signal strength monitoring
• 11-point IMSI catcher anomaly detection
• Tower locator with compass-radar arrow and auto-detected driving mode
• Pin towers to keep them on the map even when out of range
• CSV, GeoJSON, and KML export
• Configurable data retention (auto-delete)
• Background collection with visible notification
• No network required for detection — heuristics run entirely on-device
• 100% local — no data leaves your device

Cell Tower ID is built for security researchers, privacy advocates, network engineers, and anyone who wants to understand the cellular infrastructure around them.

## Category
Tools

## Content Rating
Everyone

## Tags
cell tower, signal strength, IMSI catcher, network, LTE, 5G, privacy, security

## Pricing

- **Distribution model:** Paid app (one-time purchase)
- **Base price (anchor):** €2.50 EUR
- **Localized pricing:** Use Google Play's auto-conversion for all supported countries (~$2.70 USD, ~£2.20 GBP equivalents)
- **Free trial:** None
- **In-app purchases:** None
- **Ads:** None
- **Refunds:** Standard Google Play 48-hour refund window applies

### Play Console setup checklist
1. Create a [Google Payments merchant account](https://play.google.com/console) and link it to the Play Console (required before flipping the app to paid). Account type: **Individual** (no company/CRO registration required for sole-trader sales in Ireland).
2. Under **Monetize → Products → Paid app**, set the base price to **€2.50 EUR**.
3. Set the pricing tier for all target countries; enable Google's auto-conversion from the EUR anchor.
4. Under **Tax information**, complete the tax profile (Ireland: PPS number + Form W-8BEN-E equivalent under Google's tax interview).
5. Payout settings: link an Irish IBAN bank account for monthly payouts (€1 minimum threshold).

### Revenue math (reference)
- **Google's take:** 15% on the first $1M/year in developer earnings, 30% above that.
- **Net per sale @ €2.50:** ~**€2.13** after Google's 15% cut. VAT is collected and remitted by Google for EU consumer sales, so the net figure above is what lands in the payout.
- **Tax on income:** Net earnings from app sales are reportable as self-employment / trading income to Irish Revenue once you cross the unmanaged-income thresholds. Below ~€5,000/year you can typically declare via Form 12; above that, register self-employment via Form TR1.

---

## Data Safety Form Answers

| Question | Answer |
|----------|--------|
| Does your app collect or share any user data? | Yes (collects, does not share) |
| **Location** | |
| Approximate location collected? | Yes |
| Precise location collected? | Yes |
| Is location data processed ephemerally? | No (stored in local database) |
| Is location data shared with third parties? | No |
| Is location data required or optional? | Required (core functionality) |
| **Device or other IDs** | |
| Does the app collect device IDs? | No |
| **Other data types** | |
| Other data collected? | Cell tower metadata (Cell ID, signal strength, network type) — technical data, not personal |
| **Data handling** | |
| Is data encrypted in transit? | N/A — no data transmitted |
| Is data encrypted at rest? | Protected by Android file-based encryption |
| Can users request data deletion? | Yes (configurable auto-delete + clear app data) |
| **Third-party services** | |
| Does the app use third-party services? | OpenFreeMap (map tiles only, no user data sent) |

## Content Rating Questionnaire Notes

- No violence or disturbing content
- No sexual content
- No profanity
- No controlled substances
- No gambling
- No user-generated content or social features
- No personal information shared with third parties
- App does not target children
- Expected rating: Everyone
