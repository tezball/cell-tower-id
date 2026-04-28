package com.celltowerid.android.service

import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthWcdma
import com.google.common.truth.Truth.assertThat
import com.celltowerid.android.domain.model.RadioType
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Drives CellInfoConverter at multiple Android SDK levels via Robolectric so the
// SDK_INT branches in convertLte/convertGsm/convertWcdma are actually exercised.
// Without this, all SDK gates default to whatever Robolectric runs at by default
// (usually compileSdk), and the older branches go untested.
@RunWith(RobolectricTestRunner::class)
class CellInfoConverterTest {

    // --- LTE: SDK 28 (P) -- mccString / mncString path, bandwidth available, operatorAlphaLong available ---

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `given LTE cell on API 28, when convert, then mcc and mnc come from the String fields`() {
        val identity = mockk<CellIdentityLte>(relaxed = true) {
            every { mccString } returns "310"
            every { mncString } returns "260"
            every { tac } returns 12345
            every { ci } returns 67890
            every { pci } returns 100
            every { earfcn } returns 5230
            every { bandwidth } returns 20000
            every { operatorAlphaLong } returns "T-Mobile"
        }
        val signal = mockk<CellSignalStrengthLte>(relaxed = true) {
            every { rsrp } returns -85
            every { rsrq } returns -10
            every { rssnr } returns 15
            // Don't stub `rssi` on API 28 -- the getter doesn't exist until API Q,
            // and the converter doesn't call it on API 28 either.
            every { cqi } returns 7
            every { timingAdvance } returns 4
            every { level } returns 3
        }
        val cellInfo = mockk<CellInfoLte>(relaxed = true) {
            every { isRegistered } returns true
            every { cellIdentity } returns identity
            every { cellSignalStrength } returns signal
        }

        val result = requireNotNull(
            CellInfoConverter.convert(cellInfo, 1L, 0.0, 0.0, 5f, null)
        )

        assertThat(result.radio).isEqualTo(RadioType.LTE)
        assertThat(result.mcc).isEqualTo(310)
        assertThat(result.mnc).isEqualTo(260)
        assertThat(result.tacLac).isEqualTo(12345)
        assertThat(result.cid).isEqualTo(67890L)
        assertThat(result.pciPsc).isEqualTo(100)
        assertThat(result.earfcnArfcn).isEqualTo(5230)
        assertThat(result.bandwidth).isEqualTo(20000)
        assertThat(result.rsrp).isEqualTo(-85)
        assertThat(result.rsrq).isEqualTo(-10)
        assertThat(result.sinr).isEqualTo(15)
        assertThat(result.cqi).isEqualTo(7)
        assertThat(result.timingAdvance).isEqualTo(4)
        // rssi is NOT extracted on API 28 (gated to API Q+)
        assertThat(result.rssi).isNull()
        assertThat(result.operatorName).isEqualTo("T-Mobile")
        assertThat(result.isRegistered).isTrue()
    }

    // --- LTE: SDK 29 (Q) -- rssi now exposed ---

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `given LTE cell on API 29, when convert, then rssi is now extracted`() {
        val identity = mockk<CellIdentityLte>(relaxed = true) {
            every { mccString } returns "310"
            every { mncString } returns "260"
            every { tac } returns 1
            every { ci } returns 1
            every { pci } returns 1
            every { earfcn } returns 1
            every { bandwidth } returns 1
            every { operatorAlphaLong } returns null
        }
        val signal = mockk<CellSignalStrengthLte>(relaxed = true) {
            every { rsrp } returns -85
            every { rsrq } returns -10
            every { rssnr } returns 15
            every { rssi } returns -65
            every { cqi } returns 7
            every { timingAdvance } returns 4
            every { level } returns 3
        }
        val cellInfo = mockk<CellInfoLte>(relaxed = true) {
            every { isRegistered } returns true
            every { cellIdentity } returns identity
            every { cellSignalStrength } returns signal
        }

        val result = requireNotNull(
            CellInfoConverter.convert(cellInfo, 1L, 0.0, 0.0, null, null)
        )

        assertThat(result.rssi).isEqualTo(-65)
    }

