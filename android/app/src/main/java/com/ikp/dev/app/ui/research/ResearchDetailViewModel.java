package com.ikp.dev.app.ui.research;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.data.repository.AcademyRepository;
import com.ikp.dev.app.di.ServiceLocator;

/** Loads one research article, scholar profile or published fatwa (M5). */
public final class ResearchDetailViewModel extends ViewModel {

    private final MutableLiveData<UiState<AcademyDtos.ArticleDetailDto>> article =
            new MutableLiveData<>();
    private final MutableLiveData<UiState<AcademyDtos.ScholarDto>> scholar =
            new MutableLiveData<>();
    private final MutableLiveData<UiState<AcademyDtos.FatwaDetailDto>> fatwa =
            new MutableLiveData<>();
    private final MutableLiveData<Boolean> saved = new MutableLiveData<>();
    private final AcademyRepository repository;

    public ResearchDetailViewModel() {
        this(ServiceLocator.academyRepository());
    }

    public ResearchDetailViewModel(AcademyRepository repository) {
        this.repository = repository;
    }

    public LiveData<UiState<AcademyDtos.ArticleDetailDto>> article() { return article; }

    public LiveData<UiState<AcademyDtos.ScholarDto>> scholar() { return scholar; }

    public LiveData<UiState<AcademyDtos.FatwaDetailDto>> fatwa() { return fatwa; }

    public LiveData<Boolean> saved() { return saved; }

    public void loadArticle(String id) {
        article.postValue(UiState.loading());
        ServiceLocator.executors().io.execute(() -> {
            Outcome<AcademyDtos.ArticleDetailDto> r = repository.researchArticle(id);
            article.postValue(r.isSuccess() ? UiState.success(r.value) : UiState.error(r.error));
        });
    }

    public void loadScholar(String id) {
        scholar.postValue(UiState.loading());
        ServiceLocator.executors().io.execute(() -> {
            Outcome<AcademyDtos.ScholarDto> r = repository.scholar(id);
            scholar.postValue(r.isSuccess() ? UiState.success(r.value) : UiState.error(r.error));
        });
    }

    public void loadFatwa(String id) {
        fatwa.postValue(UiState.loading());
        ServiceLocator.executors().io.execute(() -> {
            Outcome<AcademyDtos.FatwaDetailDto> r = repository.fatwa(id);
            fatwa.postValue(r.isSuccess() ? UiState.success(r.value) : UiState.error(r.error));
        });
    }

    public void saveArticle(String articleId) {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<?> r = repository.saveResearchBookmark(articleId, null);
            saved.postValue(r.isSuccess());
        });
    }

    public void saveFatwa(String fatwaId) {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<?> r = repository.saveFatwaBookmark(fatwaId);
            saved.postValue(r.isSuccess());
        });
    }

    public void noteArticle(String articleId, String text) {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<?> r = repository.saveResearchNote(articleId, text);
            saved.postValue(r.isSuccess());
        });
    }
}
