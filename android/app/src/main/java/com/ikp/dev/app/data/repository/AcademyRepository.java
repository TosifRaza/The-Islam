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

    // ----- academy -----

    public Outcome<List<AcademyDtos.CourseRowDto>> courses(int page, int size) {
        // 1. Try backend API first (has enriched subject models and lesson counts)
        Outcome<List<AcademyDtos.CourseRowDto>> apiRes = run(api.courses(page, size));
        if (apiRes.isSuccess() && apiRes.value != null && !apiRes.value.isEmpty()) {
            return apiRes;
        }
        // 2. Fallback to Supabase cloud direct
        Outcome<List<AcademyDtos.CourseRowDto>> sbFallback = fetchCoursesFromSupabase();
        if (sbFallback != null && sbFallback.isSuccess() && sbFallback.value != null && !sbFallback.value.isEmpty()) {
            return sbFallback;
        }
        // 3. Fallback to embedded default catalog (offline-first & phone standalone resilience)
        return Outcome.ok(defaultCourses());
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
        Outcome<List<AcademyDtos.DeckRowDto>> apiRes = run(api.publicDecks(page, size));
        if (apiRes.isSuccess() && apiRes.value != null && !apiRes.value.isEmpty()) {
            return apiRes;
        }
        return Outcome.ok(defaultDecks());
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
}
