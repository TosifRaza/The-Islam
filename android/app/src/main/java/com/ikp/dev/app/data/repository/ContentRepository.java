package com.ikp.dev.app.data.repository;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.database.IkpDatabase;
import com.ikp.dev.app.core.error.ErrorMapper;
import com.ikp.dev.app.core.network.ApiClient;
import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.local.entity.ContentCacheEntities.DailyContentEntity;
import com.ikp.dev.app.data.local.entity.ContentCacheEntities.SurahCacheEntity;
import com.ikp.dev.app.data.remote.ContentApi;
import com.ikp.dev.app.data.remote.dto.ContentDtos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * M4 knowledge-content repository (Quran, Hadith, Dictionary, Duas, Calendar,
 * Home/Daily, Global Search). Server authoritative; the 114-surah index and
 * the daily payload carry Room caches (TTL) for offline-start.
 */
public final class ContentRepository {

    public static final long SURAH_TTL_MS = 7L * 24 * 60 * 60 * 1000;
    public static final long DAILY_TTL_MS = 6L * 60 * 60 * 1000;

    private final ContentApi api;
    private final IkpDatabase db;
    private final Gson gson = new Gson();
    private final long surahTtlMs;
    private final long dailyTtlMs;

    public ContentRepository(ApiClient client, IkpDatabase db) {
        this(client, db, SURAH_TTL_MS, DAILY_TTL_MS);
    }

    public ContentRepository(ApiClient client, IkpDatabase db, long surahTtlMs, long dailyTtlMs) {
        this.api = client.create(ContentApi.class);
        this.db = db;
        this.surahTtlMs = surahTtlMs;
        this.dailyTtlMs = dailyTtlMs;
    }

    private static <T> Outcome<T> run(Call<ApiResponse<T>> call) {
        try {
            Response<ApiResponse<T>> res = call.execute();
            if (res.isSuccessful() && res.body() != null && res.body().data != null) {
                return Outcome.ok(res.body().data);
            }
            return Outcome.fail(ErrorMapper.fromResponse(res));
        } catch (IOException e) {
            return Outcome.fail(ErrorMapper.fromThrowable(e));
        }
    }

    public Outcome<List<ContentDtos.SurahDto>> surahs(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        List<SurahCacheEntity> cached = db.contentDao().surahs();
        if (!forceRefresh && !cached.isEmpty() && cached.get(0).cachedAtEpochMs != null
                && now - cached.get(0).cachedAtEpochMs < surahTtlMs) {
            List<ContentDtos.SurahDto> rows = new ArrayList<>();
            for (SurahCacheEntity e : cached) rows.add(fromCache(e));
            return Outcome.ok(rows);
        }
        Outcome<List<ContentDtos.SurahDto>> result = run(api.surahs());
        if (result.isSuccess() && result.value != null && !result.value.isEmpty()) {
            List<SurahCacheEntity> fresh = new ArrayList<>();
            for (ContentDtos.SurahDto s : result.value) fresh.add(toCache(s, now));
            db.contentDao().replaceSurahs(fresh);
            return result;
        } else if (!cached.isEmpty()) {
            List<ContentDtos.SurahDto> rows = new ArrayList<>();
            for (SurahCacheEntity e : cached) rows.add(fromCache(e));
            return Outcome.ok(rows);
        } else {
            List<ContentDtos.SurahDto> fallback = com.ikp.dev.app.data.content.IslamicContentProvider.getAllSurahs();
            List<SurahCacheEntity> fresh = new ArrayList<>();
            for (ContentDtos.SurahDto s : fallback) fresh.add(toCache(s, now));
            db.contentDao().replaceSurahs(fresh);
            return Outcome.ok(fallback);
        }
    }

    private static ContentDtos.SurahDto fromCache(SurahCacheEntity e) {
        ContentDtos.SurahDto s = new ContentDtos.SurahDto();
        s.id = e.surahId;
        s.nameAr = e.nameAr;
        s.nameTransliterated = e.nameTransliterated;
        s.nameEn = e.nameEn;
        s.revelationPlace = e.revelationPlace;
        s.ayahCount = e.ayahCount;
        return s;
    }

    private static SurahCacheEntity toCache(ContentDtos.SurahDto s, long now) {
        SurahCacheEntity e = new SurahCacheEntity();
        e.surahId = s.id;
        e.nameAr = s.nameAr == null ? "" : s.nameAr;
        e.nameTransliterated = s.nameTransliterated;
        e.nameEn = s.nameEn;
        e.revelationPlace = s.revelationPlace;
        e.ayahCount = s.ayahCount;
        e.cachedAtEpochMs = now;
        return e;
    }

