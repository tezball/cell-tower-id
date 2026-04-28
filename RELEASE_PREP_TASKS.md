# Cell Tower ID — Release Prep Task List

**Target:** Play Store launch, paid at €2.50, v1.0.0.
**Status (2026-04-28):** Most blockers and all high-severity code issues have been landed. The AAB builds clean, tests and lint are green. Remaining items are listed under §8 below — they all require user input (merchant account, device smoke tests) or are optional polish. README.md hygiene drift caught in this pass and addressed in §3.14 + §3.20.

Blocker definition: would either (a) get the submission rejected, (b) breach a license, (c) cause crashes/data loss in the wild, or (d) prevent the paid-app flow.

Time estimates are for a single dev. Total blocker work ≈ **1–1.5 days of focused work + a 14-day mandatory closed-testing clock** imposed by Play on new individual developer accounts. Start the closed-test clock as early as possible.

---

## 0. Start the 14-day closed-testing clock NOW

Google requires new individual developer accounts to run a closed test track with ≥20 testers for **14 consecutive days** before unlocking production. This clock is the single longest lead-time item on the whole project.

- [ ] Create the app in Play Console; upload any passable AAB (even one with the blockers below unfixed) to kick the clock
- [ ] Recruit 20 testers (email list or Google Group)
- [ ] Track day-1 / day-7 / day-14 milestones; plan production release AFTER day 14

---

## 1. Blockers (must fix before paid submission)

### 1.1 Map attribution missing — ODbL / OpenFreeMap license breach — ✅ DONE
- **Severity:** Blocker (legal + policy)
- **Where:** `app/src/main/java/com/terrycollins/celltowerid/ui/fragment/MapFragment.kt` (attribution never enabled)
- **Problem:** MapLibre's built-in attribution control is not surfaced and there is no "© OpenStreetMap contributors" text anywhere on the map surface. OpenStreetMap (ODbL) and OpenFreeMap both require visible on-map attribution.
- **Fix:** Enable MapLibre's `UiSettings.isAttributionEnabled = true` (default in most builds — verify), or add a visible `TextView` overlaid on the map bottom-right with the attribution string. Must remain visible whenever map tiles are displayed.
- **ETA:** 30 min

### 1.2 Store listing advertises features that no longer exist (OpenCelliD bundle) — ✅ DONE
- **Severity:** Blocker (Play policy: metadata must match the app)
- **Where:**
  - [docs/play-store-listing.md](docs/play-store-listing.md) lines 20–22, 44–45 — "Unknown towers not in the OpenCelliD database" + "Bundled OpenCelliD tower database for offline detection"
  - [CHANGELOG.md:18](CHANGELOG.md) — same claim
  - `fastlane/metadata/android/en-US/changelogs/1.txt` — "Bundled offline cell tower database"
  - Code reality: [MainActivity.kt:116](app/src/main/java/com/terrycollins/celltowerid/ui/MainActivity.kt) purges `UNKNOWN_TOWER` anomalies and deletes `opencellid`-sourced tower cache rows on every launch; `AnomalyType` enumerates **9 heuristics**, none of which is `UNKNOWN_TOWER`; CLAUDE.md confirms "This project ships no seeded Room data"
- **Fix:** Rewrite listing to describe the actual 9 heuristics (SIGNAL_ANOMALY, DOWNGRADE_2G, DOWNGRADE_3G, LAC_TAC_CHANGE, TRANSIENT_TOWER, OPERATOR_MISMATCH, IMPOSSIBLE_MOVE, SUSPICIOUS_PROXIMITY, PCI_INSTABILITY). Remove every "OpenCelliD" and "bundled database" reference. Update `README.md` too.
- **ETA:** 45 min

### 1.3 Privacy policy URL not publicly served — ✅ DONE
- **Severity:** Blocker (Google review 404-checks this URL)
- **Resolution:** Privacy policy is live at `https://cell-tower-id.com/privacy.html`. `strings.xml`, `release-checklist.md`, and `play-store-setup-guide.md` reference the live URL. The static site under `website/` is served at `cell-tower-id.com`.

