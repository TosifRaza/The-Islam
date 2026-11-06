package com.ikp.dev.app.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.repository.AuthRepository;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;

/**
 * Authentication ViewModel (login / logout). Registration and password
 * reset have NO backend endpoints (M3 API report) — they are intentionally
 * NOT exposed as fake flows here; the UI routes to documented-unavailable
 * screens.
 */
public final class AuthViewModel extends ViewModel {

    private final AuthRepository authRepository;

    private final MutableLiveData<UiState<Models.User>> loginState = new MutableLiveData<>();
    private final MutableLiveData<UiState<Models.User>> registerState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> logoutDone = new MutableLiveData<>();

    public AuthViewModel() {
        this(ServiceLocator.authRepository());
    }

    public AuthViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
        loginState.setValue(UiState.success(null));
        registerState.setValue(UiState.success(null));
    }

    public LiveData<UiState<Models.User>> loginState() {
        return loginState;
    }

    public LiveData<UiState<Models.User>> registerState() {
        return registerState;
    }

    public void resetRegisterState() {
        registerState.setValue(UiState.success(null));
    }

    public LiveData<Boolean> logoutDone() {
        return logoutDone;
    }

    /** Client-side validation errors mirror the backend's VAL rules. */
    public void login(String email, String password, String deviceName) {
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            loginState.setValue(UiState.error(
                    new com.ikp.dev.app.core.error.ApiError("VAL-CLIENT-001", "error_validation")));
            return;
        }
        if (password == null || password.length() < 8) {
            loginState.setValue(UiState.error(
                    new com.ikp.dev.app.core.error.ApiError("VAL-CLIENT-002", "error_validation")));
            return;
        }
        loginState.setValue(UiState.loading());
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            OutcomeHolder result = runLogin(email.trim(), password, emptyToNull(deviceName));
            if (result.error != null) {
                loginState.postValue(UiState.error(result.error));
            } else {
                loginState.postValue(UiState.success(result.user));
            }
        });
    }

    public void register(String email, String password, String displayName) {
        if (!isValidEmail(email)) {
            registerState.setValue(UiState.error(
                    new com.ikp.dev.app.core.error.ApiError("VAL-CLIENT-001", "error_validation")));
            return;
        }
        if (!isValidPassword(password)) {
            registerState.setValue(UiState.error(
                    new com.ikp.dev.app.core.error.ApiError("VAL-CLIENT-002", "error_validation")));
            return;
        }
        registerState.setValue(UiState.loading());
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            Outcome<Models.User> outcome = authRepository.register(email.trim(), password, emptyToNull(displayName));
            if (outcome.isSuccess()) {
                registerState.postValue(UiState.success(outcome.value));
            } else {
                registerState.postValue(UiState.error(outcome.error));
            }
        });
    }

    /** Best-effort immediate profile fetch after login (fills email/status). */
    public void refreshProfile() {
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(authRepository::me);
    }

    public void logout() {
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            authRepository.logout();
            logoutDone.postValue(Boolean.TRUE);
        });
    }

    // ---- pure helpers (JVM-tested via AuthViewModelTest) ----

    static final class OutcomeHolder {
        final Models.User user;
        final com.ikp.dev.app.core.error.ApiError error;

        OutcomeHolder(Models.User user, com.ikp.dev.app.core.error.ApiError error) {
            this.user = user;
            this.error = error;
        }
    }

    private OutcomeHolder runLogin(String email, String password, String deviceName) {
        Outcome<Models.User> outcome = authRepository.login(email, password, deviceName);
        return outcome.isSuccess()
                ? new OutcomeHolder(outcome.value, null)
                : new OutcomeHolder(null, outcome.error);
    }

    /** Pure email validation (same rule as UI hint text). */
    public static boolean isValidEmail(String email) {
        return email != null && email.trim().length() >= 5
                && email.contains("@") && email.indexOf('@') < email.length() - 1;
    }

    /** Pure password rule: backend rejects < 8 chars (VAL-001 otherwise). */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 8;
    }

    private static String emptyToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