    public Outcome<List<ContentDtos.AyahDto>> ayahs(int surahId, @Nullable String translation,
                                                    @Nullable String tafsir, boolean words,
                                                    int page, int limit) {
        Outcome<List<ContentDtos.AyahDto>> res = run(api.ayahs(surahId, translation, tafsir, words, page, limit));
        if (res.isSuccess() && res.value != null && !res.value.isEmpty()) {
            return res;
        }
        List<ContentDtos.AyahDto> fallback = com.ikp.dev.app.data.content.IslamicContentProvider.fetchAyahs(surahId, page, limit);
        if (!fallback.isEmpty()) {
            return Outcome.ok(fallback);
        }
        return res;
    }

    public Outcome<ContentDtos.AyahDetailDto> ayahDetail(int surahId, int ayahNumber) {
        return run(api.ayahDetail(surahId, ayahNumber));
    }

    public Outcome<List<ContentDtos.TranslationEditionDto>> translationEditions() {
        return run(api.translationEditions());
    }

    public Outcome<List<ContentDtos.TafsirEditionDto>> tafsirEditions() {
        return run(api.tafsirEditions());
    }

    public Outcome<ContentDtos.QuranSearchResultDto> quranSearch(String q) {
        return run(api.quranSearch(q, 1, 25));
    }

    public Outcome<List<ContentDtos.HadithCollectionDto>> hadithCollections() {
        long now = System.currentTimeMillis();
        DailyContentEntity cached = db.contentDao().daily("hadith_collections");
        if (cached != null && cached.payloadJson != null) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<ContentDtos.HadithCollectionDto>>(){}.getType();
            List<ContentDtos.HadithCollectionDto> list = gson.fromJson(cached.payloadJson, type);
            if (list != null && !list.isEmpty()) {
                return Outcome.ok(list);
            }
        }
        Outcome<List<ContentDtos.HadithCollectionDto>> res = run(api.hadithCollections());
        List<ContentDtos.HadithCollectionDto> data = (res.isSuccess() && res.value != null && !res.value.isEmpty())
                ? res.value
                : com.ikp.dev.app.data.content.IslamicContentProvider.getHadithCollections();

        DailyContentEntity entity = new DailyContentEntity();
        entity.date = "hadith_collections";
        entity.payloadJson = gson.toJson(data);
        entity.cachedAtEpochMs = now;
        db.contentDao().putDaily(entity);

