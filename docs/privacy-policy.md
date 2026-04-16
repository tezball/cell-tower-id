# Cell Tower ID Privacy Policy

**Last updated:** April 16, 2026

Cell Tower ID is a cell tower mapping, signal tracking, and IMSI catcher detection app. Your privacy is fundamental to the app's design.

## Data We Collect

Cell Tower ID collects the following data when you explicitly start a collection session:

- **Location data:** GPS coordinates (latitude, longitude, altitude, speed)
- **Cell tower metadata:** Cell ID (CID), Location Area Code (LAC/TAC), Mobile Country Code (MCC), Mobile Network Code (MNC), Physical Cell ID (PCI), signal strength metrics (RSRP, RSRQ, SINR, RSSI), and radio access technology (LTE, NR, GSM, WCDMA)
- **Session data:** Start and end timestamps of collection sessions

Cell Tower ID does **not** collect:
- Device identifiers (IMEI, IMSI, phone number, Android ID)
- Personal information (name, email, contacts)
- SMS, call logs, browsing history, or any other personal data

## How Data Is Stored

All data is stored **locally on your device** in a SQLite database. No data is ever transmitted to any server, cloud service, or third party.

The only network request Cell Tower ID makes is fetching map tiles from OpenFreeMap (tiles.openfreemap.org) to display the map. No user data is included in these requests.

## Your Controls

- **Start and stop:** You control when collection begins and ends
- **Data retention:** Configure automatic deletion of data older than a specified number of days (0-365) in Settings
- **Data export:** Export your data as CSV, GeoJSON, or KML files. Exports are user-initiated and shared only where you choose
- **Full deletion:** Clear all app data at any time via Android Settings > Apps > Cell Tower ID > Storage > Clear Data

## Background Location

When you start a collection session, Cell Tower ID uses background location access to continue monitoring cell towers when your screen is off. A visible notification is always displayed during active collection. You can stop collection at any time from the notification or the app.

## IMSI Catcher Detection

Cell Tower ID's anomaly detection is entirely passive and defensive. It analyzes cell tower behavior patterns (signal strength anomalies, transient towers, forced 2G downgrades) to alert you to potential threats. No data about detected anomalies is transmitted anywhere.

## Third-Party Services

- **OpenFreeMap:** Map tiles are fetched from tiles.openfreemap.org. No user data is sent.
- **OpenCelliD:** A bundled database of known cell tower locations is included in the app for anomaly detection. No queries are made to external cell tower databases at runtime.

## Changes to This Policy

We may update this privacy policy from time to time. Changes will be posted on this page with an updated revision date.

## Contact

If you have questions about this privacy policy, contact us at tezball86@gmail.com.
