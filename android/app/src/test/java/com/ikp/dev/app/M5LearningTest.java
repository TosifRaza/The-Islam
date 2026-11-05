package com.ikp.dev.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.remote.AcademyApi;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.data.repository.AcademyRepository;
import com.ikp.dev.app.ui.common.TwoLineAdapter;
import com.ikp.dev.app.util.CourseRows;
import com.ikp.dev.app.util.QuizRows;

import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * M5 JVM coverage: pure row mapping, the Gson contract for the learning DTOs
 * (including the media_url alternate naming) and the assessment privacy
 * invariant — the pre-submission question DTO must not carry correctness or
 * explanation data, while the submit body only reports questionId+optionId
 * (server-side grading is the single source of truth).
 */
public final class M5LearningTest {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    // ----- CourseRows -----

    @Test
    public void catalogueRowJoinsSubjectLevelAndLessonCount() {
        AcademyDtos.CourseRowDto c = new AcademyDtos.CourseRowDto();
        c.title = "Intro to Usul al-Fiqh";
        c.subject = new AcademyDtos.SubjectRefDto();
        c.subject.name = "Usul al-Fiqh";
        c.level = "beginner";
        c.lessonCount = 12;
        List<TwoLineAdapter.Item> rows = CourseRows.catalogue(List.of(c));
        assertEquals(1, rows.size());
        assertEquals("Intro to Usul al-Fiqh", rows.get(0).title());
        assertEquals("Usul al-Fiqh · beginner · 12 lessons", rows.get(0).subtitle());
    }

    @Test
    public void catalogueToleratesNullsAndEmptyList() {
        assertTrue(CourseRows.catalogue(null).isEmpty());
        AcademyDtos.CourseRowDto bare = new AcademyDtos.CourseRowDto();
        bare.title = "Only title";
        List<TwoLineAdapter.Item> rows = CourseRows.catalogue(List.of(bare));
        assertEquals("Only title", rows.get(0).title());
        assertEquals("", rows.get(0).subtitle());
    }

    @Test
    public void continueRowRendersPercentAndNextLessonOrNull() {
        assertNull(CourseRows.continueRow(null));
        AcademyDtos.ContinueDto empty = new AcademyDtos.ContinueDto();
        assertNull(CourseRows.continueRow(empty));

        AcademyDtos.ContinueDto c = new AcademyDtos.ContinueDto();
        c.title = "Aqeedah Basics";
        c.percent = 40;
        c.nextLessonTitle = "Lesson 3 — Tawhid";
        TwoLineAdapter.Item row = CourseRows.continueRow(c);
        assertEquals("Aqeedah Basics", row.title());
        assertEquals("40% · Lesson 3 — Tawhid", row.subtitle());
    }

    // ----- QuizRows -----

    @Test
    public void quizAndDeckRowsRenderCountsAndOwner() {
        AcademyDtos.QuizRowDto q = new AcademyDtos.QuizRowDto();
        q.title = "Fiqh of Fasting";
        q.questionCount = 10;
        q.timeLimitSeconds = 600;
        List<TwoLineAdapter.Item> quiz = QuizRows.catalogue(List.of(q));
        assertEquals("Fiqh of Fasting", quiz.get(0).title());
        assertEquals("10 questions · 600s limit", quiz.get(0).subtitle());

        AcademyDtos.DeckRowDto d = new AcademyDtos.DeckRowDto();
        d.title = "99 Names";
        d.cardCount = 99;
        d.ownerType = "user";
        List<TwoLineAdapter.Item> decks = QuizRows.decks(List.of(d));
        assertEquals("99 cards", decks.get(0).subtitle());
        assertEquals("user", decks.get(0).badge());
        assertTrue(QuizRows.decks(null).isEmpty());
    }

    // ----- Gson contract -----

    @Test
    public void courseCatalogueEnvelopeParsesWithMetaAndAlternates() {
        String json = "{\"data\":[{\"courseId\":\"c-1\",\"title\":\"Seerah 101\","
                + "\"subject\":{\"slug\":\"cat_seerah\",\"name\":\"Seerah\"},"
                + "\"lessonCount\":8,\"media_url\":\"https://x/y.pdf\"}],"
                + "\"meta\":{\"page\":1,\"size\":20,\"totalElements\":1,\"totalPages\":1}}";
        ApiResponse<List<AcademyDtos.CourseRowDto>> env = GSON.fromJson(json,
                new com.google.gson.reflect.TypeToken<ApiResponse<List<AcademyDtos.CourseRowDto>>>() { }.getType());
        assertEquals(1, env.data.size());
        assertEquals("Seerah 101", env.data.get(0).title);
        assertEquals("cat_seerah", env.data.get(0).subject.slug);
        assertEquals(1L, env.meta.totalPages.longValue());
    }

