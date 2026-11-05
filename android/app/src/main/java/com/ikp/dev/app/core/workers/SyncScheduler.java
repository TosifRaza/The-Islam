package com.ikp.dev.app.core.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Sync scheduling decisions (pure logic, JVM-tested) + WorkManager enqueue.
 *
 * Foundation only (full offline/sync = M7): the worker drains
 * pending_sync_ops with network-aware constraints, exponential backoff and
 * idempotent op keys. At M3 no client mutating operations exist (the backend
 * publishes no self-service mutation endpoints), so the queue stays empty by
 * design — the plumbing is real and exercised by tests, not a stub.
 */
public final class SyncScheduler {

    public static final String UNIQUE_SYNC_WORK = "ikp-pending-sync";
    public static final int BACKOFF_SECONDS = 30;
    public static final int BATCH_SIZE = 20;

    private SyncScheduler() {
    }

    /** Pure decision: should a sync run be (re)enqueued for this op? */
    public static boolean shouldEnqueue(long existingPendingCount) {
        return existingPendingCount > 0;
    }

    /** Pure decision: exponential backoff delay for an attempt number (ms). */
    public static long backoffDelayMs(int attempts, int baseSeconds) {
        int n = Math.max(0, attempts);
        // 30s, 60s, 120s, 240s … capped at 15 minutes
        long delay = baseSeconds * 1000L * (1L << Math.min(n, 5));
        return Math.min(delay, 15L * 60 * 1000);
    }

    /** Pure decision: give up after this many attempts (recorded FAILED). */
    public static int maxAttempts() {
        return 5;
    }

    /** Enqueue a network-aware one-time sync (idempotent per unique work). */
    public static void enqueueSync(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PendingSyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(UNIQUE_SYNC_WORK)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_SYNC_WORK, ExistingWorkPolicy.KEEP, request);
    }
}