### 1.4 `android:allowBackup="true"` on a privacy-focused paid security app — ✅ DONE
- **Severity:** Blocker-level recommendation
- **Where:** [AndroidManifest.xml:15](app/src/main/AndroidManifest.xml)
- **Problem:** Even though the Room DB is excluded by omission from `backup_rules.xml` / `data_extraction_rules.xml`, any contributor adding `<include domain="database" .../>` would silently upload precise location tracks to Google Drive. A forgotten `adb backup` on a debug build could also dump state.
- **Fix:** Set `android:allowBackup="false"`. For a security tool with no cross-device use case this is the safest option and matches competitors like Network Cell Info. If you want sharedpref restore, leave as-is but add explicit `<exclude domain="database" .../>` to both XML rule files.
- **ETA:** 15 min

### 1.5 Background-location prominent disclosure not in onboarding — ✅ DONE
- **Severity:** Blocker (common rejection cause for location apps)
- **Where:** Disclosure dialog exists at [MainActivity.kt:147](app/src/main/java/com/terrycollins/celltowerid/ui/MainActivity.kt) (uses `R.string.background_location_disclosure`), but it runs post-onboarding after fine-location is granted. Onboarding page 4 (`onboarding_permissions_desc`) does not mention background specifically.
- **Fix:** Either
  - (a) Add a dedicated onboarding page for background location using the existing `R.string.background_location_disclosure` string, OR
  - (b) Record a 30-second screencast showing the in-app disclosure dialog BEFORE the system prompt, and attach to the Play Console permissions declaration.
- **ETA:** 60 min (option a) or 20 min (option b)

