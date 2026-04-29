package com.celltowerid.android.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "anomalies",
    indices = {
        @Index(value = {"timestamp"}),
        @Index(value = {"anomaly_type"})
    }
)
public class AnomalyEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @Nullable
    @ColumnInfo(name = "latitude")
    public Double latitude;

    @Nullable
    @ColumnInfo(name = "longitude")
    public Double longitude;

    @NonNull
    @ColumnInfo(name = "anomaly_type")
    public String anomalyType = "";

    @NonNull
    @ColumnInfo(name = "severity")
    public String severity = "";

    @Nullable
    @ColumnInfo(name = "description")
    public String description;

    @Nullable
    @ColumnInfo(name = "cell_radio")
    public String cellRadio;

    @Nullable
    @ColumnInfo(name = "cell_mcc")
    public Integer cellMcc;

    @Nullable
    @ColumnInfo(name = "cell_mnc")
    public Integer cellMnc;

    @Nullable
    @ColumnInfo(name = "cell_tac_lac")
    public Integer cellTacLac;

    @Nullable
    @ColumnInfo(name = "cell_cid")
    public Long cellCid;

    @Nullable
    @ColumnInfo(name = "cell_pci")
    public Integer cellPci;

    @Nullable
    @ColumnInfo(name = "signal_strength")
    public Integer signalStrength;

    @ColumnInfo(name = "dismissed", defaultValue = "0")
    public boolean dismissed;

    @Nullable
    @ColumnInfo(name = "session_id")
    public Long sessionId;

    @ColumnInfo(name = "is_registered", defaultValue = "0")
    public boolean isRegistered;

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

    @Nullable
    @ColumnInfo(name = "earfcn_arfcn")
    public Integer earfcnArfcn;

    @Nullable
    @ColumnInfo(name = "band")
    public Integer band;

    @Nullable
    @ColumnInfo(name = "bandwidth")
    public Integer bandwidth;

    @Nullable
    @ColumnInfo(name = "operator_name")
    public String operatorName;

    @Nullable
    @ColumnInfo(name = "gps_accuracy")
    public Float gpsAccuracy;
}
