package com.ikp.dev.app.ui.research;

import android.text.TextUtils;

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
 * Research tab (M5): three directories behind one screen — research articles
 * (optionally topic-filtered), the verified-scholar directory and the
 * published-fatwa directory (search by topic text). Guests may browse;
 * saving/noteing requires sign-in (backend enforced).
 */
public final class ResearchViewModel extends ViewModel {

    public enum Mode { ARTICLES, SCHOLARS, FATWAS }

    private final MutableLiveData<UiState<List<TwoLineRow>>> state = new MutableLiveData<>();
    private final AcademyRepository repository;
    private Mode mode = Mode.ARTICLES;
    private String topic;
    private String fatwaQuery;

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

    public Mode mode() { return mode; }

    public void setMode(Mode mode) {
        if (mode != null && mode != this.mode) {
            this.mode = mode;
        }
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setFatwaQuery(String q) {
        this.fatwaQuery = (q == null || q.trim().isEmpty()) ? null : q.trim();
    }

    public void reload() {
        state.postValue(UiState.loading());
        ServiceLocator.executors().io.execute(() -> {
            switch (mode) {
                case ARTICLES -> {
                    Outcome<List<AcademyDtos.ArticleRowDto>> r = repository.researchArticles(topic, 1, 50);
                    List<TwoLineRow> rows = new ArrayList<>();
                    if (r.isSuccess() && r.value != null) {
                        for (AcademyDtos.ArticleRowDto a : r.value) {
                            rows.add(new TwoLineRow(a.articleId, a.title,
                                    a.topic == null ? null : a.topic.name,
                                    a.sourceTier));
                        }
                    }
                    publish(rows, r);
                }
                case SCHOLARS -> {
                    Outcome<List<AcademyDtos.ScholarDto>> r = repository.scholars(1, 50);
                    List<TwoLineRow> rows = new ArrayList<>();
                    if (r.isSuccess() && r.value != null) {
                        for (AcademyDtos.ScholarDto s : r.value) {
                            rows.add(new TwoLineRow(s.scholarId, s.name, s.areasOfExpertise,
                                    s.verificationStatus));
                        }
                    }
                    publish(rows, r);
                }
                case FATWAS -> {
                    Outcome<List<AcademyDtos.FatwaRowDto>> r = repository.fatwas(fatwaQuery, null, 1, 50);
                    List<TwoLineRow> rows = new ArrayList<>();
                    if (r.isSuccess() && r.value != null) {
                        for (AcademyDtos.FatwaRowDto f : r.value) {
                            rows.add(new TwoLineRow(f.fatwaId, f.question, f.category, null));
                        }
                    }
                    publish(rows, r);
                }
            }
        });
    }

    private void publish(List<TwoLineRow> rows, Outcome<?> r) {
        if (!r.isSuccess()) state.postValue(UiState.error(r.error));
        else if (rows.isEmpty()) state.postValue(UiState.empty());
        else state.postValue(UiState.success(rows));
    }
}
