package com.ikp.dev.app.data.repository;

import androidx.annotation.Nullable;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.error.ErrorMapper;
import com.ikp.dev.app.core.network.ApiClient;
import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.remote.ContentApi;
import com.ikp.dev.app.data.remote.dto.ContentDtos;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/** Cross-module user content (PRF-03). ref_key: ayah:{s}:{a} · hadith:{id} ·
 *  book:{uuid} · dua:{id} · dict:{id}. Full two-way sync lands in M7. */
public final class UserContentRepository {

    private final ContentApi api;

    public UserContentRepository(ApiClient client) {
        this.api = client.create(ContentApi.class);
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

    public Outcome<List<ContentDtos.BookmarkDto>> bookmarks() {
        return run(api.bookmarks());
    }

    public Outcome<Map<String, Object>> putBookmark(String refKey, @Nullable String note) {
        Map<String, String> body = new HashMap<>();
        body.put("refKey", refKey);
        if (note != null) body.put("note", note);
        return run(api.putBookmark(body));
    }

    public Outcome<Map<String, Object>> deleteBookmark(String refKey) {
        return run(api.deleteBookmark(refKey));
    }

    public Outcome<Map<String, Object>> putNote(String refKey, String bodyText) {
        Map<String, String> body = new HashMap<>();
        body.put("refKey", refKey);
        body.put("body", bodyText);
        return run(api.putNote(body));
    }

    public Outcome<Map<String, Object>> putFavourite(String refKey) {
        Map<String, String> body = new HashMap<>();
        body.put("refKey", refKey);
        return run(api.putFavourite(body));
    }

    public Outcome<List<ContentDtos.ProgressDto>> progress() {
        return run(api.progressList());
    }

    public Outcome<Map<String, Object>> putProgress(String refKey, Integer percent, String positionJson) {
        Map<String, Object> body = new HashMap<>();
        body.put("refKey", refKey);
        if (percent != null) body.put("percent", percent);
        body.put("positionData", positionJson == null ? "{}" : positionJson);
        return run(api.putProgress(body));
    }

    public Outcome<Map<String, Object>> recordHistory(String refKey, @Nullable String title) {
        Map<String, String> body = new HashMap<>();
        body.put("refKey", refKey);
        if (title != null) body.put("title", title);
        return run(api.postHistory(body));
    }
}
