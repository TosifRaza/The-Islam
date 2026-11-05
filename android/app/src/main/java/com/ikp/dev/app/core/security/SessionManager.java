package com.ikp.dev.app.core.security;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Single source of truth for the app's authentication state.
 *
 * States: SIGNED_OUT · GUEST (no/expired session, public browsing) ·
 * SIGNED_IN(user) · EXPIRED (session was valid once, backend rejected it —
 * UI must return the user to a secure state).
 *
 * The object is process-wide (via ServiceLocator). OkHttp's
 * TokenAuthenticator posts forceLogout(EXPIRED) on unrecoverable refresh
 * failures; screens observe {@link #state()}.
 */
public final class SessionManager {

    public enum AuthState { SIGNED_OUT, GUEST, SIGNED_IN, EXPIRED }

    /** Minimal authenticated-user view (never contains credentials). */
    public static final class CurrentUser {
        public final String userId;
        public final String displayName;
        public final String email;

        public CurrentUser(String userId, String displayName, String email) {
            this.userId = userId;
            this.displayName = displayName;
            this.email = email;
        }
    }

    public static final class SessionEvent {
        public final AuthState state;
        @androidx.annotation.Nullable
        public final CurrentUser user;

        SessionEvent(AuthState state, CurrentUser user) {
            this.state = state;
            this.user = user;
        }
    }

    private final MutableLiveData<SessionEvent> state = new MutableLiveData<>(new SessionEvent(AuthState.SIGNED_OUT, null));
    private final TokenStore tokenStore;

    public SessionManager(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    public LiveData<SessionEvent> state() {
        return state;
    }

    @androidx.annotation.Nullable
    public CurrentUser currentUserOrNull() {
        SessionEvent e = state.getValue();
        return e == null ? null : e.user;
    }

    public boolean isSignedIn() {
        SessionEvent e = state.getValue();
        return e != null && e.state == AuthState.SIGNED_IN;
    }

    public void onSignedIn(String userId, String displayName, String email) {
        state.postValue(new SessionEvent(AuthState.SIGNED_IN, new CurrentUser(userId, displayName, email)));
    }

    /** Session exists on disk but is unvalidated (startup, offline). */
    public void onUnvalidatedSession() {
        state.postValue(new SessionEvent(AuthState.GUEST, null));
    }

    public void onGuest() {
        state.postValue(new SessionEvent(AuthState.GUEST, null));
    }

    public void onSignedOut() {
        tokenStore.clear();
        state.postValue(new SessionEvent(AuthState.SIGNED_OUT, null));
    }

    /**
     * Backend rejected the session and refresh failed (TokenAuthenticator /
     * restore path). Clears local credentials and signals EXPIRED so the UI
     * returns the user to a secure state (login) with an explanation.
     */
    public void onSessionExpired() {
        tokenStore.clear();
        state.postValue(new SessionEvent(AuthState.EXPIRED, null));
    }
}