    @Test
    public void lessonDtoMapsMediaUrlAlternate() {
        String json = "{\"lessonId\":\"l-1\",\"title\":\"Reading the PDF\","
                + "\"lessonType\":\"pdf\",\"media_url\":\"https://files/x.pdf\"}";
        AcademyDtos.LessonPublicDto l = GSON.fromJson(json, AcademyDtos.LessonPublicDto.class);
        assertEquals("pdf", l.lessonType);
        assertEquals("https://files/x.pdf", l.mediaUrl);
    }

    // ----- assessment privacy invariant -----

    @Test
    public void preSubmissionQuestionDtoCarriesNoCorrectnessData() {
        for (Field f : AcademyDtos.QuestionPublicDto.class.getDeclaredFields()) {
            String n = f.getName().toLowerCase();
            assertFalse("pre-submission DTO leaks field: " + n,
                    n.contains("correct") || n.contains("explanation") || n.contains("answer"));
        }
        // The post-submission result keeps explanations by design.
        boolean hasExplanation = false;
        for (Field f : AcademyDtos.AnswerResultDto.class.getDeclaredFields()) {
            if (f.getName().equals("explanation")) hasExplanation = true;
        }
        assertTrue(hasExplanation);
    }

    @Test
    public void attemptStartedEnvelopeParsesQuestionsWithoutLeakingAnswers() {
        String json = "{\"data\":{\"attemptId\":\"a-1\",\"quizId\":\"q-1\",\"startedAt\":\"t\","
                + "\"timeLimitSeconds\":600,\"questions\":[{\"questionId\":\"qq-1\","
                + "\"prompt\":\"Which pillar is zakat?\",\"points\":1,\"sortOrder\":1,"
                + "\"options\":[{\"optionId\":\"o-1\",\"optionText\":\"Second\"},"
                + "{\"optionId\":\"o-2\",\"optionText\":\"Third\"}]}]}}";
        ApiResponse<AcademyDtos.AttemptStartedDto> env = GSON.fromJson(json,
                new com.google.gson.reflect.TypeToken<ApiResponse<AcademyDtos.AttemptStartedDto>>() { }.getType());
        assertEquals("a-1", env.data.attemptId);
        assertEquals(1, env.data.questions.size());
        assertEquals(2, env.data.questions.get(0).options.size());
        assertEquals("Second", env.data.questions.get(0).options.get(0).optionText);
        assertEquals(Integer.valueOf(600), env.data.timeLimitSeconds);
    }

    // ----- submit body construction (server-side grading) -----

    @Test
    public void submitAttemptReportsOnlyQuestionIdAndSelectedOption() throws Exception {
        RecordingApi api = new RecordingApi();
        AcademyRepository repo = new AcademyRepository(api);

        AcademyDtos.QuestionPublicDto q1 = new AcademyDtos.QuestionPublicDto();
        q1.questionId = "qq-1";
        AcademyDtos.QuestionPublicDto q2 = new AcademyDtos.QuestionPublicDto();
        q2.questionId = "qq-2";
        Map<String, String> selected = new LinkedHashMap<>();
        selected.put("qq-1", "o-2");
        // qq-2 left unanswered on purpose

        Outcome<AcademyDtos.AttemptResultDto> r = repo.submitAttempt("att-1", List.of(q1, q2), selected);
        assertTrue(r.isSuccess());
        Map<String, Object> body = api.lastSubmitBody;
        assertEquals(1, body.size()); // map size
        @SuppressWarnings("unchecked")
        List<Map<String, String>> answers = (List<Map<String, String>>) body.get("answers");
        assertEquals(2, answers.size());
        assertEquals("qq-1", answers.get(0).get("questionId"));
        assertEquals("o-2", answers.get(0).get("optionId"));
        assertEquals("qq-2", answers.get(1).get("questionId"));
        assertFalse(answers.get(1).containsKey("optionId"));
        // The body never carries any grading hints back to the server.
        assertFalse(body.toString().toLowerCase().contains("correct"));
        assertFalse(body.toString().toLowerCase().contains("explanation"));
    }

    /** Fake API that records the submit body and returns a minimal result. */
    static final class RecordingApi implements AcademyApi {
        Map<String, Object> lastSubmitBody;

