package com.terrycollins.celltowerid.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "tower_cache",
    indices = {
        @Index(value = {"radio", "mcc", "mnc", "tac_lac", "cid"}, unique = true)
    }
)
public class TowerCacheEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "radio")
    public String radio = "";

    @ColumnInfo(name = "mcc")
    public int mcc;

    @ColumnInfo(name = "mnc")
    public int mnc;

    @ColumnInfo(name = "tac_lac")
    public int tacLac;

    @ColumnInfo(name = "cid")
    public long cid;

    @Nullable
    @ColumnInfo(name = "latitude")
    public Double latitude;

    @Nullable
    @ColumnInfo(name = "longitude")
    public Double longitude;

    @Nullable
    @ColumnInfo(name = "range_meters")
    public Integer rangeMeters;

    @Nullable
    @ColumnInfo(name = "samples")
    public Integer samples;

    @Nullable
    @ColumnInfo(name = "source")
    public String source;

    @Nullable
    @ColumnInfo(name = "last_updated")
    public Long lastUpdated;
}
