package com.ikp.dev.app.ui.research;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.data.repository.AcademyRepository;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.List;

/**
 * Research tab (M5): academic research library consisting of peer-reviewed articles,
 * verified scholars directory, and authenticated fatwa library.
 */
public final class ResearchViewModel extends ViewModel {

    public enum Mode { ARTICLES, SCHOLARS, FATWAS }

    // Legacy row state for backwards compatibility
    private final MutableLiveData<UiState<List<TwoLineRow>>> state = new MutableLiveData<>();

    // Typed data states
    private final MutableLiveData<List<AcademyDtos.TopicDto>> topics = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> selectedTopic = new MutableLiveData<>(null);
    private final MutableLiveData<AcademyDtos.ArticleRowDto> featuredArticle = new MutableLiveData<>(null);
    private final MutableLiveData<UiState<List<AcademyDtos.ArticleRowDto>>> articlesState = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<AcademyDtos.ScholarDto>>> scholarsState = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<AcademyDtos.FatwaRowDto>>> fatwasState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> bookmarkSaved = new MutableLiveData<>();

    private final AcademyRepository repository;
    private Mode mode = Mode.ARTICLES;
    private String topicSlug = null;
    private String searchQuery = null;

    /** UI-neutral row (maps to TwoLineAdapter.Item). */
    public static final class TwoLineRow {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String badge;

