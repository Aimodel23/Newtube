package org.schabi.newpipe.database.history.dao;


import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import org.schabi.newpipe.database.history.model.WatchHistoryEntry;

import java.util.List;

import io.reactivex.Flowable;

@Dao
public interface WatchHistoryDAO extends HistoryDAO<WatchHistoryEntry> {
    @Query("DELETE FROM " + WatchHistoryEntry.TABLE_NAME )
    @Override
    void clearHistory();

    @Query("SELECT * FROM " + WatchHistoryEntry.TABLE_NAME)
    @Override
    Flowable<List<WatchHistoryEntry>> findAll();

    @Query("SELECT * FROM " + WatchHistoryEntry.TABLE_NAME + " WHERE " + WatchHistoryEntry.SERVICE_ID + " = :serviceId")
    @Override
    Flowable<List<WatchHistoryEntry>> listByService(int serviceId);
}