    // --- LTE: UNAVAILABLE / sentinel handling ---

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `given LTE cell with UNAVAILABLE sentinel values, when convert, then those fields are null in the result`() {
        // CellInfo.UNAVAILABLE = Int.MAX_VALUE in the platform; the converter
        // must turn either sentinel into null so downstream code doesn't think
        // the field is real.
        val identity = mockk<CellIdentityLte>(relaxed = true) {
            every { mccString } returns null  // unknown carrier
            every { mncString } returns null
            every { tac } returns CellInfo.UNAVAILABLE
            every { ci } returns CellInfo.UNAVAILABLE
            every { pci } returns CellInfo.UNAVAILABLE
            every { earfcn } returns CellInfo.UNAVAILABLE
            every { bandwidth } returns CellInfo.UNAVAILABLE
            every { operatorAlphaLong } returns null
        }
        val signal = mockk<CellSignalStrengthLte>(relaxed = true) {
            every { rsrp } returns CellInfo.UNAVAILABLE
            every { rsrq } returns CellInfo.UNAVAILABLE
            every { rssnr } returns CellInfo.UNAVAILABLE
            every { rssi } returns CellInfo.UNAVAILABLE
            every { cqi } returns CellInfo.UNAVAILABLE
            every { timingAdvance } returns CellInfo.UNAVAILABLE
            every { level } returns 0
        }
        val cellInfo = mockk<CellInfoLte>(relaxed = true) {
            every { isRegistered } returns false
            every { cellIdentity } returns identity
            every { cellSignalStrength } returns signal
        }

        val result = requireNotNull(
            CellInfoConverter.convert(cellInfo, 1L, 0.0, 0.0, null, null)
        )

        // All of these should be null instead of leaking the sentinel value.
        assertThat(result.mcc).isNull()
        assertThat(result.mnc).isNull()
        assertThat(result.tacLac).isNull()
        assertThat(result.cid).isNull()
        assertThat(result.pciPsc).isNull()
        assertThat(result.earfcnArfcn).isNull()
        assertThat(result.bandwidth).isNull()
        assertThat(result.rsrp).isNull()
        assertThat(result.rsrq).isNull()
        assertThat(result.sinr).isNull()
        assertThat(result.rssi).isNull()
        assertThat(result.cqi).isNull()
        assertThat(result.timingAdvance).isNull()
        assertThat(result.operatorName).isNull()
        assertThat(result.isRegistered).isFalse()
    }

    // --- LTE: SDK 33 (TIRAMISU) -- still uses the String-based mcc/mnc path ---

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `given LTE cell on API 33, when convert, then String-based mcc and mnc still work`() {
        val identity = mockk<CellIdentityLte>(relaxed = true) {
            every { mccString } returns "001"  // test MCC
            every { mncString } returns "01"
            every { tac } returns 100
            every { ci } returns 200
            every { pci } returns 1
            every { earfcn } returns 1
            every { bandwidth } returns 1
            every { operatorAlphaLong } returns "Test Carrier"
        }
        val signal = mockk<CellSignalStrengthLte>(relaxed = true) {
            every { rsrp } returns -90
            every { rsrq } returns -12
            every { rssnr } returns 10
            every { rssi } returns -70
            every { cqi } returns 5
            every { timingAdvance } returns 0
            every { level } returns 2
        }
        val cellInfo = mockk<CellInfoLte>(relaxed = true) {
            every { isRegistered } returns true
            every { cellIdentity } returns identity
            every { cellSignalStrength } returns signal
        }

        val result = requireNotNull(
            CellInfoConverter.convert(cellInfo, 1L, 0.0, 0.0, null, null)
        )

        assertThat(result.mcc).isEqualTo(1)
        assertThat(result.mnc).isEqualTo(1)
        assertThat(result.operatorName).isEqualTo("Test Carrier")
        // TA=0 stays as 0 (not null) -- it's a valid value, not a sentinel.
        assertThat(result.timingAdvance).isEqualTo(0)
    }

