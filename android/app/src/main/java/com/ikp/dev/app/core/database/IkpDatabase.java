package com.ikp.dev.app.core.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.ikp.dev.app.data.local.dao.ContentDao;
import com.ikp.dev.app.data.local.dao.LibraryDao;
import com.ikp.dev.app.data.local.dao.SyncDao;
import com.ikp.dev.app.data.local.dao.UserDataDao;
import com.ikp.dev.app.data.local.dao.UserProfileDao;
import com.ikp.dev.app.data.local.entity.CacheEntities;
import com.ikp.dev.app.data.local.entity.ContentCacheEntities;
import com.ikp.dev.app.data.local.entity.SyncEntities;
import com.ikp.dev.app.data.local.entity.UserDataEntities;

/**
 * Local Room database (M3 foundation, prompt §17).
 *
 * VERSIONING & MIGRATION POLICY (approved, forward-only — mirrors the backend
 * Flyway rule):
 * - schema changes bump {@link #DB_VERSION} and add a numbered Migration
 *   (MIGRATION_x_y) to {@link #MIGRATIONS}; destructive fallback is DISABLED
 *   so data loss can never be silently accepted;
 * - the database is a CACHE + local-only user data. The server is the
 *   authority: profile/subject/book rows carry cachedAtEpochMs and expire by
 *   TTL at the repository layer; user-owned rows (progress/bookmarks/notes)
 *   are local-first and will sync in M7 via pending_sync_ops;
 * - sensitive cached data (profile email) lives in an encrypted store, not
 *   here; this DB holds no credentials.
 */
@Database(
        entities = {
                CacheEntities.UserProfileEntity.class,
                CacheEntities.SubjectEntity.class,
                CacheEntities.BookEntity.class,
                UserDataEntities.UserPreferenceEntity.class,
                UserDataEntities.DownloadMetadataEntity.class,
                UserDataEntities.ReadingProgressEntity.class,
                UserDataEntities.BookmarkEntity.class,
                UserDataEntities.NoteEntity.class,
                UserDataEntities.RecentHistoryEntity.class,
                SyncEntities.PendingSyncOpEntity.class,
                SyncEntities.SyncLogEntity.class,
                // M4 (Room v2): content caches + offline utilities
                ContentCacheEntities.SurahCacheEntity.class,
                ContentCacheEntities.DailyContentEntity.class,
                ContentCacheEntities.DictionaryHistoryEntity.class,
                ContentCacheEntities.TasbihCounterEntity.class,
                ContentCacheEntities.TasbihDailyEntity.class,
        },
        version = IkpDatabase.DB_VERSION,
        exportSchema = false)
public abstract class IkpDatabase extends RoomDatabase {

    public static final int DB_VERSION = 2;
    public static final String DB_NAME = "ikp.db";

    /**
     * Forward-only migrations (mirrors the backend Flyway rule):
     * v1 = M3 initial schema; v2 = M4 content caches + Tasbih offline data.
     */
    public static final androidx.room.migration.Migration[] MIGRATIONS = {
            new androidx.room.migration.Migration(1, 2) {
                @Override
                public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS quran_surahs_cache ("
                            + "surahId INTEGER NOT NULL PRIMARY KEY, "
                            + "nameAr TEXT NOT NULL, nameTransliterated TEXT, nameEn TEXT, "
                            + "revelationPlace TEXT, ayahCount INTEGER NOT NULL, cachedAtEpochMs INTEGER)");
                    db.execSQL("CREATE TABLE IF NOT EXISTS daily_content_cache ("
                            + "date TEXT NOT NULL PRIMARY KEY, payloadJson TEXT, cachedAtEpochMs INTEGER)");
                    db.execSQL("CREATE TABLE IF NOT EXISTS dictionary_history ("
                            + "entryId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "term TEXT NOT NULL, searchedAtEpochMs INTEGER)");
                    db.execSQL("CREATE TABLE IF NOT EXISTS tasbih_counters ("
                            + "counterId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "label TEXT NOT NULL, count INTEGER NOT NULL, target INTEGER, "
                            + "vibrate INTEGER NOT NULL, sound INTEGER NOT NULL, updatedAtEpochMs INTEGER)");
                    db.execSQL("CREATE TABLE IF NOT EXISTS tasbih_daily_history ("
                            + "day TEXT NOT NULL PRIMARY KEY, total INTEGER NOT NULL, updatedAtEpochMs INTEGER)");
                }
            }
    };

    public abstract UserProfileDao userProfileDao();

    public abstract LibraryDao libraryDao();

    public abstract UserDataDao userDataDao();

    public abstract SyncDao syncDao();

    public abstract ContentDao contentDao();

    public static IkpDatabase build(Context context) {
        androidx.room.migration.Migration[] ms = MIGRATIONS;
        androidx.room.RoomDatabase.Builder<IkpDatabase> builder =
                Room.databaseBuilder(context.getApplicationContext(), IkpDatabase.class, DB_NAME)
                        .addMigrations(ms);
        // Fail loud rather than silently destroying user data.
        return builder.build();
    }
}
