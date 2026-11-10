package com.ikp.dev.app.core.network;

import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Call;
import com.ikp.dev.app.data.remote.AuthApi;
import com.ikp.dev.app.data.remote.dto.Dtos;
import com.ikp.dev.app.core.security.TokenStore;
import com.ikp.dev.app.core.security.SessionManager;

/**
 * OkHttp Authenticator implementing SAFE TOKEN REFRESH (prompt §8):
 *
 * - single-flight: concurrent 401s share one refresh call (synchronized +
 *   token-change detection, no thundering herd of refresh requests);
 * - refresh tokens are single-use (backend rotates them), so the new pair is
 *   persisted BEFORE any retry happens;
 * - on refresh failure the session is expired (SessionManager.EXPIRED) and
 *   the original request fails — the UI returns the user to a secure state;
 * - the refresh call itself never goes through this authenticator (no
 *   recursion, built on a bare authenticator-free client).
 *
 * No token values are ever logged.
 */
public final class TokenAuthenticator implements okhttp3.Authenticator {

    private final TokenStore tokenStore;
    private final AuthApi authApi;          // authenticator-free retrofit service
    private final SessionManager sessionManager;
    private final Object refreshLock = new Object();

    public TokenAuthenticator(TokenStore tokenStore, AuthApi authApi, SessionManager sessionManager) {
        this.tokenStore = tokenStore;
        this.authApi = authApi;
        this.sessionManager = sessionManager;
    }

    @Override
    @Nullable
    public Request authenticate(@Nullable Route route, @Nullable Response response) throws IOException {
        if (response == null) {
            return null;
        }
        // Never retry the refresh endpoint itself (prevents loops).
        String path = response.request().url().encodedPath();
        if (path.contains("/auth/refresh") || path.contains("/auth/login")) {
            return null;
        }

        // Avoid infinite retry loops if we already retried once
        if (response.priorResponse() != null) {
            return null;
        }

        synchronized (refreshLock) {
            String currentAccess = tokenStore.accessToken();
            String requestToken = response.request().header("Authorization");

            // Another thread already refreshed while we waited on the lock:
            // if the failed request used a STALE token, retry with the new one.
            if (currentAccess != null && requestToken != null
                    && !requestToken.equals("Bearer " + currentAccess)) {
                return retryWith(response.request(), currentAccess);
            }

            String refresh = tokenStore.refreshToken();
            if (refresh == null || refresh.isEmpty()) {
                if (tokenStore.hasSession()) {
                    sessionManager.onSessionExpired();
                }
                return null;
            }

            // Supabase session handling:
            if (isSupabaseToken(currentAccess)) {
                com.ikp.dev.app.core.supabase.SupabaseClient sb =
                        com.ikp.dev.app.di.ServiceLocator.supabaseClient();
                if (sb != null) {
                    try {
                        java.util.Map<String, String> body = new java.util.HashMap<>();
                        body.put("refresh_token", refresh);
                        retrofit2.Response<com.ikp.dev.app.core.supabase.SupabaseModels.AuthResponse> refreshRes =
                                sb.auth().refreshToken("refresh_token", body).execute();
                        if (refreshRes.isSuccessful() && refreshRes.body() != null
                                && refreshRes.body().accessToken != null) {
                            String newAccess = refreshRes.body().accessToken;
                            String newRefresh = refreshRes.body().refreshToken != null
                                    ? refreshRes.body().refreshToken : refresh;
                            tokenStore.updateTokens(newAccess, newRefresh);
                            sb.setSessionToken(newAccess);
                            return retryWith(response.request(), newAccess);
                        } else if (refreshRes.code() == 400 || refreshRes.code() == 401) {
                            // Supabase explicitly rejected the refresh token — session is revoked!
                            if (tokenStore.hasSession()) {
                                sessionManager.onSessionExpired();
                            }
                            return null;
                        }
                    } catch (Exception ignored) {
                    }
                }
                // An auxiliary API endpoint rejecting a Supabase token must never invalidate
                // the active Supabase session. Abort the individual request safely.
                return null;
            }

            // Single-flight refresh (synchronous, once) for backend identity:
            Dtos.TokenResponseDto tokens = refreshSync(refresh);
            if (tokens == null || tokens.accessToken == null || tokens.refreshToken == null) {
                if (tokenStore.hasSession()) {
                    sessionManager.onSessionExpired();
                }
                return null;
            }
            tokenStore.updateTokens(tokens.accessToken, tokens.refreshToken);
            return retryWith(response.request(), tokens.accessToken);
        }
    }

    public static boolean isSupabaseToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                int pad = 4 - (parts[1].length() % 4);
                String padded = pad == 4 ? parts[1] : parts[1] + "====".substring(0, pad);
                byte[] bytes = java.util.Base64.getUrlDecoder().decode(padded);
                String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                return json.contains("supabase.co") || json.contains("\"aud\":\"authenticated\"");
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Blocking refresh; returns null on any failure (never throws). */
    @Nullable
    Dtos.TokenResponseDto refreshSync(String refreshToken) {
        try {
            Call<ApiResponse<Dtos.TokenResponseDto>> call =
                    authApi.refresh(new Dtos.RefreshRequestDto(refreshToken));
            retrofit2.Response<ApiResponse<Dtos.TokenResponseDto>> res = call.execute();
            if (res.isSuccessful() && res.body() != null && res.body().data != null) {
                return res.body().data;
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Request retryWith(Request original, String accessToken) {
        return original.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .build();
    }
}