### 1.6 Merchant account + paid-app prerequisites — ⚠ REQUIRES USER ACTION
- **Severity:** Blocker (can't flip to paid without it)
- **Fix:**
  - [ ] Create Google Merchant Center account
  - [ ] Complete tax interview (have PPS number + IBAN ready for Irish sole trader)
  - [ ] Link to Play Console
  - [ ] Set price tier €2.50 EUR with auto-conversion for other currencies
  - [ ] Select country availability list
- **ETA:** 60 min

---

## 2. High-severity code issues (fix before paid launch) — ✅ ALL DONE

### 2.1 Precise GPS coordinates written to log file on external storage (API 24–28)
- **File:** [util/AppLog.kt:33](app/src/main/java/com/terrycollins/celltowerid/util/AppLog.kt) (log path), [service/CollectionService.kt:237](app/src/main/java/com/terrycollins/celltowerid/service/CollectionService.kt) and [ui/fragment/MapFragment.kt](app/src/main/java/com/terrycollins/celltowerid/ui/fragment/MapFragment.kt) lines 152/211/227/337–348/382/387
- **Problem:** Log file lives in `getExternalFilesDir("logs")`. On API 24–28 (minSdk 24) `/Android/data/<pkg>/files/logs/app.log` is world-readable by any app with `READ_EXTERNAL_STORAGE`. Contains per-scan lat/lng, serving cell IDs, carrier info. Contradicts the "all-local, private" marketing pitch.
- **Fix:**
  - Move log dir to `File(context.filesDir, "logs")` unconditionally in `AppLog.init` and `AppLog.logFile`
  - Update `file_provider_paths.xml` line 4 to `<files-path name="logs" path="logs/" />`
  - Strip/round lat/lng before logging (`String.format("%.3f, %.3f", lat, lon)` for ~100m precision) or gate those log sites behind `BuildConfig.DEBUG`
- **ETA:** 45 min

### 2.2 AppLog runs at full verbosity in release builds (no DEBUG gate)
- **File:** [util/AppLog.kt:53-78](app/src/main/java/com/terrycollins/celltowerid/util/AppLog.kt)
- **Problem:** 50+ `AppLog.d/e/w` call sites all persist to disk AND emit to logcat in release. Combined with 2.1 this is an active PII sink.
- **Fix:** Add `if (!BuildConfig.DEBUG) return` early-return to `AppLog.d`; keep `e`/`w` but redact coordinates. Add to `proguard-rules.pro`:
  ```
  -assumenosideeffects class android.util.Log {
      public static int d(...);
      public static int v(...);
  }
  ```
- **ETA:** 20 min

### 2.3 CollectionService foreground-promotion race — crashes on API 34+ with `ForegroundServiceTypeSecurityException`
- **File:** [service/CollectionService.kt:130-192](app/src/main/java/com/terrycollins/celltowerid/service/CollectionService.kt)
- **Problem:** Sticky restart path (intent == null branch at line 138) calls `startCollection(...)` without re-verifying `ACCESS_FINE_LOCATION`. If the user revoked permission between runs, `startForeground()` succeeds (notification channel is created) but `requestLocationUpdates` then throws `SecurityException`. On API 34+ this reliably crashes with `ForegroundServiceStartNotAllowedException`. `@SuppressLint("MissingPermission")` at line 162 hides the lint but not the crash.
- **Fix:** In both `onStartCommand` (restart path) and `startCollection`, verify fine-location is still granted; if not, post a notification explaining why scanning stopped, set `Preferences.isScanActive = false`, and `stopSelf()` before any `startForeground` call. Also have `CollectionRestartPolicy.decide(...)` check permissions.
- **ETA:** 60 min

### 2.4 `RealCellInfoProvider.getCellMeasurements` only catches `SecurityException`
- **File:** [service/RealCellInfoProvider.kt:39-43](app/src/main/java/com/terrycollins/celltowerid/service/RealCellInfoProvider.kt)
- **Problem:** Xiaomi/MediaTek devices throw `IllegalStateException` or `RuntimeException` wrapping `DeadObjectException` from `telephonyManager.allCellInfo`. A single occurrence crashes the collection cycle.
- **Fix:** Catch `Throwable` (or at minimum `Exception`) and return `emptyList()`. Log once per session via `AppLog.w`.
- **ETA:** 10 min

### 2.5 All `!!` operator uses (CLAUDE.md forbids them)
- **Files & lines:**
  - [ui/viewmodel/HuntViewModel.kt:133](app/src/main/java/com/terrycollins/celltowerid/ui/viewmodel/HuntViewModel.kt) — `target.rsrp!!`
  - [util/TowerDedup.kt:40-41](app/src/main/java/com/terrycollins/celltowerid/util/TowerDedup.kt) — `.latitude!!` / `.longitude!!`
  - [ui/viewmodel/CellListViewModel.kt:64](app/src/main/java/com/terrycollins/celltowerid/ui/viewmodel/CellListViewModel.kt) — `.maxByOrNull { it.timestamp }!!`
  - [service/AnomalyDetector.kt:132](app/src/main/java/com/terrycollins/celltowerid/service/AnomalyDetector.kt) — `knownLat!!, knownLon!!`
  - [ui/MainActivity.kt:86](app/src/main/java/com/terrycollins/celltowerid/ui/MainActivity.kt) — `as NavHostFragment` (hard cast)
- **Fix:** Replace with `?:` early-return, `requireNotNull()` with message, or `maxBy` (non-nullable overload). Use `as?` instead of `as` for the NavHostFragment cast.
- **ETA:** 30 min

### 2.6 `tower_cache` table has no lat/lon index AND no retention
- **Files:** [data/entity/TowerCacheEntity.java:12-14](app/src/main/java/com/terrycollins/celltowerid/data/entity/TowerCacheEntity.java), [ui/viewmodel/MapViewModel.kt:95-103](app/src/main/java/com/terrycollins/celltowerid/ui/viewmodel/MapViewModel.kt), [export/RetentionCleanupWorker.kt:56-66](app/src/main/java/com/terrycollins/celltowerid/export/RetentionCleanupWorker.kt)
- **Problem:** `SELECT ... WHERE latitude BETWEEN ... AND longitude BETWEEN ...` runs every 15s and is always a full table scan. No retention policy on tower_cache — table grows forever. After a few weeks of collection the UI will stutter.
- **Fix:**
  1. Add `@Index(value = {"latitude", "longitude"})` to `TowerCacheEntity` + migration (bump schema version from current → next)
  2. Call `getTowersInArea` with actual viewport bounds, not world bounds
  3. Add `deleteOlderThan(cutoffMs, keepPinned = true)` DAO method; call from `RetentionCleanupWorker`
- **ETA:** 90 min

### 2.7 Pinned-tower map refresh lag (15s) + pinned synthetic tower renders at user's GPS
- **Files:** [ui/viewmodel/MapViewModel.kt:95](app/src/main/java/com/terrycollins/celltowerid/ui/viewmodel/MapViewModel.kt), [util/PinIdentity.kt:22-39](app/src/main/java/com/terrycollins/celltowerid/util/PinIdentity.kt), [repository/TowerCacheRepository.kt:106-137](app/src/main/java/com/terrycollins/celltowerid/repository/TowerCacheRepository.kt)
- **Problem:** Pinning from Cell List takes up to 15s to reflect on Map (no reactive Flow wiring). Worse: when pinning a neighbor cell that lacks MCC/MNC identity, `pinTower` inserts a stub with `fallbackLat/Lon` = user's current GPS — then renders that stub on the map as if the tower lived at the user's position. Users will think they pinned a real nearby tower.
- **Fix:**
  1. Merge `TowerCacheRepository.getPinnedTowerEntitiesLive()` into the map's exposed towers stream for immediate updates
  2. Either (a) skip rendering pins whose `source == "pinned"` until a real observation refines their location, or (b) render them with a distinct marker + "pinned location estimate" label
- **ETA:** 60 min

### 2.8 Battery-optimization exemption prompt is missing
- **Where:** No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` anywhere in the codebase
- **Problem:** Xiaomi/OnePlus/Samsung aggressively kill foreground services in Doze. For a collection app users pay €2.50 for, silently-stopped overnight scans will be the #1 complaint.
- **Fix:** Add a one-time dialog after first "Start collection" that deep-links to system battery-optimization settings via `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Requires `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission (normal, not dangerous).
- **ETA:** 45 min

### 2.9 CSV export vulnerable to CSV formula injection
- **File:** [export/CsvExporter.kt:28-48](app/src/main/java/com/terrycollins/celltowerid/export/CsvExporter.kt)
- **Problem:** Operator names are written raw. If an OEM/carrier sets a name starting with `=`, `+`, `-`, or `@`, opening the export in Excel/Sheets runs it as a formula. Also missing UTF-8 BOM (Excel on Windows will mis-render non-ASCII operator names) and no quoting of fields containing `,`, `"`, `\n`.
- **Fix:** Prefix any field starting with `=+-@\t\r` with a single quote. Wrap any field containing `,`, `"`, or `\n` in double-quotes with internal `"` escaped to `""`. Write BOM (`\uFEFF`) at file start.
- **ETA:** 30 min

### 2.10 KML exporter doesn't escape `]]>` in CDATA descriptions
- **File:** [export/KmlExporter.kt:46-67](app/src/main/java/com/terrycollins/celltowerid/export/KmlExporter.kt)
- **Problem:** An operator string containing `]]>` produces an invalid KML file. Also `<name>` values aren't XML-escaped for `& < >`.
- **Fix:** Replace `]]>` in CDATA content with `]]]]><![CDATA[>`. XML-escape `<name>` content.
- **ETA:** 20 min

---

## 3. Medium-severity issues (should fix, or file follow-up)

### 3.1 Explicit backup-rule excludes for the Room DB
- **File:** [res/xml/backup_rules.xml](app/src/main/res/xml/backup_rules.xml), [res/xml/data_extraction_rules.xml](app/src/main/res/xml/data_extraction_rules.xml)
- If keeping `allowBackup="true"`, add explicit `<exclude domain="database" path="cellid_database" />` (and `-shm`, `-wal`) entries to both rule files so a future contributor can't accidentally include them. — 10 min

### 3.2 `MapFragment` listener re-attachment broken after retry
- **File:** [ui/fragment/MapFragment.kt:128-176](app/src/main/java/com/terrycollins/celltowerid/ui/fragment/MapFragment.kt)
- `listenersAttached` is set on first attach but never reset in the retry-button handler. After one retry, camera-idle auto-reload and tower-info tap silently stop working.
- Fix: reset `listenersAttached = false` in the retry button's click handler. — 5 min

### 3.3 Diagnostic sampler runs every 2s in release
- **File:** [ui/fragment/MapFragment.kt:322-354](app/src/main/java/com/terrycollins/celltowerid/ui/fragment/MapFragment.kt)
- Gate behind `BuildConfig.DEBUG` or remove. — 5 min

### 3.4 `lastLocation` staleness not checked
- **File:** [service/CollectionService.kt:82-98,293](app/src/main/java/com/terrycollins/celltowerid/service/CollectionService.kt)
- Every measurement gets stamped with the cached last location even if it's hours old (phone in pocket, GPS denied). Reject fixes older than 2× scan interval. — 15 min

### 3.5 HuntViewModel blocks main with `getCellMeasurements` every 1s
- **File:** [ui/viewmodel/HuntViewModel.kt:103-111](app/src/main/java/com/terrycollins/celltowerid/ui/viewmodel/HuntViewModel.kt)
- Wrap in `withContext(Dispatchers.IO)`. — 10 min

### 3.6 `TowerCacheRepository.recordObservation` read-modify-write without `@Transaction`
- **File:** [repository/TowerCacheRepository.kt:62-85](app/src/main/java/com/terrycollins/celltowerid/repository/TowerCacheRepository.kt)
- Concurrent observation + `learnPosition` from `TowerDetailViewModel` can silently flip `isPinned` to false. Wrap the DAO flow in a single `@Transaction` method or split into `INSERT OR IGNORE` + targeted `UPDATE`. — 30 min

### 3.7 Dark-mode sweep
- 17+ `@android:color/darker_gray` references in layouts, `#80FFFFFF` filter background in [fragment_map.xml:23](app/src/main/res/layout/fragment_map.xml), `#4CAF50` SERVING badge in [item_cell.xml:41](app/src/main/res/layout/item_cell.xml), tower-info card uses darker_gray. Night mode will look harsh.
- Fix: replace hardcoded color literals with theme attributes (`?attr/colorSurfaceVariant`, `?attr/colorOnSurfaceVariant`). Test the app with system dark mode. — 90 min

### 3.8 Hardcoded user-facing strings across layouts + fragments (CLAUDE.md violation, blocks localization)
- 53 instances of `android:text="..."` in `res/layout/*.xml`
- Hardcoded in: [SettingsFragment.kt](app/src/main/java/com/terrycollins/celltowerid/ui/fragment/SettingsFragment.kt) lines 51/56/62/67/97/101/114/125/147/157; [AnomalyFragment.kt](app/src/main/java/com/terrycollins/celltowerid/ui/fragment/AnomalyFragment.kt) lines 55/70/76-77/85/89; [MapFragment.kt](app/src/main/java/com/terrycollins/celltowerid/ui/fragment/MapFragment.kt) line 685-687; several in [TowerDetailActivity.kt](app/src/main/java/com/terrycollins/celltowerid/ui/TowerDetailActivity.kt)
- Fix: move all to `strings.xml` with `<xliff:g>` placeholders. — 2 h

### 3.9 Onboarding uses ancient stock Android drawables
- **File:** [ui/OnboardingActivity.kt:24-28](app/src/main/java/com/terrycollins/celltowerid/ui/OnboardingActivity.kt) uses `android.R.drawable.ic_dialog_map`, `ic_lock_idle_lock`, `ic_secure`. Dots (`OnboardingActivity.kt:76`) use `android.R.drawable.presence_invisible` — a chat-presence icon.
- For a paid app this is the first impression. Ship proper vector drawables + a proper dot indicator (TabLayoutMediator). — 60 min

### 3.10 CollectionService uses system stop icon
- **File:** [service/CollectionService.kt:399](app/src/main/java/com/terrycollins/celltowerid/service/CollectionService.kt) uses `android.R.drawable.ic_media_pause` for the Stop action. Reviewers flag mismatched icons. Ship a branded stop icon. — 15 min

### 3.11 Licenses screen missing several dependencies — ✅ DONE
- **File:** [ui/LicensesActivity.kt:21-50](app/src/main/java/com/terrycollins/celltowerid/ui/LicensesActivity.kt)
- Not credited: WorkManager, Navigation, Activity/Fragment KTX, ViewPager2, MaterialSwitch. Required by most license types. Add them or switch to `OssLicensesMenuActivity`. — 20 min
- **Resolution (2026-04-28):** Re-audited `LicensesActivity.kt` against `app/build.gradle.kts:77-117`. The original five callouts were already covered: WorkManager, Navigation, Fragment, and ViewPager2 are listed under the AndroidX umbrella entry, and MaterialSwitch ships inside `com.google.android.material:material` which has its own entry. The actual omission was **OkHttp** (Apache 2.0, `implementation(libs.okhttp)` for MapLibre's HTTP layer), now added as a dedicated entry reusing `R.raw.license_apache_2_0`.

### 3.12 No release keystore backup strategy documented
- The on-disk `keystore.properties` + `release.keystore` are the ONLY way to sign updates. Lose them and you can never ship v1.0.1 (Play App Signing can save you, but only if enrolled). Confirmed not committed to git (false alarm during review).
- Fix: Enroll in Play App Signing at first upload. Back up both files to a password manager. Rotate the current plaintext password in `keystore.properties` (it was exposed during this audit). — 30 min

### 3.13 Hardcoded `v1.0.0` in settings layout
- **File:** [res/layout/fragment_settings.xml:230](app/src/main/res/layout/fragment_settings.xml) hardcodes the version string; every future release will still display 1.0.0.
- Fix: bind to `BuildConfig.VERSION_NAME` at runtime. — 10 min

### 3.14 `README.md` claims `targetSdk 35` but actual is `36` — ✅ DONE
- **File:** [README.md:35](README.md) — inconsistency. — 2 min
- **Resolution (2026-04-28):** Updated `README.md:33` (Android SDK requirement) and `README.md:35` (Target SDK) to `36 (Android 16)`.

### 3.15 `PostNotificationsEnabled` check missing when starting foreground service
- **File:** [service/CollectionService.kt:205-215](app/src/main/java/com/terrycollins/celltowerid/service/CollectionService.kt)
- If user denies POST_NOTIFICATIONS on API 33+, the service runs with no visible dismiss. Check `notificationManager.areNotificationsEnabled()` and surface an error if false. — 20 min

### 3.16 `AnomalyFragment.showingAll` state lost on rotation
- **File:** [ui/fragment/AnomalyFragment.kt:26,82-91](app/src/main/java/com/terrycollins/celltowerid/ui/fragment/AnomalyFragment.kt)
- Move `showingAll` into `AnomalyViewModel`. — 15 min

### 3.17 Permission denied → no settings deep-link from Map
- Add a "Grant location" empty state overlay to `fragment_map.xml` that links to `ACTION_APPLICATION_DETAILS_SETTINGS` when fine-location is denied. — 30 min

### 3.18 CSV/GeoJSON/KML export: cleanup old exports
- **File:** [export/ExportWorker.kt:62-64](app/src/main/java/com/terrycollins/celltowerid/export/ExportWorker.kt)
- Directory never cleaned; repeated exports fill device. Delete exports >7 days old at worker start. — 15 min

### 3.19 Locale-unaware `String.format` (lint warnings)
- **File:** [ui/HuntActivity.kt:147,155,156](app/src/main/java/com/terrycollins/celltowerid/ui/HuntActivity.kt)
- Explicit `Locale.US` or `Locale.getDefault()`. — 5 min

### 3.20 `README.md:9` Features bullet had stale heuristic list — ✅ DONE
- **File:** [README.md:9](README.md)
- **Problem:** Top-level Features bullet listed "Seven passive detection heuristics" including the deprecated "unknown towers" entry (UNKNOWN_TOWER was removed in §1.2). The detail table at `README.md:56-68` already lists the correct nine, so the file contradicted itself.
- **Resolution (2026-04-28):** Bullet rewritten to "Nine passive detection heuristics" matching `docs/play-store-listing.md` and the existing detail table — signal anomalies, forced 2G downgrades, forced 3G downgrades, transient towers, impossible tower jumps, LAC/TAC changes, PCI instability, suspicious proximity, operator mismatches. — 5 min

---

## 4. Low-severity polish (nice-to-have, can ship without)

- [ ] Marker clustering on map (cityscale performance win) — [MapFragment.kt:625-649](app/src/main/java/com/terrycollins/celltowerid/ui/fragment/MapFragment.kt), 60 min
- [ ] Day/night map style switching — 30 min
- [ ] Map legend overlay explaining dot colors — 30 min
- [ ] Signal-over-time line chart on Tower Detail (listing-promised) — 2 h
- [ ] GSM/WCDMA chips on Cell List (parity with map) — 15 min
- [ ] BOOT_COMPLETED receiver so collection resumes after reboot — 20 min
- [ ] PCM/`setClipboardSensitiveFlag` on clipboard copy in DebugLogActivity (API 33+) — 10 min
- [ ] Explicit `android:usesCleartextTraffic="false"` + network security config pinning tiles host to HTTPS — 15 min
- [ ] Ship `app/build/outputs/mapping/release/mapping.txt` as release-workflow artifact (symbolicate Play Console stack traces) — 10 min
- [ ] Feature graphic 1024×500 for Play listing (currently absent) — 45 min
- [ ] 512×512 Play Store icon export — 20 min
- [ ] Dependency updates: AGP 9.2.0 (from 9.1.1), Room 2.8.4 (from 2.6.1), Lifecycle 2.10.0 (from 2.8.7), Material 1.13.0, CoreKtx 1.18.0 — 30 min
- [ ] Screenshot captions/localization in Play Console — 20 min
- [ ] Real export progress via `ExportWorker.setProgress` — 30 min
- [ ] 7"/10" tablet screenshots (optional, marks app as tablet-optimized) — 30 min
- [ ] Accessibility sweep: contentDescriptions on onboarding page icons, severity badges, pin toggle — 30 min
- [ ] Sensitivity preset in Settings (anomaly threshold sliders) — 60 min
- [ ] Sanitize data-extraction-rules to add explicit `<exclude domain="database" ...>` — 10 min

---

## 5. Verified OK (no action needed)

These came up during review but are already correct:
- **Keystore not committed** — `git ls-files` confirms only `keystore.properties.example` is tracked; `.gitignore` properly excludes `*.keystore` and `keystore.properties`
- **No IMEI/IMSI/AndroidID collection** — grep for all device-identifier APIs returned zero matches; matches privacy claim
- **No analytics / crash SDKs** — no Firebase, Crashlytics, Sentry, OkHttp/Retrofit; `CrashReporter` persists locally only
- **Exported components** — only `MainActivity` is exported (LAUNCHER); all others and the `CollectionService` and `FileProvider` are `exported="false"`
- **PendingIntent flags** — `FLAG_IMMUTABLE` set correctly throughout
- **Adaptive icon + monochrome + legacy icons** — all present and correct
- **AAB builds + signs** — verified locally, 17.96 MB
- **Permission set is minimal** — no restricted permissions (no READ_PHONE_STATE, SMS, CALL_LOG, QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE)
- **Target SDK 36** — exceeds Play's 2025/2026 requirement
- **ProGuard / R8 enabled** — `isMinifyEnabled` + `isShrinkResources` set on release

---

## 6. Recommended order

1. **Day 0 (today):** Upload any signed AAB to Play Console internal track → starts the 14-day clock. Blockers 1.1–1.6 are listing/policy and can be fixed while the clock runs.
2. **Days 1–2:** Fix all blockers in §1 + High-severity code in §2. Re-run `./gradlew test lint bundleRelease`.
3. **Days 3–5:** Medium-severity fixes in §3, prioritizing 3.7 (dark mode) and 3.8 (hardcoded strings) because they're visible quality.
4. **Days 5–13:** Monitor closed-test feedback; iterate on the most-reported issues. Low-severity polish in §4 where time permits.
5. **Day 14+:** Promote closed → production at 20% rollout, then 50%, then 100% over the following week.

---

## 8. Still needs user action (cannot be done in code)

Only these items remain before the AAB can be submitted to Play for review. Everything else in sections 1–3 has been landed and verified via `./gradlew test lint bundleRelease`.

1. **Verify the privacy policy URL** returns 200 in a browser before tagging — currently `https://cell-tower-id.com/privacy.html`.
2. **Create Google Merchant Center account.** Complete the Ireland tax interview (PPS number + IBAN). Link to Play Console. Set price tier €2.50 EUR with auto-conversion.
3. **Create the app in Play Console** with package `com.terrycollins.celltowerid`; enable Play App Signing on first upload.
4. **Create the 512×512 Play Store icon** (Studio: File → New → Image Asset) and the 1024×500 feature graphic.
5. **Record a 30-second screencast** of the onboarding flow (specifically the new Background Location page and the existing permission dialog) for the Play Console ACCESS_BACKGROUND_LOCATION declaration. Upload with the declaration.
6. **Smoke-test the signed APK on a real device** — see `docs/release-checklist.md:60`. At minimum: onboarding (5 pages now, including Background Location), permission flow, Start collection → notification appears → Stop action → map has attribution visible bottom-left.
7. **Upload AAB to Play Console internal track.** This starts the mandatory 14-day closed-testing clock for new individual developer accounts. Recruit ≥20 testers.
8. **Submit Data Safety form** (content in `docs/play-console-declarations.md`) and **content-rating questionnaire**.
9. **Fill the ACCESS_BACKGROUND_LOCATION and FOREGROUND_SERVICE_LOCATION** permission declarations with the copy from `docs/play-console-declarations.md` + the screen recording from step 5.
10. **Back up `release.keystore` + `keystore.properties`** to a password manager. Rotate the plaintext password that appeared in the review transcript.
11. **Pick target country availability** in Play Console.
12. **Add the 4 GitHub Actions secrets** (`SIGNING_KEY_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`) so tag-driven releases can sign the AAB in CI. Optionally add `PLAY_SERVICE_ACCOUNT_JSON` for auto-upload. See `docs/release-checklist.md:11`.

Once (1)–(6) are green, tag `v1.0.0` and push — CI builds the signed AAB → (if #12 done) auto-uploads to internal track.

## 7. Pre-submission final checklist

- [ ] `./gradlew test lint bundleRelease` green locally
- [ ] Smoke-tested signed APK on a real device (not emulator): onboarding → permission flow → collection start/stop → anomaly appears → export
- [ ] Privacy policy URL returns 200 in a browser
- [ ] Listing text matches code (9 heuristics, no OpenCelliD)
- [ ] Data Safety form submitted and consistent with code
- [ ] Content rating submitted
- [ ] Background location declaration submitted (with screencast if onboarding page not added)
- [ ] Foreground service location declaration submitted
- [ ] Closed test 14-day clock elapsed with ≥20 testers
- [ ] Merchant account live, tax interview complete, €2.50 price tier set
- [ ] Country availability chosen
- [ ] Rollback plan reviewed ([release-checklist.md:82-89](docs/release-checklist.md))
- [ ] Backup of `release.keystore` + rotated password in password manager; Play App Signing enrolled
