# M3 Database & Room Summary

Milestone: M3 · 2026-11-02

## 1. Scope

Local database FOUNDATION only (prompt §17): schema + DAOs + repository
abstraction + cache policy. Full offline/sync workflows are M7.

## 2. Database

- **Room v1** (`ikp.db`), `exportSchema=false` (dev foundation), 11 tables:
  - caches: `user_profile`, `subjects`, `books`
  - user data: `user_preferences`, `download_metadata`, `reading_progress`,
    `bookmarks`, `notes`, `recent_history`
  - sync: `pending_sync_ops`, `sync_log`
- Entities grouped in `data/local/entity/{CacheEntities,UserDataEntities,SyncEntities}`;
  DAOs in `data/local/dao/{UserProfileDao,LibraryDao,UserDataDao,SyncDao}`.

## 3. Versioning & migration policy (approved, mirrors backend Flyway rule)

- **Forward-only**: schema changes bump `IkpDatabase.DB_VERSION` and add a
  numbered `Migration` to `IkpDatabase.MIGRATIONS` (v1 = initial, array empty).
- `fallbackToDestructiveMigration()` is **never** used — data loss cannot be
  silently accepted.
- Migration tests will extend `RoomDaoTest` (androidTest) as versions grow.

## 4. Cache vs authority separation

- Server is authoritative: profile/subject/book rows carry `cachedAtEpochMs`
  and expire by repository TTLs (subjects 24 h, books 1 h; profile refreshed
  on every successful `/auth/me`). Stale caches are revalidated; failures
  degrade to cached display with the offline banner.
- User-owned rows (progress/bookmarks/notes/history) are local-first and will
  sync via `pending_sync_ops` in M7 — no risky automatic conflict overwrites.
- No credentials in the database: tokens live exclusively in the
  Keystore-backed `EncryptedTokenStore`; the Room profile cache stores only
  display data (email/status/roles JSON).

## 5. Verified behaviour

- Compile-time schema/DAO verification: `assembleDebug` (Room annotation
  processor) — **BUILD SUCCESSFUL**.
- DAO behaviour (instrumentation suite, compiled; executes on CI devices):
  subjects ordered by `position` (1→15, no position 6 in public data), slug
  primary-key upsert stability (rename, never duplicate), preferences
  upsert-by-key, **pending-sync idempotency** (duplicate opKey → IGNORE,
  `-1` rowid), state updates + 7-day DONE pruning.
- JVM-side invariants (executed): category-position mapping/order, envelope
  parsing, TTL-driven freshness helpers (`countFreshSubjects` query exercised
  in DAO tests).
