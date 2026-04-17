package com.terrycollins.celltowerid.data.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.terrycollins.celltowerid.data.AppDatabase
import com.terrycollins.celltowerid.data.entity.AnomalyEntity
import com.terrycollins.celltowerid.data.entity.MeasurementEntity
import com.terrycollins.celltowerid.data.entity.SessionEntity
import com.terrycollins.celltowerid.data.entity.TowerCacheEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: AppDatabase
    private lateinit var measurementDao: MeasurementDao
    private lateinit var anomalyDao: AnomalyDao
    private lateinit var sessionDao: SessionDao
    private lateinit var towerCacheDao: TowerCacheDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        measurementDao = db.measurementDao()
        anomalyDao = db.anomalyDao()
        sessionDao = db.sessionDao()
        towerCacheDao = db.towerCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- MeasurementDao ---

    private fun makeMeasurement(
        lat: Double = 37.7749,
        lon: Double = -122.4194,
        mcc: Int = 310,
        mnc: Int = 260,
        tacLac: Int = 100,
        cid: Long = 12345L,
        sessionId: Long? = null,
        timestamp: Long = System.currentTimeMillis()
    ): MeasurementEntity {
        val e = MeasurementEntity()
        e.latitude = lat
        e.longitude = lon
        e.radio = "LTE"
        e.mcc = mcc
        e.mnc = mnc
        e.tacLac = tacLac
        e.cid = cid
        e.sessionId = sessionId
        e.timestamp = timestamp
        return e
    }

    @Test
    fun given_measurements_when_insertAll_then_getCount_returns_correct_count() {
        measurementDao.insertAll(listOf(makeMeasurement(), makeMeasurement(cid = 99999L)))
        assertThat(measurementDao.getCount()).isEqualTo(2)
    }

    @Test
    fun given_measurements_when_getInArea_then_returns_only_within_bounds() {
        measurementDao.insertAll(listOf(
            makeMeasurement(lat = 37.77, lon = -122.42, cid = 1L),
            makeMeasurement(lat = 40.0, lon = -74.0, cid = 2L)
        ))
        val results = measurementDao.getInArea(37.0, 38.0, -123.0, -122.0)
        assertThat(results).hasSize(1)
        assertThat(results[0].cid).isEqualTo(1L)
    }

    @Test
    fun given_measurements_when_getBySession_then_filters_by_sessionId() {
        measurementDao.insertAll(listOf(
            makeMeasurement(sessionId = 1L, cid = 1L),
            makeMeasurement(sessionId = 2L, cid = 2L)
        ))
        val results = measurementDao.getBySession(1L)
        assertThat(results).hasSize(1)
    }

    @Test
    fun given_measurements_when_getMeasurementsByCell_then_filters_by_cell_identity() {
        measurementDao.insertAll(listOf(
            makeMeasurement(mcc = 310, mnc = 260, tacLac = 100, cid = 555L),
            makeMeasurement(mcc = 310, mnc = 260, tacLac = 100, cid = 999L)
        ))
        val results = measurementDao.getMeasurementsByCell(310, 260, 100, 555L)
        assertThat(results).hasSize(1)
    }

    @Test
    fun given_measurements_when_deleteOlderThan_then_removes_old_records() {
        val now = System.currentTimeMillis()
        measurementDao.insertAll(listOf(
            makeMeasurement(timestamp = now - 100_000, cid = 1L),
            makeMeasurement(timestamp = now, cid = 2L)
        ))
        measurementDao.deleteOlderThan(now - 50_000)
        assertThat(measurementDao.getCount()).isEqualTo(1)
    }

    // --- AnomalyDao ---

    private fun makeAnomaly(
        type: String = "UNKNOWN_TOWER",
        severity: String = "HIGH",
        radio: String? = "LTE",
        mcc: Int? = 310,
        mnc: Int? = 260,
        tacLac: Int? = 100,
        cid: Long? = 555L
    ): AnomalyEntity {
        val e = AnomalyEntity()
        e.timestamp = System.currentTimeMillis()
        e.anomalyType = type
        e.severity = severity
        e.description = "Test anomaly"
        e.cellRadio = radio
        e.cellMcc = mcc
        e.cellMnc = mnc
        e.cellTacLac = tacLac
        e.cellCid = cid
        return e
    }

    @Test
    fun when_insert_anomaly_then_getUndismissed_returns_it() {
        anomalyDao.insert(makeAnomaly())
        val results = anomalyDao.getUndismissed()
        assertThat(results).hasSize(1)
    }

    @Test
    fun when_dismissById_then_getUndismissed_excludes_dismissed() {
        val id = anomalyDao.insert(makeAnomaly())
        anomalyDao.dismissById(id)
        assertThat(anomalyDao.getUndismissed()).isEmpty()
    }

    @Test
    fun when_undismissById_then_getUndismissed_includes_again() {
        val id = anomalyDao.insert(makeAnomaly())
        anomalyDao.dismissById(id)
        anomalyDao.undismissById(id)
        assertThat(anomalyDao.getUndismissed()).hasSize(1)
    }

    @Test
    fun when_dismissAll_then_getUndismissed_returns_empty() {
        anomalyDao.insert(makeAnomaly())
        anomalyDao.insert(makeAnomaly(type = "SIGNAL_ANOMALY"))
        anomalyDao.dismissAll()
        assertThat(anomalyDao.getUndismissed()).isEmpty()
    }

    @Test
    fun given_duplicate_cell_anomalies_when_deleteDuplicateCellAnomalies_then_keeps_one_per_cell() {
        anomalyDao.insert(makeAnomaly(type = "UNKNOWN_TOWER", cid = 100L))
        anomalyDao.insert(makeAnomaly(type = "UNKNOWN_TOWER", cid = 100L))
        anomalyDao.insert(makeAnomaly(type = "UNKNOWN_TOWER", cid = 200L))
        anomalyDao.deleteDuplicateCellAnomalies()
        val all = anomalyDao.getAll()
        val unknownTower = all.filter { it.anomalyType == "UNKNOWN_TOWER" }
        assertThat(unknownTower.filter { it.cellCid == 100L }).hasSize(1)
        assertThat(unknownTower.filter { it.cellCid == 200L }).hasSize(1)
    }

    // --- SessionDao ---

    @Test
    fun when_insert_session_then_getById_returns_it() {
        val session = SessionEntity().apply {
            startTime = System.currentTimeMillis()
            description = "Test"
        }
        val id = sessionDao.insert(session)
        val result = sessionDao.getById(id)
        assertThat(result).isNotNull()
        assertThat(result.description).isEqualTo("Test")
    }

    @Test
    fun given_active_session_when_getActiveSession_then_returns_it() {
        val session = SessionEntity().apply {
            startTime = System.currentTimeMillis()
        }
        sessionDao.insert(session)
        assertThat(sessionDao.getActiveSession()).isNotNull()
    }

    @Test
    fun when_update_endTime_then_getActiveSession_returns_null() {
        val session = SessionEntity().apply {
            startTime = System.currentTimeMillis()
        }
        val id = sessionDao.insert(session)
        val fetched = sessionDao.getById(id)
        fetched.endTime = System.currentTimeMillis()
        sessionDao.update(fetched)
        assertThat(sessionDao.getActiveSession()).isNull()
    }

    @Test
    fun when_getAll_then_returns_ordered_by_startTime_desc() {
        val s1 = SessionEntity().apply { startTime = 1000L; description = "first" }
        val s2 = SessionEntity().apply { startTime = 2000L; description = "second" }
        sessionDao.insert(s1)
        sessionDao.insert(s2)
        val results = sessionDao.getAll()
        assertThat(results).hasSize(2)
        assertThat(results[0].description).isEqualTo("second")
    }

    // --- TowerCacheDao ---

    private fun makeTower(
        radio: String = "LTE",
        mcc: Int = 310,
        mnc: Int = 260,
        tacLac: Int = 100,
        cid: Long = 555L,
        lat: Double? = 37.77,
        lon: Double? = -122.42
    ): TowerCacheEntity {
        val e = TowerCacheEntity()
        e.radio = radio
        e.mcc = mcc
        e.mnc = mnc
        e.tacLac = tacLac
        e.cid = cid
        e.latitude = lat
        e.longitude = lon
        e.samples = 10
        e.source = "test"
        return e
    }

    @Test
    fun when_insert_tower_then_findTower_returns_by_identity() {
        towerCacheDao.insert(makeTower())
        val result = towerCacheDao.findTower("LTE", 310, 260, 100, 555L)
        assertThat(result).isNotNull()
        assertThat(result.latitude).isEqualTo(37.77)
    }

    @Test
    fun given_tower_in_area_when_getTowersInArea_then_returns_it() {
        towerCacheDao.insert(makeTower(lat = 37.77, lon = -122.42))
        towerCacheDao.insert(makeTower(lat = 40.0, lon = -74.0, cid = 999L))
        val results = towerCacheDao.getTowersInArea(37.0, 38.0, -123.0, -122.0)
        assertThat(results).hasSize(1)
    }

    @Test
    fun when_insertAll_with_same_identity_then_replaces() {
        towerCacheDao.insert(makeTower(lat = 37.77))
        towerCacheDao.insert(makeTower(lat = 38.00))
        assertThat(towerCacheDao.getCount()).isEqualTo(1)
        val result = towerCacheDao.findTower("LTE", 310, 260, 100, 555L)
        assertThat(result.latitude).isEqualTo(38.00)
    }

    @Test
    fun when_findAnyByCidRange_then_returns_tower_in_range_with_coordinates() {
        towerCacheDao.insert(makeTower(cid = 500L, lat = 37.77, lon = -122.42))
        towerCacheDao.insert(makeTower(cid = 999L, lat = null, lon = null))
        val result = towerCacheDao.findAnyByCidRange("LTE", 310, 260, 400L, 600L)
        assertThat(result).isNotNull()
        assertThat(result.cid).isEqualTo(500L)
    }
}
