package com.terrycollins.celltowerid.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "measurements",
    indices = {
        @Index(value = {"timestamp"}),
        @Index(value = {"latitude", "longitude"}),
        @Index(value = {"mcc", "mnc", "tac_lac", "cid"}),
        @Index(value = {"session_id"})
    }
)
public class MeasurementEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "latitude")
    public double latitude;

    @ColumnInfo(name = "longitude")
    public double longitude;

    @Nullable
    @ColumnInfo(name = "gps_accuracy")
    public Float gpsAccuracy;

    @Nullable
    @ColumnInfo(name = "altitude")
    public Double altitude;

    @Nullable
    @ColumnInfo(name = "speed")
    public Float speed;

    @NonNull
    @ColumnInfo(name = "radio")
    public String radio = "";

    @Nullable
    @ColumnInfo(name = "mcc")
    public Integer mcc;

    @Nullable
    @ColumnInfo(name = "mnc")
    public Integer mnc;

    @Nullable
    @ColumnInfo(name = "tac_lac")
    public Integer tacLac;

    @Nullable
    @ColumnInfo(name = "cid")
    public Long cid;

    @Nullable
    @ColumnInfo(name = "pci_psc")
    public Integer pciPsc;

    @Nullable
    @ColumnInfo(name = "earfcn_arfcn")
    public Integer earfcnArfcn;

    @Nullable
    @ColumnInfo(name = "bandwidth")
    public Integer bandwidth;

    @Nullable
    @ColumnInfo(name = "band")
    public Integer band;

    @Nullable
    @ColumnInfo(name = "rsrp")
    public Integer rsrp;

    @Nullable
    @ColumnInfo(name = "rsrq")
    public Integer rsrq;

    @Nullable
    @ColumnInfo(name = "rssi")
    public Integer rssi;

    @Nullable
    @ColumnInfo(name = "sinr")
    public Integer sinr;

    @Nullable
    @ColumnInfo(name = "cqi")
    public Integer cqi;

    @Nullable
    @ColumnInfo(name = "timing_advance")
    public Integer timingAdvance;

    @Nullable
    @ColumnInfo(name = "signal_level")
    public Integer signalLevel;

    @ColumnInfo(name = "is_registered", defaultValue = "0")
    public boolean isRegistered;

    @Nullable
    @ColumnInfo(name = "operator_name")
    public String operatorName;

    @Nullable
    @ColumnInfo(name = "session_id")
    public Long sessionId;

    @Nullable
    @ColumnInfo(name = "speed_mps")
    public Float speedMps;
}
