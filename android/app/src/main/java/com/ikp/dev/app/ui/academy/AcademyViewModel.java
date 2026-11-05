package com.ikp.dev.app.ui.academy;


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
 * Academy tab (M5): published course catalogue plus the signed-in member's
 * continue-learning rail and weak-topic summary. Guests see the catalogue
 * with an honest sign-in hint (member sections hide, nothing fabricated).
 */
public final class AcademyViewModel extends ViewModel {

    public static final class AcademyData {
        public final List<AcademyDtos.CourseRowDto> courses;
        public final List<AcademyDtos.ContinueDto> continueLearning;
        public final List<AcademyDtos.WeakTopicDto> weakTopics;
        public final boolean memberSections;

        AcademyData(List<AcademyDtos.CourseRowDto> courses, List<AcademyDtos.ContinueDto> cont,
                    List<AcademyDtos.WeakTopicDto> weak, boolean memberSections) {
            this.courses = courses;
            this.continueLearning = cont;
            this.weakTopics = weak;
            this.memberSections = memberSections;
        }
    }

    private final MutableLiveData<UiState<AcademyData>> state = new MutableLiveData<>();
    private final AcademyRepository repository;

    public AcademyViewModel() {
        this(ServiceLocator.academyRepository());
    }

    public AcademyViewModel(AcademyRepository repository) {
        this.repository = repository;
    }

    public LiveData<UiState<AcademyData>> state() {
        return state;
    }

    public void reload() {
        state.postValue(UiState.loading());
        ServiceLocator.executors().io.execute(() -> {
            boolean signedIn = ServiceLocator.sessionManager().isSignedIn();
            Outcome<List<AcademyDtos.CourseRowDto>> courses = repository.courses(1, 50);
            if (!courses.isSuccess()) {
                state.postValue(UiState.error(courses.error));
                return;
            }
            List<AcademyDtos.ContinueDto> cont = new ArrayList<>();
            List<AcademyDtos.WeakTopicDto> weak = new ArrayList<>();
            if (signedIn) {
                cont = orEmpty(repository.continueLearning(5));
                weak = orEmpty(repository.weakTopics());
            }
            if (courses.value.isEmpty() && cont.isEmpty()) {
                state.postValue(UiState.empty());
                return;
            }
            state.postValue(UiState.success(new AcademyData(courses.value, cont, weak, signedIn)));
        });
    }

    private static <T> List<T> orEmpty(Outcome<List<T>> r) {
        return r.isSuccess() && r.value != null ? r.value : new ArrayList<>();
    }
}
