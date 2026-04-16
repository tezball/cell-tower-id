package com.terrycollins.celltowerid.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.terrycollins.celltowerid.data.entity.SessionEntity;

import java.util.List;

@Dao
public interface SessionDao {

    @Insert
    long insert(SessionEntity session);

    @Update
    void update(SessionEntity session);

    @Query("SELECT * FROM sessions WHERE id = :id")
    SessionEntity getById(long id);

    @Query("SELECT * FROM sessions ORDER BY start_time DESC")
    List<SessionEntity> getAll();

    @Query("SELECT * FROM sessions WHERE end_time IS NULL LIMIT 1")
    SessionEntity getActiveSession();

    @Delete
    void delete(SessionEntity session);
}
