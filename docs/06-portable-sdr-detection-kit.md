# Portable SDR Detection Kit Guide

## Purpose

Hardware and software for portable, backpack-sized IMSI catcher detection using Software Defined Radio. All techniques are **passive reception only** -- never transmit on cellular frequencies.

---

## SDR Hardware Comparison

| SDR | Freq Range | Bandwidth | Bits | Duplex | Cost | Cell Suitability |
|-----|-----------|-----------|------|--------|------|-----------------|
| RTL-SDR Blog V4 | 24-1766 MHz | 2.4 MHz | 8 | RX only | ~$30 | Poor (GSM only) |
| HackRF One | 1 MHz-6 GHz | 20 MHz | 8 | Half | ~$320 | Adequate |
| BladeRF 2.0 micro xA4 | 47 MHz-6 GHz | 56 MHz | 12 | Full | ~$480 | Very Good |
| USRP B200 | 70 MHz-6 GHz | 56 MHz | 12 | Full | ~$1,000 | Excellent (reference) |
| USRP B210 | 70 MHz-6 GHz | 56 MHz | 12 | Full/MIMO | ~$1,600 | Excellent (reference, 2-band) |
| LimeSDR Mini 2.0 | 10 MHz-3.5 GHz | 40 MHz | 12 | Full | ~$275 | Good |
| LimeSDR USB | 100 kHz-3.8 GHz | 61 MHz | 12 | Full/MIMO | ~$550 | Good |

### Key Considerations
- **8-bit vs 12-bit ADC:** 12-bit provides much better dynamic range in crowded RF environments. Important for cellular bands.
- **Bandwidth:** LTE channels are 5-20 MHz wide. Need at least 20 MHz for meaningful LTE monitoring.
- **USRP B200/B210 is the reference platform** for srsRAN and Crocodile Hunter -- best driver support and documentation.
- **Never transmit on cellular frequencies** even if the SDR supports it.

---

## Antennas

Cellular bands span roughly **600 MHz to 2700 MHz** (ignoring mmWave 5G).

### Key US Cellular Bands (Downlink)
| Band | Frequency | Operator |
|------|-----------|----------|
| 71 | 617-652 MHz | T-Mobile |
| 12/17 | 729-746 MHz | AT&T, T-Mobile |
| 13 | 746-756 MHz | Verizon |
| 5 | 869-894 MHz | Various |
| 2/25 | 1930-1995 MHz | PCS |
| 4/66 | 2110-2155 MHz | AWS |
| 7 | 2620-2690 MHz | International LTE |
| n77 | 3700-3980 MHz | C-Band (Verizon, AT&T, T-Mobile) |

### Recommended Antennas

| Type | Coverage | Use Case | Cost |
|------|----------|----------|------|
| Wideband cellular panel (698-2700 MHz) | Most sub-3GHz bands | Best for backpack (compact, moderate gain 6-9 dBi) | $30-60 |
| Wideband discone (e.g., Diamond D130J) | 25-1300 MHz | Good for lower bands, needs small tripod | $80-120 |
| Log-periodic (LPDA) | 700-3000 MHz | Directional, good for direction-finding | $80-200 |
| Telescopic whip (comes with SDRs) | Variable | Nearby strong signals only, poor gain | Included |

**For a backpack kit:** Wideband panel antenna (698-2700 MHz) is the best compromise of size, coverage, and gain.

---

## Software Stack

### srsRAN (formerly srsLTE)
- **Repo:** `srsran/srsRAN_4G` (LTE), `srsran/srsRAN_Project` (5G)
- **License:** AGPL v3
- **Use:** Decode LTE downlink broadcast channels (MIB, SIBs), cell search, signal measurement
- **Key tools:** `cell_search` (scan for cells), `cell_measurement` (lock to cell, report quality)
- **Platform:** Linux (Ubuntu 22.04/24.04)

```bash
# Build srsRAN_4G
sudo apt install build-essential cmake libfftw3-dev libmbedtls-dev \
    libboost-program-options-dev libconfig++-dev libsctp-dev
git clone https://github.com/srsran/srsRAN_4G.git
cd srsRAN_4G && mkdir build && cd build
cmake .. && make -j$(nproc)
```

### gr-gsm (GNU Radio GSM)
- **Repo:** `ptrkrysik/gr-gsm`
- **License:** GPL v3
- **Use:** Receive/demodulate GSM downlink, decode broadcast channels
- **Key tools:**
  - `grgsm_scanner` -- scan for GSM cells (ARFCN, MCC, MNC, LAC, CID)
  - `grgsm_livemon` -- real-time GSM monitoring with GUI
- **Output:** Can pipe to Wireshark for protocol analysis
- **Works with:** RTL-SDR, HackRF, BladeRF, USRP, LimeSDR

### kalibrate-rtl
- **Repo:** `steve-m/kalibrate-rtl`
- **Use:** Calibrate SDR frequency offset using GSM FCCH bursts
- **Essential:** RTL-SDR dongles have 40-70 PPM offset; must calibrate before precise monitoring

