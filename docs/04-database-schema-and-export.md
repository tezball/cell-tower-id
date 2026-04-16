# Database Schema & Export Formats

## SQLite Schema (via Room)

### measurements table
The core table -- one row per cell observation at a point in time/space.

```sql
CREATE TABLE measurements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,          -- epoch millis
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    gps_accuracy REAL,                   -- meters
    altitude REAL,
    speed REAL,

    -- Cell identity
    radio TEXT NOT NULL,                  -- 'LTE', 'NR', 'GSM', 'WCDMA', 'CDMA', 'TDSCDMA'
    mcc INTEGER,
    mnc INTEGER,
    tac_lac INTEGER,                     -- TAC for LTE/NR, LAC for GSM/WCDMA
    cid INTEGER,                         -- CI for LTE (28-bit), NCI for NR (36-bit), CID for others
    pci_psc INTEGER,                     -- PCI for LTE/NR, PSC for WCDMA
    earfcn_arfcn INTEGER,               -- EARFCN / NRARFCN / UARFCN / ARFCN
    bandwidth INTEGER,                   -- kHz (LTE)
    band INTEGER,                        -- Band number if available

    -- Signal measurements
    rsrp INTEGER,                        -- dBm (LTE RSRP / NR SS-RSRP)
    rsrq INTEGER,                        -- dB  (LTE RSRQ / NR SS-RSRQ)
    rssi INTEGER,                        -- dBm
    sinr INTEGER,                        -- dB  (LTE RSSNR / NR SS-SINR)
    cqi INTEGER,
    timing_advance INTEGER,
    signal_level INTEGER,                -- 0-4 (Android bars)

    -- Metadata
    is_registered INTEGER DEFAULT 0,     -- 1 if serving cell, 0 if neighbor
    operator_name TEXT,
    session_id INTEGER,

    FOREIGN KEY (session_id) REFERENCES sessions(id)
);

CREATE INDEX idx_measurements_time ON measurements(timestamp);
CREATE INDEX idx_measurements_location ON measurements(latitude, longitude);
CREATE INDEX idx_measurements_cell ON measurements(mcc, mnc, tac_lac, cid);
CREATE INDEX idx_measurements_session ON measurements(session_id);
```

### sessions table
Groups measurements by collection session.

```sql
CREATE TABLE sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    start_time INTEGER NOT NULL,
    end_time INTEGER,
    measurement_count INTEGER DEFAULT 0,
    description TEXT,
    exported INTEGER DEFAULT 0
);
```

### tower_cache table
Cached tower locations from external databases (OpenCelliD, FCC ASR, etc.).

```sql
CREATE TABLE tower_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    radio TEXT NOT NULL,
    mcc INTEGER NOT NULL,
    mnc INTEGER NOT NULL,
    tac_lac INTEGER NOT NULL,
    cid INTEGER NOT NULL,
    latitude REAL,
    longitude REAL,
    range_meters INTEGER,
    samples INTEGER,
    source TEXT,                          -- 'opencellid', 'google', 'fcc', 'user'
    last_updated INTEGER,
    UNIQUE(radio, mcc, mnc, tac_lac, cid)
);
```

### anomalies table (for IMSI catcher detection)
Logs detected anomalies for review.

```sql
CREATE TABLE anomalies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    latitude REAL,
    longitude REAL,
    anomaly_type TEXT NOT NULL,          -- 'unknown_tower', 'signal_spike', 'lac_change',
                                         -- '2g_downgrade', 'encryption_downgrade'
    severity TEXT NOT NULL,              -- 'low', 'medium', 'high'
    description TEXT,
    cell_radio TEXT,
    cell_mcc INTEGER,
    cell_mnc INTEGER,
    cell_tac_lac INTEGER,
    cell_cid INTEGER,
    cell_pci INTEGER,
    signal_strength INTEGER,
    dismissed INTEGER DEFAULT 0,
    session_id INTEGER,
    FOREIGN KEY (session_id) REFERENCES sessions(id)
);

CREATE INDEX idx_anomalies_time ON anomalies(timestamp);
CREATE INDEX idx_anomalies_type ON anomalies(anomaly_type);
```

---

## Export Formats

### CSV
Widest compatibility. One row per measurement.

```csv
timestamp,lat,lon,radio,mcc,mnc,tac,cid,pci,earfcn,band,rsrp,rsrq,sinr,is_serving
1681234567890,37.7749,-122.4194,LTE,310,410,7033,17811,234,5230,66,-89,-11,15,1
1681234572890,37.7750,-122.4195,NR,310,410,7033,283648,501,627264,77,-85,-9,22,1
```

### GeoJSON
For web mapping, QGIS, and GIS tools.

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "Point",
        "coordinates": [-122.4194, 37.7749]
      },
      "properties": {
        "timestamp": 1681234567890,
        "radio": "LTE",
        "mcc": 310,
        "mnc": 410,
        "tac": 7033,
        "cid": 17811,
        "enb": 69,
        "pci": 234,
        "earfcn": 5230,
        "band": 66,
        "rsrp": -89,
        "rsrq": -11,
        "sinr": 15,
        "is_serving": true
      }
    }
  ]
}
```

### KML
For Google Earth visualization with color-coded placemarks based on signal strength.

---

## OpenCelliD Compatibility

The CSV format is designed to be compatible with OpenCelliD's schema for easy data exchange:

| OpenCelliD Field | CellID Equivalent |
|------------------|-------------------|
| `radio` | `radio` |
| `mcc` | `mcc` |
| `mnc` | `mnc` |
| `lac` | `tac_lac` |
| `cid` | `cid` |
| `lon` | `longitude` |
| `lat` | `latitude` |
| `range` | (computed from measurements) |
| `samples` | (count of measurements) |
| `averageSignal` | (avg of `rsrp`) |
