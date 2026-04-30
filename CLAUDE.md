# Cell Tower ID - Development Guide

## Project Overview

Cell Tower ID is an Android app for cell tower mapping, signal strength tracking, and IMSI catcher detection. See `docs/` for detailed specifications.

- **Package:** `com.celltowerid.android`
- **Language:** Kotlin
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 36
- **Architecture:** MVVM + Repository pattern

## Build & Run

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests on device/emulator
./gradlew lint                   # Run Android lint
./gradlew clean                  # Clean build artifacts
```

---

## Testing: TDD with BDD Style (MANDATORY)

### The Rule

**Write tests BEFORE implementation. No exceptions.** Follow Red-Green-Refactor:

1. **Red** -- Write a failing test that describes the desired behavior
2. **Green** -- Write the minimum code to make the test pass
3. **Refactor** -- Clean up while keeping tests green

### BDD Style

All tests use **Given/When/Then** structure. Test names describe behavior, not implementation.

#### Naming Convention

```kotlin
@Test
fun `given no cached towers, when querying by area, then fetches from OpenCelliD`() {
    // Given
    val repo = TowerCacheRepository(emptyDatabase, mockApi)

    // When
    val result = repo.getTowersInArea(boundingBox)

    // Then
    assertThat(result).isNotEmpty()
    verify { mockApi.getInArea(any()) }
}
```

Format: `` `given <precondition>, when <action>, then <expected outcome>` ``

For simple cases, `when/then` alone is acceptable:

```kotlin
@Test
fun `when parsing E-UTRAN CID, then extracts correct eNB ID and sector`() {
    // When
    val (enbId, sectorId) = CellIdParser.parseEutranCid(22501123)

    // Then
    assertThat(enbId).isEqualTo(87895)
    assertThat(sectorId).isEqualTo(3)
}
```

### Test Organization

Mirror the source tree:

```
app/src/main/java/com/celltowerid/android/
  ├── model/CellMeasurement.kt
  ├── repository/MeasurementRepository.kt
  └── service/AnomalyDetector.kt

app/src/test/java/com/celltowerid/android/
  ├── model/CellMeasurementTest.kt
  ├── repository/MeasurementRepositoryTest.kt
  └── service/AnomalyDetectorTest.kt

app/src/androidTest/java/com/celltowerid/android/
  ├── repository/MeasurementRepositoryIntegrationTest.kt
  └── ui/MapViewTest.kt
```

### What Goes Where

| Test type | Directory | Framework | Use for |
|-----------|-----------|-----------|---------|
| Unit tests | `src/test/` | JUnit 4 + Truth + MockK | Pure logic, ViewModels, parsers, anomaly scoring |
| Unit tests (Android) | `src/test/` | JUnit 4 + Robolectric | Code that uses Android APIs without needing a device |
| Integration tests | `src/androidTest/` | AndroidX Test + Room in-memory DB | Repository + DB, service integration |
| UI tests | `src/androidTest/` | Espresso | Activity/Fragment interactions |

### Testing Stack

```kotlin
// build.gradle.kts dependencies
testImplementation("junit:junit:4.13.2")
testImplementation("com.google.truth:truth:1.4.2")
testImplementation("io.mockk:mockk:1.13.10")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
testImplementation("org.robolectric:robolectric:4.11.1")
testImplementation("androidx.arch.core:core-testing:2.2.0")

androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("com.google.truth:truth:1.4.2")
androidTestImplementation("androidx.room:room-testing:2.6.1")
```

### Test Checklist (before every PR)

- [ ] Tests written BEFORE the implementation code
- [ ] Test names follow `given/when/then` BDD convention
- [ ] All public functions have at least one test
- [ ] Edge cases covered: null/UNAVAILABLE CellInfo values, empty lists, permission denied states
- [ ] `./gradlew test` passes
- [ ] No `@Ignore` or commented-out tests without a linked issue

---

## Android Development Notes

### CellInfo API Handling

**Always guard against unavailable values.** Different OEMs return different sentinel values:

```kotlin
// WRONG
val rsrp = cellSignalStrength.rsrp