```bash
kal -s GSM850    # scan GSM 850 band
kal -s GSM900    # scan GSM 900 band
kal -c <channel> # calibrate against specific channel
```

### LTE-Cell-Scanner
- **Repo:** `JiaoXianjun/LTE-Cell-Scanner`
- **Use:** Quick LTE cell scanning, decodes MIB (PCI, bandwidth, antenna ports)
- **Lighter than srsRAN** but less detailed (MIB only, no SIBs)

### Other Tools
- **Wireshark/tshark:** Essential companion. Decode GSMTAP output from gr-gsm/srsRAN
- **FALCON:** (`falkenber9/falcon`) Decodes LTE DCI in real time. Research tool.
- **QCSuper:** Extracts Qualcomm baseband diag data to PCAP. Requires root + Qualcomm.

---

## Portable Kit Configurations

### Budget Kit (~$625) -- GSM Monitoring

| Component | Model | Cost |
|-----------|-------|------|
| SDR | HackRF One | $320 |
| Computer | Raspberry Pi 5 (8GB) + case + SD | $100 |
| Antenna | Wideband 698-2700 MHz panel | $40 |
| GPS | USB GPS dongle (u-blox 7/8) | $15 |
| Power | 20,000 mAh USB-C PD power bank | $45 |
| Cables | SMA cables, USB cables, adapters | $30 |
| Backpack | Padded laptop backpack | $40 |
| **Subtotal** | | **~$590** |

**Software:** gr-gsm, kalibrate-rtl, custom Python logging scripts
**Runtime:** 8-12+ hours on battery (Pi 5 + SDR ~5-8W)
**Limitation:** GSM scanning only; Pi can't handle real-time LTE decoding

### Mid-Range Kit (~$1,100) -- GSM + LTE Monitoring

| Component | Model | Cost |
|-----------|-------|------|
| SDR | BladeRF 2.0 micro xA4 | $480 |
| Computer | Used ThinkPad T480/T14 (Linux) | $300-400 |
| Antenna | Wideband LPDA 698-2700 MHz | $80 |
| GPS | USB GPS (u-blox 8) | $15 |
| Power | 65W USB-C PD power bank (20K+ mAh) | $50 |
| Cables | SMA cables, USB 3.0 | $30 |
| Backpack | Padded tech backpack | $50 |
| **Subtotal** | | **~$1,000-$1,100** |

**Software:** srsRAN, Crocodile Hunter, gr-gsm, kalibrate-rtl
**Runtime:** 2-4 hours on battery (laptop-dependent)

### High-End Kit (~$2,900) -- Reference Grade

| Component | Model | Cost |
|-----------|-------|------|
| SDR | Ettus USRP B210 (MIMO, 2-band simultaneous) | $1,600 |
| Computer | ThinkPad T14s Gen 4 or similar (Linux) | $800 |
| Antenna | Directional LPDA + omnidirectional whip | $120 |
| GPS | USB GPS with external antenna | $30 |
| Power | EcoFlow River 2 (256 Wh) or large PD bank | $200 |
| Cables | Quality SMA/USB 3.0 | $50 |
| Backpack | Pelican or padded camera backpack | $80 |
| **Subtotal** | | **~$2,880** |

**Software:** Full srsRAN + Crocodile Hunter stack
**Capability:** Monitor two bands simultaneously via MIMO

---

## Legal Considerations

### Passive Reception (Generally Legal)
- Receiving and decoding broadcast radio signals is generally legal in the US, EU, and most countries
- Cell tower broadcast channels (BCCH, MIB, SIBs) are unencrypted and transmitted to all devices in range
- Monitoring your own device's radio behavior is analyzing your own equipment

### DO NOT
- **Transmit** on cellular frequencies without FCC license
- **Operate** a fake base station (illegal under multiple statutes)
- **Decrypt** encrypted traffic channels (calls, SMS, data)
- **Capture/retain** other people's IMSIs or TMSIs beyond what's needed for anomaly detection
- **Interfere** with cellular service

### Best Practices
1. Only receive, never transmit
2. Only decode broadcast channels (BCCH, MIB, SIBs, PCH)
3. Treat any incidentally captured identifiers (IMSI/TMSI) as sensitive PII
4. Document your defensive purpose
5. Check local jurisdiction's specific laws
6. Consider legal counsel for professional/commercial use

### Relevant US Law
- **FCC Part 15:** Receiving is generally unrestricted
- **ECPA (18 U.S.C. 2510-2522):** Prohibits intercepting content of communications; broadcast signaling is generally not considered "content"
- **47 U.S.C. 605:** Prohibits unauthorized divulgence of intercepted content
- **Carpenter v. United States (2018):** Strengthened privacy expectations around cell phone data

### Key Distinction
Passive monitoring of broadcast channels = generally legal.
Intercepting content of calls/data sessions = illegal.
