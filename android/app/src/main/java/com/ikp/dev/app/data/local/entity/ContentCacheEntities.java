package com.ikp.dev.app.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** M4 cache + local-only tables (Room v2, forward-only migration 1→2). */
public final class ContentCacheEntities {

    private ContentCacheEntities() {
    }

    @Entity(tableName = "quran_surahs_cache")
    public static final class SurahCacheEntity {
        @PrimaryKey
        public int surahId;
        @androidx.annotation.NonNull
        public String nameAr;
        public String nameTransliterated;
        public String nameEn;
        public String revelationPlace;
        public int ayahCount;
        public Long cachedAtEpochMs;
    }

    @Entity(tableName = "daily_content_cache")
    public static final class DailyContentEntity {
        @PrimaryKey
        @androidx.annotation.NonNull
        public String date;
        public String payloadJson;
        public Long cachedAtEpochMs;
    }

    @Entity(tableName = "dictionary_history")
    public static final class DictionaryHistoryEntity {
        @PrimaryKey(autoGenerate = true)
        public long entryId;
        @androidx.annotation.NonNull
        public String term;
        public Long searchedAtEpochMs;
    }

    @Entity(tableName = "tasbih_counters")
    public static final class TasbihCounterEntity {
        @PrimaryKey(autoGenerate = true)
        public long counterId;
        @androidx.annotation.NonNull
        public String label;
        public int count;
        public Integer target;
        public boolean vibrate;
        public boolean sound;
        public Long updatedAtEpochMs;
    }

    @Entity(tableName = "tasbih_daily_history")
    public static final class TasbihDailyEntity {
        @PrimaryKey
        @androidx.annotation.NonNull
        public String day;
        public int total;
        public Long updatedAtEpochMs;
    }
}
