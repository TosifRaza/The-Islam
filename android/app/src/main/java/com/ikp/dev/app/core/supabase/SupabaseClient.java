package com.ikp.dev.app.core.supabase;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ikp.dev.app.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit-backed client configured for Supabase REST and Auth endpoints.
 */
public final class SupabaseClient {

    private final String baseUrl;
    private final String anonKey;
    private final SupabaseInterceptor authInterceptor;
    private final Retrofit retrofit;
    private final Gson gson;
    private final SupabaseAuthApi authApi;
    private final SupabaseDatabaseApi databaseApi;

    public SupabaseClient(String baseUrl, String anonKey, boolean debugLogging) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.anonKey = anonKey;
        this.authInterceptor = new SupabaseInterceptor(anonKey);
        this.gson = new GsonBuilder().serializeNulls().create();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(msg -> {
            if (msg != null && (msg.contains("access_token") || msg.contains("refresh_token")
                    || msg.contains("apikey") || msg.contains("Authorization"))) {
                return;
            }
            android.util.Log.i("SUPABASE-HTTP", msg == null ? "" : msg);
        });
        logging.setLevel(debugLogging ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .addInterceptor(authInterceptor)
                .build();

        this.retrofit = new Retrofit.Builder()
                .baseUrl(this.baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        this.authApi = retrofit.create(SupabaseAuthApi.class);
        this.databaseApi = retrofit.create(SupabaseDatabaseApi.class);
    }

    public static SupabaseClient createDefault(boolean debugLogging) {
        return new SupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, debugLogging);
    }

    /** Set user JWT token once logged in */
    public void setSessionToken(String accessToken) {
        authInterceptor.setUserToken(accessToken);
    }

    /** Clear user JWT on logout */
    public void clearSession() {
        authInterceptor.setUserToken(null);
    }

    public SupabaseAuthApi auth() {
        return authApi;
    }

    public SupabaseDatabaseApi db() {
        return databaseApi;
    }

    public <T> T create(Class<T> service) {
        return retrofit.create(service);
    }

    public Gson getGson() {
        return gson;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
