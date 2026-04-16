# Cellular Signal Metrics Reference

## LTE Metrics

### RSRP (Reference Signal Received Power)
The primary indicator of signal strength. Measures power of the LTE reference signal at the device antenna.

| RSRP (dBm) | Quality | Bars | User Experience |
|-------------|---------|------|-----------------|
| >= -80 | Excellent | 4-5 | Max throughput, reliable |
| -80 to -90 | Good | 3-4 | Reliable streaming |
| -90 to -100 | Fair | 2-3 | Browsing OK, buffering possible |
| -100 to -110 | Poor | 1-2 | Slow data, dropped calls |
| -110 to -120 | Very Poor | 0-1 | Edge of coverage |
| < -120 | No Signal | 0 | Connection failure |

### RSRQ (Reference Signal Received Quality)
Ratio of RSRP to total wideband power (RSSI). Indicates interference/congestion level.

| RSRQ (dB) | Quality |
|------------|---------|
| >= -10 | Excellent (low interference) |
| -10 to -15 | Good |
| -15 to -20 | Fair to poor (high interference) |
| < -20 | Very poor |

### RSSI (Received Signal Strength Indicator)
Total received power across the entire channel bandwidth, including signal + noise + interference. Less useful alone but needed to compute RSRQ.

- Range: approximately -50 dBm (strong) to -110 dBm (weak)

### SINR / RSSNR (Signal to Interference plus Noise Ratio)
**Best single predictor of achievable throughput.**

| SINR (dB) | Quality | Throughput Impact |
|------------|---------|-------------------|
| >= 20 | Excellent | Max throughput, 256QAM possible |
| 13-20 | Good | Reliable 64QAM |
| 0-13 | Fair | QPSK/16QAM, reduced speed |
| < 0 | Poor | Unreliable connection |

### Timing Advance
Indicates round-trip propagation delay to the tower. Can estimate distance:
- `distance_meters ≈ TA * 78.12` (for LTE, each TA unit = ~78m)

---

## 5G NR Metrics

| LTE Metric | NR Equivalent | Range |
|------------|---------------|-------|
| RSRP | SS-RSRP | -156 to -31 dBm |
| RSRQ | SS-RSRQ | -43 to 20 dB |
| SINR | SS-SINR | -23 to 40 dB |
| -- | CSI-RSRP | -156 to -31 dBm |
| -- | CSI-RSRQ | -43 to 20 dB |
| -- | CSI-SINR | -23 to 40 dB |

- **SS-** metrics: Measured from Synchronization Signal Block (SSB)
- **CSI-** metrics: Measured from Channel State Information Reference Signal
- Sub-6GHz NR thresholds are similar to LTE
- mmWave signals degrade much faster with distance but achieve higher throughput

---

## GSM Metrics

| Metric | Unit | Range | Meaning |
|--------|------|-------|---------|
| RSSI | dBm | -113 to -51 | Total received power |
| BER | 0-7 | Bit Error Rate class | Higher = worse |

## UMTS/WCDMA Metrics

| Metric | Unit | Range | Meaning |
|--------|------|-------|---------|
| RSCP | dBm | -120 to -24 | Received Signal Code Power |
| Ec/No | dB | -24 to 1 | Energy per chip / noise ratio |

---

## Android "Bars" Mapping (Typical)

| Level | RSRP Threshold | RSSNR Threshold |
|-------|---------------|-----------------|
| 4 | >= -85 dBm | >= 12.5 dB |
| 3 | >= -95 dBm | >= -1 dB |
| 2 | >= -105 dBm | - |
| 1 | >= -115 dBm | - |
| 0 | < -115 dBm | - |

*Note: Exact thresholds are OEM-configurable and vary between manufacturers.*

---

## Color Scale for Visualization

Recommended RSRP-based color mapping for Cell Tower ID heatmaps:

| RSRP Range | Color | Hex |
|------------|-------|-----|
| >= -80 dBm | Green | #00C853 |
| -80 to -90 | Light Green | #64DD17 |
| -90 to -100 | Yellow | #FFD600 |
| -100 to -110 | Orange | #FF6D00 |
| -110 to -120 | Red | #D50000 |
| < -120 | Dark Gray | #424242 |

Use semi-transparent overlays (alpha 0.4-0.6) so the base map remains visible.
