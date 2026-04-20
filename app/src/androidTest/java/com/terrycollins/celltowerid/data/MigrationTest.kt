package com.terrycollins.celltowerid.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Locks in the Room schema spine. Any future schema change must bump
 * [AppDatabase] version and add a matching migration to
 * [AppDatabase.MIGRATIONS]; this test will fail otherwise and catch
 * data-loss regressions at build time.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun given_v1_database_when_opened_with_migration_chain_then_schema_validates() {
        // Given — create a v1 database with a sample row in every table
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO sessions (id, start_time, end_time) VALUES (1, 0, 0)"
            )
            execSQL(
                "INSERT INTO measurements " +
                    "(id, timestamp, latitude, longitude, radio, is_registered, session_id) " +
                    "VALUES (1, 0, 0.0, 0.0, 'LTE', 1, 1)"
            )
            execSQL(
                "INSERT INTO tower_cache (id, radio, mcc, mnc, tac_lac, cid) " +
                    "VALUES (1, 'LTE', 272, 5, 41002, 1205536)"
            )
            execSQL(
                "INSERT INTO anomalies (id, timestamp, anomaly_type, severity) " +
                    "VALUES (1, 0, 'SIGNAL_ANOMALY', 'MEDIUM')"
            )
            close()
        }

        // When — re-open through the real migration chain at the current version
        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(*AppDatabase.MIGRATIONS).build()

        // Then — the rows are still present
        db.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM measurements"
        ).use {
            it.moveToFirst()
            assertThat(it.getInt(0)).isEqualTo(1)
        }
        db.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM anomalies"
        ).use {
            it.moveToFirst()
            assertThat(it.getInt(0)).isEqualTo(1)
        }
        db.close()
    }

    @Test
    fun given_v2_tower_cache_rows_when_migrating_to_v3_then_pci_column_exists_and_rows_survive() {
        // Given — v2 schema with seeded tower_cache rows (no pci column)
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO tower_cache (id, radio, mcc, mnc, tac_lac, cid, latitude, longitude, source) " +
                    "VALUES (1, 'LTE', 272, 5, 41002, 1205536, 53.34, -6.42, 'opencellid')"
            )
            close()
        }

        // When — run the migration chain
        val migrated = helper.runMigrationsAndValidate(
            dbName, 3, true, *AppDatabase.MIGRATIONS
        )

        // Then — the row survives and the new pci column is present and null
        migrated.query("SELECT id, pci FROM tower_cache WHERE id = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1)
            assertThat(c.isNull(1)).isTrue()
        }
        migrated.close()
    }
}
