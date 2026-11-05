package com.ikp.dev.app.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.error.ApiError;
import com.ikp.dev.app.core.error.ErrorMapper;
import com.ikp.dev.app.core.network.ApiClient;
import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.core.security.SessionManager;
import com.ikp.dev.app.core.security.TokenStore;
import com.ikp.dev.app.data.mapper.Mappers;
import com.ikp.dev.app.data.remote.AuthApi;
import com.ikp.dev.app.data.remote.dto.Dtos;
import com.ikp.dev.app.domain.model.Models;

import java.io.IOException;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Authentication repository — the ONLY place that touches the auth endpoints.
 * Methods are SYNCHRONOUS and must run on a background executor (ViewModels
 * own threading). All failures are returned as values (never thrown) so
 * ViewModels map them to UiState deterministically.
 */
public final class AuthRepository {

    /** Result of startup session restore (prompt §7 startup logic). */
    public enum RestoreState { SIGNED_IN, SIGNED_OUT, OFFLINE_SAVED_SESSION }

    public static final class RestoreResult {
        public final RestoreState state;
        @Nullable
        public final Models.User user;
        @Nullable
        public final ApiError error;

        RestoreResult(RestoreState state, Models.User user, ApiError error) {
            this.state = state;
            this.user = user;
            this.error = error;
        }

        public static RestoreResult signedIn(Models.User user) {
            return new RestoreResult(RestoreState.SIGNED_IN, user, null);
        }

        public static RestoreResult signedOut() {
            return new RestoreResult(RestoreState.SIGNED_OUT, null, null);
        }

        public static RestoreResult offlineSavedSession() {
            return new RestoreResult(RestoreState.OFFLINE_SAVED_SESSION, null, null);
        }
    }

    private final AuthApi auth;
    private final TokenStore tokenStore;
    private final SessionManager sessionManager;

    public AuthRepository(ApiClient client, TokenStore tokenStore, SessionManager sessionManager) {
        this.auth = client.create(AuthApi.class);
        this.tokenStore = tokenStore;
        this.sessionManager = sessionManager;
    }

    /** POST /auth/login → persist session, update SessionManager. */
    public Outcome<Models.User> login(@NonNull String email, @NonNull String password, @Nullable String deviceName) {
        try {
            Response<ApiResponse<Dtos.TokenResponseDto>> res = auth
                    .login(new Dtos.LoginRequestDto(email, password, deviceName))
                    .execute();
            if (!res.isSuccessful() || res.body() == null || res.body().data == null) {
                return Outcome.fail(ErrorMapper.fromResponse(res));
            }
            Dtos.TokenResponseDto tokens = res.body().data;
            if (tokens.accessToken == null || tokens.refreshToken == null || tokens.user == null) {
                return Outcome.fail(new ApiError(ApiError.BAD_RESPONSE, "error_unknown"));
            }
            Models.User user = Mappers.toUser(tokens);
            tokenStore.save(tokens.accessToken, tokens.refreshToken,
                    tokens.user.userId == null ? "" : tokens.user.userId,
                    tokens.user.displayName == null ? "" : tokens.user.displayName,
                    "", // email arrives with /auth/me — never fabricated
                    toJson(tokens.user.roles), deviceName);
            if (user != null) {
                sessionManager.onSignedIn(user.userId, user.displayName, email);
            }
            return Outcome.ok(user);
        } catch (IOException | RuntimeException e) {
            return Outcome.fail(ErrorMapper.fromThrowable(e));
        }
    }

    /** POST /auth/logout (best effort) + local credential clear. */
    public void logout() {
        String refresh = tokenStore.refreshToken();
        if (refresh != null) {
            try {
                auth.logout(new Dtos.RefreshRequestDto(refresh)).execute();
            } catch (IOException | RuntimeException ignored) {
                // Revocation is best-effort; local clear is mandatory below.
            }
        }
        sessionManager.onSignedOut();
    }

    /**
     * Startup restore (prompt §7):
     * 1. no saved session → SIGNED_OUT;
     * 2. saved session + /auth/me ok → SIGNED_IN(user) (profile cached);
     * 3. saved session + 401 → explicit single refresh attempt → SIGNED_IN
     *    or (on failure) clear + SIGNED_OUT;
     * 4. network unreachable → OFFLINE_SAVED_SESSION (guest browsing with
     *    cached content; session is re-validated when connectivity returns —
     *    an unvalidated session is NEVER presented as authenticated).
     */
    public RestoreResult restore() {
        if (!tokenStore.hasSession()) {
            sessionManager.onGuest();
            return RestoreResult.signedOut();
        }
        try {
            Response<ApiResponse<Dtos.MeResponseDto>> res = auth.me().execute();
            if (res.isSuccessful() && res.body() != null && res.body().data != null) {
                Models.User user = Mappers.toUser(res.body().data);
                if (user != null) {
                    sessionManager.onSignedIn(user.userId, user.displayName, user.email);
                    return RestoreResult.signedIn(user);
                }
            } else if (res.code() == 401) {
                // 3. one explicit refresh attempt (deterministic, off the
                //    authenticator path) then re-validate
                Dtos.TokenResponseDto tokens = refreshExplicit();
                if (tokens != null) {
                    Response<ApiResponse<Dtos.MeResponseDto>> retry = auth.me().execute();
                    if (retry.isSuccessful() && retry.body() != null && retry.body().data != null) {
                        Models.User user = Mappers.toUser(retry.body().data);
                        if (user != null) {
                            sessionManager.onSignedIn(user.userId, user.displayName, user.email);
                            return RestoreResult.signedIn(user);
                        }
                    }
                }
                sessionManager.onSessionExpired();
                return RestoreResult.signedOut();
            }
        } catch (IOException offline) {
            // 4. offline — keep stored session for later validation
            sessionManager.onUnvalidatedSession();
            return RestoreResult.offlineSavedSession();
        } catch (RuntimeException e) {
            sessionManager.onGuest();
            return RestoreResult.signedOut();
        }
        sessionManager.onGuest();
        return RestoreResult.signedOut();
    }

