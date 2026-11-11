package com.ikp.dev.app.ui.library;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.repository.BookRepository;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;

import java.util.ArrayList;
import java.util.List;

/**
 * Books list ViewModel: query + subject + language filter, cursor-paged
 * (backend contract), debounced search, load-more support.
 */
public final class BooksViewModel extends ViewModel {

    public static final long SEARCH_DEBOUNCE_MS = 400;

    public static final class BooksData {
        public final List<Models.Book> items;
        public final boolean hasMore;

        BooksData(List<Models.Book> items, boolean hasMore) {
            this.items = items;
            this.hasMore = hasMore;
        }
    }

    private final MutableLiveData<UiState<BooksData>> state = new MutableLiveData<>();
    private final BookRepository bookRepository;
    private final List<Models.Book> accumulated = new ArrayList<>();
    private String nextCursor;
    private String subjectSlug;
    private String language;
    private String query;

    private final Handler debounce = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    public BooksViewModel() {
        this(ServiceLocator.bookRepository());
    }

    public BooksViewModel(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public LiveData<UiState<BooksData>> state() {
        return state;
    }

    public void init(String subjectSlug, String query) {
        this.subjectSlug = subjectSlug;
        this.query = query;
    }

    public String currentQuery() {
        return query;
    }

    public void setLanguage(String language) {
        this.language = language;
        reload();
    }

    public void setQuery(String q) {
        this.query = q;
        if (pendingSearch != null) {
            debounce.removeCallbacks(pendingSearch);
        }
        pendingSearch = this::reload;
        debounce.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    /** Runs the current search immediately, cancelling any pending debounce. */
    public void searchNow() {
        if (pendingSearch != null) {
            debounce.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        reload();
    }

    /** TextWatcher wiring for the search box (UI attaches). */
    public TextWatcher queryWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                setQuery(s.toString());
            }
        };
    }

    public void reload() {
        accumulated.clear();
        nextCursor = null;
        fetch(null);
    }

    public void loadMore() {
        if (nextCursor != null) {
            fetch(nextCursor);
        }
    }

    private void fetch(String cursor) {
        if (cursor == null) {
            state.postValue(UiState.loading());
        }
        final String subject = subjectSlug;
        final String lang = language;
        final String q = query;
        final String cur = cursor;
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            // Discard stale responses (filters changed while in flight)
            if (!java.util.Objects.equals(subject, subjectSlug)
                    || !java.util.Objects.equals(lang, language)
                    || !java.util.Objects.equals(q, query)) {
                return;
            }
            com.ikp.dev.app.core.common.Outcome<Models.Page<Models.Book>> r =
                    bookRepository.books(subject, lang, q, cur, false);
            if (!java.util.Objects.equals(subject, subjectSlug)
                    || !java.util.Objects.equals(lang, language)
                    || !java.util.Objects.equals(q, query)) {
                return;
            }
            if (r.isSuccess() && r.value != null) {
                accumulated.addAll(r.value.items);
                nextCursor = r.value.nextCursor;
                if (accumulated.isEmpty()) {
                    state.postValue(UiState.empty());
                } else {
                    state.postValue(UiState.success(new BooksData(
                            new ArrayList<>(accumulated), r.value.hasNext())));
                }
            } else {
                state.postValue(UiState.error(r.error));
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        debounce.removeCallbacksAndMessages(null);
    }
}
