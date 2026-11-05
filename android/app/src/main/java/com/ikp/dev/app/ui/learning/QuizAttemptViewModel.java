package com.ikp.dev.app.ui.learning;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.data.repository.AcademyRepository;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quiz attempt (M5). The presented question set is frozen server-side at
 * start; grading is server-side at submit; this VM only tracks the user's
 * selections and renders the returned result (with explanations).
 */
public final class QuizAttemptViewModel extends ViewModel {

    private final MutableLiveData<UiState<AcademyDtos.AttemptStartedDto>> start =
            new MutableLiveData<>();
    private final MutableLiveData<UiState<AcademyDtos.AttemptResultDto>> result =
            new MutableLiveData<>();
    private final AcademyRepository repository;
    private String quizId;

    public QuizAttemptViewModel() {
        this(ServiceLocator.academyRepository());
    }

    public QuizAttemptViewModel(AcademyRepository repository) {
        this.repository = repository;
    }

    public LiveData<UiState<AcademyDtos.AttemptStartedDto>> start() { return start; }

    public LiveData<UiState<AcademyDtos.AttemptResultDto>> result() { return result; }

    public void init(String quizId) {
        this.quizId = quizId;
    }

    public void begin() {
        if (quizId == null || start.getValue() != null
                && start.getValue().kind == UiState.Kind.SUCCESS) {
            return; // one attempt per entry; relaunching the screen starts a new one
        }
        start.postValue(UiState.loading());
        ServiceLocator.executors().io.execute(() -> {
            Outcome<AcademyDtos.AttemptStartedDto> r = repository.startAttempt(quizId);
            if (r.isSuccess()) start.postValue(UiState.success(r.value));
            else start.postValue(UiState.error(r.error));
        });
    }

    public void submit(AcademyDtos.AttemptStartedDto attempt, Map<String, String> selected) {
        result.postValue(UiState.loading());
        ServiceLocator.executors().io.execute(() -> {
            Map<String, String> safe = selected == null ? new LinkedHashMap<>() : selected;
            Outcome<AcademyDtos.AttemptResultDto> r =
                    repository.submitAttempt(attempt.attemptId, attempt.questions, safe);
            if (r.isSuccess()) result.postValue(UiState.success(r.value));
            else result.postValue(UiState.error(r.error));
        });
    }
}
