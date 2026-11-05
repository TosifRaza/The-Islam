package com.ikp.dev.app.core.common;

/**
 * PURE startup decision logic (prompt §7), JVM-tested. Separated from any
 * Android class so the whole flow is provable without a device.
 *
 * Inputs (booleans representing repository/auth state):
 * - onboardingSeen: first-run flag persisted locally;
 * - hasSavedSession: a refresh token exists in secure storage;
 * - restoreState: repository validation outcome for the saved session.
 */
public final class StartupPolicy {

    public enum Destination { ONBOARDING, LOGIN, HOME, HOME_OFFLINE }

    private StartupPolicy() {
    }

    public static Destination decide(boolean onboardingSeen, boolean hasSavedSession,
                                     com.ikp.dev.app.data.repository.AuthRepository.RestoreState restoreState) {
        if (!onboardingSeen) {
            return Destination.ONBOARDING;
        }
        if (!hasSavedSession) {
            return Destination.LOGIN;
        }
        switch (restoreState) {
            case SIGNED_IN:
                return Destination.HOME;
            case OFFLINE_SAVED_SESSION:
                // Saved session could not be validated (offline). Guest-browse
                // home with the offline banner; re-validate when online. Never
                // present an unvalidated session as authenticated.
                return Destination.HOME_OFFLINE;
            case SIGNED_OUT:
            default:
                return Destination.LOGIN;
        }
    }
}
