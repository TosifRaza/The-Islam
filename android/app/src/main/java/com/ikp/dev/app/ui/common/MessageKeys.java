package com.ikp.dev.app.ui.common;

import com.ikp.dev.app.R;

/**
 * Android-side resolver: stable message key → string resource id.
 * Keeps {@code core.error.ErrorMapper} free of android/R imports so it stays
 * JVM-testable. Keys not present here map to error_unknown defensively.
 */
public final class MessageKeys {

    private MessageKeys() {
    }

    public static int resIdForKey(String key) {
        if (key == null) {
            return R.string.error_unknown;
        }
        switch (key) {
            case "error_network":
                return R.string.error_network;
            case "error_timeout":
                return R.string.error_timeout;
            case "error_server":
                return R.string.error_server;
            case "error_auth":
                return R.string.error_auth;
            case "error_forbidden":
                return R.string.error_forbidden;
            case "error_not_found":
                return R.string.error_not_found;
            case "error_validation":
                return R.string.error_validation;
            case "error_rate_limited":
                return R.string.error_rate_limited;
            case "error_credentials":
                return R.string.error_credentials;
            case "error_account_suspended":
                return R.string.error_account_suspended;
            case "error_unknown":
                return R.string.error_unknown;
            case "error_timeout_note":
                return R.string.error_timeout;
            case "book_download_not_allowed":
                return R.string.book_download_not_allowed;
            case "state_error_body":
                return R.string.state_error_body;
            default:
                return R.string.error_unknown;
        }
    }
}
