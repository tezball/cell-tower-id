package com.celltowerid.android.repository

import com.celltowerid.android.data.dao.MeasurementDao
import com.celltowerid.android.data.entity.MeasurementEntity
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MeasurementRepositoryTest {

    private lateinit var measurementDao: MeasurementDao
    private lateinit var repository: MeasurementRepository

    @Before
    fun setUp() {
        measurementDao = mockk(relaxed = true)
        repository = MeasurementRepository(measurementDao)
    }

    // --- insertMeasurements ---

    @Test
    fun `given measurements when insertMeasurements then dao insertAll is called with mapped entities`() =
        runTest {
            val measurements = listOf(
                CellMeasurement(
                    timestamp = 1000L,
                    latitude = 37.7749,
                    longitude = -122.4194,
                    radio = RadioType.LTE,
                    mcc = 310,
                    mnc = 260,
                    tacLac = 12345,
                    cid = 50331905L,
                    isRegistered = true
                )
            )

            repository.insertMeasurements(measurements, sessionId = 5L)

            val entitiesSlot = slot<List<MeasurementEntity>>()
            verify { measurementDao.insertAll(capture(entitiesSlot)) }

            val captured = entitiesSlot.captured
            assertThat(captured).hasSize(1)
            assertThat(captured[0].timestamp).isEqualTo(1000L)
            assertThat(captured[0].radio).isEqualTo("LTE")
            assertThat(captured[0].mcc).isEqualTo(310)
            assertThat(captured[0].sessionId).isEqualTo(5L)
        }

    @Test
    fun `given empty list when insertMeasurements then dao insertAll is called with empty list`() =
        runTest {
            repository.insertMeasurements(emptyList(), sessionId = 1L)

            verify { measurementDao.insertAll(emptyList()) }
        }

    // --- getRecentMeasurements ---

    @Test
    fun `given entities in dao when getRecentMeasurements then returns mapped domain models`() =
        runTest {
            val entity1 = MeasurementEntity().apply {
                id = 1
                timestamp = 2000L
                latitude = 37.0
                longitude = -122.0
                radio = "LTE"
                mcc = 310
                mnc = 260
                tacLac = 12345
                cid = 50331905L
                rsrp = -85
                isRegistered = true
                operatorName = "T-Mobile"
            }
            val entity2 = MeasurementEntity().apply {
                id = 2
                timestamp = 1000L
                latitude = 38.0
                longitude = -121.0
                radio = "NR"
                mcc = 311
                mnc = 480
                tacLac = 9999
                cid = 268435457L
                rsrp = -92
                isRegistered = false
                operatorName = "Verizon"
            }
            every { measurementDao.getRecentMeasurements(10) } returns listOf(entity1, entity2)

            val results = repository.getRecentMeasurements(10)

            assertThat(results).hasSize(2)
            assertThat(results[0].timestamp).isEqualTo(2000L)
            assertThat(results[0].radio).isEqualTo(RadioType.LTE)
            assertThat(results[0].isRegistered).isTrue()
            assertThat(results[1].timestamp).isEqualTo(1000L)
            assertThat(results[1].radio).isEqualTo(RadioType.NR)
            assertThat(results[1].isRegistered).isFalse()
        }

    @Test
    fun `given no entities when getRecentMeasurements then returns empty list`() = runTest {
        every { measurementDao.getRecentMeasurements(any()) } returns emptyList()

        val results = repository.getRecentMeasurements()

        assertThat(results).isEmpty()
    }

    // --- getMeasurementsBySession ---

    @Test
    fun `given entities for session when getMeasurementsBySession then returns mapped domain models`() =
        runTest {
            val entity = MeasurementEntity().apply {
                id = 1
                timestamp = 3000L
                latitude = 37.0
                longitude = -122.0
                radio = "GSM"
                sessionId = 42L
                isRegistered = false
            }
            every { measurementDao.getBySession(42L) } returns listOf(entity)

            val results = repository.getMeasurementsBySession(42L)

            assertThat(results).hasSize(1)
            assertThat(results[0].radio).isEqualTo(RadioType.GSM)
        }

    // --- getMeasurementsInArea ---

    @Test
    fun `given entities in area when getMeasurementsInArea then returns mapped domain models`() =
        runTest {
            val entity = MeasurementEntity().apply {
                id = 1
                timestamp = 4000L
                latitude = 37.5
                longitude = -122.2
                radio = "LTE"
                isRegistered = true
            }
            every {
                measurementDao.getInArea(37.0, 38.0, -123.0, -122.0)
            } returns listOf(entity)

            val results = repository.getMeasurementsInArea(37.0, 38.0, -123.0, -122.0)

            assertThat(results).hasSize(1)
            assertThat(results[0].latitude).isEqualTo(37.5)
        }

    // --- getBestMeasurementsByCellInArea ---

    @Test
    fun `given dao returns one entity per cell, when getBestMeasurementsByCellInArea, then maps domain models keyed by CellKey`() =
        runTest {
            val entity = MeasurementEntity().apply {
                id = 1
                timestamp = 9000L
                latitude = 37.5
                longitude = -122.2
                radio = "LTE"
                mcc = 310
                mnc = 260
                tacLac = 12345
                cid = 50331905L
                rsrp = -82
            }
            every {
                measurementDao.getBestMeasurementsInArea(37.0, 38.0, -123.0, -122.0)
            } returns listOf(entity)

            val results = repository.getBestMeasurementsByCellInArea(37.0, 38.0, -123.0, -122.0)

            assertThat(results).hasSize(1)
            val key = com.celltowerid.android.domain.model.CellKey(
                radio = RadioType.LTE,
                mcc = 310,
                mnc = 260,
                tacLac = 12345,
                cid = 50331905L
            )
            assertThat(results).containsKey(key)
            assertThat(results[key]?.rsrp).isEqualTo(-82)
        }

    @Test
    fun `given entity with null mcc, when getBestMeasurementsByCellInArea, then entry is excluded from map`() =
        runTest {
            val entity = MeasurementEntity().apply {
                id = 1
                timestamp = 9000L
                latitude = 37.5
                longitude = -122.2
                radio = "LTE"
                mcc = null
                mnc = 260
                tacLac = 12345
                cid = 50331905L
                rsrp = -82
            }
            every {
                measurementDao.getBestMeasurementsInArea(any(), any(), any(), any())
            } returns listOf(entity)

            val results = repository.getBestMeasurementsByCellInArea(37.0, 38.0, -123.0, -122.0)

            assertThat(results).isEmpty()
        }

    @Test
    fun `given dao returns empty list, when getBestMeasurementsByCellInArea, then returns empty map`() =
        runTest {
            every {
                measurementDao.getBestMeasurementsInArea(any(), any(), any(), any())
            } returns emptyList()

            val results = repository.getBestMeasurementsByCellInArea(37.0, 38.0, -123.0, -122.0)

            assertThat(results).isEmpty()
        }

    // --- getMeasurementCount ---

    @Test
    fun `given count in dao when getMeasurementCount then returns count`() = runTest {
        every { measurementDao.getCount() } returns 42

        val count = repository.getMeasurementCount()

        assertThat(count).isEqualTo(42)
    }

    // --- deleteOlderThan ---

    @Test
    fun `given a cutoff, when deleteOlderThan, then dao deleteOlderThan is called with that cutoff`() =
        runTest {
            // Given
            every { measurementDao.deleteOlderThan(12345L) } returns 7

            // When
            val deleted = repository.deleteOlderThan(12345L)

            // Then
            assertThat(deleted).isEqualTo(7)
            verify(exactly = 1) { measurementDao.deleteOlderThan(12345L) }
        }

    // --- getAllMeasurements ---

    @Test
    fun `given entities in dao when getAllMeasurements then returns all mapped domain models`() =
        runTest {
            val entity = MeasurementEntity().apply {
                id = 1
                timestamp = 5000L
                latitude = 37.0
                longitude = -122.0
                radio = "CDMA"
                isRegistered = false
            }
            every { measurementDao.getAll() } returns listOf(entity)

            val results = repository.getAllMeasurements()

            assertThat(results).hasSize(1)
            assertThat(results[0].radio).isEqualTo(RadioType.CDMA)
        }
}
