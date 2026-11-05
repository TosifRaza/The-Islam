package com.ikp.dev.app.data.remote;

import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.remote.dto.ContentDtos;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** M4 knowledge-content APIs — exact backend contract. Public endpoints are
 *  guest-usable; user-content calls carry the token via AuthInterceptor. */
public interface ContentApi {

    @GET("api/v1/quran/surahs")
    Call<ApiResponse<List<ContentDtos.SurahDto>>> surahs();

    @GET("api/v1/quran/surahs/{surahId}/ayahs")
    Call<ApiResponse<List<ContentDtos.AyahDto>>> ayahs(
            @Path("surahId") int surahId,
            @Query("translation") String translation,
            @Query("tafsir") String tafsir,
            @Query("words") boolean words,
            @Query("page") int page,
            @Query("limit") int limit);

    @GET("api/v1/quran/ayahs/{surahId}:{ayahNumber}")
    Call<ApiResponse<ContentDtos.AyahDetailDto>> ayahDetail(
            @Path("surahId") int surahId,
            @Path("ayahNumber") int ayahNumber);

    @GET("api/v1/quran/translations")
    Call<ApiResponse<List<ContentDtos.TranslationEditionDto>>> translationEditions();

    @GET("api/v1/quran/tafsirs")
    Call<ApiResponse<List<ContentDtos.TafsirEditionDto>>> tafsirEditions();

    @GET("api/v1/quran/search")
    Call<ApiResponse<ContentDtos.QuranSearchResultDto>> quranSearch(
            @Query("q") String q,
            @Query("page") int page,
            @Query("limit") int limit);

    @GET("api/v1/hadith/collections")
    Call<ApiResponse<List<ContentDtos.HadithCollectionDto>>> hadithCollections();

    @GET("api/v1/hadith/collections/{code}/books")
    Call<ApiResponse<List<ContentDtos.HadithBookDto>>> hadithBooks(@Path("code") String code);

    @GET("api/v1/hadith/books/{bookId}/hadiths")
    Call<ApiResponse<List<ContentDtos.HadithDto>>> hadithsOfBook(
            @Path("bookId") long bookId,
            @Query("page") int page,
            @Query("limit") int limit);

    @GET("api/v1/hadith/hadiths/{id}")
    Call<ApiResponse<ContentDtos.HadithDto>> hadithDetail(@Path("id") long id);

    @GET("api/v1/hadith/search")
    Call<ApiResponse<List<ContentDtos.HadithDto>>> hadithSearch(
            @Query("q") String q,
            @Query("collection") String collection,
            @Query("page") int page,
            @Query("limit") int limit);

    @GET("api/v1/dictionary/search")
    Call<ApiResponse<List<ContentDtos.DictionaryEntryDto>>> dictionarySearch(
            @Query("q") String q,
            @Query("page") int page,
            @Query("limit") int limit);

    @GET("api/v1/dictionary/entries/{id}")
    Call<ApiResponse<ContentDtos.DictionaryEntryDto>> dictionaryEntry(@Path("id") long id);

    @GET("api/v1/duas/categories")
    Call<ApiResponse<List<ContentDtos.DuaCategoryDto>>> duaCategories();

    @GET("api/v1/duas")
    Call<ApiResponse<List<ContentDtos.DuaDto>>> duas(
            @Query("category") String category,
            @Query("page") int page,
            @Query("limit") int limit);

    @GET("api/v1/duas/search")
    Call<ApiResponse<List<ContentDtos.DuaDto>>> duaSearch(
            @Query("q") String q,
            @Query("page") int page,
            @Query("limit") int limit);

    @GET("api/v1/calendar/today")
    Call<ApiResponse<ContentDtos.CalendarTodayDto>> calendarToday(
            @Query("convention") String convention,
            @Query("offsetDays") Integer offsetDays,
            @Query("tz") String tz);

    @GET("api/v1/calendar/events")
    Call<ApiResponse<List<ContentDtos.IslamicEventDto>>> calendarEvents(
            @Query("hijriYear") Integer hijriYear,
            @Query("convention") String convention,
            @Query("offsetDays") Integer offsetDays);

    @GET("api/v1/calendar/conventions")
    Call<ApiResponse<List<Map<String, Object>>>> calendarConventions();

    @GET("api/v1/prayer/methods")
    Call<ApiResponse<List<Map<String, Object>>>> prayerMethods();

