package com.ikp.dev.app.data.repository;

import androidx.annotation.Nullable;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.database.IkpDatabase;
import com.ikp.dev.app.core.error.ApiError;
import com.ikp.dev.app.core.error.ErrorMapper;
import com.ikp.dev.app.core.network.ApiClient;
import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.local.entity.CacheEntities;
import com.ikp.dev.app.data.mapper.Mappers;
import com.ikp.dev.app.data.remote.LibraryApi;
import com.ikp.dev.app.data.remote.dto.LibraryDtos;
import com.ikp.dev.app.domain.model.Models;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Public books repository (browse/search/detail) with a Room read-cache.
 * Cache key groups results per query so offline review of the last browse is
 * possible; TTL {@link #BOOKS_TTL_MS}. The server is authoritative — cached
 * rows are display-only and never re-published as fresh data.
 */
public final class BookRepository {

    public static final long BOOKS_TTL_MS = 60L * 60 * 1000; // 1 h
    public static final int DEFAULT_LIMIT = 20;

    private final LibraryApi api;
    private final IkpDatabase db;
    private final long ttlMs;

    public BookRepository(ApiClient client, IkpDatabase db) {
        this(client, db, BOOKS_TTL_MS);
    }

    public BookRepository(ApiClient client, IkpDatabase db, long ttlMs) {
        this.api = client.create(LibraryApi.class);
        this.db = db;
        this.ttlMs = ttlMs;
    }

    /** Stable cache key for a browse query. */
    public static String queryKey(@Nullable String subject, @Nullable String language, @Nullable String q) {
        return (subject == null ? "" : subject) + "|" + (language == null ? "" : language)
                + "|" + (q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** Browse/search published books (cursor-paged per backend contract). */
    public Outcome<Models.Page<Models.Book>> books(@Nullable String subject, @Nullable String language,
                                                   @Nullable String q, @Nullable String cursor,
                                                   boolean forceRefresh) {
        String key = queryKey(subject, language, q);
        long now = System.currentTimeMillis();
        List<CacheEntities.BookEntity> cached = db.libraryDao().booksForQuery(key);

        // Cache is only served for the FIRST page (cursor paging re-queries).
        if (!forceRefresh && cursor == null && isFresh(cached, now)) {
            List<Models.Book> rows = new ArrayList<>();
            for (CacheEntities.BookEntity e : cached) {
                rows.add(fromEntity(e));
            }
            return Outcome.ok(new Models.Page<>(rows, null));
        }

        try {
            Response<ApiResponse<List<LibraryDtos.BookRowDto>>> res = api
                    .books(subject, language, emptyToNull(q), null, DEFAULT_LIMIT, cursor)
                    .execute();
            if (res.isSuccessful() && res.body() != null) {
                List<Models.Book> rows = Mappers.toBooks(ApiResponse.listOrEmpty(res.body().data));
                ApiResponse.Meta meta = res.body().meta;
                String nextCursor = meta == null ? null : meta.nextCursor;
                if (cursor == null) {
                    db.libraryDao().clearQuery(key);
                }
                persist(key, rows, now);
                return Outcome.ok(new Models.Page<>(rows, nextCursor));
            }
            if (!cached.isEmpty() && res.code() >= 500) {
                return Outcome.ok(new Models.Page<>(fromEntities(cached), null));
            }
            return Outcome.fail(ErrorMapper.fromResponse(res));
        } catch (IOException offline) {
            if (!cached.isEmpty() && cursor == null) {
                return Outcome.ok(new Models.Page<>(fromEntities(cached), null));
            }
            return Outcome.fail(new ApiError(ApiError.OFFLINE, "error_network"));
        } catch (RuntimeException e) {
            if (!cached.isEmpty() && cursor == null) {
                return Outcome.ok(new Models.Page<>(fromEntities(cached), null));
            }
            return Outcome.fail(ErrorMapper.fromThrowable(e));
        }
    }

    /** Published book detail (404 for unpublished — NF-001 mapped by ErrorMapper). */
    public Outcome<Models.BookDetail> detail(String bookId) {
        try {
            Response<ApiResponse<LibraryDtos.BookDetailDto>> res = api.bookDetail(bookId).execute();
            if (res.isSuccessful() && res.body() != null && res.body().data != null) {
                Models.BookDetail detail = Mappers.toBookDetail(res.body().data);
                if (detail != null) {
                    return Outcome.ok(detail);
                }
                return Outcome.fail(new ApiError(ApiError.BAD_RESPONSE, "error_unknown"));
            }
            return Outcome.fail(ErrorMapper.fromResponse(res));
        } catch (IOException | RuntimeException e) {
            return Outcome.fail(ErrorMapper.fromThrowable(e));
        }
    }

    private boolean isFresh(List<CacheEntities.BookEntity> cached, long now) {
        if (cached.isEmpty()) {
            return false;
        }
        for (CacheEntities.BookEntity e : cached) {
            if (e.cachedAtEpochMs == null || e.cachedAtEpochMs < now - ttlMs) {
                return false;
            }
        }
        return true;
    }

    private void persist(String key, List<Models.Book> rows, long now) {
        List<CacheEntities.BookEntity> entities = new ArrayList<>();
        for (Models.Book b : rows) {
            CacheEntities.BookEntity e = new CacheEntities.BookEntity();
            e.bookId = b.bookId;
            e.queryKey = key;
            e.title = b.title;
            e.subtitle = b.subtitle;
            e.language = b.language;
            e.subjectSlug = b.subjectSlug;
            e.subjectName = b.subjectName;
            e.authorsJson = toJson(b.authors);
            e.formatsJson = toJson(b.formats);
            e.downloadAllowed = b.downloadAllowed;
            e.cachedAtEpochMs = now;
            entities.add(e);
        }
        db.libraryDao().upsertBooks(entities);
    }

    private static List<Models.Book> fromEntities(List<CacheEntities.BookEntity> entities) {
        List<Models.Book> rows = new ArrayList<>();
        for (CacheEntities.BookEntity e : entities) {
            rows.add(fromEntity(e));
        }
        return rows;
    }

    private static Models.Book fromEntity(CacheEntities.BookEntity e) {
        return new Models.Book(e.bookId, e.title, e.subtitle, e.language, e.subjectSlug,
                e.subjectName, fromJson(e.authorsJson), fromJson(e.formatsJson), e.downloadAllowed);
    }

    private static String toJson(List<String> list) {
        if (list == null) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(list.get(i).replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    private static List<String> fromJson(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.length() < 2) {
            return out;
        }
        String body = json.substring(1, json.length() - 1);
        if (body.trim().isEmpty()) {
            return out;
        }
        for (String part : body.split(",", -1)) {
            String s = part.trim();
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                out.add(s.substring(1, s.length() - 1));
            }
        }
        return out;
    }

    private static String emptyToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
