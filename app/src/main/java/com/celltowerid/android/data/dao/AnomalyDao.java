package com.celltowerid.android.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.celltowerid.android.data.entity.AnomalyEntity;

import java.util.List;

@Dao
public interface AnomalyDao {

    @Insert
    long insert(AnomalyEntity anomaly);

    @Query("SELECT * FROM anomalies ORDER BY timestamp DESC")
    List<AnomalyEntity> getAll();

    @Query("SELECT * FROM anomalies WHERE anomaly_type = :anomalyType")
    List<AnomalyEntity> getByType(String anomalyType);

    @Query("SELECT * FROM anomalies WHERE dismissed = 0 ORDER BY timestamp DESC")
    List<AnomalyEntity> getUndismissed();

    @Query("SELECT * FROM anomalies ORDER BY timestamp DESC LIMIT :limit")
    List<AnomalyEntity> getRecentAnomalies(int limit);

    @Update
    void update(AnomalyEntity anomaly);

    @Query("UPDATE anomalies SET dismissed = 1")
    void dismissAll();

    @Query("UPDATE anomalies SET dismissed = 1 WHERE id = :id")
    void dismissById(long id);

    @Query("UPDATE anomalies SET dismissed = 0 WHERE id = :id")
    void undismissById(long id);

    @Query("SELECT COUNT(*) FROM anomalies WHERE anomaly_type = :type AND cell_radio = :radio AND cell_mcc = :mcc AND cell_mnc = :mnc AND cell_tac_lac = :tacLac AND cell_cid = :cid AND dismissed = 0")
    int countByCellIdentity(String type, String radio, int mcc, int mnc, int tacLac, long cid);

    @Query("DELETE FROM anomalies WHERE timestamp < :cutoffMs")
    int deleteOlderThan(long cutoffMs);

    @Query("DELETE FROM anomalies WHERE anomaly_type = :type")
    int deleteByType(String type);

    @Query(
        "DELETE FROM anomalies WHERE id NOT IN (" +
        "  SELECT MIN(id) FROM anomalies " +
        "  WHERE cell_radio IS NOT NULL AND cell_mcc IS NOT NULL AND cell_mnc IS NOT NULL " +
        "    AND cell_tac_lac IS NOT NULL AND cell_cid IS NOT NULL " +
        "  GROUP BY anomaly_type, cell_radio, cell_mcc, cell_mnc, cell_tac_lac, cell_cid" +
        ") AND cell_radio IS NOT NULL AND cell_mcc IS NOT NULL AND cell_mnc IS NOT NULL " +
        "  AND cell_tac_lac IS NOT NULL AND cell_cid IS NOT NULL"
    )
    int deleteDuplicateCellAnomalies();
}
