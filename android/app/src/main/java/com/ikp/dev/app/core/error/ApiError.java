package com.ikp.dev.app.core.error;

import androidx.annotation.Nullable;

/**
 * Normalised application error. Carries the stable backend problem+json code
 * (AUTH-001, RATE-001, …) or a synthetic local code (NET-OFFLINE, NET-TIMEOUT,
 * NET-UNKNOWN) plus a stable message KEY resolved to a string resource by the
 * Android layer ({@code ui.common.MessageKeys}). Pure data — JVM-testable.
 */
public final class ApiError {

    // Synthetic local codes (no backend counterpart)
    public static final String OFFLINE = "NET-OFFLINE";
    public static final String TIMEOUT = "NET-TIMEOUT";
    public static final String UNKNOWN = "NET-UNKNOWN";
    public static final String BAD_RESPONSE = "NET-BADRESPONSE";

    public final String code;
    public final String messageKey;
    @Nullable
    public final String rawDetail;

    public ApiError(String code, String messageKey, @Nullable String rawDetail) {
        this.code = code;
        this.messageKey = messageKey;
        this.rawDetail = rawDetail;
    }

    public ApiError(String code, String messageKey) {
        this(code, messageKey, null);
    }

    @Override
    public String toString() {
        return "ApiError{" + code + "}";
    }
}
