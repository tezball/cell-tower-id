package com.celltowerid.android.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.celltowerid.android.data.entity.MeasurementEntity;

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

    /**
     * Returns the strongest reading per unique cell within the bounding box,
     * ranked by COALESCE(rsrp, rssi) DESC (less-negative dBm = stronger),
     * timestamp DESC as tiebreaker. Within a cell all readings share the same
     * radio, so rsrp/rssi are never mixed. Cells without identifying fields
     * (cid/mcc/mnc/tac_lac null) are excluded.
     */
    @Query(
        "SELECT * FROM measurements m1 " +
        "WHERE m1.latitude BETWEEN :minLat AND :maxLat " +
        "  AND m1.longitude BETWEEN :minLon AND :maxLon " +
        "  AND m1.cid IS NOT NULL AND m1.mcc IS NOT NULL " +
        "  AND m1.mnc IS NOT NULL AND m1.tac_lac IS NOT NULL " +
        "  AND m1.id = (" +
        "    SELECT m2.id FROM measurements m2 " +
        "    WHERE m2.radio = m1.radio AND m2.mcc IS m1.mcc AND m2.mnc IS m1.mnc " +
        "      AND m2.tac_lac IS m1.tac_lac AND m2.cid = m1.cid " +
        "      AND m2.latitude BETWEEN :minLat AND :maxLat " +
        "      AND m2.longitude BETWEEN :minLon AND :maxLon " +
        "    ORDER BY COALESCE(m2.rsrp, m2.rssi) DESC, m2.timestamp DESC " +
        "    LIMIT 1" +
        "  )"
    )
    List<MeasurementEntity> getBestMeasurementsInArea(double minLat, double maxLat, double minLon, double maxLon);

    @Query(
        "SELECT COUNT(*) FROM measurements " +
        "WHERE latitude BETWEEN :minLat AND :maxLat " +
        "  AND longitude BETWEEN :minLon AND :maxLon " +
        "  AND timestamp >= :sinceMs " +
        "  AND timestamp < :beforeMs"
    )
    int countMeasurementsInArea(
        double minLat, double maxLat, double minLon, double maxLon,
        long sinceMs, long beforeMs
    );

    /**
     * Returns the most recent timestamp at which this exact cell was observed
     * inside the bounding box, within [sinceMs, beforeMs). Returns null if
     * there is no such sighting. The {@code beforeMs} bound exists so the
     * caller can exclude the current scan's row, which is already in the
     * database by the time the anomaly detector runs.
     */
    @Query(
        "SELECT MAX(timestamp) FROM measurements " +
        "WHERE radio = :radio AND mcc = :mcc AND mnc = :mnc " +
        "  AND tac_lac = :tacLac AND cid = :cid " +
        "  AND latitude BETWEEN :minLat AND :maxLat " +
        "  AND longitude BETWEEN :minLon AND :maxLon " +
        "  AND timestamp >= :sinceMs " +
        "  AND timestamp < :beforeMs"
    )
    Long findMostRecentTowerSighting(
        String radio, int mcc, int mnc, int tacLac, long cid,
        double minLat, double maxLat, double minLon, double maxLon,
        long sinceMs, long beforeMs
    );

    /**
     * Counts sightings of OTHER cells sharing the same eNB (or gNB) ID as
     * {@code excludeCid}, inside the bounding box and time window. Used by
     * POPUP_TOWER to suppress alerts when the just-observed cell is a sibling
     * sector of an established eNB — real macro sites add/lose sectors due to
     * carrier aggregation and capacity, which is not the IMSI-catcher signal
     * the detector is meant to catch.
     *
     * Caller is responsible for restricting this to LTE/NR — for UMTS/GSM the
     * CID has no eNB-encoded structure and {@code (cid >> 8)} is meaningless.
     */
    @Query(
        "SELECT COUNT(*) FROM measurements " +
        "WHERE radio = :radio AND mcc = :mcc AND mnc = :mnc " +
        "  AND tac_lac = :tacLac " +
        "  AND (cid >> 8) = :enbId AND cid != :excludeCid " +
        "  AND latitude BETWEEN :minLat AND :maxLat " +
        "  AND longitude BETWEEN :minLon AND :maxLon " +
        "  AND timestamp >= :sinceMs " +
        "  AND timestamp < :beforeMs"
    )
    int countSiblingSectorsInArea(
        String radio, int mcc, int mnc, int tacLac, long enbId, long excludeCid,
        double minLat, double maxLat, double minLon, double maxLon,
        long sinceMs, long beforeMs
    );

    /**
     * Returns the timestamp of the earliest measurement ever recorded inside
     * the bounding box, or null if the area has never been visited. Used by
     * POPUP_TOWER to gauge how mature the area's baseline is — a fresh
     * dataset (less than a few days of coverage) produces many "first time"
     * sightings that aren't really popups.
     */
    @Query(
        "SELECT MIN(timestamp) FROM measurements " +
        "WHERE latitude BETWEEN :minLat AND :maxLat " +
        "  AND longitude BETWEEN :minLon AND :maxLon"
    )
    Long findFirstMeasurementTimeInArea(
        double minLat, double maxLat, double minLon, double maxLon
    );

    /**
     * Counts distinct cell identities — distinct (radio, mcc, mnc, tac_lac, cid)
     * tuples — observed inside the bounding box and time window. Used by
     * PCI_COLLISION as a HetNet "density index": when the user has logged many
     * unique cells in a small area, the surrounding network is a dense small-cell
     * deployment (microcells/picocells) where legitimate PCI reuse at sub-2 km
     * distances is mathematically necessary given the finite 504-PCI pool, and
     * the standard 2 km collision radius generates false positives. The detector
     * compresses its evaluation radius to a tighter dense-area value when this
     * count crosses {@code PCI_DENSITY_THRESHOLD_CIDS}.
     */
    @Query(
        "SELECT COUNT(*) FROM (" +
        "  SELECT DISTINCT radio, mcc, mnc, tac_lac, cid FROM measurements " +
        "  WHERE latitude BETWEEN :minLat AND :maxLat " +
        "    AND longitude BETWEEN :minLon AND :maxLon " +
        "    AND mcc IS NOT NULL AND mnc IS NOT NULL " +
        "    AND tac_lac IS NOT NULL AND cid IS NOT NULL " +
        "    AND timestamp >= :sinceMs AND timestamp < :beforeMs" +
        ")"
    )
    int countDistinctCidsInArea(
        double minLat, double maxLat, double minLon, double maxLon,
        long sinceMs, long beforeMs
    );

    /**
     * Counts how many distinct CIDs have been observed broadcasting the given
     * PCI within the bounding box and time window, scoped to a specific
     * (radio, mcc, mnc, tacLac). Used by PCI_COLLISION: real LTE/NR networks
     * coordinate PCI assignments so adjacent cells in the same TAC don't share
     * one — a count >= 2 within a single TAC is the fingerprint of a fake cell
     * that picked an arbitrary PCI without knowing the local allocation. Cross-
     * TAC PCI reuse is a legitimate operator pattern (the same PCI value can be
     * planned into physically separated cells in different tracking areas) and
     * is not a collision.
     */
    @Query(
        "SELECT COUNT(DISTINCT cid) FROM measurements " +
        "WHERE pci_psc = :pci AND radio = :radio " +
        "  AND mcc = :mcc AND mnc = :mnc AND tac_lac = :tacLac " +
        "  AND latitude BETWEEN :minLat AND :maxLat " +
        "  AND longitude BETWEEN :minLon AND :maxLon " +
        "  AND timestamp >= :sinceMs AND timestamp < :beforeMs"
    )
    int countDistinctCidsForPci(
        String radio, int mcc, int mnc, int tacLac, int pci,
        double minLat, double maxLat, double minLon, double maxLon,
        long sinceMs, long beforeMs
    );

    /**
     * Returns the CID of the most recent prior measurement that observed the
     * given PCI inside the bounding box and time window, scoped to a specific
     * (radio, mcc, mnc, tacLac), or null if no prior sighting exists in this
     * TAC. Used by PCI_COLLISION's "reuse" branch: if the most recent CID for
     * this PCI in this TAC differs from the current one, the PCI has been
     * repurposed onto a new cell identity within the same TAC — a fake cell
     * hijacking a familiar PCI value. Cross-TAC reuse is treated as legitimate
     * and is excluded by the tacLac filter.
     */
    @Query(
        "SELECT cid FROM measurements " +
        "WHERE pci_psc = :pci AND radio = :radio " +
        "  AND mcc = :mcc AND mnc = :mnc AND tac_lac = :tacLac AND cid IS NOT NULL " +
        "  AND latitude BETWEEN :minLat AND :maxLat " +
        "  AND longitude BETWEEN :minLon AND :maxLon " +
        "  AND timestamp >= :sinceMs AND timestamp < :beforeMs " +
        "ORDER BY timestamp DESC LIMIT 1"
    )
    Long findMostRecentCidForPci(
        String radio, int mcc, int mnc, int tacLac, int pci,
        double minLat, double maxLat, double minLon, double maxLon,
        long sinceMs, long beforeMs
    );

    /**
     * Counts how many distinct eNB (or gNB) IDs — i.e., distinct values of
     * {@code (cid >> 8)} — have been observed broadcasting the given PCI within
     * the bounding box and time window, scoped to a specific (radio, mcc, mnc,
     * tacLac). Used by PCI_COLLISION to suppress false positives when a single
     * physical site reuses one PCI across multiple of its own sectors (carrier
     * aggregation, overlay layers) — a legitimate operator pattern that
     * {@code countDistinctCidsForPci} alone cannot distinguish from a fake cell.
     *
     * Caller is responsible for restricting this to LTE/NR — for UMTS/GSM the
     * CID has no eNB-encoded structure and {@code (cid >> 8)} is meaningless.
     */
    @Query(
        "SELECT COUNT(DISTINCT cid >> 8) FROM measurements " +
        "WHERE pci_psc = :pci AND radio = :radio " +
        "  AND mcc = :mcc AND mnc = :mnc AND tac_lac = :tacLac " +
        "  AND latitude BETWEEN :minLat AND :maxLat " +
        "  AND longitude BETWEEN :minLon AND :maxLon " +
        "  AND timestamp >= :sinceMs AND timestamp < :beforeMs"
    )
    int countDistinctEnbsForPci(
        String radio, int mcc, int mnc, int tacLac, int pci,
        double minLat, double maxLat, double minLon, double maxLon,
        long sinceMs, long beforeMs
    );

    @Query("SELECT * FROM measurements")
    List<MeasurementEntity> getAll();

    @Query("SELECT COUNT(*) FROM measurements")
    int getCount();

    @Query("DELETE FROM measurements WHERE timestamp < :cutoffMs")
    int deleteOlderThan(long cutoffMs);

    @Delete
    void delete(MeasurementEntity measurement);
}
