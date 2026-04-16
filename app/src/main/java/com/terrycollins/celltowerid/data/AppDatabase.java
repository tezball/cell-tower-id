package com.terrycollins.celltowerid.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.terrycollins.celltowerid.data.dao.AnomalyDao;
import com.terrycollins.celltowerid.data.dao.MeasurementDao;
import com.terrycollins.celltowerid.data.dao.SessionDao;
import com.terrycollins.celltowerid.data.dao.TowerCacheDao;
import com.terrycollins.celltowerid.data.entity.AnomalyEntity;
import com.terrycollins.celltowerid.data.entity.MeasurementEntity;
import com.terrycollins.celltowerid.data.entity.SessionEntity;
import com.terrycollins.celltowerid.data.entity.TowerCacheEntity;

@Database(
    entities = {
        MeasurementEntity.class,
        SessionEntity.class,
        TowerCacheEntity.class,
        AnomalyEntity.class
    },
    version = 2,
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

    public static final Migration[] MIGRATIONS = new Migration[] {
        MIGRATION_1_2
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
