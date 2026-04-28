# Changelog

All notable changes to Cell Tower ID are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] — 2026-04-17

Initial Play Store release.

### Added
- Interactive cell tower map with MapLibre + OpenFreeMap tiles
- Real-time signal strength monitoring (RSRP, RSRQ, SINR, RSSI) for LTE, 5G NR, GSM, WCDMA, TD-SCDMA, CDMA
- Background collection sessions with foreground service + persistent notification
- Tower locator (Locate mode) with hot/cold directional guidance based on signal strength
- 9-point IMSI catcher anomaly detection: abnormal signal strength, forced 2G downgrade, forced 3G downgrade, transient towers, impossible jumps (cached-position check), PCI instability, LAC/TAC change, suspicious proximity (TA=0 + moderate RSRP while stationary), operator/carrier mismatch
- Pin cell towers so they remain visible on the map even when out of range
- CSV, GeoJSON, and KML export of measurements
- Configurable data retention with auto-delete (0–365 days)
- Onboarding flow with permissions rationale (foreground location, background location, notifications)
- Debug log activity for in-app diagnostics
- Open-source license attribution screen

### Privacy
- Zero telemetry, zero analytics, zero crash reporting
- All measurements stored locally in Room SQLite database
- No network egress except map-tile URLs (no user data sent)
- No device identifiers (IMEI, IMSI, phone number) accessed
- SQLite database explicitly excluded from Android cloud backup / device transfer

### Build
- Min SDK 24 (Android 7.0), Target SDK 36 (Android 16)
- ProGuard/R8 minification + resource shrinking enabled for release builds
- GitHub Actions CI: builds debug APK on push, signed release AAB + APK on tag
