package com.celltowerid.android.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.celltowerid.android.data.dao.AnomalyDao;
import com.celltowerid.android.data.dao.MeasurementDao;
import com.celltowerid.android.data.dao.SessionDao;
import com.celltowerid.android.data.dao.TowerCacheDao;
import com.celltowerid.android.data.entity.AnomalyEntity;
import com.celltowerid.android.data.entity.MeasurementEntity;
import com.celltowerid.android.data.entity.SessionEntity;
import com.celltowerid.android.data.entity.TowerCacheEntity;

@Database(
    entities = {
        MeasurementEntity.class,
        SessionEntity.class,
        TowerCacheEntity.class,
        AnomalyEntity.class
    },
    version = 6,
    exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    /**
     * Ordered chain of Room migrations. Items are only ever added here, never
     * removed or reordered. Keep this array in sync with the version numbers
     * exported under app/schemas/.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE measurements ADD COLUMN speed_mps REAL");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE tower_cache ADD COLUMN pci INTEGER");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE tower_cache ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Spatial-lookup index so getTowersInArea stops doing a full
            // table scan every time the map viewport changes.
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_tower_cache_latitude_longitude " +
                    "ON tower_cache(latitude, longitude)"
            );
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Carry the full triggering-measurement context on each anomaly
            // row so the alert -> tower detail screen can show every signal
            // field, not just signal_strength.
            db.execSQL("ALTER TABLE anomalies ADD COLUMN is_registered INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN rsrp INTEGER");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN rsrq INTEGER");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN rssi INTEGER");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN sinr INTEGER");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN cqi INTEGER");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN timing_advance INTEGER");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN signal_level INTEGER");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN earfcn_arfcn INTEGER");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN band INTEGER");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN bandwidth INTEGER");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN operator_name TEXT");
            db.execSQL("ALTER TABLE anomalies ADD COLUMN gps_accuracy REAL");

            // Backfill the lat/long spatial index for fresh-installed v5
            // databases. Devices that came up through MIGRATION_4_5 already
            // have it; the IF NOT EXISTS guard makes this a no-op there.
            // Declared on TowerCacheEntity so Room's schema validator
            // knows to expect it.
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_tower_cache_latitude_longitude " +
                    "ON tower_cache(latitude, longitude)"
            );
        }
    };

    public static final Migration[] MIGRATIONS = new Migration[] {
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6
    };

    public abstract MeasurementDao measurementDao();

    public abstract SessionDao sessionDao();

    public abstract TowerCacheDao towerCacheDao();

    public abstract AnomalyDao anomalyDao();

    @NonNull
    public static AppDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "cellid_database"
                    ).addMigrations(MIGRATIONS).build();
                }
            }
        }
        return INSTANCE;
    }
}
