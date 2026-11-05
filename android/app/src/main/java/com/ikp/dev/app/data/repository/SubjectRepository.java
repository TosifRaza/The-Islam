package com.ikp.dev.app.data.repository;

import androidx.annotation.NonNull;

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
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Library categories repository. Offline-first: serves the Room cache when
 * fresh (TTL {@link #SUBJECTS_TTL_MS}) and revalidates from
 * GET /api/v1/subjects; on network failure returns the cached list. The
 * backend already hides category position 06 (OD-03) — the client must NOT
 * re-filter (single source of truth) but tests assert the invariant.
 */
public final class SubjectRepository {

    public static final long SUBJECTS_TTL_MS = 24L * 60 * 60 * 1000; // 24 h

    private final LibraryApi api;
    private final IkpDatabase db;
    private final long ttlMs;

    public SubjectRepository(ApiClient client, IkpDatabase db, long ttlMs) {
        this.api = client.create(LibraryApi.class);
        this.db = db;
        this.ttlMs = ttlMs;
    }

    public SubjectRepository(ApiClient client, IkpDatabase db) {
        this(client, db, SUBJECTS_TTL_MS);
    }

    /**
     * @param forceRefresh skip the TTL and hit the network (pull-to-refresh).
     */
    public Outcome<List<Models.Subject>> subjects(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        List<CacheEntities.SubjectEntity> cached = db.libraryDao().subjectsInOrder();
        boolean fresh = db.libraryDao().countFreshSubjects(now - ttlMs) > 0;

        if (!forceRefresh && fresh && !cached.isEmpty()) {
            return Outcome.ok(Mappers.toSubjects(toDtos(cached)));
        }

        try {
            Response<ApiResponse<List<LibraryDtos.SubjectDto>>> res = api.subjects().execute();
            if (res.isSuccessful() && res.body() != null) {
                List<Models.Subject> subjects = Mappers.toSubjects(ApiResponse.listOrEmpty(res.body().data));
                persist(subjects, now);
                return Outcome.ok(subjects);
            }
            // Server error with a usable cache → serve cache (offline-friendly)
            if (!cached.isEmpty() && res.code() >= 500) {
                return Outcome.ok(Mappers.toSubjects(toDtos(cached)));
            }
            return Outcome.fail(ErrorMapper.fromResponse(res));
        } catch (IOException offline) {
            if (!cached.isEmpty()) {
                return Outcome.ok(Mappers.toSubjects(toDtos(cached)));
            }
            return Outcome.fail(new ApiError(ApiError.OFFLINE, "error_network"));
        } catch (RuntimeException e) {
            if (!cached.isEmpty()) {
                return Outcome.ok(Mappers.toSubjects(toDtos(cached)));
            }
            return Outcome.fail(ErrorMapper.fromThrowable(e));
        }
    }

    private void persist(List<Models.Subject> subjects, long now) {
        List<CacheEntities.SubjectEntity> rows = new java.util.ArrayList<>();
        for (Models.Subject s : subjects) {
            CacheEntities.SubjectEntity e = new CacheEntities.SubjectEntity();
            e.slug = s.slug;
            e.name = s.name;
            e.nameAr = s.nameAr;
            e.description = s.description;
            e.position = s.position;
            e.cachedAtEpochMs = now;
            rows.add(e);
        }
        db.libraryDao().clearSubjects();
        db.libraryDao().upsertSubjects(rows);
    }

    @NonNull
    private static List<LibraryDtos.SubjectDto> toDtos(List<CacheEntities.SubjectEntity> cached) {
        List<LibraryDtos.SubjectDto> dtos = new java.util.ArrayList<>();
        for (CacheEntities.SubjectEntity e : cached) {
            LibraryDtos.SubjectDto d = new LibraryDtos.SubjectDto();
            d.slug = e.slug;
            d.name = e.name;
            d.nameAr = e.nameAr;
            d.description = e.description;
            d.position = e.position;
            dtos.add(d);
        }
        return dtos;
    }
}
