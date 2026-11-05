package com.ikp.dev.app.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.repository.AuthRepository;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;

import java.util.List;

/** Devices & sessions ViewModel (real /auth/sessions + revocation). */
public final class SessionsViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<Models.SessionInfo>>> state = new MutableLiveData<>();
    private final AuthRepository authRepository;

    public SessionsViewModel() {
        this(ServiceLocator.authRepository());
    }

    public SessionsViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<UiState<List<Models.SessionInfo>>> state() {
        return state;
    }

    public void load() {
        state.postValue(UiState.loading());
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            com.ikp.dev.app.core.common.Outcome<List<Models.SessionInfo>> r = authRepository.sessions();
            if (r.isSuccess() && r.value != null) {
                if (r.value.isEmpty()) {
                    state.postValue(UiState.empty());
                } else {
                    state.postValue(UiState.success(r.value));
                }
            } else if (r.isSuccess()) {
                state.postValue(UiState.empty());
            } else {
                state.postValue(UiState.error(r.error));
            }
        });
    }

    public void revoke(long sessionId, long currentSessionId) {
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            com.ikp.dev.app.core.common.Outcome<Boolean> r =
                    authRepository.revokeSession(sessionId, currentSessionId);
            if (r.isSuccess()) {
                load();
            } else {
                state.postValue(UiState.error(r.error));
            }
        });
    }
}