    /** GET /auth/me (synchronous). */
    public Outcome<Models.User> me() {
        try {
            Response<ApiResponse<Dtos.MeResponseDto>> res = auth.me().execute();
            if (res.isSuccessful() && res.body() != null && res.body().data != null) {
                Models.User user = Mappers.toUser(res.body().data);
                if (user != null) {
                    sessionManager.onSignedIn(user.userId, user.displayName, user.email);
                    cacheProfile(user);
                    return Outcome.ok(user);
                }
                return Outcome.fail(new ApiError(ApiError.BAD_RESPONSE, "error_unknown"));
            }
            return Outcome.fail(ErrorMapper.fromResponse(res));
        } catch (IOException | RuntimeException e) {
            return Outcome.fail(ErrorMapper.fromThrowable(e));
        }
    }

    /** GET /auth/sessions (synchronous). */
    public Outcome<List<Models.SessionInfo>> sessions() {
        try {
            Response<ApiResponse<List<Dtos.SessionDto>>> res = auth.sessions().execute();
            if (res.isSuccessful() && res.body() != null) {
                return Outcome.ok(Mappers.toSessions(ApiResponse.listOrEmpty(res.body().data)));
            }
            return Outcome.fail(ErrorMapper.fromResponse(res));
        } catch (IOException | RuntimeException e) {
            return Outcome.fail(ErrorMapper.fromThrowable(e));
        }
    }

    /** DELETE /auth/sessions/{id} (synchronous; own-session revocation rejected client-side). */
    public Outcome<Boolean> revokeSession(long sessionId, long currentSessionId) {
        if (sessionId == currentSessionId) {
            return Outcome.fail(new ApiError("VAL-CLIENT-001", "error_validation"));
        }
        try {
            Response<Void> res = auth.revokeSession(sessionId).execute();
            if (res.isSuccessful()) {
                return Outcome.ok(Boolean.TRUE);
            }
            return Outcome.fail(ErrorMapper.fromResponse(res));
        } catch (IOException | RuntimeException e) {
            return Outcome.fail(ErrorMapper.fromThrowable(e));
        }
    }

    private Dtos.TokenResponseDto refreshExplicit() {
        String refresh = tokenStore.refreshToken();
        if (refresh == null) {
            return null;
        }
        try {
            Response<ApiResponse<Dtos.TokenResponseDto>> res =
                    auth.refresh(new Dtos.RefreshRequestDto(refresh)).execute();
            if (res.isSuccessful() && res.body() != null && res.body().data != null) {
                Dtos.TokenResponseDto tokens = res.body().data;
                if (tokens.accessToken != null && tokens.refreshToken != null) {
                    tokenStore.updateTokens(tokens.accessToken, tokens.refreshToken);
                    return tokens;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through
        }
        return null;
    }

    /** Cache the validated profile for offline display (Room, no credentials). */
    private void cacheProfile(Models.User user) {
        try {
            com.ikp.dev.app.core.database.IkpDatabase db = ServiceLocatorBridge.databaseOrNull();
            if (db == null) {
                return;
            }
            com.ikp.dev.app.data.local.entity.CacheEntities.UserProfileEntity e =
                    new com.ikp.dev.app.data.local.entity.CacheEntities.UserProfileEntity();
            e.userId = user.userId == null ? "unknown" : user.userId;
            e.email = user.email;
            e.displayName = user.displayName;
            e.language = user.language;
            e.status = user.status;
            e.rolesJson = toJson(user.roles);
            e.permissionsJson = toJson(user.permissions);
            e.createdAtEpochMs = parseInstant(user.createdAt);
            e.cachedAtEpochMs = System.currentTimeMillis();
            db.userProfileDao().upsert(e);
        } catch (RuntimeException ignored) {
            // Cache write is best-effort; never blocks the login path.
        }
    }

    private static long parseInstant(@Nullable String iso) {
        if (iso == null) {
            return 0L;
        }
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String toJson(List<String> list) {
        if (list == null) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(list.get(i).replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    /** Avoids a direct ServiceLocator dependency cycle in tests. */
    public static final class ServiceLocatorBridge {
        private static volatile com.ikp.dev.app.core.database.IkpDatabase database;

        public static void provide(com.ikp.dev.app.core.database.IkpDatabase db) {
            database = db;
        }

        public static com.ikp.dev.app.core.database.IkpDatabase databaseOrNull() {
            return database;
        }
    }
}
