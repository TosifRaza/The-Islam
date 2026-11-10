package com.ikp.dev.app.ui.academy;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.data.repository.AcademyRepository;
import com.ikp.dev.app.data.repository.SubjectRepository;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Academy tab (Phases 1-3):
 * Full Islamic learning platform — course catalogue with category chips,
 * debounced search, continue-learning rail, flashcard decks, and daily lesson.
 */
public final class AcademyViewModel extends ViewModel {

    public static final class DailyLessonInfo {
        public final String title;
        public final String preview;
        public final String duration;
        @Nullable public final String courseId;
        @Nullable public final String lessonId;

        public DailyLessonInfo(String title, String preview, String duration,
                               @Nullable String courseId, @Nullable String lessonId) {
            this.title = title;
            this.preview = preview;
            this.duration = duration;
            this.courseId = courseId;
            this.lessonId = lessonId;
        }
    }

    public static final class AcademyData {
        public final List<AcademyDtos.CourseRowDto> courses;
        public final List<AcademyDtos.ContinueDto> continueLearning;
        public final List<AcademyDtos.WeakTopicDto> weakTopics;
        public final List<CategoryChipAdapter.ChipItem> categories;
        public final List<AcademyDtos.DeckRowDto> flashcards;
        public final DailyLessonInfo dailyLesson;
        public final boolean memberSections;
        @Nullable public final String activeCategorySlug;
        @Nullable public final String activeSearchQuery;

        public AcademyData(List<AcademyDtos.CourseRowDto> courses, List<AcademyDtos.ContinueDto> cont,
                    List<AcademyDtos.WeakTopicDto> weak, boolean memberSections) {
            this(courses, cont, weak, new ArrayList<>(), new ArrayList<>(), null, memberSections, null, null);
        }

        public AcademyData(List<AcademyDtos.CourseRowDto> courses,
                           List<AcademyDtos.ContinueDto> continueLearning,
                           List<AcademyDtos.WeakTopicDto> weakTopics,
                           List<CategoryChipAdapter.ChipItem> categories,
                           List<AcademyDtos.DeckRowDto> flashcards,
                           @Nullable DailyLessonInfo dailyLesson,
                           boolean memberSections,
                           @Nullable String activeCategorySlug,
                           @Nullable String activeSearchQuery) {
            this.courses = courses;
            this.continueLearning = continueLearning;
            this.weakTopics = weakTopics;
            this.categories = categories;
            this.flashcards = flashcards;
            this.dailyLesson = dailyLesson;
            this.memberSections = memberSections;
            this.activeCategorySlug = activeCategorySlug;
            this.activeSearchQuery = activeSearchQuery;
        }
    }

    private final MutableLiveData<UiState<AcademyData>> state = new MutableLiveData<>();
    private final AcademyRepository repository;
    private final SubjectRepository subjectRepository;

    private final List<AcademyDtos.CourseRowDto> rawCourses = new ArrayList<>();
    private final List<AcademyDtos.ContinueDto> rawContinue = new ArrayList<>();
    private final List<AcademyDtos.WeakTopicDto> rawWeak = new ArrayList<>();
    private final List<CategoryChipAdapter.ChipItem> rawCategories = new ArrayList<>();
    private final List<AcademyDtos.DeckRowDto> rawDecks = new ArrayList<>();
    private DailyLessonInfo rawDailyLesson;
    private boolean isMember = false;

    private String currentCategorySlug = null;
    private String currentSearchQuery = "";

    public AcademyViewModel() {
        this(ServiceLocator.academyRepository(), ServiceLocator.subjectRepository());
    }

    public AcademyViewModel(AcademyRepository repository) {
        this(repository, ServiceLocator.subjectRepository());
    }

    public AcademyViewModel(AcademyRepository repository, SubjectRepository subjectRepository) {
        this.repository = repository;
        this.subjectRepository = subjectRepository;
    }

    public LiveData<UiState<AcademyData>> state() {
        return state;
    }

