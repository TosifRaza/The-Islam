package com.ikp.dev.app.ui.library;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.repository.SubjectRepository;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;

import java.util.List;

/** Library categories ViewModel — real GET /api/v1/subjects, offline cache. */
public final class LibraryViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<Models.Subject>>> state = new MutableLiveData<>();
    private final SubjectRepository subjectRepository;

    public LibraryViewModel() {
        this(ServiceLocator.subjectRepository());
    }

    public LibraryViewModel(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    public LiveData<UiState<List<Models.Subject>>> state() {
        return state;
    }

    public void load(boolean forceRefresh) {
        state.postValue(UiState.loading());
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            com.ikp.dev.app.core.common.Outcome<List<Models.Subject>> r =
                    subjectRepository.subjects(forceRefresh);
            if (r.isSuccess()) {
                if (r.value == null || r.value.isEmpty()) {
                    state.postValue(UiState.empty());
                } else {
                    state.postValue(UiState.success(r.value));
                }
            } else {
                state.postValue(UiState.error(r.error));
            }
        });
    }
}