        @Override
        public Call<ApiResponse<AcademyDtos.AttemptResultDto>> submitAttempt(
                String attemptId, Map<String, Object> body) {
            lastSubmitBody = body;
            AcademyDtos.AttemptResultDto result = new AcademyDtos.AttemptResultDto();
            result.attemptId = attemptId;
            result.scorePct = 50;
            result.passed = false;
            result.passScorePct = 70;
            result.status = "submitted";
            return new FakeCall<>(Response.success(new ApiResponse<>(result, null)));
        }

        @Override public Call<ApiResponse<List<AcademyDtos.CourseRowDto>>> courses(int p, int s) { throw unsupported(); }
        @Override public Call<ApiResponse<AcademyDtos.CourseDetailDto>> course(String id) { throw unsupported(); }
        @Override public Call<ApiResponse<AcademyDtos.EnrolDto>> enrol(String id) { throw unsupported(); }
        @Override public Call<ApiResponse<AcademyDtos.ProgressDto>> completeLesson(String id) { throw unsupported(); }
        @Override public Call<ApiResponse<AcademyDtos.ProgressDto>> progress(String id) { throw unsupported(); }
        @Override public Call<ApiResponse<List<AcademyDtos.ContinueDto>>> continueLearning(int l) { throw unsupported(); }
        @Override public Call<ApiResponse<List<AcademyDtos.QuizRowDto>>> quizzes(int p, int s) { throw unsupported(); }
        @Override public Call<ApiResponse<AcademyDtos.AttemptStartedDto>> startAttempt(String q) { throw unsupported(); }
        @Override public Call<ApiResponse<List<AcademyDtos.AttemptRowDto>>> attemptHistory(String q, int p, int s) { throw unsupported(); }
        @Override public Call<ApiResponse<List<AcademyDtos.WeakTopicDto>>> weakTopics() { throw unsupported(); }
        @Override public Call<ApiResponse<List<AcademyDtos.DeckRowDto>>> publicDecks(int p, int s) { throw unsupported(); }
        @Override public Call<ApiResponse<AcademyDtos.DeckDetailDto>> deck(String id) { throw unsupported(); }
        @Override public Call<ApiResponse<List<AcademyDtos.DeckRowDto>>> myDecks() { throw unsupported(); }
        @Override public Call<ApiResponse<AcademyDtos.DeckDetailDto>> createMyDeck(Map<String, Object> b) { throw unsupported(); }
        @Override public Call<ApiResponse<List<AcademyDtos.TopicDto>>> researchTopics() { throw unsupported(); }
        @Override public Call<ApiResponse<List<AcademyDtos.ArticleRowDto>>> researchArticles(String t, int p, int s) { throw unsupported(); }
        @Override public Call<ApiResponse<AcademyDtos.ArticleDetailDto>> researchArticle(String id) { throw unsupported(); }
        @Override public Call<ApiResponse<List<AcademyDtos.ScholarDto>>> scholars(int p, int s) { throw unsupported(); }
        @Override public Call<ApiResponse<AcademyDtos.ScholarDto>> scholar(String id) { throw unsupported(); }
        @Override public Call<ApiResponse<List<AcademyDtos.FatwaRowDto>>> fatwas(String q, String c, int p, int s) { throw unsupported(); }
        @Override public Call<ApiResponse<AcademyDtos.FatwaDetailDto>> fatwa(String id) { throw unsupported(); }
        @Override public Call<ApiResponse<Map<String, Object>>> putBookmark(Map<String, String> b) { throw unsupported(); }
        @Override public Call<ApiResponse<Map<String, Object>>> putNote(Map<String, String> b) { throw unsupported(); }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used in this test");
        }
    }

    /** Minimal synchronous Call for the fake API (mirrors TokenRefreshTest). */
    static final class FakeCall<T> implements Call<T> {
        private final Response<T> response;
        FakeCall(Response<T> response) { this.response = response; }
        @Override public Response<T> execute() { return response; }
        @Override public void enqueue(Callback<T> cb) { throw unsupported(); }
        @Override public boolean isExecuted() { return false; }
        @Override public void cancel() { }
        @Override public boolean isCanceled() { return false; }
        @Override public Call<T> clone() { return new FakeCall<>(response); }
        @Override public okhttp3.Request request() {
            return new okhttp3.Request.Builder().url("https://ikp.test/fake").build();
        }
        @Override public okio.Timeout timeout() { return okio.Timeout.NONE; }
        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used in this test");
        }
    }
}
