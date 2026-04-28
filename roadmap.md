# Cell Tower ID — Roadmap to Production

**Goal:** Ship v1.0.0 to Google Play Store as a paid app (€2.50 EUR).
**Status:** Pre-launch. App builds and signs; ~1.5 days of code/content work + 14-day closed-testing clock stand between today and production.

Detailed task list lives in [RELEASE_PREP_TASKS.md](RELEASE_PREP_TASKS.md).

---

## Timeline overview

```
Day 0   │ Phase 0: Internal-track upload (starts 14-day clock)
Day 1-2 │ Phase 1: Launch blockers
Day 3-5 │ Phase 2: High-severity code fixes + release rebuild
Day 6-9 │ Phase 3: Quality & polish
Day 10-14│ Phase 4: Closed testing (Google-mandated minimum)
Day 15-22│ Phase 5: Staged production rollout (20% → 50% → 100%)
Week 4+ │ Phase 6: Post-launch monitoring + v1.0.1 patch window
```

The 14-day closed-testing clock is a hard Google requirement for new individual developer accounts. Run Phases 1–3 **in parallel** with it, not sequentially.

---

## Phase 0 — Kick the clock (Day 0) — ⚠ USER ACTION

**Why first:** Every day not spent burning the 14-day clock is a day the launch gets delayed.

The current signed AAB is ready at `app/build/outputs/bundle/release/app-release.aab` (18 MB, v1.0.0). Upload it today.

- [ ] Create the app in Play Console with package `com.celltowerid.android`
- [ ] Upload the current signed AAB to the internal test track
- [ ] Enable Play App Signing at first upload (upload-key compromise becomes recoverable)
- [ ] Create a closed test track and recruit ≥20 testers via email/Google Group
- [ ] Back up `release.keystore` + `keystore.properties` to a password manager; rotate the plaintext password in `keystore.properties`

All Phase 1 + Phase 2 code/content blockers are already landed — you can upload today and the clock starts now.

---

## Phase 1 — Launch blockers (Days 1–2) — 4 of 6 DONE

Gets the listing submittable. Every item here would either cause a Play Store rejection, a license breach, or prevent the paid-app flow.

| # | Blocker | Status | File(s) |
|---|---------|--------|---------|
| 1.1 | OpenStreetMap attribution on map | ✅ Done | `MapFragment.kt` + `fragment_map.xml` |
| 1.2 | Listing rewrite: remove OpenCelliD, fix 7→9 heuristic count | ✅ Done | `docs/play-store-listing.md`, `CHANGELOG.md`, `fastlane/…/1.txt`, `README.md` |
| 1.3 | Enable GitHub Pages for `docs/`; verify privacy-policy URL | ⚠ User | GitHub repo settings |
| 1.4 | Set `android:allowBackup="false"` + explicit DB excludes | ✅ Done | `AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml` |
| 1.5 | Add Background Location onboarding page | ✅ Done | `OnboardingActivity.kt`, `strings.xml` |
| 1.6 | Merchant account, tax interview, €2.50 price tier | ⚠ User | Play Console + Merchant Center |

**Exit criteria:** Store listing text matches code ✅. `./gradlew bundleRelease` green ✅. Privacy URL returns HTTP 200 (pending user enabling GitHub Pages).

---

## Phase 2 — High-severity code fixes (Days 3–5) — ✅ ALL DONE

These are crashes-on-common-paths or clear misbehaviors, plus privacy fixes that tighten the paid-app value claim.

### Crash/race fixes ✅
- [x] Verify fine-location before `startForeground(...)` in `CollectionService` (API 34+ crash on sticky restart if permission revoked)
- [x] Broaden exception catch in `RealCellInfoProvider.getCellMeasurements` (Xiaomi/MediaTek `DeadObjectException`)
- [x] Replace every `!!` with `?:` / `as?` / `requireNotNull` — `HuntViewModel`, `TowerDedup`, `CellListViewModel`, `AnomalyDetector`, `MainActivity`
- [x] Battery-optimization exemption dialog (first start only); added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission

### Data & perf fixes ✅
- [x] Added `@Index(latitude, longitude)` to `TowerCacheEntity` + migration 4→5 with regression test
- [x] Added `deleteOlderThanKeepingPinned` DAO method; wired into `RetentionCleanupWorker`
- [x] Moved `AppLog` dir from `getExternalFilesDir("logs")` → `filesDir/logs`; updated `file_provider_paths.xml`
- [x] Gated `AppLog.d` behind `BuildConfig.DEBUG`; added `-assumenosideeffects` for `android.util.Log.d/v` in release
- [x] `@Transaction`-wrapped `upsertPreservingPin` on DAO; `recordObservation` uses it to avoid pin-flag race
- [x] `MapFragment` observes `pinnedTowerEntities()` and refreshes immediately on pin/unpin (kills 15s lag)
- [x] `lastLocation` staleness check (reject fixes older than 2× scan interval) to stop stamping measurements with old GPS

### Export hardening ✅
- [x] `CsvExporter`: formula-injection sanitization + field quoting + UTF-8 BOM; regression tests added
- [x] `KmlExporter`: escape `]]>` in CDATA; `xmlEscape` for `<name>` content; regression tests added

### Other landed polish
- [x] MapFragment `listenersAttached` reset on retry (lost-listeners bug)
- [x] Diagnostic sampler gated behind `BuildConfig.DEBUG`
- [x] `HuntViewModel.tick` now runs telephony call on `Dispatchers.IO` (no more 1 Hz main-thread jerks)
- [x] Settings version row bound to `BuildConfig.VERSION_NAME`
- [x] `HuntActivity.String.format` calls use `Locale.US`
- [x] Map attribution TextView overlay + MapLibre built-in attribution enabled
- [x] README targetSdk corrected (35 → 36)
- [x] `android:usesCleartextTraffic="false"` added

