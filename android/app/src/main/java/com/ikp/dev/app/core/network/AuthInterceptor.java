package com.ikp.dev.app.core.network;

import com.ikp.dev.app.core.security.TokenStore;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Adds the bearer token to every request when a session exists. Tokens are
 * read from the Keystore-backed store and never logged.
 */
public final class AuthInterceptor implements Interceptor {

    private final TokenStore tokenStore;

    public AuthInterceptor(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String token = tokenStore.accessToken();
        if (token == null || token.isEmpty() || original.header("Authorization") != null) {
            return chain.proceed(original);
        }
        Request authed = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
        return chain.proceed(authed);
    }
}
