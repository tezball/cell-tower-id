package com.terrycollins.cellid.util

import com.terrycollins.cellid.domain.model.CellMeasurement
import com.terrycollins.cellid.domain.model.RadioType
import com.terrycollins.cellid.domain.model.SignalQuality
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignalClassifierTest {

    // --- classifyLteRsrp ---

    @Test
    fun `given RSRP at -80 when classifying LTE then returns EXCELLENT`() {
        assertThat(SignalClassifier.classifyLteRsrp(-80)).isEqualTo(SignalQuality.EXCELLENT)
    }

    @Test
    fun `given RSRP above -80 when classifying LTE then returns EXCELLENT`() {
        assertThat(SignalClassifier.classifyLteRsrp(-50)).isEqualTo(SignalQuality.EXCELLENT)
    }

    @Test
    fun `given RSRP at -81 when classifying LTE then returns GOOD`() {
        assertThat(SignalClassifier.classifyLteRsrp(-81)).isEqualTo(SignalQuality.GOOD)
    }

    @Test
    fun `given RSRP at -90 when classifying LTE then returns GOOD`() {
        assertThat(SignalClassifier.classifyLteRsrp(-90)).isEqualTo(SignalQuality.GOOD)
    }

    @Test
    fun `given RSRP at -91 when classifying LTE then returns FAIR`() {
        assertThat(SignalClassifier.classifyLteRsrp(-91)).isEqualTo(SignalQuality.FAIR)
    }

    @Test
    fun `given RSRP at -100 when classifying LTE then returns FAIR`() {
        assertThat(SignalClassifier.classifyLteRsrp(-100)).isEqualTo(SignalQuality.FAIR)
    }

    @Test
    fun `given RSRP at -101 when classifying LTE then returns POOR`() {
        assertThat(SignalClassifier.classifyLteRsrp(-101)).isEqualTo(SignalQuality.POOR)
    }

    @Test
    fun `given RSRP at -110 when classifying LTE then returns POOR`() {
        assertThat(SignalClassifier.classifyLteRsrp(-110)).isEqualTo(SignalQuality.POOR)
    }

    @Test
    fun `given RSRP at -111 when classifying LTE then returns VERY_POOR`() {
        assertThat(SignalClassifier.classifyLteRsrp(-111)).isEqualTo(SignalQuality.VERY_POOR)
    }

    @Test
    fun `given RSRP at -120 when classifying LTE then returns VERY_POOR`() {
        assertThat(SignalClassifier.classifyLteRsrp(-120)).isEqualTo(SignalQuality.VERY_POOR)
    }

    @Test
    fun `given RSRP below -120 when classifying LTE then returns NO_SIGNAL`() {
        assertThat(SignalClassifier.classifyLteRsrp(-121)).isEqualTo(SignalQuality.NO_SIGNAL)
    }

    // --- classifyLteSinr ---

    @Test
    fun `given SINR at 20 when classifying then returns EXCELLENT`() {
        assertThat(SignalClassifier.classifyLteSinr(20)).isEqualTo(SignalQuality.EXCELLENT)
    }

    @Test
    fun `given SINR at 19 when classifying then returns GOOD`() {
        assertThat(SignalClassifier.classifyLteSinr(19)).isEqualTo(SignalQuality.GOOD)
    }

    @Test
    fun `given SINR at 13 when classifying then returns GOOD`() {
        assertThat(SignalClassifier.classifyLteSinr(13)).isEqualTo(SignalQuality.GOOD)
    }

    @Test
    fun `given SINR at 0 when classifying then returns FAIR`() {
        assertThat(SignalClassifier.classifyLteSinr(0)).isEqualTo(SignalQuality.FAIR)
    }

    @Test
    fun `given SINR at -1 when classifying then returns POOR`() {
        assertThat(SignalClassifier.classifyLteSinr(-1)).isEqualTo(SignalQuality.POOR)
    }

    // --- classifyNrSsRsrp ---

    @Test
    fun `given SS-RSRP at -80 when classifying NR then returns EXCELLENT`() {
        assertThat(SignalClassifier.classifyNrSsRsrp(-80)).isEqualTo(SignalQuality.EXCELLENT)
    }

    @Test
    fun `given SS-RSRP at -91 when classifying NR then returns FAIR`() {
        assertThat(SignalClassifier.classifyNrSsRsrp(-91)).isEqualTo(SignalQuality.FAIR)
    }

    @Test
    fun `given SS-RSRP at -121 when classifying NR then returns NO_SIGNAL`() {
        assertThat(SignalClassifier.classifyNrSsRsrp(-121)).isEqualTo(SignalQuality.NO_SIGNAL)
    }

    // --- classifyGsmRssi ---

    @Test
    fun `given RSSI at -70 when classifying GSM then returns EXCELLENT`() {
        assertThat(SignalClassifier.classifyGsmRssi(-70)).isEqualTo(SignalQuality.EXCELLENT)
    }

    @Test
    fun `given RSSI at -71 when classifying GSM then returns GOOD`() {
        assertThat(SignalClassifier.classifyGsmRssi(-71)).isEqualTo(SignalQuality.GOOD)
    }

    @Test
    fun `given RSSI at -85 when classifying GSM then returns GOOD`() {
        assertThat(SignalClassifier.classifyGsmRssi(-85)).isEqualTo(SignalQuality.GOOD)
    }

    @Test
    fun `given RSSI at -100 when classifying GSM then returns FAIR`() {
        assertThat(SignalClassifier.classifyGsmRssi(-100)).isEqualTo(SignalQuality.FAIR)
    }

    @Test
    fun `given RSSI at -110 when classifying GSM then returns POOR`() {
        assertThat(SignalClassifier.classifyGsmRssi(-110)).isEqualTo(SignalQuality.POOR)
    }

    @Test
    fun `given RSSI below -110 when classifying GSM then returns NO_SIGNAL`() {
        assertThat(SignalClassifier.classifyGsmRssi(-111)).isEqualTo(SignalQuality.NO_SIGNAL)
    }

    // --- classify (CellMeasurement) ---

    @Test
    fun `given LTE measurement with RSRP when classified then uses LTE classifier`() {
        // Given
        val measurement = CellMeasurement(
            timestamp = System.currentTimeMillis(),
            latitude = 37.0,
            longitude = -122.0,
            radio = RadioType.LTE,
            rsrp = -85
        )

        // When
        val quality = SignalClassifier.classify(measurement)

        // Then
        assertThat(quality).isEqualTo(SignalQuality.GOOD)
    }

    @Test
    fun `given NR measurement with RSRP when classified then uses NR classifier`() {
        // Given
        val measurement = CellMeasurement(
            timestamp = System.currentTimeMillis(),
            latitude = 37.0,
            longitude = -122.0,
            radio = RadioType.NR,
            rsrp = -95
        )

        // When
        val quality = SignalClassifier.classify(measurement)

        // Then
        assertThat(quality).isEqualTo(SignalQuality.FAIR)
    }

    @Test
    fun `given GSM measurement with RSSI when classified then uses GSM classifier`() {
        // Given
        val measurement = CellMeasurement(
            timestamp = System.currentTimeMillis(),
            latitude = 37.0,
            longitude = -122.0,
            radio = RadioType.GSM,
            rssi = -80
        )

        // When
        val quality = SignalClassifier.classify(measurement)

        // Then
        assertThat(quality).isEqualTo(SignalQuality.GOOD)
    }

    @Test
    fun `given WCDMA measurement with RSSI when classified then uses GSM classifier as fallback`() {
        // Given
        val measurement = CellMeasurement(
            timestamp = System.currentTimeMillis(),
            latitude = 37.0,
            longitude = -122.0,
            radio = RadioType.WCDMA,
            rssi = -65
        )

        // When
        val quality = SignalClassifier.classify(measurement)

        // Then
        assertThat(quality).isEqualTo(SignalQuality.EXCELLENT)
    }

    @Test
    fun `given LTE measurement with no signal data when classified then returns NO_SIGNAL`() {
        // Given
        val measurement = CellMeasurement(
            timestamp = System.currentTimeMillis(),
            latitude = 37.0,
            longitude = -122.0,
            radio = RadioType.LTE
        )

        // When
        val quality = SignalClassifier.classify(measurement)

        // Then
        assertThat(quality).isEqualTo(SignalQuality.NO_SIGNAL)
    }

    // --- getColorForRsrp ---

    @Test
    fun `given excellent RSRP when getting color then returns green`() {
        assertThat(SignalClassifier.getColorForRsrp(-75)).isEqualTo("#00C853")
    }

    @Test
    fun `given poor RSRP when getting color then returns orange`() {
        assertThat(SignalClassifier.getColorForRsrp(-105)).isEqualTo("#FF6D00")
    }
}
