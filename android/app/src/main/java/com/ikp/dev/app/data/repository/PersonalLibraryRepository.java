package com.ikp.dev.app.data.repository;

import androidx.annotation.Nullable;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.error.ErrorMapper;
import com.ikp.dev.app.core.network.ApiClient;
import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.remote.ContentApi;
import com.ikp.dev.app.data.remote.dto.ContentDtos;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

/** Personal Knowledge Library repository (M4 §10). STRICT PRIVACY: uploads go
 *  to the authenticated principal's own bucket; every access is server-side
 *  ownership checked; the client never sends user ids. */
public final class PersonalLibraryRepository {

    private final ContentApi api;

    public PersonalLibraryRepository(ApiClient client) {
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

    public Outcome<ContentDtos.PersonalBookDto> upload(File file, String title,
                                                       @Nullable String author,
                                                       @Nullable String description) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        String mime = name.endsWith(".pdf") ? "application/pdf" : "application/epub+zip";
        RequestBody fileBody = RequestBody.create(file, MediaType.parse(mime));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), fileBody);
        return run(api.uploadPersonalBook(part,
                RequestBody.create(title, MediaType.parse("text/plain")),
                RequestBody.create(author == null ? "" : author, MediaType.parse("text/plain")),
                RequestBody.create(description == null ? "" : description, MediaType.parse("text/plain"))));
    }

    public Outcome<List<ContentDtos.PersonalBookDto>> books(@Nullable String q, int page, int limit) {
        return run(api.personalBooks(q, page, limit));
    }

    public Outcome<ContentDtos.PersonalBookDto> patch(String bookId, @Nullable String title,
                                                      @Nullable String author, @Nullable String description) {
        Map<String, String> body = new HashMap<>();
        if (title != null) body.put("title", title);
        if (author != null) body.put("author", author);
        if (description != null) body.put("description", description);
        return run(api.patchPersonalBook(bookId, body));
    }

    public Outcome<Map<String, Object>> delete(String bookId) {
        return run(api.deletePersonalBook(bookId));
    }

    public Outcome<ContentDtos.SignedUrlDto> downloadToken(String bookId) {
        return run(api.personalDownloadToken(bookId));
    }

    public Outcome<List<ContentDtos.PersonalCollectionDto>> collections() {
        return run(api.personalCollections());
    }

    public Outcome<Map<String, Object>> createCollection(String name) {
        Map<String, String> body = new HashMap<>();
        body.put("name", name);
        return run(api.createPersonalCollection(body));
    }

    public Outcome<Map<String, Object>> addBookToCollection(String collectionId, String bookId) {
        return run(api.addBookToCollection(collectionId, bookId));
    }
}
