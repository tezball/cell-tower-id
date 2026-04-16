package com.terrycollins.cellid.service

import com.terrycollins.cellid.domain.model.CellMeasurement
import com.terrycollins.cellid.domain.model.RadioType
import kotlin.random.Random

class MockCellInfoProvider : CellInfoProvider {

    override fun isAvailable(): Boolean = true

    override fun getCellMeasurements(
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float?,
        speedMps: Float?
    ): List<CellMeasurement> {
        val timestamp = System.currentTimeMillis()
        val measurements = mutableListOf<CellMeasurement>()

        // Serving LTE cell (registered) with good signal
        measurements.add(
            CellMeasurement(
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                gpsAccuracy = gpsAccuracy,
                radio = RadioType.LTE,
                mcc = 310,
                mnc = 260,
                tacLac = 12345,
                cid = 50331905L,  // eNB 196608, sector 1
                pciPsc = 214,
                earfcnArfcn = 2050,
                bandwidth = 15000,
                rsrp = -82 + Random.nextInt(-3, 4),
                rsrq = -9 + Random.nextInt(-3, 4),
                rssi = -55 + Random.nextInt(-3, 4),
                sinr = 18 + Random.nextInt(-3, 4),
                cqi = 12 + Random.nextInt(-2, 3),
                timingAdvance = 2,
                signalLevel = 3,
                isRegistered = true,
                operatorName = "T-Mobile"
            )
        )

        // Neighbor LTE cell #1 - moderate signal
        measurements.add(
            CellMeasurement(
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                gpsAccuracy = gpsAccuracy,
                radio = RadioType.LTE,
                mcc = 310,
                mnc = 260,
                tacLac = 12345,
                cid = 50331906L,  // eNB 196608, sector 2
                pciPsc = 215,
                earfcnArfcn = 2050,
                rsrp = -98 + Random.nextInt(-3, 4),
                rsrq = -13 + Random.nextInt(-3, 4),
                rssi = -70 + Random.nextInt(-3, 4),
                sinr = 8 + Random.nextInt(-3, 4),
                signalLevel = 2,
                isRegistered = false,
                operatorName = "T-Mobile"
            )
        )

        // Neighbor LTE cell #2 - weak signal
        measurements.add(
            CellMeasurement(
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                gpsAccuracy = gpsAccuracy,
                radio = RadioType.LTE,
                mcc = 310,
                mnc = 260,
                tacLac = 12346,
                cid = 50397441L,  // different eNB
                pciPsc = 120,
                earfcnArfcn = 5230,
                rsrp = -108 + Random.nextInt(-3, 4),
                rsrq = -16 + Random.nextInt(-3, 4),
                rssi = -82 + Random.nextInt(-3, 4),
                sinr = 2 + Random.nextInt(-3, 4),
                signalLevel = 1,
                isRegistered = false,
                operatorName = "T-Mobile"
            )
        )

        // Neighbor NR cell - 5G detected nearby
        measurements.add(
            CellMeasurement(
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                gpsAccuracy = gpsAccuracy,
                radio = RadioType.NR,
                mcc = 310,
                mnc = 260,
                tacLac = 12345,
                cid = 268435457L,
                pciPsc = 502,
                earfcnArfcn = 627264,
                rsrp = -92 + Random.nextInt(-3, 4),
                rsrq = -11 + Random.nextInt(-3, 4),
                sinr = 14 + Random.nextInt(-3, 4),
                signalLevel = 2,
                isRegistered = false,
                operatorName = "T-Mobile"
            )
        )

        // Occasionally add a 5th GSM neighbor (50% chance)
        if (Random.nextBoolean()) {
            measurements.add(
                CellMeasurement(
                    timestamp = timestamp,
                    latitude = latitude,
                    longitude = longitude,
                    gpsAccuracy = gpsAccuracy,
                    radio = RadioType.GSM,
                    mcc = 310,
                    mnc = 260,
                    tacLac = 501,
                    cid = 4120L,
                    pciPsc = null,
                    earfcnArfcn = 150,
                    rssi = -95 + Random.nextInt(-3, 4),
                    signalLevel = 1,
                    isRegistered = false,
                    operatorName = "T-Mobile"
                )
            )
        }

        return measurements
    }
}
