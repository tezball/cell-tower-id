# Android CellInfo API Reference

## Overview

Android provides cell tower data through `TelephonyManager` and the `CellInfo` class hierarchy. This is the primary data source for Cell Tower ID.

---

## Core APIs

### `TelephonyManager.getAllCellInfo()`
- Available since API 17 (Android 4.2)
- Returns `List<CellInfo>` for all observed cells (serving + neighbors)
- Can return stale data; prefer `requestCellInfoUpdate()` on API 29+

### `TelephonyManager.requestCellInfoUpdate()` (API 29+)
- Callback-based, returns fresh data
- Preferred over synchronous `getAllCellInfo()`

### Continuous Listening
- **API 31+:** `TelephonyManager.registerTelephonyCallback()` with `TelephonyCallback.CellInfoListener`
- **API 17-30:** `PhoneStateListener.LISTEN_CELL_INFO` (deprecated API 31)

### Deprecated
- `getNeighboringCellInfo()` -- removed in API 29, do not use

---

## CellInfo Subclass Hierarchy

Each subclass contains a `CellIdentity*` and `CellSignalStrength*`:

| Subclass | API Level | Technology |
|----------|-----------|------------|
| `CellInfoGsm` | 17 | 2G GSM |
| `CellInfoCdma` | 17 | 2G/3G CDMA |
| `CellInfoWcdma` | 18 | 3G UMTS |
| `CellInfoLte` | 17 | 4G LTE |
| `CellInfoTdscdma` | 29 | 3G TD-SCDMA |
| `CellInfoNr` | 29 | 5G NR |

---

## CellIdentity Fields by Technology

### CellIdentityLte (API 17+)
| Method | Description | API Level |
|--------|-------------|-----------|
| `getMccString()` | Mobile Country Code | 28+ (int version from 17) |
| `getMncString()` | Mobile Network Code | 28+ |
| `getTac()` | Tracking Area Code | 17 |
| `getCi()` | Cell Identity (28-bit E-UTRAN CID) | 17 |
| `getPci()` | Physical Cell ID (0-503) | 17 |
| `getEarfcn()` | E-UTRA Absolute Radio Freq Channel | 24 |
| `getBandwidth()` | Channel bandwidth (kHz) | 28 |
| `getBands()` | Frequency band indicators | 30 |

**Deriving eNB ID from Cell Identity:**
```
eNB_ID = CI >> 8      // upper 20 bits
sector_ID = CI & 0xFF // lower 8 bits
```

### CellIdentityNr (API 29+)
| Method | Description | API Level |
|--------|-------------|-----------|
| `getMccString()` | Mobile Country Code | 29 |
| `getMncString()` | Mobile Network Code | 29 |
| `getTac()` | Tracking Area Code | 29 |
| `getNci()` | NR Cell Identity (36-bit) | 29 |
| `getPci()` | Physical Cell ID (0-1007) | 29 |
| `getNrarfcn()` | NR Absolute Radio Freq Channel | 29 |
| `getBands()` | Band numbers | 30 |

### CellIdentityGsm (API 17+)
| Method | Description | API Level |
|--------|-------------|-----------|
| `getMcc()`/`getMccString()` | Mobile Country Code | 17/28 |
| `getMnc()`/`getMncString()` | Mobile Network Code | 17/28 |
| `getLac()` | Location Area Code | 17 |
| `getCid()` | Cell ID (16-bit) | 17 |
| `getArfcn()` | ARFCN | 24 |
| `getBsic()` | Base Station Identity Code | 24 |

### CellIdentityWcdma (API 18+)
| Method | Description | API Level |
|--------|-------------|-----------|
| `getMcc()`/`getMccString()` | Mobile Country Code | 18/28 |
| `getMnc()`/`getMncString()` | Mobile Network Code | 18/28 |
| `getLac()` | Location Area Code | 18 |
| `getCid()` | UTRAN Cell ID (28-bit) | 18 |
| `getPsc()` | Primary Scrambling Code | 18 |
| `getUarfcn()` | UARFCN | 24 |

