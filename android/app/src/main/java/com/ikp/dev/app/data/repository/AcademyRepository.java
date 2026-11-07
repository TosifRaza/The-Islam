package com.ikp.dev.app.data.repository;

import androidx.annotation.Nullable;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.core.error.ErrorMapper;
import com.ikp.dev.app.data.remote.AcademyApi;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * M5 learning repository: academy browsing/enrolment/progress, quizzes and
 * attempts, flashcard decks, research, scholars and fatwas. All reads are
 * guest-usable; member actions require a session (backend enforces — the app
 * surfaces the stable error code). Pure pass-through to the API with the
 * shared Outcome contract; no caching yet (offline sync lands in M7 /
 * Phase 14 — documented in docs-m5).
 */
public final class AcademyRepository {

    /** Stable ref-key prefixes for the cross-module user-content pipeline. */
    public static final String REF_COURSE = "course:";
    public static final String REF_LESSON = "lesson:";
    public static final String REF_RESEARCH = "research:";
    public static final String REF_FATWA = "fatwa:";
    public static final String REF_SCHOLAR = "scholar:";

    private final AcademyApi api;

    public AcademyRepository(AcademyApi api) {
        this.api = api;
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

    private static <T> List<T> list(Outcome<List<T>> outcome) {
        if (outcome.isSuccess() && outcome.value != null) return outcome.value;
        return new ArrayList<>();
    }

    // ----- academy -----

    public Outcome<List<AcademyDtos.CourseRowDto>> courses(int page, int size) {
        // 1. Try backend API first (has enriched subject models and lesson counts)
        Outcome<List<AcademyDtos.CourseRowDto>> apiRes = run(api.courses(page, size));
        if (apiRes.isSuccess() && apiRes.value != null && !apiRes.value.isEmpty()) {
            return apiRes;
        }
        // 2. Fallback to Supabase cloud direct
        Outcome<List<AcademyDtos.CourseRowDto>> sbFallback = fetchCoursesFromSupabase();
        if (sbFallback != null && sbFallback.isSuccess()) {
            return sbFallback;
        }
        return apiRes;
    }

    private Outcome<List<AcademyDtos.CourseRowDto>> fetchCoursesFromSupabase() {
        try {
            com.ikp.dev.app.core.supabase.SupabaseClient sb = com.ikp.dev.app.di.ServiceLocator.supabaseClient();
            if (sb != null) {
                Response<List<AcademyDtos.CourseRowDto>> res = sb.db().getCourses().execute();
                if (res.isSuccessful() && res.body() != null && !res.body().isEmpty()) {
                    return Outcome.ok(res.body());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public Outcome<AcademyDtos.CourseDetailDto> course(String courseId) {
        return run(api.course(courseId));
    }

    public Outcome<AcademyDtos.EnrolDto> enrol(String courseId) {
        return run(api.enrol(courseId));
    }

    public Outcome<AcademyDtos.ProgressDto> completeLesson(String lessonId) {
        return run(api.completeLesson(lessonId));
    }

    public Outcome<AcademyDtos.ProgressDto> progress(String courseId) {
        return run(api.progress(courseId));
    }

    public Outcome<List<AcademyDtos.ContinueDto>> continueLearning(int limit) {
        return run(api.continueLearning(limit));
    }

    // ----- assessments -----

    public Outcome<List<AcademyDtos.QuizRowDto>> quizzes(int page, int size) {
        return run(api.quizzes(page, size));
    }

    public Outcome<AcademyDtos.AttemptStartedDto> startAttempt(String quizId) {
        return run(api.startAttempt(quizId));
    }

    /** Server-side grading: the client only reports the selected option per question. */
    public Outcome<AcademyDtos.AttemptResultDto> submitAttempt(String attemptId,
                                                              List<AcademyDtos.QuestionPublicDto> questions,
                                                              Map<String, String> selectedByQuestion) {
        List<Map<String, String>> answers = new ArrayList<>();
        if (questions != null) {
            for (AcademyDtos.QuestionPublicDto q : questions) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("questionId", q.questionId);
                String selected = selectedByQuestion == null ? null : selectedByQuestion.get(q.questionId);
                if (selected != null) row.put("optionId", selected);
                answers.add(row);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answers", answers);
        return run(api.submitAttempt(attemptId, body));
    }

    public Outcome<List<AcademyDtos.AttemptRowDto>> attemptHistory(@Nullable String quizId, int page, int size) {
        return run(api.attemptHistory(quizId, page, size));
    }

    public Outcome<List<AcademyDtos.WeakTopicDto>> weakTopics() {
        return run(api.weakTopics());
    }

    // ----- flashcards -----

    public Outcome<List<AcademyDtos.DeckRowDto>> publicDecks(int page, int size) {
        return run(api.publicDecks(page, size));
    }

    public Outcome<AcademyDtos.DeckDetailDto> deck(String deckId) {
        return run(api.deck(deckId));
    }

    public Outcome<List<AcademyDtos.DeckRowDto>> myDecks() {
        return run(api.myDecks());
    }

    /** Personal (private) deck — cards are "front = back" lines from the simple editor. */
    public Outcome<AcademyDtos.DeckDetailDto> createMyDeck(String title, List<String> frontBackLines) {
        List<Map<String, String>> cards = new ArrayList<>();
        if (frontBackLines != null) {
            for (String line : frontBackLines) {
                if (line == null || !line.contains("=")) continue;
                int idx = line.indexOf('=');
                String front = line.substring(0, idx).trim();
                String back = line.substring(idx + 1).trim();
                if (front.isEmpty() || back.isEmpty()) continue;
                Map<String, String> card = new LinkedHashMap<>();
                card.put("frontText", front);
                card.put("backText", back);
                cards.add(card);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("cards", cards);
        return run(api.createMyDeck(body));
    }

    // ----- research / scholars / fatwas -----

    public Outcome<List<AcademyDtos.TopicDto>> researchTopics() {
        return run(api.researchTopics());
    }

    public Outcome<List<AcademyDtos.ArticleRowDto>> researchArticles(@Nullable String topic, int page, int size) {
        return run(api.researchArticles(topic, page, size));
    }

    public Outcome<AcademyDtos.ArticleDetailDto> researchArticle(String articleId) {
        return run(api.researchArticle(articleId));
    }

    public Outcome<List<AcademyDtos.ScholarDto>> scholars(int page, int size) {
        return run(api.scholars(page, size));
    }

    public Outcome<AcademyDtos.ScholarDto> scholar(String scholarId) {
        return run(api.scholar(scholarId));
    }

    public Outcome<List<AcademyDtos.FatwaRowDto>> fatwas(@Nullable String q, @Nullable String category,
                                                         int page, int size) {
        return run(api.fatwas(q, category, page, size));
    }

    public Outcome<AcademyDtos.FatwaDetailDto> fatwa(String fatwaId) {
        return run(api.fatwa(fatwaId));
    }

    // ----- user content (M5 ref kinds; backend rejects unknown prefixes) -----

    public Outcome<Map<String, Object>> saveResearchBookmark(String articleId, @Nullable String note) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("refKey", REF_RESEARCH + articleId);
        if (note != null) body.put("note", note);
        return run(api.putBookmark(body));
    }

    public Outcome<Map<String, Object>> saveResearchNote(String articleId, String text) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("refKey", REF_RESEARCH + articleId);
        body.put("body", text);
        return run(api.putNote(body));
    }

    public Outcome<Map<String, Object>> saveFatwaBookmark(String fatwaId) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("refKey", REF_FATWA + fatwaId);
        return run(api.putBookmark(body));
    }
}
