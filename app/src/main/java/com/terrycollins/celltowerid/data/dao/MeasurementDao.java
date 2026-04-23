package com.terrycollins.celltowerid.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.terrycollins.celltowerid.data.entity.MeasurementEntity;

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

    @Query("SELECT * FROM measurements")
    List<MeasurementEntity> getAll();

    @Query("SELECT COUNT(*) FROM measurements")
    int getCount();

    @Query("DELETE FROM measurements WHERE timestamp < :cutoffMs")
    int deleteOlderThan(long cutoffMs);

    @Delete
    void delete(MeasurementEntity measurement);
}
