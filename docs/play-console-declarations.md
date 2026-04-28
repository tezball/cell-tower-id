# Cell Tower ID — Play Console Declarations

Copy/paste content for the Play Console *App content* and *Permissions declaration* forms. Anchored to the actual code paths so Google reviewers can verify the claims.

---

## Permissions declarations

### `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`

**Purpose statement (in-app, Play Console):**
> Cell Tower ID requires precise location to associate each cell tower observation with the GPS coordinates where it was measured. This is the core function of the app: building a local map of cell towers and signal strength as you move through an area. Without precise location, observations cannot be plotted on the map, towers cannot be color-coded by signal strength, and the IMSI catcher proximity-based heuristics (impossible tower jumps, abnormally strong signals at distance) cannot work.

**Code reference:** `app/src/main/java/com/celltowerid/android/service/CollectionService.kt:185-200` and `app/src/main/java/com/celltowerid/android/service/RealCellInfoProvider.kt:23-30`

---

### `ACCESS_BACKGROUND_LOCATION`

**Purpose statement:**
> The app supports continuous collection sessions where the user phone-mounts the device and walks or drives through an area to map cell coverage. During a session, the screen is typically off to save battery. Background location is required so that location updates continue to flow to the foreground service while the screen is locked, allowing each cell tower observation to be GPS-tagged. The user explicitly starts and stops the collection session; collection never runs without an active foreground service notification visible to the user. Background location is never used outside an active, user-initiated collection session.

**Code reference:** `app/src/main/java/com/celltowerid/android/service/CollectionService.kt:130-160` (foreground service registers `LocationCallback` with `FusedLocationProviderClient`); `app/src/main/java/com/celltowerid/android/ui/MainActivity.kt:49,59-60` (permission requested only after foreground location granted, with explicit user-facing rationale defined in `app/src/main/res/values/strings.xml:23-28`).

---

### `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION`

**Purpose statement:**
> Cell tower observations must be collected continuously over a session (typically 10–60 minutes) at intervals of 1–10 seconds. This requires a foreground service rather than a background worker because: (1) WorkManager's minimum interval is 15 minutes, far too coarse, and (2) the user needs an obvious, persistent notification with a *Stop* action so collection is always controllable. The `FOREGROUND_SERVICE_LOCATION` subtype is declared on the service in `AndroidManifest.xml:60` matching its actual purpose.

**Code reference:** `app/src/main/AndroidManifest.xml:57-60`, `app/src/main/java/com/celltowerid/android/service/CollectionService.kt:303-345` (notification channel + persistent notification with explicit *Stop* action).

---

### `POST_NOTIFICATIONS`

**Purpose statement:**
> Required on Android 13+ to display the foreground service notification that surfaces collection status (current measurement count, current radio access type) and provides the user-facing *Stop* control. Without this permission the foreground service still runs, but the user cannot see the required transparency notification. No other notifications are posted by the app.

**Code reference:** `app/src/main/java/com/celltowerid/android/ui/MainActivity.kt:39-41` (conditional request on API 33+).

---

## Data Safety form — verified answers

The following table mirrors `docs/play-store-listing.md` and has been cross-checked against the code. Answers are accurate as of v0.1.0.

| Field | Answer | Source of truth in code |
|---|---|---|
| Does the app collect or share user data? | Yes (collects, does not share) | — |
| **Location** | | |
| Approximate location | Yes | `CollectionService.kt:170,194-199` (lat/lon) |
| Precise location | Yes | `RealCellInfoProvider.kt:32-48` |
| Shared with third parties | No | No network egress for data — verified by absence of any HTTP client call site that includes location/cell payloads. Only network call is map-tile fetch from `tiles.openfreemap.org` (URL-only, no payload). |
| Required or optional | Required | Core function of the app |
| Processed ephemerally | No | Stored in local Room SQLite DB (`AppDatabase.kt`) |
| **Device or other IDs** | | |
| Device ID, IMEI, IMSI, Android ID | No | Verified: no `READ_PHONE_STATE`, no `getDeviceId()`, no `getSubscriberId()`, no `getLine1Number()`, no `getSimSerialNumber()` calls anywhere in `app/src/main/`. |
| **Other data — Cell tower metadata** | Yes (Other / non-personal) | `RealCellInfoProvider.kt` — MCC, MNC, TAC/LAC, CID, PCI, EARFCN, RSRP, RSRQ, SINR, RSSI, signal level, operator alpha-long. None of these identify a person; they describe the cellular environment. |
| **Data handling** | | |
| Encrypted in transit | N/A | No data is transmitted. |
| Encrypted at rest | Yes (Android FBE) | App data lives in app-private storage protected by Android File-Based Encryption. |
| User can request deletion | Yes | Configurable auto-delete in Settings (0–365 days) + Android *Settings → Apps → Cell Tower ID → Storage → Clear Data* always works. |
| **Third-party services** | | |
| Map tiles | OpenFreeMap (`tiles.openfreemap.org`) — tile URL fetches only, no user data sent | `app/build.gradle.kts:31-35` |
| Cell tower DB | None — the app ships no external cell tower database and makes no runtime queries. Tower metadata is self-learned on device from the user's own observations. | `TowerCacheRepository.recordObservation()` |
| Analytics, crash reporting, ads | None | Verified: no Firebase, Crashlytics, Sentry, or analytics dependencies in `app/build.gradle.kts` |

---

## Other content declarations

| Section | Answer |
|---|---|
| News app | No |
| COVID-19 contact tracing | No |
| Government app | No |
| Financial features | No |
| Health & fitness | No |
| Children-targeted (Designed for Families) | No (target audience: 18+) |
| Ads | No |
| In-app purchases | No |
| User-generated content | No |

## Content rating (IARC) summary

- No violence, sexual content, profanity, controlled substances, gambling
- No social/communication features, no UGC
- App does not target children
- **Expected rating: Everyone (PEGI 3 / ESRB E)**
