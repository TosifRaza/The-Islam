package com.ikp.dev.app.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.repository.BookRepository;
import com.ikp.dev.app.data.repository.SubjectRepository;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;

import java.util.ArrayList;
import java.util.List;

/**
 * Home ViewModel. REAL data only: subjects + books come from the backend;
 * daily-content areas (Ayah/Hadith of Day, Hijri, prayer times) have no
 * backend service at M3 and are rendered as documented unavailable states —
 * no fabricated religious content anywhere.
 */
public final class HomeViewModel extends ViewModel {

    public static final class HomeData {
        public final List<Models.Subject> subjects;
        public final List<Models.Book> books;

        HomeData(List<Models.Subject> subjects, List<Models.Book> books) {
            this.subjects = subjects;
            this.books = books;
        }
    }

    private final MutableLiveData<UiState<HomeData>> state = new MutableLiveData<>();
    private final SubjectRepository subjectRepository;
    private final BookRepository bookRepository;

    public HomeViewModel() {
        this(ServiceLocator.subjectRepository(), ServiceLocator.bookRepository());
    }

    public HomeViewModel(SubjectRepository subjectRepository, BookRepository bookRepository) {
        this.subjectRepository = subjectRepository;
        this.bookRepository = bookRepository;
    }

    public LiveData<UiState<HomeData>> state() {
        return state;
    }

    public void load(boolean forceRefresh) {
        state.postValue(UiState.loading());
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            Outcome<List<Models.Subject>> subjects = subjectRepository.subjects(forceRefresh);
            if (!subjects.isSuccess()) {
                state.postValue(UiState.error(subjects.error));
                return;
            }
            // Books rail: latest published (no subject filter). Failure
            // degrades to an empty rail — subjects already carry the error.
            Outcome<Models.Page<Models.Book>> books =
                    bookRepository.books(null, null, null, null, forceRefresh);
            List<Models.Book> bookList = books.value == null
                    ? new ArrayList<>() : books.value.items;
            state.postValue(UiState.success(new HomeData(subjects.value, bookList)));
        });
    }
}
