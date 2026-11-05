package com.ikp.dev.app.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** WorkManager sync foundation (prompt §17): pending ops + sync log. */
public final class SyncEntities {

    private SyncEntities() {
    }

    /** Pending synchronisation operation. Idempotency: opKey dedupes retries. */
    @Entity(tableName = "pending_sync_ops")
    public static final class PendingSyncOpEntity {
        @PrimaryKey(autoGenerate = true)
        public long opId;
        @androidx.annotation.NonNull
        public String opType;
        @androidx.annotation.NonNull
        public String opKey;
        public String payloadJson;
        public String state;          // PENDING | RUNNING | DONE | FAILED
        public int attempts;
        public String lastError;
        public Long createdAtEpochMs;
        public Long updatedAtEpochMs;
    }

    /** Sync audit log (local only). */
    @Entity(tableName = "sync_log")
    public static final class SyncLogEntity {
        @PrimaryKey(autoGenerate = true)
        public long logId;
        public long opId;
        public String action;
        public String detail;
        public Long createdAtEpochMs;
    }
}
