package com.celltowerid.android.data.entity;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sessions")
public class SessionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "start_time")
    public long startTime;

    @Nullable
    @ColumnInfo(name = "end_time")
    public Long endTime;

    @ColumnInfo(name = "measurement_count", defaultValue = "0")
    public int measurementCount;

    @Nullable
    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "exported", defaultValue = "0")
    public boolean exported;
}
