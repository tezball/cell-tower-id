package com.celltowerid.android.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.celltowerid.android.data.entity.MeasurementEntity;

import java.util.List;

@Dao
public interface MeasurementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MeasurementEntity> measurements);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MeasurementEntity measurement);

    @Query("SELECT * FROM measurements WHERE session_id = :sessionId")
    List<MeasurementEntity> getBySession(long sessionId);

    @Query("SELECT * FROM measurements WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon")
    List<MeasurementEntity> getInArea(double minLat, double maxLat, double minLon, double maxLon);

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC LIMIT :limit")
    List<MeasurementEntity> getRecentMeasurements(int limit);

    @Query(
        "SELECT * FROM measurements " +
        "WHERE timestamp >= COALESCE(" +
        "  (SELECT MAX(timestamp) FROM measurements WHERE timestamp >= :sinceMs), " +
        "  :sinceMs" +
        ") - :epsilonMs " +
        "ORDER BY timestamp DESC"
    )
    List<MeasurementEntity> getMeasurementsFromLatestScan(long sinceMs, long epsilonMs);

    @Query("SELECT * FROM measurements WHERE mcc = :mcc AND mnc = :mnc AND tac_lac = :tacLac AND cid = :cid")
    List<MeasurementEntity> getMeasurementsByCell(int mcc, int mnc, int tacLac, long cid);

    /**
     * Returns the strongest reading per unique cell within the bounding box,
     * ranked by COALESCE(rsrp, rssi) DESC (less-negative dBm = stronger),
     * timestamp DESC as tiebreaker. Within a cell all readings share the same
     * radio, so rsrp/rssi are never mixed. Cells without identifying fields
     * (cid/mcc/mnc/tac_lac null) are excluded.
     */
    @Query(
        "SELECT * FROM measurements m1 " +
        "WHERE m1.latitude BETWEEN :minLat AND :maxLat " +
        "  AND m1.longitude BETWEEN :minLon AND :maxLon " +
        "  AND m1.cid IS NOT NULL AND m1.mcc IS NOT NULL " +
        "  AND m1.mnc IS NOT NULL AND m1.tac_lac IS NOT NULL " +
        "  AND m1.id = (" +
        "    SELECT m2.id FROM measurements m2 " +
        "    WHERE m2.radio = m1.radio AND m2.mcc IS m1.mcc AND m2.mnc IS m1.mnc " +
        "      AND m2.tac_lac IS m1.tac_lac AND m2.cid = m1.cid " +
        "      AND m2.latitude BETWEEN :minLat AND :maxLat " +
        "      AND m2.longitude BETWEEN :minLon AND :maxLon " +
        "    ORDER BY COALESCE(m2.rsrp, m2.rssi) DESC, m2.timestamp DESC " +
        "    LIMIT 1" +
        "  )"
    )
    List<MeasurementEntity> getBestMeasurementsInArea(double minLat, double maxLat, double minLon, double maxLon);

    @Query(
        "SELECT COUNT(*) FROM measurements " +
        "WHERE latitude BETWEEN :minLat AND :maxLat " +
        "  AND longitude BETWEEN :minLon AND :maxLon " +
        "  AND timestamp >= :sinceMs"
    )
    int countMeasurementsInArea(
        double minLat, double maxLat, double minLon, double maxLon, long sinceMs
    );

    @Query(
        "SELECT COUNT(*) FROM measurements " +
        "WHERE radio = :radio AND mcc = :mcc AND mnc = :mnc " +
        "  AND tac_lac = :tacLac AND cid = :cid " +
        "  AND latitude BETWEEN :minLat AND :maxLat " +
        "  AND longitude BETWEEN :minLon AND :maxLon " +
        "  AND timestamp >= :sinceMs"
    )
    int countTowerObservationsInArea(
        String radio, int mcc, int mnc, int tacLac, long cid,
        double minLat, double maxLat, double minLon, double maxLon, long sinceMs
    );

    @Query("SELECT * FROM measurements")
    List<MeasurementEntity> getAll();

    @Query("SELECT COUNT(*) FROM measurements")
    int getCount();

    @Query("DELETE FROM measurements WHERE timestamp < :cutoffMs")
    int deleteOlderThan(long cutoffMs);

    @Delete
    void delete(MeasurementEntity measurement);
}
