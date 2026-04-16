# Existing Cell Tower Apps & Services Landscape

## Overview

This document surveys existing cell tower mapping apps, signal monitoring tools, and tower databases. Understanding these is essential for designing CellID's feature set and data architecture.

---

## Cell Tower Mapping Apps

### CellMapper
- **Platform:** Android (data collection) + web map (cellmapper.net)
- **Model:** Crowdsourced -- Android app passively collects cell data and uploads it
- **Key Features:**
  - Maps individual cell sites with sector-level detail (eNB/gNB sectors)
  - Tower locations, sector azimuths (estimated from crowdsourced data), frequency bands, technology
  - Displays PCI, eNB ID, TAC, frequency/EARFCN
  - Web map: filter by carrier (MCC/MNC), technology (4G/5G), band
  - Signal strength heatmap along routes
- **Data Exposed:** MCC, MNC, TAC, eNB ID (derived from E-UTRAN CID), sector ID, PCI, EARFCN/band, RSRP, RSRQ, RSSI, RSSNR
- **API:** No public API. Internal JSON endpoints exist (`api.cellmapper.net/v6/getTowers`) but are undocumented and scraping is prohibited
- **Open Source:** No
- **Limitations:** Android-only for collection (iOS can't access cell radio info). Data quality varies by region/contributor density

### Tower Collector
- **Platform:** Android
- **Model:** Open-source data collection app, uploads to OpenCelliD
- **Key Features:**
  - Collects cell tower observations (cell ID, signal strength, GPS)
  - Exports to CSV/GPX
  - Supports GSM, UMTS, LTE, NR
  - Minimal UI, focused on collection
- **Open Source:** Yes -- GitHub `zamojski/TowerCollector`, MPL 2.0
- **Relevance:** Excellent reference codebase for Android cell data collection, handles OEM quirks

### Network Cell Info Lite (by M2Catalyst/Wilysis)
- **Platform:** Android
- **Features:** Real-time cell info display (serving + neighbors), signal metrics gauges, map with estimated tower location, CSV logging
- **Data:** Reads from Android CellInfo API; tower locations from OpenCelliD
- **Open Source:** No

### SignalCheck Pro
- **Platform:** Android
- **Features:** Detailed signal info including carrier aggregation details, band identification, all signal metrics. Handles device-specific reporting quirks
- **Open Source:** No
- **Notable:** Considered the most accurate signal measurement app for Android

### NetMonitor / Cell Tower Locator (various)
- **Platform:** Android
- **Features:** Basic cell info display, signal strength, map with tower location
- **Common across category:** Uses Android CellInfo API + external databases for tower locations

---

## Signal Coverage & Analytics Services

### OpenSignal
- **Current Status:** Pivoted to B2B analytics (acquired by Ookla/Ziff Davis in 2023)
- **Consumer App:** Still exists -- speed tests, coverage maps, signal history
- **API:** No public API for tower data (sold commercially)
- **Historical:** Was one of the first crowdsourced signal mapping apps

### Speedtest by Ookla
- **Platform:** Android, iOS, Web, Desktop
- **Signal Features:** Speed tests with geo-tagged metadata, coverage maps
- **Data Collected:** Carrier, network type, signal strength, cell ID (Android), coordinates
- **API:** Commercial B2B product (Speedtest Intelligence / Ookla Cell Analytics)
- **Open Data:** Publishes aggregated speed data on AWS Open Data (performance, not towers)

---

## Cell Tower Databases

### OpenCelliD
- **URL:** opencellid.org
- **Type:** Largest open database of cell tower locations worldwide
- **Data Format:** CSV with columns: `radio, mcc, mnc, lac, cid, unit, lon, lat, range, samples, changeable, created, updated, averageSignal`
- **API Access:**
  - REST: `https://opencellid.org/cell/get?key=KEY&mcc=X&mnc=X&lac=X&cellid=X&format=json`
  - Area query: `getInArea` with bounding box
  - Bulk download: Full gzipped CSV (free with registration, 2 downloads/day)
  - Rate limit: 5,000 requests/day free
- **License:** CC-BY-SA 4.0
- **Coverage:** Dense in Europe/Asia, sparser in rural areas
- **Owned by:** Unwired Labs

### Mozilla Location Services (MLS)
- **Status:** SHUT DOWN (decommissioned 2024)
- **What it was:** Free, open-source geolocation service (cell + WiFi)
- **Legacy:** Data partially merged into OpenCelliD. Server code (Ichnaea) is on GitHub as architectural reference
- **Lesson:** Crowdsourced geolocation services need sustained infrastructure investment

### FCC Antenna Structure Registration (ASR)
- **URL:** wireless2.fcc.gov/UlsApp/AsrSearch/
- **Content:** Physical tower structures (location, height, type, owner). Structures >200ft or near airports
- **Does NOT contain:** Carrier equipment details, cell IDs, frequencies, sector configs
- **Format:** Pipe-delimited text files via FCC ULS bulk download
- **Relevance:** Complement to crowdsourced data for physical tower locations. Many cell sites (rooftops, small cells) won't be here

### Other Databases
- **radiocells.org:** Open database, Google-compatible geolocation API, open-source scanner app
- **Wigle.net:** Primarily WiFi but includes cell observations
- **Google Geolocation API:** Most complete proprietary database. POST cell tower info, get location. Free 10K monthly, then ~$5/1K requests
- **Unwired Labs Location API:** Commercial wrapper around OpenCelliD data

---

## iOS Limitations

**Critical for platform decisions:** iOS does not expose cell tower data to third-party apps.

| Data | Android | iOS |
|------|---------|-----|
| Cell ID (CID/eNB/gNB) | Yes | No |
| LAC / TAC | Yes | No |
| PCI, EARFCN | Yes | No |
| Signal strength (RSRP/RSRQ/SINR) | Yes | No |
| MCC / MNC | Yes | Yes (carrier info only, deprecated iOS 16.4) |
| Network type (LTE/5G) | Yes | Yes (CTTelephonyNetworkInfo) |

**Field Test Mode** (`*3001#12345#*`) shows this data on iPhone screens but is NOT accessible programmatically.

**Conclusion:** CellID must target Android first. An iOS version would be limited to carrier name, network type, and GPS location.

---

## Summary: Key Takeaways for CellID

1. **Android is the only viable platform** for cell tower data collection
2. **CellMapper is the gold standard** for feature inspiration but is proprietary with no API
3. **OpenCelliD is the primary open data source** for baseline tower locations
4. **Tower Collector is the best open-source reference** for Android cell data collection
5. **No existing app combines tower mapping + signal tracking + IMSI catcher detection** -- this is CellID's opportunity
6. **FCC ASR provides physical tower locations** as a secondary data source
