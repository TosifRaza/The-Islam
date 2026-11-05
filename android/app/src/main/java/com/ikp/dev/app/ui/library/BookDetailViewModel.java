package com.ikp.dev.app.ui.library;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.local.dao.UserDataDao;
import com.ikp.dev.app.data.local.entity.UserDataEntities;
import com.ikp.dev.app.data.repository.BookRepository;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;

/**
 * Book detail ViewModel + local recent-history recording (Room foundation).
 * The download action is a documented-unavailable capability: the public API
 * does not expose file UUIDs required by the download-token endpoint — the
 * VM exposes that fact instead of pretending.
 */
public final class BookDetailViewModel extends ViewModel {

    private final MutableLiveData<UiState<Models.BookDetail>> state = new MutableLiveData<>();
    private final BookRepository bookRepository;

    public BookDetailViewModel() {
        this(ServiceLocator.bookRepository());
    }

    public BookDetailViewModel(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public LiveData<UiState<Models.BookDetail>> state() {
        return state;
    }

    public void load(String bookId) {
        state.postValue(UiState.loading());
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            com.ikp.dev.app.core.common.Outcome<Models.BookDetail> r = bookRepository.detail(bookId);
            if (r.isSuccess() && r.value != null) {
                recordRecent(bookId, r.value.title);
                state.postValue(UiState.success(r.value));
            } else if (r.isSuccess()) {
                state.postValue(UiState.empty());
            } else {
                state.postValue(UiState.error(r.error));
            }
        });
    }

    private void recordRecent(String bookId, String title) {
        try {
            com.ikp.dev.app.core.database.IkpDatabase db = ServiceLocator.database();
            if (db == null) {
                return;
            }
            UserDataDao dao = db.userDataDao();
            dao.removeRecent(bookId, "book");
            UserDataEntities.RecentHistoryEntity e = new UserDataEntities.RecentHistoryEntity();
            e.contentId = bookId;
            e.contentType = "book";
            e.title = title;
            e.viewedAtEpochMs = System.currentTimeMillis();
            dao.addRecent(e);
        } catch (RuntimeException ignored) {
            // history is best-effort
        }
    }
}