        return Outcome.ok(data);
    }

    public Outcome<List<ContentDtos.HadithBookDto>> hadithBooks(String code) {
        long now = System.currentTimeMillis();
        String dbKey = "hadith_books_" + (code == null ? "bukhari" : code.toLowerCase());
        DailyContentEntity cached = db.contentDao().daily(dbKey);
        if (cached != null && cached.payloadJson != null) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<ContentDtos.HadithBookDto>>(){}.getType();
            List<ContentDtos.HadithBookDto> list = gson.fromJson(cached.payloadJson, type);
            if (list != null && !list.isEmpty()) {
                return Outcome.ok(list);
            }
        }
        Outcome<List<ContentDtos.HadithBookDto>> res = run(api.hadithBooks(code));
        List<ContentDtos.HadithBookDto> data = (res.isSuccess() && res.value != null && !res.value.isEmpty())
                ? res.value
                : com.ikp.dev.app.data.content.IslamicContentProvider.getHadithBooks(code);

        DailyContentEntity entity = new DailyContentEntity();
        entity.date = dbKey;
        entity.payloadJson = gson.toJson(data);
        entity.cachedAtEpochMs = now;
        db.contentDao().putDaily(entity);

        return Outcome.ok(data);
    }

    public Outcome<List<ContentDtos.HadithDto>> hadithsOfBook(long bookId, int page, int limit) {
        long now = System.currentTimeMillis();
        String dbKey = "hadiths_book_" + bookId + "_p" + page;
        DailyContentEntity cached = db.contentDao().daily(dbKey);
        if (cached != null && cached.payloadJson != null) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<ContentDtos.HadithDto>>(){}.getType();
            List<ContentDtos.HadithDto> list = gson.fromJson(cached.payloadJson, type);
            if (list != null && !list.isEmpty()) {
                return Outcome.ok(list);
            }
        }
        Outcome<List<ContentDtos.HadithDto>> res = run(api.hadithsOfBook(bookId, page, limit));
        List<ContentDtos.HadithDto> data = (res.isSuccess() && res.value != null && !res.value.isEmpty())
                ? res.value
                : com.ikp.dev.app.data.content.IslamicContentProvider.getHadithsOfBook(bookId, page, limit);

        DailyContentEntity entity = new DailyContentEntity();
        entity.date = dbKey;
        entity.payloadJson = gson.toJson(data);
        entity.cachedAtEpochMs = now;
        db.contentDao().putDaily(entity);

        return Outcome.ok(data);
    }

    public Outcome<List<ContentDtos.HadithDto>> hadithSearch(String q, @Nullable String collection) {
        return run(api.hadithSearch(q, collection, 1, 25));
    }

    public Outcome<List<ContentDtos.DictionaryEntryDto>> dictionarySearch(String q) {
        return run(api.dictionarySearch(q, 1, 25));
    }

    public Outcome<List<ContentDtos.DuaCategoryDto>> duaCategories() {
        long now = System.currentTimeMillis();
        DailyContentEntity cached = db.contentDao().daily("dua_categories");
        if (cached != null && cached.payloadJson != null) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<ContentDtos.DuaCategoryDto>>(){}.getType();
            List<ContentDtos.DuaCategoryDto> list = gson.fromJson(cached.payloadJson, type);
            if (list != null && !list.isEmpty()) {
                return Outcome.ok(list);
            }
        }
        Outcome<List<ContentDtos.DuaCategoryDto>> res = run(api.duaCategories());
        List<ContentDtos.DuaCategoryDto> data = (res.isSuccess() && res.value != null && !res.value.isEmpty())
                ? res.value
                : com.ikp.dev.app.data.content.IslamicContentProvider.getDuaCategories();

        DailyContentEntity entity = new DailyContentEntity();
        entity.date = "dua_categories";
        entity.payloadJson = gson.toJson(data);
        entity.cachedAtEpochMs = now;
        db.contentDao().putDaily(entity);

        return Outcome.ok(data);
    }

    public Outcome<List<ContentDtos.DuaDto>> duas(@Nullable String category, int page, int limit) {
        long now = System.currentTimeMillis();
        String dbKey = "duas_" + (category == null ? "all" : category) + "_p" + page;
        DailyContentEntity cached = db.contentDao().daily(dbKey);
        if (cached != null && cached.payloadJson != null) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<ContentDtos.DuaDto>>(){}.getType();
            List<ContentDtos.DuaDto> list = gson.fromJson(cached.payloadJson, type);
            if (list != null && !list.isEmpty()) {
                return Outcome.ok(list);
            }
        }
        Outcome<List<ContentDtos.DuaDto>> res = run(api.duas(category, page, limit));
        List<ContentDtos.DuaDto> data = (res.isSuccess() && res.value != null && !res.value.isEmpty())
                ? res.value
                : com.ikp.dev.app.data.content.IslamicContentProvider.getDuas(category, page, limit);

        DailyContentEntity entity = new DailyContentEntity();
        entity.date = dbKey;
        entity.payloadJson = gson.toJson(data);
        entity.cachedAtEpochMs = now;
        db.contentDao().putDaily(entity);

        return Outcome.ok(data);
    }

    public Outcome<ContentDtos.CalendarTodayDto> calendarToday(@Nullable Integer offsetDays, String tz) {
        return run(api.calendarToday(null, offsetDays, tz));
    }

    public Outcome<List<ContentDtos.IslamicEventDto>> calendarEvents(@Nullable Integer hijriYear) {
        return run(api.calendarEvents(hijriYear, null, null));
    }

    public Outcome<List<Map<String, Object>>> prayerMethods() {
        return run(api.prayerMethods());
    }

    public Outcome<ContentDtos.HomePayloadDto> home(String tz) {
        return run(api.home(tz));
    }

    public Outcome<ContentDtos.DailyContentDto> dailyContent(String date, boolean forceRefresh) {
        long now = System.currentTimeMillis();
        DailyContentEntity cached = db.contentDao().daily(date);
        boolean fresh = cached != null && cached.cachedAtEpochMs != null
                && now - cached.cachedAtEpochMs < dailyTtlMs;
        if (!forceRefresh && fresh && cached.payloadJson != null) {
            return Outcome.ok(gson.fromJson(cached.payloadJson, ContentDtos.DailyContentDto.class));
        }
        Outcome<ContentDtos.DailyContentDto> result = run(api.dailyContent(date));
        if (result.isSuccess()) {
            DailyContentEntity e = new DailyContentEntity();
            e.date = date;
            e.payloadJson = gson.toJson(result.value);
            e.cachedAtEpochMs = now;
            db.contentDao().putDaily(e);
            db.contentDao().trimDaily(now - 7L * 24 * 60 * 60 * 1000);
        } else if (cached != null && cached.payloadJson != null) {
            return Outcome.ok(gson.fromJson(cached.payloadJson, ContentDtos.DailyContentDto.class));
        }
        return result;
    }

    public Outcome<Map<String, ContentDtos.SearchGroupDto>> globalSearch(String q) {
        return run(api.globalSearch(q, null, 5));
    }
}
