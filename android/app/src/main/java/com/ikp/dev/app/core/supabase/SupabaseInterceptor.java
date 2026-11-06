package com.ikp.dev.app.core.supabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Injects required Supabase headers (apikey, Authorization, Content-Type).
 * If a user token is provided, Authorization uses Bearer <userToken>;
 * otherwise it defaults to Bearer <anonKey>.
 */
public final class SupabaseInterceptor implements Interceptor {

    private final String anonKey;
    @Nullable
    private volatile String userToken;

    public SupabaseInterceptor(String anonKey) {
        this.anonKey = anonKey;
    }

    public void setUserToken(@Nullable String userToken) {
        this.userToken = userToken;
    }

    @Nullable
    public String getUserToken() {
        return userToken;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String currentToken = userToken;
        String authHeader = "Bearer " + (currentToken != null && !currentToken.trim().isEmpty() ? currentToken : anonKey);

        Request.Builder builder = original.newBuilder()
                .header("apikey", anonKey)
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json");

        return chain.proceed(builder.build());
    }
}
