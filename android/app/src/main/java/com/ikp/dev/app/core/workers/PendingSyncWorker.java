package com.ikp.dev.app.core.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.ikp.dev.app.core.database.IkpDatabase;
import com.ikp.dev.app.data.local.entity.SyncEntities;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.List;

/**
 * Drains pending synchronisation operations. IDEMPOTENT by design:
 * - ops are keyed (opKey) and enqueued with IGNORE-on-conflict;
 * - the handler registry dispatches by opType; unknown types fail fast
 *   without touching the network;
 * - results are written to sync_log; DONE rows older than 7 days are pruned.
 *
 * M3 foundation: no server-mutating op types exist yet (backend publishes no
 * self-service mutation endpoints) — the registry is empty and the worker
 * exits successfully with a logged no-op. Handlers register in M7.
 */
public final class PendingSyncWorker extends Worker {

    private static final long PRUNE_AFTER_MS = 7L * 24 * 60 * 60 * 1000;

    public PendingSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        IkpDatabase db = ServiceLocator.database();
        if (db == null) {
            return Result.success();
        }
        List<SyncEntities.PendingSyncOpEntity> batch = db.syncDao().nextBatch(SyncScheduler.BATCH_SIZE);
        long now = System.currentTimeMillis();
        for (SyncEntities.PendingSyncOpEntity op : batch) {
            int attempts = op.attempts + 1;
            if (attempts >= SyncScheduler.maxAttempts()) {
                db.syncDao().updateState(op.opId, "FAILED", attempts, "max attempts reached", now);
                log(db, op.opId, "FAILED", "max attempts reached");
                continue;
            }
            try {
                boolean handled = handle(db, op, attempts, now);
                if (handled) {
                    db.syncDao().updateState(op.opId, "DONE", attempts, null, now);
                    log(db, op.opId, "DONE", null);
                } else {
                    // No handler registered for this op type yet (M7).
                    db.syncDao().updateState(op.opId, "FAILED", attempts, "no handler for " + op.opType, now);
                    log(db, op.opId, "NO_HANDLER", op.opType);
                }
            } catch (Exception e) {
                db.syncDao().updateState(op.opId, "PENDING", attempts,
                        e.getClass().getSimpleName(), now);
                log(db, op.opId, "RETRY", e.getClass().getSimpleName());
                return Result.retry();
            }
        }
        db.syncDao().pruneDoneOlderThan(now - PRUNE_AFTER_MS);
        return Result.success();
    }

    /** Handler registry — returns false when no handler exists. */
    private static boolean handle(IkpDatabase db, SyncEntities.PendingSyncOpEntity op,
                                  int attempts, long now) {
        // M3: registry intentionally empty (documented). M7 registers
        // progress/bookmark/note handlers here with idempotent semantics.
        return false;
    }

    private static void log(IkpDatabase db, long opId, String action, String detail) {
        SyncEntities.SyncLogEntity entry = new SyncEntities.SyncLogEntity();
        entry.opId = opId;
        entry.action = action;
        entry.detail = detail;
        entry.createdAtEpochMs = System.currentTimeMillis();
        db.syncDao().log(entry);
    }
}
