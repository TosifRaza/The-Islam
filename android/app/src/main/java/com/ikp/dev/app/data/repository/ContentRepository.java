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
        if (!forceRefresh && !cached.isEmpty()) {
            List<ContentDtos.SurahDto> rows = new ArrayList<>();
            for (SurahCacheEntity e : cached) rows.add(fromCache(e));
            return Outcome.ok(rows);
        }

        // Cache empty: seed from authoritative local catalog immediately for instant load
        if (!forceRefresh) {
            List<ContentDtos.SurahDto> fallback = com.ikp.dev.app.data.content.IslamicContentProvider.getAllSurahs();
            List<SurahCacheEntity> fresh = new ArrayList<>();
            for (ContentDtos.SurahDto s : fallback) fresh.add(toCache(s, now));
            db.contentDao().replaceSurahs(fresh);
            com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
                try {
                    Outcome<List<ContentDtos.SurahDto>> result = run(api.surahs());
                    if (result.isSuccess() && result.value != null && !result.value.isEmpty()) {
                        List<SurahCacheEntity> netFresh = new ArrayList<>();
                        for (ContentDtos.SurahDto s : result.value) netFresh.add(toCache(s, System.currentTimeMillis()));
                        db.contentDao().replaceSurahs(netFresh);
                    }
                } catch (Exception ignored) {}
            });
            return Outcome.ok(fallback);
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

        // Cache empty: return bundled authentic collections instantly
        List<ContentDtos.HadithCollectionDto> data = com.ikp.dev.app.data.content.IslamicContentProvider.getHadithCollections();
        DailyContentEntity entity = new DailyContentEntity();
        entity.date = "hadith_collections";
        entity.payloadJson = gson.toJson(data);
        entity.cachedAtEpochMs = now;
        db.contentDao().putDaily(entity);

        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            try {
                Outcome<List<ContentDtos.HadithCollectionDto>> res = run(api.hadithCollections());
                if (res.isSuccess() && res.value != null && !res.value.isEmpty()) {
                    DailyContentEntity fresh = new DailyContentEntity();
                    fresh.date = "hadith_collections";
                    fresh.payloadJson = gson.toJson(res.value);
                    fresh.cachedAtEpochMs = System.currentTimeMillis();
                    db.contentDao().putDaily(fresh);
                }
            } catch (Exception ignored) {}
        });

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

        // Cache empty: return bundled books instantly
        List<ContentDtos.HadithBookDto> data = com.ikp.dev.app.data.content.IslamicContentProvider.getHadithBooks(code);
        DailyContentEntity entity = new DailyContentEntity();
        entity.date = dbKey;
        entity.payloadJson = gson.toJson(data);
        entity.cachedAtEpochMs = now;
        db.contentDao().putDaily(entity);

        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            try {
                Outcome<List<ContentDtos.HadithBookDto>> res = run(api.hadithBooks(code));
                if (res.isSuccess() && res.value != null && !res.value.isEmpty()) {
                    DailyContentEntity fresh = new DailyContentEntity();
                    fresh.date = dbKey;
                    fresh.payloadJson = gson.toJson(res.value);
                    fresh.cachedAtEpochMs = System.currentTimeMillis();
                    db.contentDao().putDaily(fresh);
                }
            } catch (Exception ignored) {}
        });

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

        // Cache empty: return bundled ahadith instantly
        List<ContentDtos.HadithDto> data = com.ikp.dev.app.data.content.IslamicContentProvider.getHadithsOfBook(bookId, page, limit);
        DailyContentEntity entity = new DailyContentEntity();
        entity.date = dbKey;
        entity.payloadJson = gson.toJson(data);
        entity.cachedAtEpochMs = now;
        db.contentDao().putDaily(entity);

        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            try {
                Outcome<List<ContentDtos.HadithDto>> res = run(api.hadithsOfBook(bookId, page, limit));
                if (res.isSuccess() && res.value != null && !res.value.isEmpty()) {
                    DailyContentEntity fresh = new DailyContentEntity();
                    fresh.date = dbKey;
                    fresh.payloadJson = gson.toJson(res.value);
                    fresh.cachedAtEpochMs = System.currentTimeMillis();
                    db.contentDao().putDaily(fresh);
                }
            } catch (Exception ignored) {}
        });

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

        List<ContentDtos.DuaCategoryDto> data = com.ikp.dev.app.data.content.IslamicContentProvider.getDuaCategories();
        DailyContentEntity entity = new DailyContentEntity();
        entity.date = "dua_categories";
        entity.payloadJson = gson.toJson(data);
        entity.cachedAtEpochMs = now;
        db.contentDao().putDaily(entity);

        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            try {
                Outcome<List<ContentDtos.DuaCategoryDto>> res = run(api.duaCategories());
                if (res.isSuccess() && res.value != null && !res.value.isEmpty()) {
                    DailyContentEntity fresh = new DailyContentEntity();
                    fresh.date = "dua_categories";
                    fresh.payloadJson = gson.toJson(res.value);
                    fresh.cachedAtEpochMs = System.currentTimeMillis();
                    db.contentDao().putDaily(fresh);
                }
            } catch (Exception ignored) {}
        });

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

        List<ContentDtos.DuaDto> data = com.ikp.dev.app.data.content.IslamicContentProvider.getDuas(category, page, limit);
        DailyContentEntity entity = new DailyContentEntity();
        entity.date = dbKey;
        entity.payloadJson = gson.toJson(data);
        entity.cachedAtEpochMs = now;
        db.contentDao().putDaily(entity);

        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            try {
                Outcome<List<ContentDtos.DuaDto>> res = run(api.duas(category, page, limit));
                if (res.isSuccess() && res.value != null && !res.value.isEmpty()) {
                    DailyContentEntity fresh = new DailyContentEntity();
                    fresh.date = dbKey;
                    fresh.payloadJson = gson.toJson(res.value);
                    fresh.cachedAtEpochMs = System.currentTimeMillis();
                    db.contentDao().putDaily(fresh);
                }
            } catch (Exception ignored) {}
        });

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
        DailyContentEntity cached = db.contentDao().daily("home_payload");
        if (cached != null && cached.payloadJson != null) {
            try {
                ContentDtos.HomePayloadDto p = gson.fromJson(cached.payloadJson, ContentDtos.HomePayloadDto.class);
                if (p != null) return Outcome.ok(p);
            } catch (Exception ignored) {}
        }
        ContentDtos.HomePayloadDto fallback = defaultHomePayload();
        DailyContentEntity entity = new DailyContentEntity();
        entity.date = "home_payload";
        entity.payloadJson = gson.toJson(fallback);
        entity.cachedAtEpochMs = System.currentTimeMillis();
        db.contentDao().putDaily(entity);

        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            try {
                Outcome<ContentDtos.HomePayloadDto> res = run(api.home(tz));
                if (res.isSuccess() && res.value != null) {
                    DailyContentEntity fresh = new DailyContentEntity();
                    fresh.date = "home_payload";
                    fresh.payloadJson = gson.toJson(res.value);
                    fresh.cachedAtEpochMs = System.currentTimeMillis();
                    db.contentDao().putDaily(fresh);
                }
            } catch (Exception ignored) {}
        });

        return Outcome.ok(fallback);
    }

    public static ContentDtos.HomePayloadDto defaultHomePayload() {
        ContentDtos.HomePayloadDto p = new ContentDtos.HomePayloadDto();
        p.hijriToday = new ContentDtos.HijriDto();
        p.hijriToday.day = 16;
        p.hijriToday.month = 11;
        p.hijriToday.year = 1446;
        p.gregorian = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        return p;
    }

    public Outcome<ContentDtos.DailyContentDto> dailyContent(String date, boolean forceRefresh) {
        long now = System.currentTimeMillis();
        DailyContentEntity cached = db.contentDao().daily(date);
        boolean fresh = cached != null && cached.cachedAtEpochMs != null
                && now - cached.cachedAtEpochMs < dailyTtlMs;
        if (!forceRefresh && fresh && cached.payloadJson != null) {
            try {
                ContentDtos.DailyContentDto d = gson.fromJson(cached.payloadJson, ContentDtos.DailyContentDto.class);
                if (d != null) return Outcome.ok(d);
            } catch (Exception ignored) {}
        }

        ContentDtos.DailyContentDto fallback = defaultDailyContent(date);
        if (!forceRefresh) {
            DailyContentEntity e = new DailyContentEntity();
            e.date = date;
            e.payloadJson = gson.toJson(fallback);
            e.cachedAtEpochMs = now;
            db.contentDao().putDaily(e);

            com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
                try {
                    Outcome<ContentDtos.DailyContentDto> result = run(api.dailyContent(date));
                    if (result.isSuccess() && result.value != null) {
                        DailyContentEntity freshEntity = new DailyContentEntity();
                        freshEntity.date = date;
                        freshEntity.payloadJson = gson.toJson(result.value);
                        freshEntity.cachedAtEpochMs = System.currentTimeMillis();
                        db.contentDao().putDaily(freshEntity);
                    }
                } catch (Exception ignored) {}
            });

            return Outcome.ok(fallback);
        }

        Outcome<ContentDtos.DailyContentDto> result = run(api.dailyContent(date));
        if (result.isSuccess() && result.value != null) {
            DailyContentEntity e = new DailyContentEntity();
            e.date = date;
            e.payloadJson = gson.toJson(result.value);
            e.cachedAtEpochMs = now;
            db.contentDao().putDaily(e);
            return result;
        } else if (cached != null && cached.payloadJson != null) {
            return Outcome.ok(gson.fromJson(cached.payloadJson, ContentDtos.DailyContentDto.class));
        }
        return Outcome.ok(fallback);
    }

    public static ContentDtos.DailyContentDto defaultDailyContent(String date) {
        ContentDtos.DailyContentDto d = new ContentDtos.DailyContentDto();
        d.date = date;

        d.ayah_of_day = new ContentDtos.SlotDto();
        d.ayah_of_day.attribution = "Surah Az-Zumar (39:53)";
        d.ayah_of_day.ayah = new ContentDtos.AyahInSlotDto();
        d.ayah_of_day.ayah.surahId = 39;
        d.ayah_of_day.ayah.surahName = "Az-Zumar";
        d.ayah_of_day.ayah.surahNameAr = "الزمر";
        d.ayah_of_day.ayah.ayahNumber = 53;
        d.ayah_of_day.ayah.textUthmani = "قُلْ يَٰعِبَادِىَ ٱلَّذِينَ أَسْرَفُوا۟ عَلَىٰٓ أَنفُسِهِمْ لَا تَقْنَطُوا۟ مِن رَّحْمَةِ ٱللَّهِ ۚ إِنَّ ٱللَّهَ يَغْفِرُ ٱلذُّنُوبَ جَمِيعًا ۚ إِنَّهُۥ هُوَ ٱلْغَفُورُ ٱلرَّحِيمُ";

        d.hadith_of_day = new ContentDtos.SlotDto();
        d.hadith_of_day.attribution = "Sahih Muslim (2699)";
        d.hadith_of_day.hadith = new ContentDtos.HadithInSlotDto();
        d.hadith_of_day.hadith.textEn = "Whoever travels a path in search of knowledge, Allah will make easy for him a path to Paradise.";
        d.hadith_of_day.hadith.textAr = "مَنْ سَلَكَ طَرِيقًا يَلْتَمِسُ فِيهِ عِلْمًا سَهَّلَ اللَّهُ لَهُ بِهِ طَرِيقًا إِلَى الْجَنَّةِ";

        d.daily_reminder = new ContentDtos.SlotDto();
        d.daily_reminder.customText = "Begin each deed with Bismillah, hold fast to the Sunnah, and maintain sincerity in all actions.";

        d.islamic_event = new ContentDtos.SlotDto();
        d.islamic_event.event = new ContentDtos.IslamicEventDto();
        d.islamic_event.event.nameEn = "Daily Remembrance of Allah (Dhikr)";

        return d;
    }

    public Outcome<Map<String, ContentDtos.SearchGroupDto>> globalSearch(String q) {
        return run(api.globalSearch(q, null, 5));
    }
}
