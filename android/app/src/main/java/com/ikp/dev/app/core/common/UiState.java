package com.ikp.dev.app.core.common;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Screen state envelope used by every ViewModel (prompt §19). A screen renders
 * exactly one of: Loading / Success / Empty / Error, plus an app-wide offline
 * banner. Error carries a stable backend code when one exists (see
 * {@link com.ikp.dev.app.core.error.ApiError}).
 */
public final class UiState<T> {

    public enum Kind { LOADING, SUCCESS, EMPTY, ERROR }

    @Nullable
    public final T data;
    public final Kind kind;
    @Nullable
    public final com.ikp.dev.app.core.error.ApiError error;

    private UiState(Kind kind, @Nullable T data, @Nullable com.ikp.dev.app.core.error.ApiError error) {
        this.kind = kind;
        this.data = data;
        this.error = error;
    }

    public static <T> UiState<T> loading() {
        return new UiState<>(Kind.LOADING, null, null);
    }

    public static <T> UiState<T> success(T data) {
        return new UiState<>(Kind.SUCCESS, data, null);
    }

    public static <T> UiState<T> empty() {
        return new UiState<>(Kind.EMPTY, null, null);
    }

    public static <T> UiState<T> error(com.ikp.dev.app.core.error.ApiError error) {
        return new UiState<>(Kind.ERROR, null, error);
    }

    public boolean isLoading() {
        return kind == Kind.LOADING;
    }

    public boolean isSuccess() {
        return kind == Kind.SUCCESS;
    }

    public boolean isEmpty() {
        return kind == Kind.EMPTY;
    }

    public boolean isError() {
        return kind == Kind.ERROR;
    }

    /** Convenience mutable holder for screens that expose one observable state. */
    public static class State<T> extends MutableLiveData<UiState<T>> {
        public State() {
            super(UiState.loading());
        }

        public void postLoading() {
            postValue(UiState.loading());
        }

        public void postSuccess(T data) {
            postValue(UiState.success(data));
        }

        public void postEmpty() {
            postValue(UiState.empty());
        }

        public void postError(com.ikp.dev.app.core.error.ApiError error) {
            postValue(UiState.error(error));
        }
    }
}
