package com.ikp.dev.app.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.core.security.SessionManager;
import com.ikp.dev.app.data.repository.AuthRepository;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;

/**
 * Profile ViewModel: real /auth/me data, sessions list, logout.
 */
public final class ProfileViewModel extends ViewModel {

    private final MutableLiveData<UiState<Models.User>> profileState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> logoutDone = new MutableLiveData<>();
    private final AuthRepository authRepository;
    private final SessionManager sessionManager;

    public ProfileViewModel() {
        this(ServiceLocator.authRepository(), ServiceLocator.sessionManager());
    }

    public ProfileViewModel(AuthRepository authRepository, SessionManager sessionManager) {
        this.authRepository = authRepository;
        this.sessionManager = sessionManager;
    }

    public LiveData<UiState<Models.User>> profileState() {
        return profileState;
    }

    public LiveData<Boolean> logoutDone() {
        return logoutDone;
    }

    public boolean isSignedIn() {
        return sessionManager.isSignedIn();
    }

    public void loadProfile() {
        if (!isSignedIn()) {
            profileState.postValue(UiState.success(null));
            return;
        }
        profileState.postValue(UiState.loading());
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            com.ikp.dev.app.core.common.Outcome<Models.User> r = authRepository.me();
            if (r.isSuccess() && r.value != null) {
                profileState.postValue(UiState.success(r.value));
            } else if (r.isSuccess()) {
                profileState.postValue(UiState.empty());
            } else {
                profileState.postValue(UiState.error(r.error));
            }
        });
    }

    public void logout() {
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            authRepository.logout();
            logoutDone.postValue(Boolean.TRUE);
        });
    }
}
