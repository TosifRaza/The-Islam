package com.ikp.dev.app.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entities for the offline foundation (prompt §17). Server data is the
 * authority; everything here is a CACHE with a fetchedAt timestamp and a
 * caller-side TTL. Database versioning & migration policy: see
 * {@link com.ikp.dev.app.core.database.IkpDatabase}.
 */
public final class CacheEntities {

    private CacheEntities() {
    }

    /** Cached user profile from /auth/me (never contains credentials). */
    @Entity(tableName = "user_profile")
    public static final class UserProfileEntity {
        @PrimaryKey
        @androidx.annotation.NonNull
        public String userId;
        public String email;
        public String displayName;
        public String language;
        public String status;
        public String rolesJson;
        public String permissionsJson;
        public Long createdAtEpochMs;
        public Long cachedAtEpochMs;
    }

    /** Cached library categories (slug IS the stable public id, CAT-*). */
    @Entity(tableName = "subjects")
    public static final class SubjectEntity {
        @PrimaryKey
        @androidx.annotation.NonNull
        public String slug;
        public String name;
        public String nameAr;
        public String description;
        public Integer position;
        public Long cachedAtEpochMs;
    }

    /** Cached published books (latest browse result; queryKey groups lists). */
    @Entity(tableName = "books")
    public static final class BookEntity {
        @PrimaryKey
        @androidx.annotation.NonNull
        public String bookId;
        public String queryKey;
        public String title;
        public String subtitle;
        public String language;
        public String subjectSlug;
        public String subjectName;
        public String authorsJson;
        public String formatsJson;
        public boolean downloadAllowed;
        public Long cachedAtEpochMs;
    }
}
