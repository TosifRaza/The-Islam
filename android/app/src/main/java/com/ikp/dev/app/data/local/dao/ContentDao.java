package com.ikp.dev.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.ikp.dev.app.data.local.entity.ContentCacheEntities.DailyContentEntity;
import com.ikp.dev.app.data.local.entity.ContentCacheEntities.DictionaryHistoryEntity;
import com.ikp.dev.app.data.local.entity.ContentCacheEntities.SurahCacheEntity;
import com.ikp.dev.app.data.local.entity.ContentCacheEntities.TasbihCounterEntity;
import com.ikp.dev.app.data.local.entity.ContentCacheEntities.TasbihDailyEntity;

import java.util.List;

/** M4 content cache + offline utility data access (Room v2). */
@Dao
public interface ContentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void replaceSurahs(List<SurahCacheEntity> surahs);

    @Query("SELECT * FROM quran_surahs_cache ORDER BY surahId")
    List<SurahCacheEntity> surahs();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putDaily(DailyContentEntity entity);

    @Query("SELECT * FROM daily_content_cache WHERE date = :date")
    DailyContentEntity daily(String date);

    @Query("DELETE FROM daily_content_cache WHERE cachedAtEpochMs < :beforeEpochMs")
    void trimDaily(long beforeEpochMs);

    @Insert
    long addDictionaryHistory(DictionaryHistoryEntity entity);

    @Query("SELECT DISTINCT term FROM dictionary_history ORDER BY searchedAtEpochMs DESC LIMIT :limit")
    List<String> recentDictionaryTerms(int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsertCounter(TasbihCounterEntity counter);

    @Query("SELECT * FROM tasbih_counters ORDER BY updatedAtEpochMs DESC")
    List<TasbihCounterEntity> counters();

    @Query("SELECT * FROM tasbih_counters WHERE counterId = :id")
    TasbihCounterEntity counter(long id);

    @Query("UPDATE tasbih_counters SET count = :count, updatedAtEpochMs = :now WHERE counterId = :id")
    void setCount(long id, int count, long now);

    @Query("DELETE FROM tasbih_counters WHERE counterId = :id")
    void deleteCounter(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDaily(TasbihDailyEntity day);

    @Query("SELECT * FROM tasbih_daily_history WHERE day = :day")
    TasbihDailyEntity dailyTotal(String day);

    @Query("SELECT * FROM tasbih_daily_history ORDER BY day DESC LIMIT :limit")
    List<TasbihDailyEntity> dailyHistory(int limit);
}
