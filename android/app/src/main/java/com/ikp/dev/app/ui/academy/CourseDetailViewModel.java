package com.ikp.dev.app.ui.academy;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.data.repository.AcademyRepository;
import com.ikp.dev.app.di.ServiceLocator;

/**
 * Course detail (M5): structure + enrolment + progress. Lesson completion
 * auto-enrols server-side, so the progress row always reflects the backend.
 */
public final class CourseDetailViewModel extends ViewModel {

    private final MutableLiveData<UiState<AcademyDtos.CourseDetailDto>> state = new MutableLiveData<>();
    private final MutableLiveData<AcademyDtos.ProgressDto> progress = new MutableLiveData<>();
    private final MutableLiveData<Boolean> enrolDone = new MutableLiveData<>();
    private final AcademyRepository repository;
    private String courseId;

    public CourseDetailViewModel() {
        this(ServiceLocator.academyRepository());
    }

    public CourseDetailViewModel(AcademyRepository repository) {
        this.repository = repository;
    }

    public LiveData<UiState<AcademyDtos.CourseDetailDto>> state() { return state; }

    public LiveData<AcademyDtos.ProgressDto> progress() { return progress; }

    public LiveData<Boolean> enrolDone() { return enrolDone; }

    public void init(String courseId) {
        if (courseId != null && !courseId.equals(this.courseId)) {
            this.courseId = courseId;
        }
    }

    public void reload() {
        if (courseId == null) return;
        state.postValue(UiState.loading());
        ServiceLocator.executors().io.execute(() -> {
            Outcome<AcademyDtos.CourseDetailDto> r = repository.course(courseId);
            if (!r.isSuccess()) {
                state.postValue(UiState.error(r.error));
                return;
            }
            state.postValue(UiState.success(r.value));
            Outcome<AcademyDtos.ProgressDto> p = repository.progress(courseId);
            if (p.isSuccess()) progress.postValue(p.value);
        });
    }

    public void enrol() {
        if (courseId == null) return;
        ServiceLocator.executors().io.execute(() -> {
            Outcome<AcademyDtos.EnrolDto> r = repository.enrol(courseId);
            enrolDone.postValue(r.isSuccess());
            Outcome<AcademyDtos.ProgressDto> p = repository.progress(courseId);
            if (p.isSuccess()) progress.postValue(p.value);
        });
    }

    public void completeLesson(String lessonId) {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<AcademyDtos.ProgressDto> r = repository.completeLesson(lessonId);
            if (r.isSuccess()) {
                progress.postValue(r.value);
                // course completion status changes with progress; refresh quietly
                Outcome<AcademyDtos.CourseDetailDto> fresh = repository.course(courseId);
                if (fresh.isSuccess()) state.postValue(UiState.success(fresh.value));
            }
        });
    }
}