// RIGHT
val rsrp = cellSignalStrength.rsrp.takeIf { it != CellInfo.UNAVAILABLE }
```

Check every field from `CellIdentity` and `CellSignalStrength` for `CellInfo.UNAVAILABLE` or `Integer.MAX_VALUE` before use.

### Deriving eNB from E-UTRAN Cell ID

```kotlin
val enbId = eutranCellId shr 8       // upper 20 bits
val sectorId = eutranCellId and 0xFF // lower 8 bits
```

### Permissions

Runtime permissions are required. Always handle the denied case gracefully:

- `ACCESS_FINE_LOCATION` -- mandatory for `getAllCellInfo()` on API 29+
- `ACCESS_BACKGROUND_LOCATION` -- for background collection (API 29+, must request separately)
- `FOREGROUND_SERVICE_LOCATION` -- required on API 34+
- `POST_NOTIFICATIONS` -- required on API 33+ for foreground service notification

### Background Collection

Use a **Foreground Service** with `android:foregroundServiceType="location"`. Show a persistent notification. Use `FusedLocationProviderClient` for GPS.

### Kotlin Style

- Use Kotlin idioms: `data class`, `sealed class`, `when`, extension functions
- No `!!` operator -- use `?.let {}`, `?: return`, or `requireNotNull()` with a message
- Use `Flow` for reactive data streams from Room/repositories
- Coroutines for async work; never block the main thread

### Room Database

- Define entities with `@Entity`, DAOs with `@Dao`, database with `@Database`
- Always provide migrations (`Migration(N, N+1)`) -- never use `fallbackToDestructiveMigration()` in release builds
- This project ships no seeded Room data. The `tower_cache` table is self-learned from `CollectionService` observations — see `TowerCacheRepository.recordObservation(...)`.
- Test DAOs with in-memory database in `androidTest/`

### API Level Branching

When using APIs not available on minSdk 24:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    // API 29+ code (e.g., CellInfoNr, requestCellInfoUpdate)
} else {
    // Fallback for API 24-28
}
```

### Resource Conventions

- Strings: always in `strings.xml`, never hardcoded
- Dimensions: use `dp` for layout, `sp` for text
- Colors: define in `colors.xml`, reference via theme attributes where possible

---

## Keep Code, Docs, and Website in Sync (MANDATORY)

Whenever a change touches user-visible behavior, surface it in **all three** layers in the same PR. The marketing site and Play Store listing are facts about what the app does — they go stale fast and undermine trust when they drift from the code.

### When this rule fires

Any change that adds, removes, renames, or materially alters:

- An `AnomalyType` (the IMSI-catcher detector lineup) or its severity
- A user-facing feature listed on the website or in `docs/play-store-listing.md` (map filters, export formats, retention controls, tower locator, background collection, pin towers, etc.)
- A required permission
- The supported Android version range (`minSdk` / `targetSdk`)
- The privacy posture (network requests, what's collected, what's shared)

### What to update in the same PR

1. **Code** — the implementation and its tests.
2. **Docs** — at minimum:
   - `docs/05-imsi-catcher-detection.md` — for any detector change (add/remove the row in the scoring table, update weights and severity).
   - `docs/07-app-architecture.md` — for component, data-flow, or schema changes.
   - `docs/play-store-listing.md` — full description bullets, feature list, and any count phrasing ("ten detection heuristics", "10-point IMSI catcher anomaly detection").
   - `docs/privacy-policy.md` — any change to what's collected, stored, or transmitted.
3. **Website** (`website/index.html`) — hero subtitle, the three pillars, the feature grid, the threats grid (HIGH-severity cards) and the "Plus N more" disclosure, the FAQ, and any numeric counts. Threat-card copy should mirror `AnomalyType.explanation`. If you add a HIGH-severity detector, add a `<article class="threat">` card; if it's MEDIUM/LOW, add it inside the `<details class="threats-extra">` block and bump the summary count.

### Detector-count grep checklist

When the `AnomalyType` count changes, search the repo for stale counts before committing:

```bash
grep -rn -E "nine|ten|9-point|10-point|9 (passive|detection)|10 (passive|detection)" website/ docs/
```

Update every match. The current count is the number of entries in [AnomalyType.kt](app/src/main/java/com/celltowerid/android/domain/model/AnomalyType.kt).

### When NOT to update website/docs

Pure internal refactors, bug fixes that restore previously-documented behavior, test-only changes, build/CI tweaks, and developer-only tooling. If in doubt: would a user reading the Play Store listing notice? If yes, update.

---

## Project-Specific References

- `docs/02-android-cellinfo-api.md` -- Full CellInfo API field reference by technology and API level
- `docs/03-signal-metrics-reference.md` -- RSRP/RSRQ/SINR thresholds and color scales
- `docs/04-database-schema-and-export.md` -- SQLite schema and export formats
- `docs/05-imsi-catcher-detection.md` -- Anomaly detection techniques and scoring model
- `docs/07-app-architecture.md` -- Full architecture diagram and component design
- Tower Collector source (`github.com/zamojski/TowerCollector`) -- reference for CellInfo API usage patterns