    @GET("api/v1/home")
    Call<ApiResponse<ContentDtos.HomePayloadDto>> home(@Query("tz") String tz);

    @GET("api/v1/home/daily")
    Call<ApiResponse<ContentDtos.DailyContentDto>> dailyContent(@Query("date") String date);

    @GET("api/v1/search")
    Call<ApiResponse<Map<String, ContentDtos.SearchGroupDto>>> globalSearch(
            @Query("q") String q,
            @Query("groups") String groups,
            @Query("perGroup") int perGroup);

    @Multipart
    @POST("api/v1/personal/books")
    Call<ApiResponse<ContentDtos.PersonalBookDto>> uploadPersonalBook(
            @Part MultipartBody.Part file,
            @Part("title") RequestBody title,
            @Part("author") RequestBody author,
            @Part("description") RequestBody description);

    @GET("api/v1/personal/books")
    Call<ApiResponse<List<ContentDtos.PersonalBookDto>>> personalBooks(
            @Query("q") String q,
            @Query("page") int page,
            @Query("limit") int limit);

    @GET("api/v1/personal/books/{bookId}")
    Call<ApiResponse<ContentDtos.PersonalBookDto>> personalBook(@Path("bookId") String bookId);

    @PATCH("api/v1/personal/books/{bookId}")
    Call<ApiResponse<ContentDtos.PersonalBookDto>> patchPersonalBook(
            @Path("bookId") String bookId,
            @Body Map<String, String> body);

    @DELETE("api/v1/personal/books/{bookId}")
    Call<ApiResponse<Map<String, Object>>> deletePersonalBook(@Path("bookId") String bookId);

    @POST("api/v1/personal/books/{bookId}/download-token")
    Call<ApiResponse<ContentDtos.SignedUrlDto>> personalDownloadToken(@Path("bookId") String bookId);

    @GET("api/v1/personal/collections")
    Call<ApiResponse<List<ContentDtos.PersonalCollectionDto>>> personalCollections();

    @POST("api/v1/personal/collections")
    Call<ApiResponse<Map<String, Object>>> createPersonalCollection(@Body Map<String, String> body);

    @PUT("api/v1/personal/collections/{collectionId}/books/{bookId}")
    Call<ApiResponse<Map<String, Object>>> addBookToCollection(
            @Path("collectionId") String collectionId,
            @Path("bookId") String bookId);

    @DELETE("api/v1/personal/collections/{collectionId}/books/{bookId}")
    Call<ApiResponse<Map<String, Object>>> removeBookFromCollection(
            @Path("collectionId") String collectionId,
            @Path("bookId") String bookId);

    @GET("api/v1/user-content/bookmarks")
    Call<ApiResponse<List<ContentDtos.BookmarkDto>>> bookmarks();

    @PUT("api/v1/user-content/bookmarks")
    Call<ApiResponse<Map<String, Object>>> putBookmark(@Body Map<String, String> body);

    @DELETE("api/v1/user-content/bookmarks")
    Call<ApiResponse<Map<String, Object>>> deleteBookmark(@Query("refKey") String refKey);

    @GET("api/v1/user-content/notes")
    Call<ApiResponse<List<ContentDtos.NoteDto>>> notes();

    @PUT("api/v1/user-content/notes")
    Call<ApiResponse<Map<String, Object>>> putNote(@Body Map<String, String> body);

    @GET("api/v1/user-content/favourites")
    Call<ApiResponse<List<ContentDtos.FavouriteDto>>> favourites();

    @PUT("api/v1/user-content/favourites")
    Call<ApiResponse<Map<String, Object>>> putFavourite(@Body Map<String, String> body);

    @DELETE("api/v1/user-content/favourites")
    Call<ApiResponse<Map<String, Object>>> deleteFavourite(@Query("refKey") String refKey);

    @GET("api/v1/user-content/progress")
    Call<ApiResponse<List<ContentDtos.ProgressDto>>> progressList();

    @PUT("api/v1/user-content/progress")
    Call<ApiResponse<Map<String, Object>>> putProgress(@Body Map<String, Object> body);

    @POST("api/v1/user-content/history")
    Call<ApiResponse<Map<String, Object>>> postHistory(@Body Map<String, String> body);

    @GET("api/v1/user-content/history")
    Call<ApiResponse<List<ContentDtos.HistoryRowDto>>> history();
}