**Exit criteria:** All unit tests pass ✅. Regression tests added for CSV injection, KML escape, DAO upsert/retention, and v4→v5 migration ✅. Release AAB builds green (`app/build/outputs/bundle/release/app-release.aab`, 18 MB) ✅.

---

## Phase 3 — Quality & polish (Days 6–9)

Land concurrently with closed-testing feedback.

| Area | Items |
|------|-------|
| Dark mode | Replace hardcoded `@android:color/darker_gray`, `#80FFFFFF`, `#4CAF50` with theme attributes across layouts |
| Strings | Move 50+ hardcoded `android:text="..."` to `strings.xml` with `xliff:g` placeholders (enables localization later) |
| Onboarding | Replace stock Android drawables + `presence_invisible` dots with custom vectors + styled indicator |
| Notifications | Use a branded stop icon instead of `android.R.drawable.ic_media_pause` |
| Settings | Bind version row to `BuildConfig.VERSION_NAME` |
| Map UX | Reset `listenersAttached` on retry; gate diagnostic sampler behind `BuildConfig.DEBUG` |
| Robustness | Staleness check on cached `lastLocation`; `Dispatchers.IO` for `HuntViewModel` polling; `@Transaction` for `recordObservation` |
| Exports | Prune exports older than 7 days; differentiate "nothing to export" from "export failed" |
| Accessibility | `contentDescription` sweep; `String.format(Locale, ...)` for lint |
| Licenses | Add missing AndroidX modules (WorkManager, Navigation, VP2, MaterialSwitch) |
| Permissions UX | Empty state with "Grant location" settings-deep-link on Map |

**Exit criteria:** Zero lint warnings (or explicitly suppressed with rationale). Dark mode readable on a real device. `./gradlew test lint bundleRelease` green.

---

## Phase 4 — Closed testing (Days 10–14)

Clock continues. Incorporate tester feedback.

- [ ] Daily: triage Play Console internal-track Android Vitals for crashes/ANRs
- [ ] Daily: triage tester feedback
- [ ] Upload a fresh build at least every 3 days to keep testers active
- [ ] Track OEM-specific issues (Samsung, Xiaomi, OnePlus, Pixel)
- [ ] Smoke test background-location permission flow on at least one Android 10, 12, 14 device

**Exit criteria:** 14 days elapsed. No `Stability=Bad` or `Slow Cold Starts > 5%` on Android Vitals. No open P0/P1 tester reports.

---

## Phase 5 — Staged rollout (Days 15–22)

Google allows controlled %-based rollout on Production. Use it.

- [ ] Day 15: Promote internal → closed → production at **20%** rollout
- [ ] Day 16–17: Watch Vitals, reviews, Merchant Center for first sales
- [ ] Day 18: If clean, bump to **50%**
- [ ] Day 21: If still clean, bump to **100%**
- [ ] Halt-rollout trigger: crash rate > 1%, ANR rate > 0.5%, or any 1-star review referencing data loss / wrong location / account issues

---

## Phase 6 — Post-launch (Week 4+)

Ongoing. Cadence target: v1.0.1 patch within 2 weeks of v1.0.0 production.

- [ ] Collect low-severity polish items deferred from Phase 3 into a v1.0.1 milestone
- [ ] Marker clustering, day/night map style, tower detail line chart, BOOT_COMPLETED receiver, tablet screenshots
- [ ] Dependency updates: AGP 9.2.0, Room 2.8.4, Lifecycle 2.10.0, Material 1.13.0, CoreKtx 1.18.0
- [ ] Upload ProGuard `mapping.txt` as release-workflow artifact for every future release (symbolicates Play Console traces)
- [ ] Monitor review velocity; reply to every 1–3★ review within 48h for the first month
- [ ] Consider localization (es, de, fr, nl) once EN reviews hit 4.3★ average

---

## Definition of done

- App available on Play Store at €2.50 EUR
- Closed-test graduation confirmed
- 100% rollout on Production
- Android Vitals: crash-free rate ≥ 99%, ANR-free rate ≥ 99.5%
- Privacy policy and listing consistent with shipping code
- v1.0.1 milestone opened with deferred polish items

---

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Play policy rejection on background-location | Medium | Blocker | Phase 1 disclosure-in-onboarding + screencast |
| Metadata mismatch rejection (OpenCelliD claims) | High if not fixed | Blocker | Phase 1 rewrite |
| OEM-killed foreground service on Xiaomi/OnePlus | High | 1-star reviews | Battery-opt prompt + document in listing |
| Privacy-policy 404 at review time | Low | Blocker | Phase 1 verification |
| Keystore loss | Low | Catastrophic | Play App Signing enrollment + password-manager backup |
| Crash surge on one OEM after 100% rollout | Medium | Rollback required | Staged rollout + halt-rollout runbook |
| Merchant-account tax-interview delay | Medium | Launch delay | Start in Phase 0; have PPS + IBAN ready |

---

## Cross-references

- Task-level detail: [RELEASE_PREP_TASKS.md](RELEASE_PREP_TASKS.md)
- Per-release runbook: [docs/release-checklist.md](docs/release-checklist.md)
- Play Console form answers: [docs/play-console-declarations.md](docs/play-console-declarations.md)
- Listing copy: [docs/play-store-listing.md](docs/play-store-listing.md) *(needs Phase 1 rewrite)*
- Privacy policy: [docs/privacy-policy.md](docs/privacy-policy.md)