    // --- GSM ---

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `given GSM cell on API 28, when convert, then mcc and mnc come from String fields and arfcn is captured`() {
        val identity = mockk<CellIdentityGsm>(relaxed = true) {
            every { mccString } returns "310"
            every { mncString } returns "410"
            every { lac } returns 5000
            every { cid } returns 60000
            every { psc } returns 100
            every { arfcn } returns 124
            every { operatorAlphaLong } returns "AT&T"
        }
        val signal = mockk<CellSignalStrengthGsm>(relaxed = true) {
            every { dbm } returns -80
            every { timingAdvance } returns 2
            every { level } returns 3
        }
        val cellInfo = mockk<CellInfoGsm>(relaxed = true) {
            every { isRegistered } returns true
            every { cellIdentity } returns identity
            every { cellSignalStrength } returns signal
        }

        val result = requireNotNull(
            CellInfoConverter.convert(cellInfo, 1L, 0.0, 0.0, null, null)
        )

        assertThat(result.radio).isEqualTo(RadioType.GSM)
        assertThat(result.mcc).isEqualTo(310)
        assertThat(result.mnc).isEqualTo(410)
        assertThat(result.tacLac).isEqualTo(5000)
        assertThat(result.cid).isEqualTo(60000L)
        assertThat(result.pciPsc).isEqualTo(100)
        assertThat(result.earfcnArfcn).isEqualTo(124)
        assertThat(result.rssi).isEqualTo(-80)
        assertThat(result.timingAdvance).isEqualTo(2)
        assertThat(result.operatorName).isEqualTo("AT&T")
    }

    // --- WCDMA ---

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `given WCDMA cell on API 29, when convert, then identity and signal fields are extracted`() {
        val identity = mockk<CellIdentityWcdma>(relaxed = true) {
            every { mccString } returns "310"
            every { mncString } returns "260"
            every { lac } returns 1234
            every { cid } returns 5678
            every { psc } returns 200
            every { uarfcn } returns 10800
            every { operatorAlphaLong } returns "T-Mobile"
        }
        val signal = mockk<CellSignalStrengthWcdma>(relaxed = true) {
            every { dbm } returns -95
            every { level } returns 2
        }
        val cellInfo = mockk<CellInfoWcdma>(relaxed = true) {
            every { isRegistered } returns false
            every { cellIdentity } returns identity
            every { cellSignalStrength } returns signal
        }

        val result = requireNotNull(
            CellInfoConverter.convert(cellInfo, 1L, 0.0, 0.0, null, null)
        )

        assertThat(result.radio).isEqualTo(RadioType.WCDMA)
        assertThat(result.mcc).isEqualTo(310)
        assertThat(result.mnc).isEqualTo(260)
        assertThat(result.tacLac).isEqualTo(1234)
        assertThat(result.cid).isEqualTo(5678L)
        assertThat(result.pciPsc).isEqualTo(200)
        assertThat(result.earfcnArfcn).isEqualTo(10800)
        assertThat(result.rssi).isEqualTo(-95)
        assertThat(result.isRegistered).isFalse()
    }

    // --- Speed propagation (cross-cutting concern) ---

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `given speedMps is provided, when convert LTE, then result has same speedMps`() {
        val cellInfo = makeMinimalLte()

        val result = requireNotNull(
            CellInfoConverter.convert(cellInfo, 1L, 37.0, -122.0, 5f, speedMps = 18.5f)
        )

        assertThat(result.speedMps).isEqualTo(18.5f)
        assertThat(result.gpsAccuracy).isEqualTo(5f)
        assertThat(result.latitude).isEqualTo(37.0)
        assertThat(result.longitude).isEqualTo(-122.0)
    }

    private fun makeMinimalLte(): CellInfoLte {
        val identity = mockk<CellIdentityLte>(relaxed = true) {
            every { mccString } returns "310"
            every { mncString } returns "260"
            every { tac } returns 1
            every { ci } returns 1
            every { pci } returns 1
            every { earfcn } returns 1
            every { bandwidth } returns 1
            every { operatorAlphaLong } returns null
        }
        val signal = mockk<CellSignalStrengthLte>(relaxed = true) {
            every { rsrp } returns -85
            every { rsrq } returns -10
            every { rssnr } returns 15
            every { rssi } returns -65
            every { cqi } returns 7
            every { timingAdvance } returns 0
            every { level } returns 3
        }
        return mockk<CellInfoLte>(relaxed = true) {
            every { isRegistered } returns true
            every { cellIdentity } returns identity
            every { cellSignalStrength } returns signal
        }
    }
}
