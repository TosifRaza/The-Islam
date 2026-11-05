# M3 WorkManager Foundation Report

Milestone: M3 · 2026-11-02 · Scope: foundation only (full sync = M7)

## 1. Architecture

- `SyncScheduler` (pure decisions + enqueue):
  - constraints `NetworkType.CONNECTED`; `BackoffPolicy.EXPONENTIAL` 30 s;
    unique work name `ikp-pending-sync` with `ExistingWorkPolicy.KEEP`
    (no duplicate drains);
  - pure, JVM-tested decisions: `shouldEnqueue`, `backoffDelayMs`
    (30 s→…→capped 15 min), `maxAttempts=5`, batch size 20.
- `PendingSyncWorker`:
  - drains `pending_sync_ops` (PENDING/FAILED) oldest-first;
  - **idempotent by design**: ops are deduplicated by `opKey`
    (INSERT IGNORE), attempts counted, failures recorded with `lastError`;
  - every transition is appended to `sync_log`; DONE rows older than 7 days
    are pruned;
  - handler registry dispatches by `opType` — **empty at M3** (the backend
    publishes no self-service mutation endpoints, so the queue is empty by
    design; handlers register in M7 with idempotent semantics).

## 2. Failure states

| Condition | Behaviour |
|---|---|
| No network | worker not started (constraint); enqueued op waits |
| Transient exception | op returned to PENDING with incremented attempts → `Result.retry()` (exponential backoff) |
| Unknown opType | op marked FAILED (`no handler`), logged — never silently dropped |
| Attempts ≥ 5 | op FAILED permanently, logged for diagnostics |

No automatic conflict overwrites: the design appends/merges nothing server-side
at M3; conflict policy is an M7 decision.

## 3. Verification

- Unit tests (executed): enqueue decision, exponential backoff curve + cap,
  finite max attempts (`CoreLogicTest`).
- DAO idempotency (instrumentation, compiled; executes on CI devices):
  duplicate opKey insert ignored; state transitions; prune semantics
  (`RoomDaoTest.pendingSyncOpsAreIdempotentByKey`, `syncLogRecordsAndPrunes`).
- WorkManager default initialisation is used; the worker resolves its
  dependencies from the ServiceLocator at execution time.
