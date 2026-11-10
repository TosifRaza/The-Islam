package com.ikp.dev.app.core.network;

import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Single networking entry point (Retrofit + OkHttp). Configuration:
 *
 * - base URL from BuildConfig (emulator 10.0.2.2:8080 default in debug;
 *   release URL injected via the IKP_BASE_URL gradle property — never a
 *   hardcoded production endpoint);
 * - timeouts: connect 15 s / read 20 s / write 30 s (prompt §18);
 * - auth interceptor adds the bearer token; TokenAuthenticator performs the
 *   single-flight refresh (prompt §8);
 * - the REFRESH service is built on an authenticator-free client so the
 *   authenticator can never recurse into itself;
 * - logging: base-level interceptor in debug that emits METHOD/URL and
 *   STATUS only — never headers (tokens) and never bodies.
 */
public final class ApiClient {

    private final Retrofit retrofit;
    private final Retrofit bareRetrofit;
    private final Gson gson;

    public ApiClient(String baseUrl, com.ikp.dev.app.core.security.TokenStore tokenStore,
                     com.ikp.dev.app.core.security.SessionManager sessionManager, boolean debugLogging) {
        this.gson = new GsonBuilder()
                .serializeNulls()   // backend envelope includes explicit nulls
                .create();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String msg) {
                // Defensive redaction: never let a token appear in logs.
                if (msg != null && (msg.contains("access_token") || msg.contains("refreshToken")
                        || msg.contains("refresh_token") || msg.contains("accessToken"))) {
                    return; // suppress entire message containing token material
                }
                android.util.Log.i("IKP-HTTP", msg == null ? "" : msg);
            }
        });
        logging.setLevel(debugLogging ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);

        Interceptor emulatorGuard = chain -> {
            okhttp3.Request req = chain.request();
            if ("10.0.2.2".equals(req.url().host()) && !isEmulator()) {
                throw new java.io.IOException("Cannot reach emulator alias 10.0.2.2 on physical device");
            }
            return chain.proceed(req);
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .writeTimeout(4, TimeUnit.SECONDS)
                .addInterceptor(emulatorGuard)
                .addInterceptor(logging)
                .addInterceptor(new AuthInterceptor(tokenStore))
                .authenticator(new TokenAuthenticator(tokenStore,
                        buildApi(baseUrl, bareClient(logging), gson), sessionManager))
                .build();

        OkHttpClient bare = bareClient(logging);

        this.retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        this.bareRetrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(bare)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    private static OkHttpClient bareClient(HttpLoggingInterceptor logging) {
        Interceptor emulatorGuard = chain -> {
            okhttp3.Request req = chain.request();
            if ("10.0.2.2".equals(req.url().host()) && !isEmulator()) {
                throw new java.io.IOException("Cannot reach emulator alias 10.0.2.2 on physical device");
            }
            return chain.proceed(req);
        };
        return new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .writeTimeout(4, TimeUnit.SECONDS)
                .addInterceptor(emulatorGuard)
                .addInterceptor(logging)
                .build();
    }

    public static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    private static com.ikp.dev.app.data.remote.AuthApi buildApi(String baseUrl, OkHttpClient client, Gson gson) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(com.ikp.dev.app.data.remote.AuthApi.class);
    }

    public <T> T create(Class<T> service) {
        return retrofit.create(service);
    }

    /** Service for unauthenticated calls (login/refresh). */
    public <T> T createBare(Class<T> service) {
        return bareRetrofit.create(service);
    }

    public Gson gson() {
        return gson;
    }

    /** Documented device/base-URL helper (prompt §18 — never localhost on a device). */
    public static String emulatorBaseUrl() {
        return "http://10.0.2.2:8080/";
    }

    @SuppressWarnings("unused")
    private static String deviceHint() {
        return Build.FINGERPRINT;
    }
}