### CellIdentityCdma (API 17+)
| Method | Description |
|--------|-------------|
| `getSystemId()` | System ID |
| `getNetworkId()` | Network ID |
| `getBasestationId()` | Base Station ID |
| `getLatitude()` | Lat (CDMA uniquely provides this) |
| `getLongitude()` | Lon |

---

## CellSignalStrength Fields by Technology

### CellSignalStrengthLte
| Method | Unit | Range | API Level |
|--------|------|-------|-----------|
| `getRsrp()` | dBm | -140 to -44 | 17 |
| `getRsrq()` | dB | -20 to -3 | 17 |
| `getRssi()` | dBm | -110 to -50 | 29 |
| `getRssnr()` | dB | -20 to 30 | 17 |
| `getCqi()` | - | 0-15 | 26 |
| `getTimingAdvance()` | - | 0-1282 | 17 |
| `getLevel()` | - | 0-4 (bars) | 17 |

### CellSignalStrengthNr (API 29+)
| Method | Unit | Range |
|--------|------|-------|
| `getSsRsrp()` | dBm | -156 to -31 |
| `getSsRsrq()` | dB | -43 to 20 |
| `getSsSinr()` | dB | -23 to 40 |
| `getCsiRsrp()` | dBm | -156 to -31 |
| `getCsiRsrq()` | dB | -43 to 20 |
| `getCsiSinr()` | dB | -23 to 40 |
| `getLevel()` | - | 0-4 |

### CellSignalStrengthGsm
| Method | Unit | Range | API Level |
|--------|------|-------|-----------|
| `getRssi()` / `getDbm()` | dBm | -113 to -51 | 17 |
| `getBitErrorRate()` | - | 0-7 | 17 |
| `getTimingAdvance()` | - | 0-219 | 17 |

### CellSignalStrengthWcdma
| Method | Unit | Range | API Level |
|--------|------|-------|-----------|
| `getRscp()` | dBm | -120 to -24 | 28 |
| `getEcNo()` | dB | -24 to 1 | 28 |
| `getRssi()` / `getDbm()` | dBm | - | 18 |

---

## Permissions

| Permission | Required For | API Level |
|------------|-------------|-----------|
| `ACCESS_FINE_LOCATION` | `getAllCellInfo()` | 29+ (mandatory) |
| `ACCESS_COARSE_LOCATION` | Cell info (older APIs) | 17-28 |
| `READ_PHONE_STATE` | Cell info (older APIs) | 17-28 |
| `ACCESS_BACKGROUND_LOCATION` | Background cell collection | 29+ |
| `FOREGROUND_SERVICE` | Foreground service | 26+ |
| `FOREGROUND_SERVICE_LOCATION` | Location foreground service | 34+ |

**Important:** On Android 10+ (API 29), location services must be *enabled* on the device, not just permitted.

---

## Android Version Changelog

| API | Version | Changes |
|-----|---------|---------|
| 17 | 4.2 | `getAllCellInfo()` introduced. GSM, CDMA, LTE. |
| 18 | 4.3 | `CellInfoWcdma` added |
| 24 | 7.0 | EARFCN/UARFCN/ARFCN fields added |
| 26 | 8.0 | CQI for LTE, `getAsuLevel()` standardized |
| 28 | 9.0 | Bandwidth for LTE, `getMccString()`/`getMncString()`, RSCP/EcNo for WCDMA |
| 29 | 10 | `CellInfoNr`, `CellInfoTdscdma`, `requestCellInfoUpdate()`, RSSI for LTE. `ACCESS_FINE_LOCATION` mandatory. `getNeighboringCellInfo()` removed |
| 30 | 11 | `getBands()` added to identity classes |
| 31 | 12 | `PhoneStateListener` deprecated; use `TelephonyCallback` |
| 34 | 14 | `FOREGROUND_SERVICE_LOCATION` required for location foreground services |

---

## OEM Quirks

- Different OEMs implement CellInfo differently; some return `Integer.MAX_VALUE` or `CellInfo.UNAVAILABLE` for unsupported fields
- Samsung, Qualcomm, and MediaTek chipsets have varying completeness
- Neighboring cell info is particularly inconsistent (some devices report many, others none)
- Always check for `UNAVAILABLE` before using any value
- Reference Tower Collector's source for handling these edge cases
