package com.ikp.dev.app.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** User-owned local data foundations (prompt §17) — M3 scope: schema + DAOs. */
public final class UserDataEntities {

    private UserDataEntities() {
    }

    /** Key-value user preferences (theme, locale choice, reader defaults). */
    @Entity(tableName = "user_preferences")
    public static final class UserPreferenceEntity {
        @PrimaryKey
        @androidx.annotation.NonNull
        public String key;
        public String value;
        public Long updatedAtEpochMs;
    }

    /** Download metadata for the future download manager (M4+). */
    @Entity(tableName = "download_metadata")
    public static final class DownloadMetadataEntity {
        @PrimaryKey
        @androidx.annotation.NonNull
        public String mediaId;
        public String bookId;
        public String title;
        public String format;
        public Long sizeBytes;
        public String sha256;
        public String status;
        public Long urlExpiresAtEpochMs;
        public Long downloadedAtEpochMs;
        public String filePath;
    }

    /** Reading progress (per content item). */
    @Entity(tableName = "reading_progress")
    public static final class ReadingProgressEntity {
        @PrimaryKey
        @androidx.annotation.NonNull
        public String contentId;
        public String contentType;
        public Integer positionPercent;
        public Long updatedAtEpochMs;
    }

    /** Bookmark metadata. */
    @Entity(tableName = "bookmarks")
    public static final class BookmarkEntity {
        @PrimaryKey(autoGenerate = true)
        public long bookmarkId;
        @androidx.annotation.NonNull
        public String contentId;
        public String contentType;
        public String title;
        public String snippet;
        public Long createdAtEpochMs;
    }

    /** Note metadata (full note text storage arrives with reader modules). */
    @Entity(tableName = "notes")
    public static final class NoteEntity {
        @PrimaryKey(autoGenerate = true)
        public long noteId;
        @androidx.annotation.NonNull
        public String contentId;
        public String contentType;
        public String body;
        public Long createdAtEpochMs;
        public Long updatedAtEpochMs;
    }

    /** Recent history (library/home "recently viewed"). */
    @Entity(tableName = "recent_history")
    public static final class RecentHistoryEntity {
        @PrimaryKey(autoGenerate = true)
        public long entryId;
        @androidx.annotation.NonNull
        public String contentId;
        public String contentType;
        public String title;
        public Long viewedAtEpochMs;
    }
}
