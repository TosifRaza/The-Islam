package com.ikp.dev.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.core.network.TokenAuthenticator;
import com.ikp.dev.app.core.security.SessionManager;
import com.ikp.dev.app.core.security.TokenStore;
import com.ikp.dev.app.data.remote.AuthApi;
import com.ikp.dev.app.data.remote.dto.Dtos;

import org.junit.Rule;
import org.junit.Test;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * JVM tests for SAFE TOKEN REFRESH (prompt §8): rotation persistence and the
 * single-flight guarantee (concurrent 401s → exactly one refresh call).
 * Uses a hand-rolled fake AuthApi + in-memory TokenStore — no Android, no
 * network.
 */
public class TokenRefreshTest {

    @Rule
    public final InstantTaskExecutorRule instantExecutor = new InstantTaskExecutorRule();

    // ---------------- fakes ----------------

    static final class FakeTokenStore implements TokenStore {
        String access = "stale-access";
        String refresh = "valid-refresh";
        int tokenUpdates;
        boolean cleared;

        @Override
        public String accessToken() {
            return access;
        }

        @Override
        public String refreshToken() {
            return refresh;
        }

        @Override
        public String userId() {
            return "u-1";
        }

        @Override
        public String displayName() {
            return "Dev";
        }

        @Override
        public String email() {
            return null;
        }

        @Override
        public String rolesJson() {
            return "[]";
        }

        @Override
        public String deviceName() {
            return null;
        }

        @Override
        public void save(String a, String r, String u, String d, String e, String ro, String dn) {
            access = a;
            refresh = r;
        }

        @Override
        public void updateTokens(String a, String r) {
            tokenUpdates++;
            access = a;
            refresh = r;
        }

        @Override
        public void clear() {
            cleared = true;
            access = null;
            refresh = null;
        }

        @Override
        public boolean hasSession() {
            return refresh != null;
        }
    }

    /** Fake AuthApi whose refresh either succeeds once or always fails. */
    static final class FakeAuthApi implements AuthApi {
        final AtomicInteger refreshCalls = new AtomicInteger();
        final boolean failRefresh;

        FakeAuthApi(boolean failRefresh) {
            this.failRefresh = failRefresh;
        }

        @Override
        public Call<ApiResponse<Dtos.TokenResponseDto>> login(Dtos.LoginRequestDto request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Call<ApiResponse<Dtos.TokenResponseDto>> refresh(Dtos.RefreshRequestDto request) {
            refreshCalls.incrementAndGet();
            // Simulate a slow rotating endpoint so concurrent callers pile up
            return new FakeCall<>(failRefresh ? error401() : ok());
        }

        @Override
        public Call<Void> logout(Dtos.RefreshRequestDto request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Call<ApiResponse<Dtos.MeResponseDto>> me() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Call<ApiResponse<java.util.List<Dtos.SessionDto>>> sessions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Call<Void> revokeSession(long sessionId) {
            throw new UnsupportedOperationException();
        }

        private static Response<ApiResponse<Dtos.TokenResponseDto>> ok() {
            Dtos.TokenResponseDto tokens = new Dtos.TokenResponseDto();
            tokens.accessToken = "new-access";
            tokens.refreshToken = "rotated-refresh";
            return Response.success(new ApiResponse<>(tokens, null));
        }

        private static Response<ApiResponse<Dtos.TokenResponseDto>> error401() {
            return Response.error(401, ResponseBody.create(
                    MediaType.get("application/problem+json"),
                    "{\"code\":\"AUTH-005\",\"detail\":\"invalid refresh token\"}"));
        }
    }

    /** Minimal retrofit Call that replays a prepared response. */
    static final class FakeCall<T> implements Call<T> {
        private final Response<T> response;

        FakeCall(Response<T> response) {
            this.response = response;
        }

        @Override
        public Response<T> execute() {
            try {
                Thread.sleep(150); // widen the race window
            } catch (InterruptedException ignored) {
            }
            return response;
        }

        @Override
        public void enqueue(Callback<T> callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isExecuted() {
            return false;
        }

        @Override
        public Call<T> clone() {
            return this;
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public void cancel() {
        }

        @Override
        public okhttp3.Request request() {
            return new Request.Builder().url("http://localhost/").build();
        }

        @Override
        public okio.Timeout timeout() {
            return okio.Timeout.NONE;
        }
    }

    private static Request requestWithToken(String token) {
        return new Request.Builder()
                .url("http://10.0.2.2:8080/api/v1/auth/me")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create("{}", okhttp3.MediaType.get("application/json")))
                .build();
    }

    @Test
    public void successfulRefreshRotatesAndPersistsNewPair() throws Exception {
        FakeTokenStore store = new FakeTokenStore();
        FakeAuthApi api = new FakeAuthApi(false);
        SessionManager sessions = new SessionManager(store);
        TokenAuthenticator authenticator = new TokenAuthenticator(store, api, sessions);

        okhttp3.Response response = new okhttp3.Response.Builder()
                .request(requestWithToken("stale-access"))
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .build();

        Request retry = authenticator.authenticate(null, response);

        assertNotNull(retry);
        assertEquals("Bearer new-access", retry.header("Authorization"));
        assertEquals(1, api.refreshCalls.get());
        assertEquals(1, store.tokenUpdates);
        assertEquals("rotated-refresh", store.refreshToken());
    }

    @Test
    public void failedRefreshClearsSessionAndAbortsRequest() throws Exception {
        FakeTokenStore store = new FakeTokenStore();
        FakeAuthApi api = new FakeAuthApi(true);
        SessionManager sessions = new SessionManager(store);
        TokenAuthenticator authenticator = new TokenAuthenticator(store, api, sessions);

        okhttp3.Response response = new okhttp3.Response.Builder()
                .request(requestWithToken("stale-access"))
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .build();

        Request retry = authenticator.authenticate(null, response);

        assertNull(retry);
        assertEquals(1, api.refreshCalls.get());
        assertEquals(0, store.tokenUpdates);
        assertNull(store.accessToken()); // session cleared — secure state
    }

    @Test
    public void concurrent401sTriggerExactlyOneRefresh() throws Exception {
        final FakeTokenStore store = new FakeTokenStore();
        final FakeAuthApi api = new FakeAuthApi(false);
        SessionManager sessions = new SessionManager(store);
        final TokenAuthenticator authenticator = new TokenAuthenticator(store, api, sessions);

        okhttp3.Response response = new okhttp3.Response.Builder()
                .request(requestWithToken("stale-access"))
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .build();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(8);
        final AtomicInteger retries = new AtomicInteger();
        for (int i = 0; i < 8; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    // All threads use the SAME stale token → after the first
                    // thread refreshes, the others must reuse the fresh token
                    // WITHOUT extra refresh calls.
                    String before = store.accessToken();
                    okhttp3.Response resp = new okhttp3.Response.Builder()
                            .request(requestWithToken(before))
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(401)
                            .message("Unauthorized")
                            .build();
                    Request retry = authenticator.authenticate(null, resp);
                    if (retry != null) {
                        retries.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals("exactly one refresh for 8 concurrent 401s", 1, api.refreshCalls.get());
        assertEquals(8, retries.get());
        assertEquals("rotated-refresh", store.refreshToken());
    }
}