    public void reload() {
        // 1. Instant local render: populate seed/cached data immediately so the UI is ready with ZERO delay
        if (rawCourses.isEmpty()) {
            rawCourses.addAll(repository.getCachedOrSeedCourses());
        }
        if (rawCategories.isEmpty()) {
            rawCategories.add(new CategoryChipAdapter.ChipItem(null, "All"));
            for (Models.Subject s : SubjectRepository.defaultSubjects()) {
                rawCategories.add(new CategoryChipAdapter.ChipItem(s.slug, s.name));
            }
        }
        if (rawDecks.isEmpty()) {
            rawDecks.addAll(repository.getCachedOrSeedDecks());
        }
        if (rawDailyLesson == null) {
            String firstCourseId = !rawCourses.isEmpty() ? rawCourses.get(0).courseId : "c0000001-0000-0000-0000-000000000001";
            rawDailyLesson = new DailyLessonInfo(
                    "The Excellence of Seeking Knowledge",
                    "Whoever travels a path in search of knowledge, Allah will make easy for him a path to Paradise. (Sahih Muslim)",
                    "3 min read",
                    firstCourseId,
                    null
            );
        }
        publishFilteredState();

        // 2. Fetch fresh remote data asynchronously in the background without blocking the screen
        ServiceLocator.executors().io.execute(() -> {
            boolean signedIn = ServiceLocator.sessionManager().isSignedIn();
            isMember = signedIn;

            // 1. Fetch courses
            Outcome<List<AcademyDtos.CourseRowDto>> coursesOutcome = repository.courses(1, 50);
            if (coursesOutcome.isSuccess() && coursesOutcome.value != null && !coursesOutcome.value.isEmpty()) {
                rawCourses.clear();
                rawCourses.addAll(coursesOutcome.value);
            }

            // 2. Fetch continue learning & weak topics if signed in
            rawContinue.clear();
            rawWeak.clear();
            if (signedIn) {
                try {
                    rawContinue.addAll(orEmpty(repository.continueLearning(5)));
                    rawWeak.addAll(orEmpty(repository.weakTopics()));
                } catch (Exception ignored) {
                }
            }

            // 3. Fetch subjects / categories
            Set<String> seenSlugs = new HashSet<>();
            List<CategoryChipAdapter.ChipItem> freshCategories = new ArrayList<>();
            freshCategories.add(new CategoryChipAdapter.ChipItem(null, "All"));
            try {
                if (subjectRepository != null) {
                    Outcome<List<Models.Subject>> subjectsOutcome = subjectRepository.subjects(false);
                    if (subjectsOutcome.isSuccess() && subjectsOutcome.value != null) {
                        for (Models.Subject s : subjectsOutcome.value) {
                            if (s.slug != null && !seenSlugs.contains(s.slug)) {
                                seenSlugs.add(s.slug);
                                freshCategories.add(new CategoryChipAdapter.ChipItem(s.slug, s.name));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            // Supplement with course subjects if any are missing
            for (AcademyDtos.CourseRowDto c : rawCourses) {
                if (c.subject != null && c.subject.slug != null && !seenSlugs.contains(c.subject.slug)) {
                    seenSlugs.add(c.subject.slug);
                    freshCategories.add(new CategoryChipAdapter.ChipItem(c.subject.slug, c.subject.name));
                }
            }
            if (!freshCategories.isEmpty()) {
                rawCategories.clear();
                rawCategories.addAll(freshCategories);
            }

            // 4. Fetch flashcard decks
            try {
                Outcome<List<AcademyDtos.DeckRowDto>> decksOutcome = repository.publicDecks(1, 10);
                if (decksOutcome.isSuccess() && decksOutcome.value != null && !decksOutcome.value.isEmpty()) {
                    rawDecks.clear();
                    rawDecks.addAll(decksOutcome.value);
                }
            } catch (Exception ignored) {
            }

            publishFilteredState();
        });
    }

    public void filterByCategory(@Nullable String slug) {
        currentCategorySlug = slug;
        publishFilteredState();
    }

    public void searchCourses(@Nullable String query) {
        currentSearchQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        publishFilteredState();
    }

    private void publishFilteredState() {
        List<AcademyDtos.CourseRowDto> filtered = new ArrayList<>();
        for (AcademyDtos.CourseRowDto c : rawCourses) {
            // Category check
            if (currentCategorySlug != null && !currentCategorySlug.isEmpty()) {
                if (c.subject == null || !currentCategorySlug.equalsIgnoreCase(c.subject.slug)) {
                    continue;
                }
            }

            // Search query check
            if (!currentSearchQuery.isEmpty()) {
                boolean matchTitle = c.title != null && c.title.toLowerCase(Locale.ROOT).contains(currentSearchQuery);
                boolean matchDesc = c.description != null && c.description.toLowerCase(Locale.ROOT).contains(currentSearchQuery);
                boolean matchInstructor = c.instructorDisplayName != null && c.instructorDisplayName.toLowerCase(Locale.ROOT).contains(currentSearchQuery);
                boolean matchSubject = c.subject != null && c.subject.name != null && c.subject.name.toLowerCase(Locale.ROOT).contains(currentSearchQuery);
                if (!matchTitle && !matchDesc && !matchInstructor && !matchSubject) {
                    continue;
                }
            }

            filtered.add(c);
        }

        AcademyData data = new AcademyData(
                filtered,
                new ArrayList<>(rawContinue),
                new ArrayList<>(rawWeak),
                new ArrayList<>(rawCategories),
                new ArrayList<>(rawDecks),
                rawDailyLesson,
                isMember,
                currentCategorySlug,
                currentSearchQuery
        );
        state.postValue(UiState.success(data));
    }

    private static <T> List<T> orEmpty(Outcome<List<T>> r) {
        return r.isSuccess() && r.value != null ? r.value : new ArrayList<>();
    }
}
