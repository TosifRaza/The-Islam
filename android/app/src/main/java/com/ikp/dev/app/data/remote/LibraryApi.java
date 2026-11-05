package com.ikp.dev.app.data.remote;

import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.remote.dto.LibraryDtos;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Public library APIs — exact M1/M2 backend contract (docs/05 §3.4/§3.5).
 * The public book list/detail payload does NOT expose file UUIDs (format
 * names only), so the download-token flow cannot be completed by any client;
 * the Android UI treats download as a documented-unavailable capability.
 */
public interface LibraryApi {

    @GET("api/v1/subjects")
    Call<ApiResponse<List<LibraryDtos.SubjectDto>>> subjects();

    @GET("api/v1/subjects/{slug}")
    Call<ApiResponse<LibraryDtos.SubjectDto>> subject(@Path("slug") String slug);

    @GET("api/v1/library/books")
    Call<ApiResponse<List<LibraryDtos.BookRowDto>>> books(
            @Query("subject") String subjectSlug,
            @Query("language") String language,
            @Query("q") String query,
            @Query("format") String format,
            @Query("limit") Integer limit,
            @Query("cursor") String cursor);

    @GET("api/v1/library/books/{bookId}")
    Call<ApiResponse<LibraryDtos.BookDetailDto>> bookDetail(@Path("bookId") String bookId);

    /** Authorised download → short-lived signed URL (needs authentication). */
    @POST("api/v1/library/books/{bookId}/download-token")
    Call<ApiResponse<LibraryDtos.DownloadTokenDto>> downloadToken(
            @Path("bookId") String bookId,
            @Body Map<String, String> body);
}
