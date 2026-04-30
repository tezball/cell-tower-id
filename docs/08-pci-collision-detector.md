# PCI Collision Detector (Design)

**Status:** Implemented. See `AnomalyDetector.checkPciCollision` and `AnomalyDetectorTest`. This document remains the canonical spec — edit it when changing detector behavior.

## Motivation

PCI (Physical Cell Identity) is a 0–503 integer that LTE/NR base stations broadcast on the air interface. Real network operators coordinate PCI assignments so that geographically adjacent cells don't share the same value — a PCI collision causes interference and dropped handovers, so legitimate networks avoid them deliberately.

A fake cell (IMSI catcher) typically picks an arbitrary PCI without knowing what's locally allocated. This produces three observable fingerprints:

1. **True PCI collision** — the same PCI is observed with two different `(mcc, mnc, tac, cid)` identities at the same location and time.
2. **PCI reuse over time** — a familiar PCI suddenly hosts a new CID. The legitimate cell didn't change PCI (real eNBs never do — see existing `PCI_INSTABILITY` detector); a different cell appropriated the PCI.
3. **PCI roaming** — the same PCI "moves" across CIDs in the bbox over time, suggesting an attacker cycling identities.

This complements the existing detectors:

| Detector | Fingerprint |
|---|---|
| `PCI_INSTABILITY` | Same CID, PCI changed (cell impersonating itself) |
| `POPUP_TOWER` | New CID appears in familiar area |
| `PCI_COLLISION` (this) | Same PCI used by multiple CIDs OR familiar PCI repurposed |

`PCI_COLLISION` is the cleanest IMSI-catcher tell of the three because real networks structurally avoid it.

## Detector spec

New method `checkPciCollision(measurement: CellMeasurement): AnomalyEvent?` in [`AnomalyDetector.kt`](../app/src/main/java/com/celltowerid/android/service/AnomalyDetector.kt), registered alongside the other detectors in `analyze()`.

### Severity

`HIGH`. PCI collisions in real networks are rare and indicate either operator misconfiguration (hand-fixable) or a fake cell. Always HIGH; no bootstrap demotion (the signal is fingerprint-grade, not statistical).

### Gates (in order)

1. Registered to network — else return null.
2. Has `mcc, mnc, tacLac, cid` — else return null.
3. Has `pciPsc` — else return null (the detector inputs are PCI-keyed).
4. Speed gate — `speedMps == null || speedMps <= DRIVING_SPEED_MPS`. Driving past tightly-packed urban cells can briefly observe legitimate PCI reuse outside the device's actual serving cell; gate it.
5. Dedupe key — `pci-{radio}-{mcc}-{mnc}-{pci}-{6h-bucket}`. Same dedupe shape as POPUP_TOWER (re-fire after 6h of absence).
6. **Collision check** — query 1 below. If ≥ 2 distinct CIDs use this PCI in the bbox + time window, fire `PCI_COLLISION`.
7. **Reuse check** — query 2 below. Otherwise, if the most-recent CID using this PCI in the window is a *different* CID than the current one, fire `PCI_REUSE`.

Both branches produce an `AnomalyType.PCI_COLLISION` event (single new enum value); the description text distinguishes "collision" vs "reuse."

### DAO additions

Add to [`MeasurementDao.java`](../app/src/main/java/com/celltowerid/android/data/dao/MeasurementDao.java):

```java
@Query(
    "SELECT COUNT(DISTINCT cid) FROM measurements " +
    "WHERE pci_psc = :pci AND radio = :radio " +
    "  AND mcc = :mcc AND mnc = :mnc " +
    "  AND latitude BETWEEN :minLat AND :maxLat " +
    "  AND longitude BETWEEN :minLon AND :maxLon " +
    "  AND timestamp >= :sinceMs AND timestamp < :beforeMs"
)
int countDistinctCidsForPci(
    String radio, int mcc, int mnc, int pci,
    double minLat, double maxLat, double minLon, double maxLon,
    long sinceMs, long beforeMs
);

@Query(
    "SELECT cid FROM measurements " +
    "WHERE pci_psc = :pci AND radio = :radio " +
    "  AND mcc = :mcc AND mnc = :mnc AND cid IS NOT NULL " +
    "  AND latitude BETWEEN :minLat AND :maxLat " +
    "  AND longitude BETWEEN :minLon AND :maxLon " +
    "  AND timestamp >= :sinceMs AND timestamp < :beforeMs " +
    "ORDER BY timestamp DESC LIMIT 1"
)
Long findMostRecentCidForPci(
    String radio, int mcc, int mnc, int pci,
    double minLat, double maxLat, double minLon, double maxLon,
    long sinceMs, long beforeMs
);
```

