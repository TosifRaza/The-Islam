package com.ikp.dev.app.data.remote;

import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.data.remote.dto.ContentDtos;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** M5 learning & research APIs — exact backend contract. Academy catalogue,
 *  research, scholar and fatwa reads are guest-usable; enrolment, attempts,
 *  flashcard practice with personal decks and user-content saves carry the
 *  token via AuthInterceptor. */
public interface AcademyApi {

    // ----- academy -----

    @GET("api/v1/academy/courses")
    Call<ApiResponse<List<AcademyDtos.CourseRowDto>>> courses(
            @Query("page") int page, @Query("size") int size);

    @GET("api/v1/academy/courses/{courseId}")
    Call<ApiResponse<AcademyDtos.CourseDetailDto>> course(@Path("courseId") String courseId);

    @POST("api/v1/academy/courses/{courseId}/enrol")
    Call<ApiResponse<AcademyDtos.EnrolDto>> enrol(@Path("courseId") String courseId);

    @POST("api/v1/academy/lessons/{lessonId}/complete")
    Call<ApiResponse<AcademyDtos.ProgressDto>> completeLesson(@Path("lessonId") String lessonId);

    @GET("api/v1/academy/courses/{courseId}/progress")
    Call<ApiResponse<AcademyDtos.ProgressDto>> progress(@Path("courseId") String courseId);

    @GET("api/v1/academy/continue")
    Call<ApiResponse<List<AcademyDtos.ContinueDto>>> continueLearning(@Query("limit") int limit);

    // ----- assessments -----

    @GET("api/v1/assessments/quizzes")
    Call<ApiResponse<List<AcademyDtos.QuizRowDto>>> quizzes(
            @Query("page") int page, @Query("size") int size);

    @POST("api/v1/assessments/quizzes/{quizId}/attempts")
    Call<ApiResponse<AcademyDtos.AttemptStartedDto>> startAttempt(@Path("quizId") String quizId);

    @POST("api/v1/assessments/attempts/{attemptId}/submit")
    Call<ApiResponse<AcademyDtos.AttemptResultDto>> submitAttempt(
            @Path("attemptId") String attemptId, @Body Map<String, Object> body);

    @GET("api/v1/assessments/attempts")
    Call<ApiResponse<List<AcademyDtos.AttemptRowDto>>> attemptHistory(
            @Query("quizId") String quizId, @Query("page") int page, @Query("size") int size);

    @GET("api/v1/assessments/weak-topics")
    Call<ApiResponse<List<AcademyDtos.WeakTopicDto>>> weakTopics();

    // ----- flashcards -----

    @GET("api/v1/flashcards/decks")
    Call<ApiResponse<List<AcademyDtos.DeckRowDto>>> publicDecks(
            @Query("page") int page, @Query("size") int size);

    @GET("api/v1/flashcards/decks/{deckId}")
    Call<ApiResponse<AcademyDtos.DeckDetailDto>> deck(@Path("deckId") String deckId);

    @GET("api/v1/flashcards/my/decks")
    Call<ApiResponse<List<AcademyDtos.DeckRowDto>>> myDecks();

    @POST("api/v1/flashcards/my/decks")
    Call<ApiResponse<AcademyDtos.DeckDetailDto>> createMyDeck(@Body Map<String, Object> body);

    // ----- research / scholars / fatwas -----

    @GET("api/v1/research/topics")
    Call<ApiResponse<List<AcademyDtos.TopicDto>>> researchTopics();

    @GET("api/v1/research/articles")
    Call<ApiResponse<List<AcademyDtos.ArticleRowDto>>> researchArticles(
            @Query("topic") String topic, @Query("page") int page, @Query("size") int size);

    @GET("api/v1/research/articles/{articleId}")
    Call<ApiResponse<AcademyDtos.ArticleDetailDto>> researchArticle(@Path("articleId") String articleId);

    @GET("api/v1/scholars")
    Call<ApiResponse<List<AcademyDtos.ScholarDto>>> scholars(
            @Query("page") int page, @Query("size") int size);

    @GET("api/v1/scholars/{scholarId}")
    Call<ApiResponse<AcademyDtos.ScholarDto>> scholar(@Path("scholarId") String scholarId);

    @GET("api/v1/fatwas")
    Call<ApiResponse<List<AcademyDtos.FatwaRowDto>>> fatwas(
            @Query("q") String q, @Query("category") String category,
            @Query("page") int page, @Query("size") int size);

    @GET("api/v1/fatwas/{fatwaId}")
    Call<ApiResponse<AcademyDtos.FatwaDetailDto>> fatwa(@Path("fatwaId") String fatwaId);

    // ----- user content (M5 ref kinds) -----

    @PUT("api/v1/user-content/bookmarks")
    Call<ApiResponse<Map<String, Object>>> putBookmark(@Body Map<String, String> body);

    @PUT("api/v1/user-content/notes")
    Call<ApiResponse<Map<String, Object>>> putNote(@Body Map<String, String> body);
}
