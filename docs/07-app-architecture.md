# Cell Tower ID App Architecture

## Overview

Cell Tower ID is an Android app that collects cell tower data, visualizes signal strength on maps, and detects potential IMSI catchers through anomaly analysis.

---

## Target Platform

- **Android only** (iOS cannot access cell tower identifiers or signal metrics)
- **minSdk:** 24 (Android 7.0) -- EARFCN support
- **targetSdk:** 36
- **Recommended minimum for full features:** API 29 (Android 10) -- NR/5G support, `requestCellInfoUpdate()`

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────┐
│                     UI Layer                         │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐ │
│  │ Map View │  │ Cell List│  │ Anomaly Dashboard │ │
│  │(MapLibre)│  │  View    │  │                   │ │
│  └──────────┘  └──────────┘  └───────────────────┘ │
├─────────────────────────────────────────────────────┤
│                  ViewModel Layer                     │
│  ┌──────────────┐  ┌────────────┐  ┌─────────────┐ │
│  │MapViewModel  │  │CellViewModel│  │AnomalyVM   │ │
│  └──────────────┘  └────────────┘  └─────────────┘ │
├─────────────────────────────────────────────────────┤
│                 Repository Layer                     │
│  ┌──────────────────┐  ┌──────────────────────────┐ │
│  │MeasurementRepo   │  │TowerCacheRepo            │ │
│  │(local DB)        │  │(self-learned from obs)   │ │
│  └──────────────────┘  └──────────────────────────┘ │
├─────────────────────────────────────────────────────┤
│                  Service Layer                       │
│  ┌──────────────────────────────────────────────┐   │
│  │ CollectionService (Foreground Service)        │   │
│  │  ├── CellInfoProvider (TelephonyManager)      │   │
│  │  ├── LocationProvider (FusedLocationClient)    │   │
│  │  ├── AnomalyDetector (real-time analysis)     │   │
│  │  └── MeasurementWriter (batched Room inserts) │   │
│  └──────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────┤
│                  Data Layer (Room)                   │
│  measurements | sessions | tower_cache | anomalies  │
└─────────────────────────────────────────────────────┘
```

---

## Key Components

### CollectionService (Foreground Service)

The core background component that collects cell and location data.

```
CollectionService (Foreground Service, type=location)
  ├── LocationProvider (FusedLocationProviderClient)
  │     └── requestLocationUpdates(interval=5s, priority=HIGH_ACCURACY)
  ├── CellInfoProvider
  │     ├── TelephonyCallback.CellInfoListener (API 31+)
  │     └── fallback: requestCellInfoUpdate() on timer (API 29+)
  │     └── fallback: getAllCellInfo() on timer (API 17-28)
  ├── AnomalyDetector
  │     ├── Signal strength anomaly detection
  │     ├── RAT downgrade monitoring (2G and 3G)
  │     ├── LAC/TAC change tracking
  │     ├── Transient tower tracking
  │     ├── Suspicious proximity (timing advance ≈ 0 + moderate RSRP)
  │     ├── PCI instability (PCI change vs. self-learned history)
  │     └── Impossible move (cell position jumped > 20 km)
  ├── MeasurementWriter
  │     └── Batched Room DAO inserts (every 5-10 measurements)
  └── NotificationManager
        └── Persistent notification: session duration, count, current cell
```

**Manifest requirements:**
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".service.CollectionService"
    android:foregroundServiceType="location"
    android:exported="false" />
```

### Map View

Display tower observations as color-coded markers on an interactive map.

**Library:** MapLibre GL Native (open-source fork of Mapbox GL)
- Vector tiles, offline tile cache, no API key (uses OpenFreeMap)

**Marker rendering:**
- One `CircleLayer` marker per observed tower
- LTE sectors collapsed to one dot per eNodeB (strongest RSRP among sectors)
- Marker color encodes RSRP from EXCELLENT (green) → NO_SIGNAL (gray); see signal metrics doc for the color scale
- Pinned towers get a yellow stroke and larger radius

### Anomaly Detector

Real-time analysis of incoming cell data against baselines.

**Checks (no root required):**
1. Signal anomaly: RSRP much stronger than expected for the operator's recent baseline
2. 2G downgrade: RAT change from LTE/NR to GSM
3. 3G downgrade: RAT change from LTE/NR to WCDMA/CDMA
4. LAC/TAC change: unexpected while stationary (or rapid while driving)
5. Transient tower: appears and disappears within minutes
6. Operator mismatch: MCC/MNC doesn't match licensed operators (US only)
7. Impossible move: cell appears > 20 km from its prior self-observed location
8. Suspicious proximity: Timing Advance ≈ 0 with only moderate RSRP while stationary (IMSI catcher signature)
9. PCI instability: cell identity reports a different Physical Cell ID than previously observed (cloned cell signature)

**Output:** Anomaly records in `anomalies` table + user notification for high-severity events. All detection is on-device; no backend or shared database is involved.

---

## Dependencies

```kotlin
// Room (database)
implementation("androidx.room:room-runtime:2.6.1")
annotationProcessor("androidx.room:room-compiler:2.6.1")

// Location
implementation("com.google.android.gms:play-services-location:21.1.0")

// Maps (choose one)
implementation("org.maplibre.gl:android-sdk:11.0.0")  // recommended
// OR: implementation("org.osmdroid:osmdroid-android:6.1.18")

// JSON/Export
implementation("com.google.code.gson:gson:2.10.1")

// Background work
implementation("androidx.work:work-runtime:2.9.0")

// Lifecycle
implementation("androidx.lifecycle:lifecycle-service:2.7.0")
implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
```

---

## Battery Optimization

| Strategy | Impact |
|----------|--------|
| Configurable collection interval (5s walking, 15-30s driving) | High |
| Reduce GPS to BALANCED_POWER when high precision not needed | Medium |
| Stop collection when stationary (ActivityRecognitionClient) | Medium |
| Batch DB writes (5-10 measurements per transaction) | Low |
| Release WakeLock promptly when stopping | Low |

**Estimated impact:** Continuous GPS + cell polling at 5s interval ~5-8% battery/hour.

---

## Export Pipeline

```
CollectionService → Room DB → ExportWorker (WorkManager)
                                    ├── CSV
                                    ├── GeoJSON
                                    └── KML
```

Export can be triggered manually or scheduled via WorkManager.

---

## Data Flow: Tower Cache Population

The `tower_cache` table is purely self-learned — the app ships no seed data and makes no external DB queries. All rows are written by `CollectionService.collectOnce()` via `TowerCacheRepository.recordObservation(...)`.

```
1. CollectionService scans cell info every N seconds
2. For each registered cell with a complete (mcc, mnc, tacLac, cid):
   a. AnomalyDetector analyzes the measurement against the prior cached PCI/position
   b. Repository upserts the cell into tower_cache with source = "observed",
      storing lat/lon/pci from the current sighting
3. Subsequent sightings of the same cell:
   a. IMPOSSIBLE_MOVE check reads the cached lat/lon
   b. PCI_INSTABILITY check reads the cached pci
   c. Cache row is overwritten with the latest sighting
```

No upload, no external request. All data stays on the phone.

---

## Future: SDR Integration

For advanced users with SDR hardware, Cell Tower ID could integrate with a companion Linux service:

```
Android App (Cell Tower ID) ←── WebSocket/USB ──→ Linux Service (laptop/Pi)
                                              ├── srsRAN cell_search
                                              ├── gr-gsm scanner
                                              └── Anomaly correlation
```

This would enable deeper protocol analysis while keeping the Android app as the primary UI.