        public TwoLineRow(String id, String title, String subtitle, String badge) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.badge = badge;
        }
    }

    public ResearchViewModel() {
        this(ServiceLocator.academyRepository());
    }

    public ResearchViewModel(AcademyRepository repository) {
        this.repository = repository;
    }

    public LiveData<UiState<List<TwoLineRow>>> state() { return state; }

    public LiveData<List<AcademyDtos.TopicDto>> topics() { return topics; }

    public LiveData<String> selectedTopic() { return selectedTopic; }

    public LiveData<AcademyDtos.ArticleRowDto> featuredArticle() { return featuredArticle; }

    public LiveData<UiState<List<AcademyDtos.ArticleRowDto>>> articlesState() { return articlesState; }

    public LiveData<UiState<List<AcademyDtos.ScholarDto>>> scholarsState() { return scholarsState; }

    public LiveData<UiState<List<AcademyDtos.FatwaRowDto>>> fatwasState() { return fatwasState; }

    public LiveData<Boolean> bookmarkSaved() { return bookmarkSaved; }

    public Mode mode() { return mode; }

    public void setMode(Mode mode) {
        if (mode != null && mode != this.mode) {
            this.mode = mode;
            this.topicSlug = null;
            this.selectedTopic.setValue(null);
        }
    }

    public void setTopic(@Nullable String slug) {
        this.topicSlug = (slug == null || slug.trim().isEmpty()) ? null : slug.trim();
        this.selectedTopic.setValue(this.topicSlug);
    }

    public void setFatwaQuery(@Nullable String q) {
        this.searchQuery = (q == null || q.trim().isEmpty()) ? null : q.trim();
    }

    public void loadTopics() {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<AcademyDtos.TopicDto>> r = repository.researchTopics();
            if (r.isSuccess() && r.value != null) {
                topics.postValue(r.value);
            }
        });
    }

    public void reload() {
        state.postValue(UiState.loading());
        articlesState.postValue(UiState.loading());
        scholarsState.postValue(UiState.loading());
        fatwasState.postValue(UiState.loading());

        // Ensure topics are loaded
        if (topics.getValue() == null || topics.getValue().isEmpty()) {
            loadTopics();
        }

        ServiceLocator.executors().io.execute(() -> {
            switch (mode) {
                case ARTICLES -> loadArticlesInternal();
                case SCHOLARS -> loadScholarsInternal();
                case FATWAS -> loadFatwasInternal();
            }
        });
    }

    private void loadArticlesInternal() {
        Outcome<List<AcademyDtos.ArticleRowDto>> r = repository.researchArticles(topicSlug, 1, 50);
        List<TwoLineRow> rows = new ArrayList<>();
        List<AcademyDtos.ArticleRowDto> filteredArticles = new ArrayList<>();

        if (r.isSuccess() && r.value != null) {
            for (AcademyDtos.ArticleRowDto a : r.value) {
                if (matchesQuery(a.title, a.summary, a.authorDisplayName)) {
                    filteredArticles.add(a);
                    rows.add(new TwoLineRow(a.articleId, a.title,
                            a.topic == null ? null : a.topic.name,
                            a.sourceTier));
                }
            }
        }

        // Set featured article
        if (!filteredArticles.isEmpty()) {
            featuredArticle.postValue(filteredArticles.get(0));
        } else {
            featuredArticle.postValue(null);
        }

        if (!r.isSuccess()) {
            articlesState.postValue(UiState.error(r.error));
            state.postValue(UiState.error(r.error));
        } else if (filteredArticles.isEmpty()) {
            articlesState.postValue(UiState.empty());
            state.postValue(UiState.empty());
        } else {
            articlesState.postValue(UiState.success(filteredArticles));
            state.postValue(UiState.success(rows));
        }
    }

    private void loadScholarsInternal() {
        Outcome<List<AcademyDtos.ScholarDto>> r = repository.scholars(1, 50);
        List<TwoLineRow> rows = new ArrayList<>();
        List<AcademyDtos.ScholarDto> filteredScholars = new ArrayList<>();

        if (r.isSuccess() && r.value != null) {
            for (AcademyDtos.ScholarDto s : r.value) {
                if (matchesQuery(s.name, s.qualifications, s.areasOfExpertise)) {
                    filteredScholars.add(s);
                    rows.add(new TwoLineRow(s.scholarId, s.name, s.areasOfExpertise,
                            s.verificationStatus));
                }
            }
        }

        if (!r.isSuccess()) {
            scholarsState.postValue(UiState.error(r.error));
            state.postValue(UiState.error(r.error));
        } else if (filteredScholars.isEmpty()) {
            scholarsState.postValue(UiState.empty());
            state.postValue(UiState.empty());
        } else {
            scholarsState.postValue(UiState.success(filteredScholars));
            state.postValue(UiState.success(rows));
        }
    }

    private void loadFatwasInternal() {
        Outcome<List<AcademyDtos.FatwaRowDto>> r = repository.fatwas(searchQuery, topicSlug, 1, 50);
        List<TwoLineRow> rows = new ArrayList<>();
        List<AcademyDtos.FatwaRowDto> filteredFatwas = new ArrayList<>();

        if (r.isSuccess() && r.value != null) {
            for (AcademyDtos.FatwaRowDto f : r.value) {
                filteredFatwas.add(f);
                rows.add(new TwoLineRow(f.fatwaId, f.question, f.category, null));
            }
        }

        if (!r.isSuccess()) {
            fatwasState.postValue(UiState.error(r.error));
            state.postValue(UiState.error(r.error));
        } else if (filteredFatwas.isEmpty()) {
            fatwasState.postValue(UiState.empty());
            state.postValue(UiState.empty());
        } else {
            fatwasState.postValue(UiState.success(filteredFatwas));
            state.postValue(UiState.success(rows));
        }
    }

    private boolean matchesQuery(String... fields) {
        if (searchQuery == null || searchQuery.isEmpty()) return true;
        String q = searchQuery.toLowerCase();
        for (String f : fields) {
            if (f != null && f.toLowerCase().contains(q)) return true;
        }
        return false;
    }

    public void saveArticleBookmark(String articleId) {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<?> r = repository.saveResearchBookmark(articleId, null);
            bookmarkSaved.postValue(r.isSuccess());
        });
    }

    public void saveFatwaBookmark(String fatwaId) {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<?> r = repository.saveFatwaBookmark(fatwaId);
            bookmarkSaved.postValue(r.isSuccess());
        });
    }
}
