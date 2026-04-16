package com.terrycollins.cellid.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.terrycollins.cellid.data.entity.TowerCacheEntity;

import java.util.List;

@Dao
public interface TowerCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TowerCacheEntity> towers);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TowerCacheEntity tower);

    @Query("SELECT * FROM tower_cache WHERE radio = :radio AND mcc = :mcc AND mnc = :mnc AND tac_lac = :tacLac AND cid = :cid LIMIT 1")
    TowerCacheEntity findTower(String radio, int mcc, int mnc, int tacLac, long cid);

    @Query("SELECT * FROM tower_cache WHERE radio = :radio AND mcc = :mcc AND mnc = :mnc AND cid BETWEEN :minCid AND :maxCid AND latitude IS NOT NULL AND longitude IS NOT NULL LIMIT 1")
    TowerCacheEntity findAnyByCidRange(String radio, int mcc, int mnc, long minCid, long maxCid);

    @Query("SELECT * FROM tower_cache WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon")
    List<TowerCacheEntity> getTowersInArea(double minLat, double maxLat, double minLon, double maxLon);

    @Query("SELECT * FROM tower_cache")
    List<TowerCacheEntity> getAll();

    @Query("SELECT COUNT(*) FROM tower_cache")
    int getCount();
}
