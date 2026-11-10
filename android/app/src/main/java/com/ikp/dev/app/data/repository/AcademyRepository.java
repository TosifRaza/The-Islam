package com.ikp.dev.app.data.repository;

import androidx.annotation.NonNull;
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
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * M5 learning repository: academy browsing/enrolment/progress, quizzes and
 * attempts, flashcard decks, research, scholars and fatwas. Offline-first and
 * mobile-resilient with embedded default seed catalogue, Supabase cloud direct,
 * and backend API support.
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

    private static volatile List<AcademyDtos.CourseRowDto> cachedCourses = null;
    private static volatile List<AcademyDtos.DeckRowDto> cachedDecks = null;

    public List<AcademyDtos.CourseRowDto> getCachedOrSeedCourses() {
        if (cachedCourses == null || cachedCourses.isEmpty()) {
            cachedCourses = defaultCourses();
        }
        return new ArrayList<>(cachedCourses);
    }

    public List<AcademyDtos.DeckRowDto> getCachedOrSeedDecks() {
        if (cachedDecks == null || cachedDecks.isEmpty()) {
            cachedDecks = defaultDecks();
        }
        return new ArrayList<>(cachedDecks);
    }

    // ----- academy -----

    public Outcome<List<AcademyDtos.CourseRowDto>> courses(int page, int size) {
        // 1. Try Supabase cloud direct first (fastest cloud provider)
        Outcome<List<AcademyDtos.CourseRowDto>> sbFallback = fetchCoursesFromSupabase();
        if (sbFallback != null && sbFallback.isSuccess() && sbFallback.value != null && !sbFallback.value.isEmpty()) {
            cachedCourses = sbFallback.value;
            return sbFallback;
        }
        // 2. Try backend API
        try {
            Outcome<List<AcademyDtos.CourseRowDto>> apiRes = run(api.courses(page, size));
            if (apiRes.isSuccess() && apiRes.value != null && !apiRes.value.isEmpty()) {
                cachedCourses = apiRes.value;
                return apiRes;
            }
        } catch (Exception ignored) {}
        // 3. Fallback to embedded default catalog (offline-first & phone standalone resilience)
        if (cachedCourses == null || cachedCourses.isEmpty()) {
            cachedCourses = defaultCourses();
        }
        return Outcome.ok(cachedCourses);
    }

    private Outcome<List<AcademyDtos.CourseRowDto>> fetchCoursesFromSupabase() {
        try {
            com.ikp.dev.app.core.supabase.SupabaseClient sb = com.ikp.dev.app.di.ServiceLocator.supabaseClient();
            if (sb != null) {
                Response<List<AcademyDtos.CourseRowDto>> res = sb.db().getCourses().execute();
                if (res.isSuccessful() && res.body() != null && !res.body().isEmpty()) {
                    List<AcademyDtos.CourseRowDto> list = res.body();
                    for (AcademyDtos.CourseRowDto c : list) {
                        enrichCourse(c);
                    }
                    return Outcome.ok(list);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void enrichCourse(AcademyDtos.CourseRowDto c) {
        if (c == null) return;
        if (c.subject == null) {
            c.subject = new AcademyDtos.SubjectRefDto();
            String t = (c.title != null ? c.title : "").toLowerCase(Locale.ROOT);
            if (t.contains("tajweed") || t.contains("recitation") || t.contains("quran")) {
                c.subject.slug = "cat_quran_tafsir";
                c.subject.name = "Al-Quran & Tafsir";
            } else if (t.contains("aqeedah") || t.contains("iman") || t.contains("tawheed")) {
                c.subject.slug = "cat_aqeedah";
                c.subject.name = "Aqeedah";
            } else if (t.contains("fiqh") || t.contains("taharah") || t.contains("salah") || t.contains("prayer")) {
                c.subject.slug = "cat_fiqh";
                c.subject.name = "Fiqh";
            } else if (t.contains("arabic")) {
                c.subject.slug = "cat_arabic_academy";
                c.subject.name = "Quranic Arabic";
            } else if (t.contains("seerah") || t.contains("prophet") || t.contains("history")) {
                c.subject.slug = "cat_history_seerah";
                c.subject.name = "Seerah & History";
            } else {
                c.subject.slug = "cat_general";
                c.subject.name = "Islamic Studies";
            }
        }
        if (c.lessonCount == null || c.lessonCount == 0) {
            c.lessonCount = c.estimatedHours != null ? Math.max(4, c.estimatedHours) : 6;
        }
    }

    public Outcome<AcademyDtos.CourseDetailDto> course(String courseId) {
        Outcome<AcademyDtos.CourseDetailDto> apiRes = run(api.course(courseId));
        if (apiRes.isSuccess() && apiRes.value != null) {
            return apiRes;
        }
        return Outcome.ok(defaultCourseDetail(courseId));
    }

    public Outcome<AcademyDtos.EnrolDto> enrol(String courseId) {
        Outcome<AcademyDtos.EnrolDto> apiRes = run(api.enrol(courseId));
        if (apiRes.isSuccess() && apiRes.value != null) {
            return apiRes;
        }
        AcademyDtos.EnrolDto e = new AcademyDtos.EnrolDto();
        e.courseId = courseId;
        e.status = "active";
        return Outcome.ok(e);
    }

    public Outcome<AcademyDtos.ProgressDto> completeLesson(String lessonId) {
        Outcome<AcademyDtos.ProgressDto> apiRes = run(api.completeLesson(lessonId));
        if (apiRes.isSuccess() && apiRes.value != null) {
            return apiRes;
        }
        AcademyDtos.ProgressDto p = new AcademyDtos.ProgressDto();
        p.totalLessons = 5;
        p.completedLessons = 1;
        p.percent = 20;
        p.status = "active";
        return Outcome.ok(p);
    }

    public Outcome<AcademyDtos.ProgressDto> progress(String courseId) {
        Outcome<AcademyDtos.ProgressDto> apiRes = run(api.progress(courseId));
        if (apiRes.isSuccess() && apiRes.value != null) {
            return apiRes;
        }
        AcademyDtos.ProgressDto p = new AcademyDtos.ProgressDto();
        p.courseId = courseId;
        p.totalLessons = 5;
        p.completedLessons = 0;
        p.percent = 0;
        p.status = "active";
        return Outcome.ok(p);
    }

    public Outcome<List<AcademyDtos.ContinueDto>> continueLearning(int limit) {
        Outcome<List<AcademyDtos.ContinueDto>> apiRes = run(api.continueLearning(limit));
        if (apiRes.isSuccess() && apiRes.value != null && !apiRes.value.isEmpty()) {
            return apiRes;
        }
        return Outcome.ok(new ArrayList<>());
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
        Outcome<List<AcademyDtos.WeakTopicDto>> apiRes = run(api.weakTopics());
        if (apiRes.isSuccess() && apiRes.value != null) {
            return apiRes;
        }
        return Outcome.ok(new ArrayList<>());
    }

    // ----- flashcards -----

    public Outcome<List<AcademyDtos.DeckRowDto>> publicDecks(int page, int size) {
        try {
            Outcome<List<AcademyDtos.DeckRowDto>> apiRes = run(api.publicDecks(page, size));
            if (apiRes.isSuccess() && apiRes.value != null && !apiRes.value.isEmpty()) {
                cachedDecks = apiRes.value;
                return apiRes;
            }
        } catch (Exception ignored) {}
        if (cachedDecks == null || cachedDecks.isEmpty()) {
            cachedDecks = defaultDecks();
        }
        return Outcome.ok(cachedDecks);
    }

    public Outcome<AcademyDtos.DeckDetailDto> deck(String deckId) {
        Outcome<AcademyDtos.DeckDetailDto> apiRes = run(api.deck(deckId));
        if (apiRes.isSuccess() && apiRes.value != null) {
            return apiRes;
        }
        return Outcome.ok(defaultDeckDetail(deckId));
    }

    public Outcome<List<AcademyDtos.DeckRowDto>> myDecks() {
        Outcome<List<AcademyDtos.DeckRowDto>> apiRes = run(api.myDecks());
        if (apiRes.isSuccess() && apiRes.value != null) {
            return apiRes;
        }
        return Outcome.ok(new ArrayList<>());
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
        Outcome<List<AcademyDtos.TopicDto>> apiRes = run(api.researchTopics());
        if (apiRes.isSuccess() && apiRes.value != null && !apiRes.value.isEmpty()) {
            return apiRes;
        }
        return Outcome.ok(defaultResearchTopics());
    }

    public Outcome<List<AcademyDtos.ArticleRowDto>> researchArticles(@Nullable String topic, int page, int size) {
        Outcome<List<AcademyDtos.ArticleRowDto>> apiRes = run(api.researchArticles(topic, page, size));
        if (apiRes.isSuccess() && apiRes.value != null && !apiRes.value.isEmpty()) {
            return apiRes;
        }
        return Outcome.ok(defaultResearchArticles(topic));
    }

    public Outcome<AcademyDtos.ArticleDetailDto> researchArticle(String articleId) {
        Outcome<AcademyDtos.ArticleDetailDto> apiRes = run(api.researchArticle(articleId));
        if (apiRes.isSuccess() && apiRes.value != null) {
            return apiRes;
        }
        return Outcome.ok(defaultArticleDetail(articleId));
    }

    public Outcome<List<AcademyDtos.ScholarDto>> scholars(int page, int size) {
        Outcome<List<AcademyDtos.ScholarDto>> apiRes = run(api.scholars(page, size));
        if (apiRes.isSuccess() && apiRes.value != null && !apiRes.value.isEmpty()) {
            return apiRes;
        }
        return Outcome.ok(defaultScholars());
    }

    public Outcome<AcademyDtos.ScholarDto> scholar(String scholarId) {
        Outcome<AcademyDtos.ScholarDto> apiRes = run(api.scholar(scholarId));
        if (apiRes.isSuccess() && apiRes.value != null) {
            return apiRes;
        }
        return Outcome.ok(defaultScholarDetail(scholarId));
    }

    public Outcome<List<AcademyDtos.FatwaRowDto>> fatwas(@Nullable String q, @Nullable String category,
                                                         int page, int size) {
        Outcome<List<AcademyDtos.FatwaRowDto>> apiRes = run(api.fatwas(q, category, page, size));
        if (apiRes.isSuccess() && apiRes.value != null && !apiRes.value.isEmpty()) {
            return apiRes;
        }
        return Outcome.ok(defaultFatwas(q, category));
    }

    public Outcome<AcademyDtos.FatwaDetailDto> fatwa(String fatwaId) {
        Outcome<AcademyDtos.FatwaDetailDto> apiRes = run(api.fatwa(fatwaId));
        if (apiRes.isSuccess() && apiRes.value != null) {
            return apiRes;
        }
        return Outcome.ok(defaultFatwaDetail(fatwaId));
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

    // ----- DEFAULT / OFFLINE FALLBACK DATA CATALOGUE -----

    @NonNull
    public static List<AcademyDtos.CourseRowDto> defaultCourses() {
        List<AcademyDtos.CourseRowDto> list = new ArrayList<>();

        AcademyDtos.CourseRowDto c1 = new AcademyDtos.CourseRowDto();
        c1.courseId = "c0000005-0000-0000-0000-000000000005";
        c1.title = "Tajweed Rules & Recitation Excellence";
        c1.description = "Master the rules of Noon Sakinah, Meem Sakinah, Madd, and articulation points (Makharij) with practical audio examples.";
        c1.language = "en";
        c1.level = "beginner";
        c1.estimatedHours = 6;
        c1.lessonCount = 6;
        c1.instructorDisplayName = "Qari Ibrahim As-Sudais";
        c1.subject = new AcademyDtos.SubjectRefDto();
        c1.subject.slug = "cat_quran_tafsir";
        c1.subject.name = "Al-Quran & Tafsir";
        list.add(c1);

        AcademyDtos.CourseRowDto c2 = new AcademyDtos.CourseRowDto();
        c2.courseId = "c0000001-0000-0000-0000-000000000001";
        c2.title = "Foundations of Islamic Aqeedah";
        c2.description = "A structured introduction to the six pillars of Iman, Tawheed, and core Islamic beliefs according to Ahlus Sunnah wal-Jama'ah.";
        c2.language = "en";
        c2.level = "beginner";
        c2.estimatedHours = 8;
        c2.lessonCount = 5;
        c2.instructorDisplayName = "Shaykh Abdullah Mansoor";
        c2.subject = new AcademyDtos.SubjectRefDto();
        c2.subject.slug = "cat_aqeedah";
        c2.subject.name = "Aqeedah";
        list.add(c2);

        AcademyDtos.CourseRowDto c3 = new AcademyDtos.CourseRowDto();
        c3.courseId = "c0000003-0000-0000-0000-000000000003";
        c3.title = "Fiqh of Taharah & Salah";
        c3.description = "Step-by-step practical guide to purification (Wudu, Ghusl, Tayammum) and the rulings, conditions, and pillars of daily Salah.";
        c3.language = "en";
        c3.level = "beginner";
        c3.estimatedHours = 10;
        c3.lessonCount = 8;
        c3.instructorDisplayName = "Mufti Bilal Khan";
        c3.subject = new AcademyDtos.SubjectRefDto();
        c3.subject.slug = "cat_fiqh";
        c3.subject.name = "Fiqh";
        list.add(c3);

        AcademyDtos.CourseRowDto c4 = new AcademyDtos.CourseRowDto();
        c4.courseId = "c0000002-0000-0000-0000-000000000002";
        c4.title = "Quranic Arabic: Level 1 Basics";
        c4.description = "Learn essential Quranic vocabulary, basic grammar (Nahw), and morphological patterns to understand the Quran directly.";
        c4.language = "en";
        c4.level = "beginner";
        c4.estimatedHours = 12;
        c4.lessonCount = 10;
        c4.instructorDisplayName = "Ustadh Tariq Al-Hashimi";
        c4.subject = new AcademyDtos.SubjectRefDto();
        c4.subject.slug = "cat_arabic_academy";
        c4.subject.name = "Quranic Arabic";
        list.add(c4);

        AcademyDtos.CourseRowDto c5 = new AcademyDtos.CourseRowDto();
        c5.courseId = "c0000004-0000-0000-0000-000000000004";
        c5.title = "Seerah: Life of Prophet Muhammad ﷺ";
        c5.description = "A comprehensive journey through the Makkan and Madinan eras of the Messenger of Allah ﷺ and lessons for contemporary life.";
        c5.language = "en";
        c5.level = "intermediate";
        c5.estimatedHours = 15;
        c5.lessonCount = 12;
        c5.instructorDisplayName = "Dr. Umar Farooq";
        c5.subject = new AcademyDtos.SubjectRefDto();
        c5.subject.slug = "cat_history_seerah";
        c5.subject.name = "Seerah & History";
        list.add(c5);

        return list;
    }

    @NonNull
    public static AcademyDtos.CourseDetailDto defaultCourseDetail(@Nullable String courseId) {
        List<AcademyDtos.CourseRowDto> defaults = defaultCourses();
        AcademyDtos.CourseRowDto matched = null;
        for (AcademyDtos.CourseRowDto c : defaults) {
            if (c.courseId.equalsIgnoreCase(courseId)
                    || (courseId != null && ((courseId.contains("105") && c.courseId.endsWith("05"))
                    || (courseId.contains("101") && c.courseId.endsWith("01"))
                    || (courseId.contains("102") && c.courseId.endsWith("02"))
                    || (courseId.contains("103") && c.courseId.endsWith("03"))
                    || (courseId.contains("104") && c.courseId.endsWith("04"))))) {
                matched = c;
                break;
            }
        }
        if (matched == null && !defaults.isEmpty()) {
            matched = defaults.get(0);
        }

        AcademyDtos.CourseDetailDto detail = new AcademyDtos.CourseDetailDto();
        detail.courseId = matched != null ? matched.courseId : courseId;
        detail.title = matched != null ? matched.title : "Islamic Studies Course";
        detail.description = matched != null ? matched.description : "";
        detail.language = matched != null ? matched.language : "en";
        detail.level = matched != null ? matched.level : "beginner";
        detail.subject = matched != null ? matched.subject : null;
        detail.estimatedHours = matched != null ? matched.estimatedHours : 6;
        detail.instructorDisplayName = matched != null ? matched.instructorDisplayName : "Verified Scholar";
        detail.instructorCredentialsNote = "Authorized Islamic Scholar";

        List<AcademyDtos.ChapterPublicDto> chapters = new ArrayList<>();
        AcademyDtos.ChapterPublicDto ch1 = new AcademyDtos.ChapterPublicDto();
        ch1.chapterId = "ch-1";
        ch1.title = "Chapter 1: Foundational Principles";
        ch1.description = "Essential core concepts and primary proofs from Quran and Sunnah";

        List<AcademyDtos.LessonPublicDto> lessons = new ArrayList<>();
        AcademyDtos.LessonPublicDto l1 = new AcademyDtos.LessonPublicDto();
        l1.lessonId = "l-1";
        l1.title = "Lesson 1: Introduction and Core Proofs";
        l1.lessonType = "text";
        l1.durationMinutes = 15;
        l1.sortOrder = 1;
        l1.bodyText = (matched != null ? matched.description : "Knowledge in Islam is illuminated by divine revelation.")
                + "\n\nSeeking authentic Islamic knowledge is an obligation upon every believer. In this foundational module, students examine primary evidences, definitions, and proper etiquette for spiritual and intellectual elevation.";
        lessons.add(l1);

        AcademyDtos.LessonPublicDto l2 = new AcademyDtos.LessonPublicDto();
        l2.lessonId = "l-2";
        l2.title = "Lesson 2: Practical Application & Reflection";
        l2.lessonType = "text";
        l2.durationMinutes = 20;
        l2.sortOrder = 2;
        l2.bodyText = "True comprehension translates into sincere righteous action. Reflect upon these teachings and incorporate consistent daily remembrance, sincere worship, and high moral character in all aspects of daily life.";
        lessons.add(l2);

        ch1.lessons = lessons;
        chapters.add(ch1);

        AcademyDtos.ChapterPublicDto ch2 = new AcademyDtos.ChapterPublicDto();
        ch2.chapterId = "ch-2";
        ch2.title = "Chapter 2: In-Depth Study & Comprehension";
        ch2.description = "Deep dive into classical texts, juristic opinions, and comprehension checks.";

        List<AcademyDtos.LessonPublicDto> lessons2 = new ArrayList<>();
        AcademyDtos.LessonPublicDto l3 = new AcademyDtos.LessonPublicDto();
        l3.lessonId = "l-3";
        l3.title = "Lesson 3: Comprehensive Review & Key Takeaways";
        l3.lessonType = "text";
        l3.durationMinutes = 25;
        l3.sortOrder = 3;
        l3.bodyText = "Review of the fundamental rules and synthesis of concepts covered in the previous lessons. Completing this section unlocks the chapter assessment quiz.";
        lessons2.add(l3);
        ch2.lessons = lessons2;
        chapters.add(ch2);

        detail.chapters = chapters;
        return detail;
    }

    @NonNull
    public static List<AcademyDtos.DeckRowDto> defaultDecks() {
        List<AcademyDtos.DeckRowDto> list = new ArrayList<>();

        AcademyDtos.DeckRowDto d1 = new AcademyDtos.DeckRowDto();
        d1.deckId = "f0000001-0000-0000-0000-000000000001";
        d1.title = "99 Names of Allah (Asma ul-Husna)";
        d1.description = "Memorize the beautiful names of Allah with their Arabic script and English meanings.";
        d1.language = "en";
        d1.subjectSlug = "cat_aqeedah";
        d1.cardCount = 10;
        d1.ownerType = "admin";
        d1.publishState = "published";
        list.add(d1);

        AcademyDtos.DeckRowDto d2 = new AcademyDtos.DeckRowDto();
        d2.deckId = "f0000002-0000-0000-0000-000000000002";
        d2.title = "Essential Daily Duas";
        d2.description = "Authentic supplications from Hisnul Muslim for morning, evening, sleep, and meals.";
        d2.language = "en";
        d2.subjectSlug = "cat_duas";
        d2.cardCount = 3;
        d2.ownerType = "admin";
        d2.publishState = "published";
        list.add(d2);

        AcademyDtos.DeckRowDto d3 = new AcademyDtos.DeckRowDto();
        d3.deckId = "f0000003-0000-0000-0000-000000000003";
        d3.title = "Foundational 40 Hadith of An-Nawawi";
        d3.description = "Core Hadiths embodying the fundamental principles of Islamic faith and character.";
        d3.language = "en";
        d3.subjectSlug = "cat_hadith";
        d3.cardCount = 5;
        d3.ownerType = "admin";
        d3.publishState = "published";
        list.add(d3);

        return list;
    }

    @NonNull
    public static AcademyDtos.DeckDetailDto defaultDeckDetail(@Nullable String deckId) {
        AcademyDtos.DeckDetailDto dto = new AcademyDtos.DeckDetailDto();
        dto.deckId = deckId != null ? deckId : "f0000001-0000-0000-0000-000000000001";

        List<AcademyDtos.CardDto> cards = new ArrayList<>();
        if (deckId != null && deckId.contains("02")) {
            dto.title = "Essential Daily Duas";
            dto.description = "Authentic supplications from Hisnul Muslim for morning, evening, sleep, and meals.";
            dto.language = "en";
            dto.subjectSlug = "cat_duas";
            dto.ownerType = "admin";
            dto.publishState = "published";

            addCard(cards, "c-1", "Dua upon waking up\n\nالْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ",
                    "All praise is for Allah, who gave us life after giving us death, and unto Him is the resurrection. (Sahih al-Bukhari)", 1);
            addCard(cards, "c-2", "Dua before sleeping\n\nبِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا",
                    "In Your name, O Allah, I die and I live. (Sahih al-Bukhari)", 2);
            addCard(cards, "c-3", "Sayyid al-Istighfar\n\nاللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ خَلَقْتَنِي وَأَنَا عَبْدُكَ...",
                    "The master supplication for seeking forgiveness from Allah for sins. (Sahih al-Bukhari)", 3);
        } else if (deckId != null && deckId.contains("03")) {
            dto.title = "Foundational 40 Hadith of An-Nawawi";
            dto.description = "Core Hadiths embodying the fundamental principles of Islamic faith and character.";
            dto.language = "en";
            dto.subjectSlug = "cat_hadith";
            dto.ownerType = "admin";
            dto.publishState = "published";

            addCard(cards, "c-1", "Hadith 1: Actions are by Intentions\n\nإِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ",
                    "Actions are judged by motives, and every person will have only what they intended. (Sahih al-Bukhari & Muslim)", 1);
            addCard(cards, "c-2", "Hadith 2: Islam, Iman, and Ihsan (Hadith Jibreel)",
                    "Islam is the 5 pillars, Iman is the 6 articles of faith, and Ihsan is worshipping Allah as if you see Him. (Sahih Muslim)", 2);
        } else {
            dto.title = "99 Names of Allah (Asma ul-Husna)";
            dto.description = "Memorize the beautiful names of Allah with their Arabic script and English meanings.";
            dto.language = "en";
            dto.subjectSlug = "cat_aqeedah";
            dto.ownerType = "admin";
            dto.publishState = "published";

            addCard(cards, "c-1", "Ar-Rahman (الرَّحْمَٰنُ)", "The Entirely Merciful — possessing all-encompassing mercy for all creation in this world.", 1);
            addCard(cards, "c-2", "Ar-Raheem (الرَّحِيمُ)", "The Especially Merciful — bestowing specific mercy upon the believers on the Day of Judgment.", 2);
            addCard(cards, "c-3", "Al-Malik (الْمَلِكُ)", "The Sovereign Lord — the supreme ruler and absolute owner of the universe.", 3);
            addCard(cards, "c-4", "Al-Quddus (الْقُدُّوسُ)", "The Most Holy — pure from any imperfection, defect, or partner.", 4);
            addCard(cards, "c-5", "As-Salam (السَّلَامُ)", "The Source of Peace — free from every flaw, and the giver of safety and tranquility.", 5);
            addCard(cards, "c-6", "Al-Mu'min (الْمُؤْمِنُ)", "The Granter of Security — who confirms the truth of His promises and protects believers.", 6);
            addCard(cards, "c-7", "Al-Muhaymin (الْمُهَيْمِنُ)", "The Ever-Watching Protector — who oversees, protects, and witnesses all actions.", 7);
            addCard(cards, "c-8", "Al-Aziz (الْعَزِيزُ)", "The All-Mighty — the invincible whom nothing can overcome or overpower.", 8);
            addCard(cards, "c-9", "Al-Jabbar (الْجَبَّارُ)", "The Compeller — the Restorer of broken hearts and the supreme authority.", 9);
            addCard(cards, "c-10", "Al-Mutakabbir (الْمُتَكَبِّرُ)", "The Supreme — transcendent in greatness beyond all created things.", 10);
        }
        dto.cards = cards;
        return dto;
    }

    private static void addCard(List<AcademyDtos.CardDto> list, String id, String front, String back, int order) {
        AcademyDtos.CardDto c = new AcademyDtos.CardDto();
        c.cardId = id;
        c.frontText = front;
        c.backText = back;
        c.sortOrder = order;
        list.add(c);
    }

    // ================= RESEARCH, SCHOLARS & FATWAS CATALOG =================

    @NonNull
    public static List<AcademyDtos.TopicDto> defaultResearchTopics() {
        List<AcademyDtos.TopicDto> list = new ArrayList<>();
        list.add(createTopic("all", "All Topics", "الكل", 0));
        list.add(createTopic("quran", "Quranic Sciences", "علوم القرآن", 1));
        list.add(createTopic("hadith", "Hadith & Sunnah", "الحديث والسنة", 2));
        list.add(createTopic("fiqh", "Islamic Jurisprudence", "الفقه الإسلامي", 3));
        list.add(createTopic("usul_al_fiqh", "Usul al-Fiqh", "أصول الفقه", 4));
        list.add(createTopic("aqeedah", "Islamic Aqeedah", "العقيدة الإسلامية", 5));
        list.add(createTopic("seerah", "Seerah of Prophet ﷺ", "السيرة النبوية", 6));
        list.add(createTopic("islamic_history", "Islamic Civilization", "التاريخ والحضارة", 7));
        list.add(createTopic("arabic_language", "Arabic Philology", "اللغة والأدب", 8));
        list.add(createTopic("islamic_finance", "Islamic Finance", "المعاملات المالية", 9));
        list.add(createTopic("family", "Family & Society", "الأسرة والمجتمع", 10));
        return list;
    }

    private static AcademyDtos.TopicDto createTopic(String slug, String name, String nameAr, int order) {
        AcademyDtos.TopicDto t = new AcademyDtos.TopicDto();
        t.slug = slug;
        t.name = name;
        t.nameAr = nameAr;
        t.sortOrder = order;
        t.status = "active";
        return t;
    }

    @NonNull
    public static List<AcademyDtos.ArticleRowDto> defaultResearchArticles(@Nullable String topicFilter) {
        List<AcademyDtos.ArticleRowDto> all = new ArrayList<>();

        AcademyDtos.ArticleRowDto a1 = new AcademyDtos.ArticleRowDto();
        a1.articleId = "20000000-0000-0000-0000-000000000001";
        a1.title = "Methodology of Hadith Authentication: Rigorous Standards of the Muhaddithin";
        a1.titleAr = "منهجية توثيق الحديث الشريف عند أئمة الحديث";
        a1.topic = new AcademyDtos.ArticleTopicDto();
        a1.topic.slug = "hadith";
        a1.topic.name = "Hadith & Sunnah";
        a1.sourceTier = "secondary";
        a1.status = "published";
        a1.language = "en";
        a1.authorDisplayName = "Dr. Muhammad Al-Azhari";
        a1.summary = "An analytical study of the five criteria of hadith authenticity (Ittisal as-Sanad, Adalah, Dabt, Adam ash-Shudhudh, Adam al-Illah) and the empirical precision developed by classical scholars.";
        a1.readingMinutes = 8;
        a1.updatedAt = "2026-11-09";
        all.add(a1);

        AcademyDtos.ArticleRowDto a2 = new AcademyDtos.ArticleRowDto();
        a2.articleId = "20000000-0000-0000-0000-000000000002";
        a2.title = "The Maqasid (Higher Objectives) of Islamic Shariah";
        a2.titleAr = "مقاصد الشريعة الإسلامية ومفهوم المصلحة العامة";
        a2.topic = new AcademyDtos.ArticleTopicDto();
        a2.topic.slug = "usul_al_fiqh";
        a2.topic.name = "Usul al-Fiqh";
        a2.sourceTier = "secondary";
        a2.status = "published";
        a2.language = "en";
        a2.authorDisplayName = "Shaykh Dr. Ahmad Al-Madani";
        a2.summary = "Exploration of the essential objectives of divine legislation formulated by Imam al-Shatibi and Imam al-Ghazali: preservation of Religion, Life, Intellect, Lineage, and Wealth.";
        a2.readingMinutes = 10;
        a2.updatedAt = "2026-11-09";
        all.add(a2);

        AcademyDtos.ArticleRowDto a3 = new AcademyDtos.ArticleRowDto();
        a3.articleId = "20000000-0000-0000-0000-000000000003";
        a3.title = "Tafsir Methodology: Principles for Understanding the Noble Quran";
        a3.titleAr = "قواعد وأصول في تفسير القرآن العظيم";
        a3.topic = new AcademyDtos.ArticleTopicDto();
        a3.topic.slug = "quran";
        a3.topic.name = "Quranic Sciences";
        a3.sourceTier = "primary";
        a3.status = "published";
        a3.language = "en";
        a3.authorDisplayName = "Dr. Fatima Al-Zahra";
        a3.summary = "A comprehensive treatise on the legitimate ranks of exegesis: Quran by Quran, Quran by authentic Sunnah, sayings of the Companions, and classical Arabic linguistic conventions.";
        a3.readingMinutes = 7;
        a3.updatedAt = "2026-11-09";
        all.add(a3);

        AcademyDtos.ArticleRowDto a4 = new AcademyDtos.ArticleRowDto();
        a4.articleId = "20000000-0000-0000-0000-000000000004";
        a4.title = "Foundational Principles of Islamic Ethical Governance";
        a4.titleAr = "أصول الحكم الراشد والعدالة الاجتماعية في التاريخ الإسلامي";
        a4.topic = new AcademyDtos.ArticleTopicDto();
        a4.topic.slug = "islamic_history";
        a4.topic.name = "Islamic Civilization";
        a4.sourceTier = "editorial";
        a4.status = "published";
        a4.language = "en";
        a4.authorDisplayName = "Editorial Scholarly Board";
        a4.summary = "An editorial analysis examining the constitutional compact of the Sahifah of Madinah, accountability (Shura), social welfare, and institutional checks in early Islamic administration.";
        a4.readingMinutes = 6;
        a4.updatedAt = "2026-11-09";
        all.add(a4);

        AcademyDtos.ArticleRowDto a5 = new AcademyDtos.ArticleRowDto();
        a5.articleId = "20000000-0000-0000-0000-000000000005";
        a5.title = "The Comprehensive Concept of Tawheed in Islamic Theology";
        a5.titleAr = "مفهوم التوحيد الشامل وأقسامه في العقيدة الإسلامية";
        a5.topic = new AcademyDtos.ArticleTopicDto();
        a5.topic.slug = "aqeedah";
        a5.topic.name = "Islamic Aqeedah";
        a5.sourceTier = "primary";
        a5.status = "published";
        a5.language = "en";
        a5.authorDisplayName = "Dr. Muhammad Al-Azhari";
        a5.summary = "A scholarly exposition of the foundational doctrine of Monotheism: Tawheed ar-Rububiyyah, Tawheed al-Uluhiyyah, and Tawheed al-Asma was-Sifat.";
        a5.readingMinutes = 9;
        a5.updatedAt = "2026-11-09";
        all.add(a5);

        AcademyDtos.ArticleRowDto a6 = new AcademyDtos.ArticleRowDto();
        a6.articleId = "20000000-0000-0000-0000-000000000006";
        a6.title = "Principles of Islamic Commercial Law: Contemporary Transactions & Ethical Finance";
        a6.titleAr = "أصول فقه المعاملات المالية المعاصرة ومبادئ الاقتصاد الإسلامي";
        a6.topic = new AcademyDtos.ArticleTopicDto();
        a6.topic.slug = "fiqh";
        a6.topic.name = "Islamic Jurisprudence";
        a6.sourceTier = "primary";
        a6.status = "published";
        a6.language = "en";
        a6.authorDisplayName = "Shaykh Dr. Ahmad Al-Madani";
        a6.summary = "An in-depth jurisprudential study on contract mechanics, prohibition of Gharar (uncertainty) and Riba (usury), and contemporary Islamic financial instruments.";
        a6.readingMinutes = 11;
        a6.updatedAt = "2026-11-09";
        all.add(a6);

        AcademyDtos.ArticleRowDto a7 = new AcademyDtos.ArticleRowDto();
        a7.articleId = "20000000-0000-0000-0000-000000000007";
        a7.title = "The Diplomatic Letters and Treaties in the Prophetic Seerah";
        a7.titleAr = "الوثائق النبوية والعهود الدبلوماسية في السيرة الشريفة";
        a7.topic = new AcademyDtos.ArticleTopicDto();
        a7.topic.slug = "seerah";
        a7.topic.name = "Seerah of the Prophet ﷺ";
        a7.sourceTier = "secondary";
        a7.status = "published";
        a7.language = "en";
        a7.authorDisplayName = "Dr. Muhammad Al-Azhari";
        a7.summary = "A historical and juridical analysis of the Treaty of Hudaybiyyah, the Constitution of Madinah, and the Prophetic letters to world leaders.";
        a7.readingMinutes = 9;
        a7.updatedAt = "2026-11-09";
        all.add(a7);

        AcademyDtos.ArticleRowDto a8 = new AcademyDtos.ArticleRowDto();
        a8.articleId = "20000000-0000-0000-0000-000000000008";
        a8.title = "Linguistic Precision and Rhetorical Structure in Classical Arabic";
        a8.titleAr = "الإعجاز البياني والدلالات اللغوية في اللسان العربي";
        a8.topic = new AcademyDtos.ArticleTopicDto();
        a8.topic.slug = "arabic_language";
        a8.topic.name = "Arabic Philology";
        a8.sourceTier = "secondary";
        a8.status = "published";
        a8.language = "en";
        a8.authorDisplayName = "Dr. Fatima Al-Zahra";
        a8.summary = "An academic exploration of classical Balaghah (rhetoric), semantic shifts, and syntactical precision in classical Arabic literature.";
        a8.readingMinutes = 8;
        a8.updatedAt = "2026-11-09";
        all.add(a8);

        AcademyDtos.ArticleRowDto a9 = new AcademyDtos.ArticleRowDto();
        a9.articleId = "20000000-0000-0000-0000-000000000009";
        a9.title = "Methodological Traditions of the Classical Islamic Compendia";
        a9.titleAr = "المصنفات الموسوعية ومنهج التدوين عند أئمة التراث";
        a9.topic = new AcademyDtos.ArticleTopicDto();
        a9.topic.slug = "classical_literature";
        a9.topic.name = "Classical Literature";
        a9.sourceTier = "editorial";
        a9.status = "published";
        a9.language = "en";
        a9.authorDisplayName = "Editorial Scholarly Board";
        a9.summary = "A survey of the major encyclopedic works in the Islamic golden age: compilation methodologies, indexing systems, and preservation of scholarly lineages.";
        a9.readingMinutes = 7;
        a9.updatedAt = "2026-11-09";
        all.add(a9);

        AcademyDtos.ArticleRowDto a10 = new AcademyDtos.ArticleRowDto();
        a10.articleId = "20000000-0000-0000-0000-000000000010";
        a10.title = "Comparative Jurisprudence: Roots of Scholarly Divergence Across the Madhahib";
        a10.titleAr = "الفقه المقارن وأسباب اختلاف الأئمة الفقهاء";
        a10.topic = new AcademyDtos.ArticleTopicDto();
        a10.topic.slug = "comparative_studies";
        a10.topic.name = "Comparative Studies";
        a10.sourceTier = "primary";
        a10.status = "published";
        a10.language = "en";
        a10.authorDisplayName = "Shaykh Dr. Ahmad Al-Madani";
        a10.summary = "An analytical examination of Ikhtilaf (scholarly divergence) based on linguistic interpretation, hadith reach, and juristic maxims (Qawa'id Fiqhiyyah).";
        a10.readingMinutes = 12;
        a10.updatedAt = "2026-11-09";
        all.add(a10);

        if (topicFilter == null || topicFilter.isEmpty() || "all".equalsIgnoreCase(topicFilter)) {
            return all;
        }
        List<AcademyDtos.ArticleRowDto> filtered = new ArrayList<>();
        for (AcademyDtos.ArticleRowDto a : all) {
            if (a.topic != null && (topicFilter.equalsIgnoreCase(a.topic.slug)
                    || a.topic.name.toLowerCase().contains(topicFilter.toLowerCase())
                    || topicFilter.toLowerCase().contains(a.topic.name.toLowerCase()))) {
                filtered.add(a);
            }
        }
        return filtered;
    }

    @NonNull
    public static AcademyDtos.ArticleDetailDto defaultArticleDetail(@Nullable String articleId) {
        AcademyDtos.ArticleDetailDto d = new AcademyDtos.ArticleDetailDto();
        d.articleId = articleId != null ? articleId : "20000000-0000-0000-0000-000000000001";
        d.language = "en";
        d.status = "published";
        d.publishedAt = "November 9, 2026";
        d.topic = new AcademyDtos.ArticleTopicDto();
        List<AcademyDtos.CitationDto> citations = new ArrayList<>();

        if (articleId != null && articleId.endsWith("02")) {
            d.title = "The Maqasid (Higher Objectives) of Islamic Shariah";
            d.titleAr = "مقاصد الشريعة الإسلامية ومفهوم المصلحة العامة";
            d.sourceTier = "secondary";
            d.authorDisplayName = "Shaykh Dr. Ahmad Al-Madani";
            d.readingMinutes = 10;
            d.topic.slug = "usul_al_fiqh";
            d.topic.name = "Usul al-Fiqh";
            d.summary = "Exploration of the essential objectives of divine legislation formulated by Imam al-Shatibi and Imam al-Ghazali: preservation of Religion, Life, Intellect, Lineage, and Wealth.";
            d.body = "### The Philosophical Grounding of Shariah Rulings\n\nIslamic jurisprudence is not a mechanical aggregation of arbitrary injunctions; rather, every divine command and prohibition is oriented toward realizing human welfare (Maslahah) and averting harm (Mafsadah) in both this worldly life and the Hereafter.\n\n### The Five Inviolable Necessities (Ad-Daruriyyat al-Khams)\n\nClassical jurists, notably Imam Abu Hamid al-Ghazali (d. 505 AH) in Al-Mustasfa and Imam al-Shatibi (d. 790 AH) in Al-Muwafaqat, systematically categorized the universal objectives into five foundational necessities:\n\n1. Preservation of Faith (Hifdh ad-Deen): Enacted through the protection of core creed, freedom of worship, and performance of obligations.\n2. Preservation of Human Life (Hifdh an-Nafs): Manifested in the absolute sanctity of life, prohibition of murder, and mandates for bodily sustenance and medical care.\n3. Preservation of Intellect (Hifdh al-Aql): Safeguarded through education, contemplation, and the absolute prohibition of intoxicants and corrupting substances.\n4. Preservation of Lineage and Honour (Hifdh an-Nasl wal-Ird): Protected through lawful marriage, family cohesion, and severe prohibition of slander and adultery.\n5. Preservation of Property (Hifdh al-Mal): Upheld through property rights, fair contracts, prohibition of usury (Riba), theft, and fraud.\n\n### The Three Tiers of Welfare\n\nThe jurists further structured Maslahah into three hierarchical levels:\n- Necessities (Daruriyyat): Elements without which societal life disintegrates into chaos.\n- Needs (Hajiyyat): Provisions that alleviate hardship and promote ease, such as concessions during illness or travel.\n- Embellishments (Tahsiniyyat): Noble manners, aesthetic purity, and refinement of character.\n\n### Application in Contemporary Islamic Legal Reasoning\n\nUnderstanding Maqasid provides modern scholars with the tools for Ijtihad when confronting novel bioethical, economic, and technological questions, ensuring that new rulings harmonize with the overarching wisdom of the Quran and Sunnah.";

            addCitation(citations, 1, "external", "Al-Shatibi, Al-Muwafaqat fi Usul ash-Shariah", "The primary intent of Shariah is the realization of welfare for creation.", "Foundational text on Maqasid theory");
            addCitation(citations, 2, "external", "Al-Ghazali, Al-Mustasfa min Ilm al-Usul", "The order of necessities begins with religion, then life, intellect, lineage, and wealth.", "Classical hierarchy of necessities");
        } else if (articleId != null && articleId.endsWith("03")) {
            d.title = "Tafsir Methodology: Principles for Understanding the Noble Quran";
            d.titleAr = "قواعد وأصول في تفسير القرآن العظيم";
            d.sourceTier = "primary";
            d.authorDisplayName = "Dr. Fatima Al-Zahra";
            d.readingMinutes = 7;
            d.topic.slug = "quran";
            d.topic.name = "Quranic Sciences";
            d.summary = "A comprehensive treatise on the legitimate ranks of exegesis: Quran by Quran, Quran by authentic Sunnah, sayings of the Companions, and classical Arabic linguistic conventions.";
            d.body = "### Introduction: The Authority of the Divine Word\n\nThe Noble Quran is the direct speech of Allah, revealed in a clear Arabic tongue. Approaching its interpretation requires disciplined adherence to established hermeneutical principles to guard against subjective speculation (Tafsir bir-Ra'y al-Madhmum).\n\n### The Primary Hierarchy of Exegesis (Tafsir bil-Ma'thur)\n\nImam Ibn Kathir and Shaykh al-Islam Ibn Taymiyyah outline the soundest methodological ranking as follows:\n\n1. Exegesis of Quran by Quran: What is mentioned in brief in one verse is elaborated in detail elsewhere. For example, the 'Path of those upon whom You have bestowed favor' (Surah Al-Fatihah 1:7) is explicitly elucidated in Surah An-Nisa (4:69) as the prophets, the truthful, the martyrs, and the righteous.\n2. Exegesis by the Authentic Sunnah: The Messenger of Allah ﷺ was charged by Allah with explaining the revelation: 'And We sent down to you the message that you may make clear to the people what was sent down to them' (Surah An-Nahl 16:44).\n3. The Explanations of the Sahabah (Companions): Specifically scholars among them such as Abdullah ibn Abbas (the Translator of the Quran), Abdullah ibn Mas'ud, and Ali ibn Abi Talib, who witnessed the circumstances of revelation (Asbab an-Nuzul).\n4. The Understanding of the Tabi'in (Successors): The premier students of the Companions in Makkah, Madinah, and Kufah.\n5. Classical Arabic Linguistics: Interpreting words according to their pristine linguistic usage during the revelation era without anachronistic modern impositions.";

            addQuranCitation(citations, 1, 4, 59, "O you who have believed, obey Allah and obey the Messenger and those in authority among you...", "Textual basis for hermeneutical authority");
            addQuranCitation(citations, 2, 16, 44, "And We sent down to you the message that you may make clear to the people what was sent down to them.", "Proof for prophetic explanation of the Quran");
        } else if (articleId != null && articleId.endsWith("04")) {
            d.title = "Foundational Principles of Islamic Ethical Governance";
            d.titleAr = "أصول الحكم الراشد والعدالة الاجتماعية في التاريخ الإسلامي";
            d.sourceTier = "editorial";
            d.authorDisplayName = "Editorial Scholarly Board";
            d.readingMinutes = 6;
            d.topic.slug = "islamic_history";
            d.topic.name = "Islamic Civilization";
            d.summary = "An editorial analysis examining the constitutional compact of the Sahifah of Madinah, accountability (Shura), social welfare, and institutional checks in early Islamic administration.";
            d.body = "### The Madinan Constitution: Blueprint for Pluralism and Rule of Law\n\nUpon his arrival in Madinah, the Prophet Muhammad ﷺ enacted the Sahifah (Constitution of Madinah), establishing a pioneering civic confederation founded upon mutual defense, justice, sanctity of life and property, and freedom of religious practice for Muslim and Jewish tribes alike.\n\n### Core Pillars of Islamic Public Administration\n\n1. Justice as an Absolute Mandate (Al-Adl): In Islam, justice is not a political expediency but a divine covenant. Allah commands: 'Indeed, Allah commands you to render trusts to whom they are due and when you judge between people to judge with justice' (Surah An-Nisa 4:58).\n2. Mutual Consultation (Ash-Shura): Collective governance requiring leadership to consult specialists and community representatives before enacting public policies.\n3. Fiduciary Accountability (Amanah): Public office is an onerous trust, not a vehicle for self-enrichment or nepotism.\n4. Institutional Welfare (Bayt al-Mal): Systematic distribution of resources ensuring that orphans, the impoverished, travelers, and the elderly receive dignified sustenance from public revenues.\n\n### Historical Lessons for Contemporary Institutions\n\nThe governance model of the Rightly Guided Caliphs demonstrates that ethical integrity, transparency, and accessible courts form the indispensable prerequisites for enduring peace and social cohesion.";

            addQuranCitation(citations, 1, 4, 58, "Indeed, Allah commands you to render trusts to whom they are due and when you judge between people to judge with justice.", "Constitutional mandate for judicial impartiality");
        } else if (articleId != null && articleId.endsWith("05")) {
            d.title = "The Comprehensive Concept of Tawheed in Islamic Theology";
            d.titleAr = "مفهوم التوحيد الشامل وأقسامه في العقيدة الإسلامية";
            d.sourceTier = "primary";
            d.authorDisplayName = "Dr. Muhammad Al-Azhari";
            d.readingMinutes = 9;
            d.topic.slug = "aqeedah";
            d.topic.name = "Islamic Aqeedah";
            d.summary = "A scholarly exposition of the foundational doctrine of Monotheism: Tawheed ar-Rububiyyah, Tawheed al-Uluhiyyah, and Tawheed al-Asma was-Sifat.";
            d.body = "### The Centrality of Tawheed\n\nTawheed (the absolute Oneness of Allah) is the bedrock of Islam and the core message communicated by every Prophet sent to humanity. It encompasses not merely acknowledging a supreme Creator, but singularly directing every act of devotion, hope, fear, and reliance to Allah alone.\n\n### The Three Interconnected Categories\n\nClassical scholars structured the understanding of divine unity into three non-negotiable dimensions:\n\n1. Tawheed ar-Rububiyyah (Oneness of Lordship): Affirming Allah alone as the Creator, Sustainer, Provider, and Controller of the cosmos.\n2. Tawheed al-Uluhiyyah (Oneness of Worship): Directing prayer, sacrifice, supplication (Dua), repentance, and vows solely to Allah. This was the primary axis of debate between the Messengers and their opponents.\n3. Tawheed al-Asma was-Sifat (Oneness of Names and Attributes): Affirming all names and descriptions that Allah and His Messenger have affirmed without resemblance to creation (Tashbeeh), denial (Ta'teel), or arbitrary interpretation (Ta'weel). Allah says: 'There is nothing like unto Him, and He is the Hearing, the Seeing' (Surah Ash-Shura 42:11).\n\n### Theological Implications for Human Dignity\n\nTrue monotheism liberates the human conscience from subservience to wealth, tyrants, and superstition, grounding the believer in profound spiritual peace and moral clarity.";

            addQuranCitation(citations, 1, 42, 11, "There is nothing like unto Him, and He is the Hearing, the Seeing.", "Foundational theological principle of transcendence");
            addQuranCitation(citations, 2, 112, 1, "Say: He is Allah, [who is] One. Allah, the Eternal Refuge...", "Complete articulation of Tawheed");
        } else if (articleId != null && articleId.endsWith("06")) {
            d.title = "Principles of Islamic Commercial Law: Contemporary Transactions & Ethical Finance";
            d.titleAr = "أصول فقه المعاملات المالية المعاصرة ومبادئ الاقتصاد الإسلامي";
            d.sourceTier = "primary";
            d.authorDisplayName = "Shaykh Dr. Ahmad Al-Madani";
            d.readingMinutes = 11;
            d.topic.slug = "fiqh";
            d.topic.name = "Islamic Jurisprudence";
            d.summary = "An in-depth jurisprudential study on contract mechanics, prohibition of Gharar (uncertainty) and Riba (usury), and contemporary Islamic financial instruments.";
            d.body = "### The Foundations of Islamic Commercial Jurisprudence (Fiqh al-Mu'amalat)\n\nIn classical Islamic jurisprudence, the default ruling governing all commercial transactions and contracts is permissibility (Al-Asl fil-Mu'amalat al-Ibahah), provided that specific divine prohibitions are not breached.\n\n### Core Inviolable Prohibitions in Islamic Finance\n\n1. Riba (Usury and Unjust Increment): Any predetermined surplus charged on a loan without legitimate consideration or risk-sharing is categorically forbidden in the Quran: 'Allah has permitted trade and forbidden usury' (Surah Al-Baqarah 2:275).\n2. Gharar (Excessive Ambiguity and Uncertainty): Contracts wherein the subject matter, delivery date, or price are indeterminate or outside the control of the parties are invalid.\n3. Maysir and Qimar (Gambling and Speculative Chance): Financial arrangements dependent upon zero-sum chance rather than genuine productive economic utility.\n\n### Classical Contracts and Contemporary Applications\n\nClassical contracts provide robust foundations for ethical modern commerce:\n- Murabahah (Cost-Plus Sale): Transparent cost disclosure with agreed profit margin.\n- Mudarabah (Profit-Sharing Partnership): Capital provided by an investor while expertise and labor are provided by an entrepreneur.\n- Musharakah (Equity Joint Venture): Shared capital, proportional profit distribution, and equity risk allocation.\n- Ijarah (Lease and Hire Purchase): Transfer of usufruct without transferring immediate asset ownership.\n\n### Conclusion\n\nIslamic finance is inherently tied to asset-backed, ethical real-economy activities, creating resilient financial systems free from speculative excess.";

            addQuranCitation(citations, 1, 2, 275, "Allah has permitted trade and forbidden usury.", "Constitutional prohibition of Riba in financial contracts");
            addCitation(citations, 2, "external", "Ibn Rushd, Bidayat al-Mujtahid wa Nihayat al-Muqtasid", "The causes of contract nullification are four: Riba, Gharar, unlawful goods, and coercion.", "Standard classical synthesis of financial prohibitions");
        } else {
            d.title = "Methodology of Hadith Authentication: Rigorous Standards of the Muhaddithin";
            d.titleAr = "منهجية توثيق الحديث الشريف عند أئمة الحديث";
            d.sourceTier = "secondary";
            d.authorDisplayName = "Dr. Muhammad Al-Azhari";
            d.readingMinutes = 8;
            d.topic.slug = "hadith";
            d.topic.name = "Hadith & Sunnah";
            d.summary = "An analytical study of the five criteria of hadith authenticity (Ittisal as-Sanad, Adalah, Dabt, Adam ash-Shudhudh, Adam al-Illah) and the empirical precision developed by classical scholars.";
            d.body = "### Introduction: The Science of Transmission\n\nThe preservation of the prophetic tradition (Sunnah) stands as a unique intellectual and empirical monument in the history of human scholarship. The scholars of Hadith (Muhaddithin) formulated a comprehensive epistemological framework to evaluate both the chain of transmitters (Isnad) and the content of the transmission (Matn).\n\n### The Five Universal Conditions for Authenticity (Sahih)\n\nAccording to the classical consensus articulated by Imam Ibn al-Salah (d. 643 AH) in his Muqaddimah, a narration achieves the designation of Sahih (sound) only when five rigorous, concurrent criteria are met:\n\n1. Continuous Sanad (Ittisal as-Sanad): Every single narrator in the chain must have received the text directly from their immediate predecessor from beginning to end without interruption.\n2. Moral Integrity (Adalah): Each narrator must be an adult Muslim of upright character, free from open sinfulness, heresy, and violations of modesty.\n3. Mnemonic Accuracy (Dabt): The transmitter must demonstrate flawless preservation of the text, either through exact photographic memory (Dabt as-Sadr) or verified, preserved written codices (Dabt al-Kitab).\n4. Absence of Irregularity (Adam ash-Shudhudh): The narration must not contradict the transmission of another narrator who is superior in reliability or backed by an established majority.\n5. Freedom from Hidden Defects (Adam al-Illah): The report must be entirely free from subtle, concealed defects that only master critics can detect after collating all existing paths of transmission.\n\n### Rigorous Standards of Narrator Verification\n\nThe scholars instituted the discipline of Ilm ar-Rijal (Biographical Evaluation), documenting the birth dates, travels, teachers, students, memory retention, and life circumstances of over 100,000 transmitters. This guaranteed that spurious or fabricated reports were quarantined and catalogued.\n\n### Conclusion and Modern Significance\n\nThe methods established by Imam al-Bukhari, Imam Muslim, and their contemporaries predate modern historical-critical methodologies by centuries, providing a verified bedrock for Islamic legal and theological derivation.";

            addCitation(citations, 1, "external", "Sahih al-Bukhari, Kitab Bad al-Wahy", "Actions are judged by their intentions...", "Foundational hadith criterion on transmission");
            addCitation(citations, 2, "external", "Ibn al-Salah, Muqaddimat Ibn al-Salah fi Ulum al-Hadith", "The definition of Sahih hadith requires continuous transmission by upright, precise narrators.", "Standard classical criterion");
        }
        d.citations = citations;
        return d;
    }

    private static void addCitation(List<AcademyDtos.CitationDto> list, int id, String kind, String extRef, String quote, String note) {
        AcademyDtos.CitationDto c = new AcademyDtos.CitationDto();
        c.citationId = id;
        c.citationKind = kind;
        c.externalRef = extRef;
        c.quote = quote;
        c.note = note;
        list.add(c);
    }

    private static void addQuranCitation(List<AcademyDtos.CitationDto> list, int id, int surah, int ayah, String quote, String note) {
        AcademyDtos.CitationDto c = new AcademyDtos.CitationDto();
        c.citationId = id;
        c.citationKind = "quran_ayah";
        c.surahId = surah;
        c.ayahNumber = ayah;
        c.externalRef = "Surah " + surah + ":" + ayah;
        c.quote = quote;
        c.note = note;
        list.add(c);
    }

    @NonNull
    public static List<AcademyDtos.ScholarDto> defaultScholars() {
        List<AcademyDtos.ScholarDto> list = new ArrayList<>();

        AcademyDtos.ScholarDto s1 = new AcademyDtos.ScholarDto();
        s1.scholarId = "10000000-0000-0000-0000-000000000001";
        s1.name = "Dr. Muhammad Al-Azhari";
        s1.nameAr = "د. محمد الأزهري";
        s1.qualifications = "Ph.D. in Usul al-Hadith, Al-Azhar University; Faculty Member in Islamic Studies";
        s1.areasOfExpertise = "Hadith Sciences, Narrator Criticism (Ilm ar-Rijal), Comparative Fiqh";
        s1.biography = "Senior researcher in classical hadith verification methodologies and narrator criticism (Rijal). Serves as an academic fellow with over twenty published monographs on the textual integrity of the Sahihayn.";
        s1.verificationStatus = "verified";
        s1.verifiedAt = "2025-01-15";
        s1.articleCount = 14;
        s1.fatwaCount = 38;
        list.add(s1);

        AcademyDtos.ScholarDto s2 = new AcademyDtos.ScholarDto();
        s2.scholarId = "10000000-0000-0000-0000-000000000002";
        s2.name = "Shaykh Dr. Ahmad Al-Madani";
        s2.nameAr = "الشيخ د. أحمد المدني";
        s2.qualifications = "Doctorate in Shariah & Usul al-Fiqh, Islamic University of Madinah; Advisor in Islamic Banking";
        s2.areasOfExpertise = "Principles of Jurisprudence (Usul al-Fiqh), Islamic Banking, Financial Contracts";
        s2.biography = "Specialist in classical jurisprudential maxims (Qawa'id Fiqhiyyah) and their contemporary applications in ethical commerce, contracts, and digital finance.";
        s2.verificationStatus = "verified";
        s2.verifiedAt = "2025-02-20";
        s2.articleCount = 11;
        s2.fatwaCount = 52;
        list.add(s2);

        AcademyDtos.ScholarDto s3 = new AcademyDtos.ScholarDto();
        s3.scholarId = "10000000-0000-0000-0000-000000000003";
        s3.name = "Dr. Fatima Al-Zahra";
        s3.nameAr = "د. فاطمة الزهراء";
        s3.qualifications = "Ph.D. in Quranic Sciences & Classical Arabic Linguistics, Umm Al-Qura University";
        s3.areasOfExpertise = "Quranic Exegesis (Tafsir), Classical Arabic Philology, Family Ethics";
        s3.biography = "Author of seminal treatises examining thematic coherence in Makkan Surahs and structural analysis of Quranic rhetoric. Frequent speaker on Islamic family ethics and Quranic contemplation.";
        s3.verificationStatus = "verified";
        s3.verifiedAt = "2025-03-10";
        s3.articleCount = 9;
        s3.fatwaCount = 27;
        list.add(s3);

        return list;
    }

    @NonNull
    public static AcademyDtos.ScholarDto defaultScholarDetail(@Nullable String scholarId) {
        List<AcademyDtos.ScholarDto> scholars = defaultScholars();
        if (scholarId != null) {
            for (AcademyDtos.ScholarDto s : scholars) {
                if (scholarId.equalsIgnoreCase(s.scholarId)) {
                    return s;
                }
            }
        }
        return scholars.get(0);
    }

    @NonNull
    public static List<AcademyDtos.FatwaRowDto> defaultFatwas(@Nullable String query, @Nullable String category) {
        List<AcademyDtos.FatwaRowDto> all = new ArrayList<>();

        AcademyDtos.FatwaRowDto f1 = new AcademyDtos.FatwaRowDto();
        f1.fatwaId = "30000000-0000-0000-0000-000000000001";
        f1.question = "What is the ruling on combining prayers (Jam') during severe rainfall, heavy storms, or travel?";
        f1.category = "Salah";
        f1.scholarName = "Dr. Muhammad Al-Azhari";
        f1.publicationDate = "October 15, 2026";
        f1.answerExcerpt = "Combining Dhuhr with Asr, and Maghrib with Isha is an authentic concession (Rukhsah) during legal travel or severe hazardous storms.";
        all.add(f1);

        AcademyDtos.FatwaRowDto f2 = new AcademyDtos.FatwaRowDto();
        f2.fatwaId = "30000000-0000-0000-0000-000000000002";
        f2.question = "How should Zakat be calculated on modern financial assets, company shares, and retirement pension funds?";
        f2.category = "Zakat";
        f2.scholarName = "Shaykh Dr. Ahmad Al-Madani";
        f2.publicationDate = "October 22, 2026";
        f2.answerExcerpt = "Zakat of 2.5% is due annually on liquid cash and short-term trading stocks meeting Nisab; locked pension funds are assessed upon access.";
        all.add(f2);

        AcademyDtos.FatwaRowDto f3 = new AcademyDtos.FatwaRowDto();
        f3.fatwaId = "30000000-0000-0000-0000-000000000003";
        f3.question = "What are the rulings on fasting during Ramadan for pregnant women or those suffering from chronic medical conditions?";
        f3.category = "Fasting";
        f3.scholarName = "Dr. Fatima Al-Zahra";
        f3.publicationDate = "October 29, 2026";
        f3.answerExcerpt = "Chronic illness qualifies for permanent exemption with Fidyah; temporary illness and pregnancy require making up the missed days when able.";
        all.add(f3);

        AcademyDtos.FatwaRowDto f4 = new AcademyDtos.FatwaRowDto();
        f4.fatwaId = "30000000-0000-0000-0000-000000000004";
        f4.question = "What is the Islamic ruling regarding trading and investing in cryptocurrencies and digital tokens?";
        f4.category = "Islamic Finance";
        f4.scholarName = "Shaykh Dr. Ahmad Al-Madani";
        f4.publicationDate = "November 2, 2026";
        f4.answerExcerpt = "Requires genuine utility and absence of pure gambling speculation (Maysir) or interest yields (Riba); speculative trading is impermissible.";
        all.add(f4);

        AcademyDtos.FatwaRowDto f5 = new AcademyDtos.FatwaRowDto();
        f5.fatwaId = "30000000-0000-0000-0000-000000000005";
        f5.question = "What are the obligations regarding upholding family ties (Silat ar-Rahim) when relatives are hostile or unreciprocal?";
        f5.category = "Family";
        f5.scholarName = "Dr. Muhammad Al-Azhari";
        f5.publicationDate = "November 5, 2026";
        f5.answerExcerpt = "The Prophet ﷺ taught that true upholding of kinship ties is connecting with those who sever relations, while setting courteous boundaries against harm.";
        all.add(f5);

        List<AcademyDtos.FatwaRowDto> filtered = new ArrayList<>();
        for (AcademyDtos.FatwaRowDto f : all) {
            boolean matchesCat = (category == null || category.isEmpty() || "all".equalsIgnoreCase(category)
                    || category.equalsIgnoreCase(f.category));
            boolean matchesQ = (query == null || query.trim().isEmpty()
                    || (f.question != null && f.question.toLowerCase().contains(query.trim().toLowerCase()))
                    || (f.category != null && f.category.toLowerCase().contains(query.trim().toLowerCase()))
                    || (f.scholarName != null && f.scholarName.toLowerCase().contains(query.trim().toLowerCase())));
            if (matchesCat && matchesQ) {
                filtered.add(f);
            }
        }
        return filtered;
    }

    @NonNull
    public static AcademyDtos.FatwaDetailDto defaultFatwaDetail(@Nullable String fatwaId) {
        AcademyDtos.FatwaDetailDto d = new AcademyDtos.FatwaDetailDto();
        d.fatwaId = fatwaId != null ? fatwaId : "30000000-0000-0000-0000-000000000001";
        d.language = "en";
        d.review = new AcademyDtos.FatwaReviewDto();
        d.review.reviewDate = "2026-11-08";
        d.review.scholar = new AcademyDtos.ScholarDto();

        if (fatwaId != null && fatwaId.endsWith("02")) {
            d.question = "How should Zakat be calculated on modern financial assets, company shares, and retirement pension funds?";
            d.category = "Zakat";
            d.publicationDate = "October 22, 2026";
            d.review.scholar.name = "Shaykh Dr. Ahmad Al-Madani";
            d.review.scholar.qualifications = "Doctorate in Shariah & Usul al-Fiqh, Islamic University of Madinah";
            d.review.reviewNotes = "Conforms to standards established by the International Islamic Fiqh Academy.";
            d.answer = "Praise be to Allah.\n\nZakat is the third pillar of Islam and is payable on lawful wealth that meets the minimum threshold (Nisab) and remains in one's ownership for a full lunar year (Hawl).\n\n### 1. Liquid Cash and Bank Savings\nAll personal liquid funds, savings accounts, and accessible cash are subject to a flat 2.5% rate annually, provided the total net wealth equals or exceeds the Nisab (the cash equivalent of 85 grams of pure gold).\n\n### 2. Company Stocks and Equities\n- Short-Term Trading (Speculation): If shares were acquired with the intention of frequent resale for trading gains, Zakat of 2.5% is due on the full market value of the portfolio at the end of the Zakat year.\n- Long-Term Investment: If shares were bought for annual dividends without intent to liquidate, Zakat is due on the company's underlying zakatable current assets (cash and receivables), which typically equates to approximately 25-30% of market value, or 2.5% on the dividends received.\n\n### 3. Retirement & Pension Accounts (401k, IRA, Provident Funds)\n- If the contributor has full, penalty-free access to withdraw the balance, Zakat is paid annually on the accessible amount after subtracting anticipated mandatory early withdrawal taxes and statutory penalties.\n- If the funds are legally locked and inaccessible until retirement age, the majority of contemporary jurists (including the International Islamic Fiqh Academy) rule that no annual Zakat is required while the money is locked; rather, Zakat is paid once upon receiving the payout for one year.\n\nAnd Allah knows best.";
        } else if (fatwaId != null && fatwaId.endsWith("03")) {
            d.question = "What are the rulings on fasting during Ramadan for pregnant women or those suffering from chronic medical conditions?";
            d.category = "Fasting";
            d.publicationDate = "October 29, 2026";
            d.review.scholar.name = "Dr. Fatima Al-Zahra";
            d.review.scholar.qualifications = "Ph.D. in Quranic Sciences & Classical Arabic Linguistics, Umm Al-Qura University";
            d.review.reviewNotes = "Verified according to classical juristic consensus and contemporary medical ethics.";
            d.answer = "Praise be to Allah.\n\nAllah has made the religion easy and free of undue distress. In Surah Al-Baqarah (2:185), Allah explicitly declares: 'Allah intends for you ease and does not intend for you hardship.'\n\n### 1. Chronic or Permanent Illness\nIndividuals who suffer from serious chronic ailments (such as advanced diabetes requiring strict insulin intervals, severe kidney failure, or terminal conditions) where fasting would cause genuine medical harm, are permanently exempt upon the diagnosis of an honest medical physician.\n- Obligation: Such individuals do not make up the fast days (Qada); instead, they pay Fidyah — feeding one poor person for each day missed (estimated at approximately 1.5 kg of staple food or its cash equivalent per day).\n\n### 2. Temporary Illness\nThose who suffer from a temporary curable illness (such as severe infection, acute fever, or surgical recovery) break their fast and must make up the missed days day-for-day after Ramadan once they recover. No Fidyah is required.\n\n### 3. Pregnancy and Breastfeeding\nIf a pregnant or nursing mother genuinely fears for her own physical health or the health and development of her unborn child or infant, she is permitted to break her fast.\n- According to the majority of scholars, she makes up the missed days (Qada) when able without paying Fidyah.\n\nAnd Allah knows best.";
        } else if (fatwaId != null && fatwaId.endsWith("04")) {
            d.question = "What is the Islamic ruling regarding trading and investing in cryptocurrencies and digital tokens?";
            d.category = "Islamic Finance";
            d.publicationDate = "November 2, 2026";
            d.review.scholar.name = "Shaykh Dr. Ahmad Al-Madani";
            d.review.scholar.qualifications = "Doctorate in Shariah & Usul al-Fiqh, Islamic University of Madinah";
            d.review.reviewNotes = "Reviewed against AAOIFI and International Fiqh Academy guidelines on digital assets.";
            d.answer = "Praise be to Allah.\n\nThe Islamic legal assessment of cryptocurrencies (such as Bitcoin, Ethereum, and utility tokens) is a subject of active research among contemporary Shariah advisory boards, including the AAOIFI, the International Islamic Fiqh Academy, and national Fatwa councils.\n\n### Essential Shariah Determinants for Currencies and Assets:\n1. Mal Mutaqawwam (Legitimate Recognised Wealth): The asset must possess real customary utility and benefit that the Shariah acknowledges.\n2. Freedom from Excessive Uncertainty (Gharar Fahish): Contracts dominated by pure speculation where value fluctuates wildly without economic substance resemble gambling (Maysir).\n3. Absence of Usurious Schemes (Riba): Yield farming, algorithmic leverage, and synthetic derivatives that generate guaranteed interest-like returns on loans are strictly prohibited.\n\n### Current Scholarly Perspectives:\n- Strict Caution / Prohibition: A notable group of fatwa councils consider speculative crypto assets impermissible due to high anonymity facilitating unlawful trade, extreme volatility, and lack of sovereign backing.\n- Permissibility with Conditions: Other bodies consider tokens permissible if they function as genuine utility tokens or decentralized payment networks, provided the project is halal, compliant with financial laws, and not utilized for leveraged speculation.\n\nAnd Allah knows best.";
        } else if (fatwaId != null && fatwaId.endsWith("05")) {
            d.question = "What are the obligations regarding upholding family ties (Silat ar-Rahim) when relatives are hostile or unreciprocal?";
            d.category = "Family";
            d.publicationDate = "November 5, 2026";
            d.review.scholar.name = "Dr. Muhammad Al-Azhari";
            d.review.scholar.qualifications = "Ph.D. in Usul al-Hadith, Al-Azhar University";
            d.review.reviewNotes = "Approved by the Editorial Scholarly Committee.";
            d.answer = "Praise be to Allah.\n\nUpholding kinship ties (Silat ar-Rahim) is among the weightiest obligations in Islamic character. Allah has linked maintaining family ties with divine mercy, and severed relations with divine displeasure.\n\n### 1. The True Definition of Silat ar-Rahim\nThe Messenger of Allah ﷺ explicitly clarified that upholding ties is not merely reciprocating kindness when relatives treat you well. In Sahih al-Bukhari (5987), the Prophet ﷺ said:\n'The one who maintains ties of kinship is not the one who merely reciprocates. Rather, the one who maintains ties is the one who, when kinship ties are severed, connects them.'\n\n### 2. Dealing with Hostile or Strained Relatives\nA man came to the Prophet ﷺ and said: 'O Messenger of Allah, I have relatives with whom I try to maintain ties, but they cut me off; I treat them with kindness, but they treat me harshly; I am gentle with them, but they are hostile toward me.'\nThe Prophet ﷺ replied: 'If you are as you say, it is as though you are feeding them hot ashes, and Allah will remain your supporter against them as long as you continue upon this.' (Sahih Muslim, 2558).\n\n### 3. Practical Guidelines for Protection\n- One must maintain at least the baseline of connection: greeting them on Eid, inquiring after their health during illness, offering condolences in bereavement, and preventing malice.\n- If close physical proximity leads to verified emotional or physical abuse, one may maintain a respectful boundary while preserving courteous communication and avoiding severed relations (Qati'at ar-Rahim).\n\nAnd Allah knows best.";
        } else {
            d.question = "What is the ruling on combining prayers (Jam') during severe rainfall, heavy storms, or travel?";
            d.category = "Salah";
            d.publicationDate = "October 15, 2026";
            d.review.scholar.name = "Dr. Muhammad Al-Azhari";
            d.review.scholar.qualifications = "Ph.D. in Usul al-Hadith, Al-Azhar University";
            d.review.reviewNotes = "Approved after scholarly verification of weather and travel conditions.";
            d.answer = "Praise be to Allah.\n\nIt is authentically established from the Sunnah that combining two prayers — specifically Dhuhr with Asr, and Maghrib with Isha — is a legal concession (Rukhsah) granted by Allah to alleviate hardship for the Muslim community.\n\n### 1. During Travel (Safar)\nWhen traveling a distance that constitutes legal travel (generally 80 km or more according to majority scholars), a traveler may combine Dhuhr and Asr together (either at the time of the earlier prayer or the later prayer) and Maghrib and Isha together. Shortening four-rak'ah prayers to two rak'ahs (Qasr) is also strongly recommended during travel.\n\n### 2. During Severe Rain, Flood, or Hazardous Weather\nThe Prophet ﷺ permitted the people of Madinah to combine Maghrib and Isha at the mosque during intense rain that drenches clothing and makes walking to the congregation hazardous (Sahih al-Bukhari, Sahih Muslim).\n- This concession specifically applies to congregational prayer at the mosque to spare worshippers from excessive mud, extreme cold, or dangerous conditions.\n- Dhuhr and Asr may also be combined according to the Hanbali and Shafi'i schools if the rain presents severe difficulty.\n- Performing the prayer at home without hardship does not justify combining simply due to mild rain.\n\nAnd Allah knows best.";
        }
        return d;
    }
}
