package com.ikp.dev.app.ui.splash;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.StartupPolicy;
import com.ikp.dev.app.core.security.TokenStore;
import com.ikp.dev.app.data.repository.AuthRepository;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;

/**
 * Startup orchestration (prompt §7):
 * 1. local configuration (onboarding flag) → 2. inspect saved session →
 * 3. validate/refresh via backend where necessary → 4. decide destination.
 * Threading: the repository call runs on the single background executor.
 */
public final class SplashViewModel extends ViewModel {

    public enum Next { ONBOARDING, LOGIN, HOME, HOME_OFFLINE }

    private final MutableLiveData<Next> navigation = new MutableLiveData<>();
    private final AuthRepository authRepository;
    private final TokenStore tokenStore;

    public SplashViewModel() {
        this(ServiceLocator.authRepository(), ServiceLocator.tokenStore(),
                ServiceLocator.appPrefs() != null);
    }

    /** Test constructor. */
    public SplashViewModel(AuthRepository authRepository, TokenStore tokenStore, boolean hasPrefs) {
        this.authRepository = authRepository;
        this.tokenStore = tokenStore;
    }

    public LiveData<Next> navigation() {
        return navigation;
    }

    public void start() {
        com.ikp.dev.app.di.ServiceLocator.executors().single.execute(() -> {
            boolean onboardingSeen = onboardingSeen();
            boolean hasSession = tokenStore.hasSession();
            AuthRepository.RestoreResult restore = hasSession
                    ? authRepository.restore()
                    : null;
            AuthRepository.RestoreState state = restore == null
                    ? AuthRepository.RestoreState.SIGNED_OUT
                    : restore.state;
            StartupPolicy.Destination d = StartupPolicy.decide(onboardingSeen, hasSession, state);
            switch (d) {
                case ONBOARDING:
                    navigation.postValue(Next.ONBOARDING);
                    break;
                case LOGIN:
                    navigation.postValue(Next.LOGIN);
                    break;
                case HOME_OFFLINE:
                    navigation.postValue(Next.HOME_OFFLINE);
                    break;
                case HOME:
                default:
                    navigation.postValue(Next.HOME);
                    break;
            }
        });
    }

    private boolean onboardingSeen() {
        android.content.SharedPreferences prefs = ServiceLocator.appPrefs();
        return prefs != null && prefs.getBoolean("onboarding_seen", false);
    }

    public static void markOnboardingSeen() {
        android.content.SharedPreferences prefs = ServiceLocator.appPrefs();
        if (prefs != null) {
            prefs.edit().putBoolean("onboarding_seen", true).apply();
        }
    }
}
