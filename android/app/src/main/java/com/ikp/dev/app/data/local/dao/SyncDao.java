package com.ikp.dev.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.ikp.dev.app.data.local.entity.SyncEntities.PendingSyncOpEntity;
import com.ikp.dev.app.data.local.entity.SyncEntities.SyncLogEntity;

import java.util.List;

@Dao
public interface SyncDao {

    /** Idempotent enqueue: ignore when the same opKey is still pending. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long enqueue(PendingSyncOpEntity op);

    @Query("SELECT * FROM pending_sync_ops WHERE state IN ('PENDING','FAILED') ORDER BY opId ASC LIMIT :batch")
    List<PendingSyncOpEntity> nextBatch(int batch);

    @Query("SELECT COUNT(*) FROM pending_sync_ops WHERE opKey = :opKey AND state IN ('PENDING','RUNNING','FAILED')")
    int countPendingByKey(String opKey);

    @Query("UPDATE pending_sync_ops SET state = :state, attempts = :attempts, lastError = :lastError, updatedAtEpochMs = :now WHERE opId = :opId")
    void updateState(long opId, String state, int attempts, String lastError, long now);

    @Insert
    void log(SyncLogEntity entry);

    @Query("DELETE FROM pending_sync_ops WHERE state = 'DONE' AND updatedAtEpochMs < :olderThan")
    void pruneDoneOlderThan(long olderThan);
}
