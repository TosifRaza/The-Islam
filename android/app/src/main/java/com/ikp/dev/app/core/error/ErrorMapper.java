package com.ikp.dev.app.core.error;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import okhttp3.ResponseBody;
import retrofit2.HttpException;
import retrofit2.Response;

/**
 * Maps any failure to an {@link ApiError}. PURE JAVA (no android.* / R
 * imports) so it is unit-testable on the JVM and reusable by the
 * SDK-free test path.
 *
 * User-facing message keys are stable string-resource names
 * (e.g. "error_credentials"); the Android layer resolves them via
 * {@code ui.common.MessageKeys.resIdForKey} so this class stays pure.
 *
 * Backend error contract: RFC 7807 application/problem+json with a stable
 * "code" property (docs/05-api-specification.md §2). Server detail text is
 * never shown verbatim to users.
 */
public final class ErrorMapper {

    // Stable backend codes from the approved catalogue
    public static final String AUTH_INVALID_CREDENTIALS = "AUTH-001";
    public static final String AUTH_UNAUTHORIZED = "AUTH-002";
    public static final String AUTH_SUSPENDED = "AUTH-004";
    public static final String AUTH_BAD_REFRESH = "AUTH-005";
    public static final String AUTHZ = "AUTHZ-001";
    public static final String AUTHZ_OWNER = "AUTHZ-002";
    public static final String VALIDATION = "VAL-001";
    public static final String NOT_FOUND = "NF-001";
    public static final String RATE_LIMIT = "RATE-001";
    public static final String LICENCE = "LIC-001";
    public static final String UPLOAD_NOT_CLEAN = "UPLOAD-002";
    public static final String WORKFLOW = "WORKFLOW-001";
    public static final String SERVER = "SERVER-001";

    private static final Gson GSON = new Gson();

    private ErrorMapper() {
    }

    /** problem+json body model (subset the app needs). */
    public static final class Problem {
        public String type;
        public String title;
        public Integer status;
        public String code;
        public String detail;
        public String instance;
    }

    /** Parse a problem+json body. Returns null when the body is not problem+json. */
    public static Problem parseProblem(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            Problem p = GSON.fromJson(body, Problem.class);
            return (p != null && p.code != null) ? p : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    /** Map a stable backend code to a user-facing message key. */
    public static String messageKeyForCode(String code) {
        if (code == null) {
            return "error_unknown";
        }
        switch (code) {
            case AUTH_INVALID_CREDENTIALS:
                return "error_credentials";
            case AUTH_UNAUTHORIZED:
            case AUTH_BAD_REFRESH:
                return "error_auth";
            case AUTH_SUSPENDED:
                return "error_account_suspended";
            case AUTHZ:
            case AUTHZ_OWNER:
                return "error_forbidden";
            case VALIDATION:
                return "error_validation";
            case NOT_FOUND:
                return "error_not_found";
            case RATE_LIMIT:
                return "error_rate_limited";
            case LICENCE:
                return "book_download_not_allowed";
            case SERVER:
                return "error_server";
            default:
                return "error_unknown";
        }
    }

    /** Map an HTTP status when no problem+json code could be parsed. */
    public static String messageKeyForStatus(int status) {
        if (status == 401) {
            return "error_auth";
        }
        if (status == 403) {
            return "error_forbidden";
        }
        if (status == 404) {
            return "error_not_found";
        }
        if (status == 429) {
            return "error_rate_limited";
        }
        if (status >= 400 && status < 500) {
            return "error_validation";
        }
        if (status >= 500) {
            return "error_server";
        }
        return "error_unknown";
    }

    /**
     * Top-level mapping: retrofit HttpException → problem+json code when
     * available; IOException family → offline/timeout; anything else → unknown.
     */
    public static ApiError fromThrowable(Throwable t) {
        if (t instanceof HttpException) {
            return fromResponse(((HttpException) t).response());
        }
        if (t instanceof SocketTimeoutException) {
            return new ApiError(ApiError.TIMEOUT, "error_timeout");
        }
        if (t instanceof UnknownHostException || t instanceof ConnectException || t instanceof IOException) {
            return new ApiError(ApiError.OFFLINE, "error_network");
        }
        return new ApiError(ApiError.UNKNOWN, "error_unknown");
    }

    /** Map a raw retrofit Response (error branch). Null response → unknown. */
    public static ApiError fromResponse(Response<?> response) {
        if (response == null) {
            return new ApiError(ApiError.UNKNOWN, "error_unknown");
        }
        ResponseBody body = response.errorBody();
        if (body != null) {
            try {
                String text = body.string();
                Problem p = parseProblem(text);
                if (p != null) {
                    return new ApiError(p.code, messageKeyForCode(p.code), p.detail);
                }
            } catch (IOException ignored) {
                // fall through to status mapping
            }
        }
        return new ApiError("HTTP-" + response.code(), messageKeyForStatus(response.code()));
    }
}