Schema: no new entity. The existing `measurements.pci_psc` column (`MeasurementEntity`, `CellMeasurement.pciPsc`) is sufficient.

**Optional index** (defer until measured): if query plans become slow at scale, add to `MeasurementEntity`:
```java
@Index(value = {"pci_psc", "mcc", "mnc"})
```
Don't add prematurely — `EXPLAIN QUERY PLAN` first; the existing `(timestamp)` and bbox indices may already be sufficient.

### Constants (companion object in `AnomalyDetector`)

```kotlin
private const val PCI_COLLISION_RADIUS_METERS = 2_000.0  // same bbox as POPUP_TOWER
private const val PCI_COLLISION_WINDOW_MS = 7L * 24 * 60 * 60 * 1000  // 7 days
```

## Test plan (BDD)

In [`AnomalyDetectorTest.kt`](../app/src/test/java/com/celltowerid/android/service/AnomalyDetectorTest.kt), mirroring the POPUP_TOWER block. Required cases:

- `given two distinct CIDs sharing a PCI in the bbox, when measurement arrives, then PCI_COLLISION fires HIGH`
- `given a single CID per PCI in the bbox, when measurement arrives, then no PCI_COLLISION`
- `given a familiar PCI now hosted by a new CID, when measurement arrives, then PCI_REUSE fires`
- `given driving speed above threshold, when collision conditions met, then no PCI_COLLISION` (speed gate)
- `given measurement with null pciPsc, when checkPciCollision runs, then returns null`
- `given PCI_COLLISION fires once, when same PCI seen 5 minutes later, then no duplicate alert` (dedupe)
- `given gap > 6 hours after PCI_COLLISION, when same PCI seen again, then re-fires` (dedupe bucket roll)

## Open questions for the implementation PR

1. **Bbox radius.** POPUP_TOWER uses 2 km. PCI collisions can manifest further out (PCI is a small integer space, 504 values across all of LTE), but 2 km matches the user's "places they actually go" intuition. **Recommendation:** start at 2 km, widen if false-negatives appear in field testing.

2. **Window length.** POPUP_TOWER uses 7 days. PCI reuse over months is rarer and noisier (operators do occasionally renumber). **Recommendation:** 7 days for first cut.

3. **Severity demotion in bootstrap.** POPUP_TOWER demotes to MEDIUM when the area baseline is < 7 days. PCI_COLLISION is a fingerprint-grade signal, not a statistical one — a true collision is suspicious even on a fresh dataset. **Recommendation:** always HIGH, no bootstrap demotion.

4. **Should this fire while unregistered?** POPUP_TOWER fires only when registered (the cell is actively serving you). PCI_COLLISION could in principle fire on neighbour-list cells too — a fake cell broadcasting nearby is dangerous even if you haven't camped on it. **Recommendation:** keep registered-only for v1 to match the existing detector pattern; revisit if neighbour-cell observations are valuable.

5. **Threat-score weight.** Update `computeThreatScore` in `AnomalyDetector.kt` — POPUP_TOWER is weighted 3, IMPOSSIBLE_MOVE is 6. PCI_COLLISION is comparable in fingerprint quality to IMPOSSIBLE_MOVE. **Recommendation:** weight 4 (between POPUP and IMPOSSIBLE_MOVE) for v1.

## Out of scope

- Cross-operator collisions (different `(mcc, mnc)` sharing a PCI). PCI is operator-private — a collision *across* operators isn't itself an attack signal.
- Decoding the actual PCI cell-search sequence (PSS/SSS) — that's SDR territory, not phone-API territory. See `docs/06-portable-sdr-detection-kit.md`.
- Implementing this detector — that's a follow-up PR.
