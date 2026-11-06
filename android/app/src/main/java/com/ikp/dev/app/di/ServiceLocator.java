package com.ikp.dev.app.di;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.ikp.dev.app.BuildConfig;
import com.ikp.dev.app.core.common.NetworkMonitor;
import com.ikp.dev.app.core.database.IkpDatabase;
import com.ikp.dev.app.core.network.ApiClient;
import com.ikp.dev.app.core.security.EncryptedTokenStore;
import com.ikp.dev.app.core.security.SessionManager;
import com.ikp.dev.app.core.security.TokenStore;
import com.ikp.dev.app.core.supabase.SupabaseClient;
import com.ikp.dev.app.data.remote.AcademyApi;
import com.ikp.dev.app.data.repository.AcademyRepository;
import com.ikp.dev.app.data.repository.AuthRepository;
import com.ikp.dev.app.data.repository.AuthRepository.ServiceLocatorBridge;
import com.ikp.dev.app.data.repository.BookRepository;
import com.ikp.dev.app.data.repository.ContentRepository;
import com.ikp.dev.app.data.repository.PersonalLibraryRepository;
import com.ikp.dev.app.data.repository.UserContentRepository;
import com.ikp.dev.app.data.repository.SubjectRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manual dependency injection (Service Locator pattern).
 *
 * DECISION (architecture, documented in the M3 report): Java-only codebase +
 * pinned toolchain — annotation-processor DI (Hilt/Dagger) would require the
 * Kotlin toolchain (kapt/ksp) that the approved stack excludes. A hand-rolled
 * locator keeps the dependency graph explicit, small and testable
 * ({@link #resetForTests()}). Revisit only with an approved stack change.
 */
public final class ServiceLocator {

    private static final Object LOCK = new Object();

    private static IkpDatabase database;
    private static TokenStore tokenStore;
    private static SessionManager sessionManager;
    private static NetworkMonitor networkMonitor;
    private static ApiClient apiClient;
    private static SupabaseClient supabaseClient;
    private static AuthRepository authRepository;
    private static SubjectRepository subjectRepository;
    private static BookRepository bookRepository;
    private static ContentRepository contentRepository;
    private static UserContentRepository userContentRepository;
    private static PersonalLibraryRepository personalLibraryRepository;
    private static AcademyRepository academyRepository;
    private static ExecutorService ioExecutor;
    private static SharedPreferences appPrefs;
    private static AppExecutors appExecutors;

    /** Named executors: ViewModel work must never run on the main thread. */
    public static final class AppExecutors {
        public final ExecutorService io = Executors.newFixedThreadPool(3);
        public final ExecutorService single = Executors.newSingleThreadExecutor();
    }

    private ServiceLocator() {
    }

    public static void init(Context context) {
        synchronized (LOCK) {
            if (ioExecutor != null) {
                return; // idempotent (process-wide singleton graph)
            }
            Context app = context.getApplicationContext();
            appPrefs = app.getSharedPreferences("ikp_prefs", Context.MODE_PRIVATE);
            database = IkpDatabase.build(app);
            tokenStore = new EncryptedTokenStore(app);
            sessionManager = new SessionManager(tokenStore);
            networkMonitor = new NetworkMonitor(app);
            apiClient = new ApiClient(BuildConfig.BASE_URL, tokenStore, sessionManager,
                    BuildConfig.DEBUG);
            supabaseClient = SupabaseClient.createDefault(BuildConfig.DEBUG);
            if (tokenStore.hasSession() && tokenStore.accessToken() != null) {
                supabaseClient.setSessionToken(tokenStore.accessToken());
            }
            authRepository = new AuthRepository(apiClient, tokenStore, sessionManager);
            ServiceLocatorBridge.provide(database);
            subjectRepository = new SubjectRepository(apiClient, database);
            bookRepository = new BookRepository(apiClient, database);
            contentRepository = new ContentRepository(apiClient, database);
            userContentRepository = new UserContentRepository(apiClient);
            personalLibraryRepository = new PersonalLibraryRepository(apiClient);
            academyRepository = new AcademyRepository(apiClient.create(AcademyApi.class));
            appExecutors = new AppExecutors();
        }
    }

    public static IkpDatabase database() {
        return database;
    }

    public static TokenStore tokenStore() {
        return tokenStore;
    }

    public static SessionManager sessionManager() {
        return sessionManager;
    }

    public static NetworkMonitor networkMonitor() {
        return networkMonitor;
    }

    public static AuthRepository authRepository() {
        return authRepository;
    }

    public static SubjectRepository subjectRepository() {
        return subjectRepository;
    }

    public static BookRepository bookRepository() {
        return bookRepository;
    }

    public static ContentRepository contentRepository() {
        return contentRepository;
    }

    public static UserContentRepository userContentRepository() {
        return userContentRepository;
    }

    public static PersonalLibraryRepository personalLibraryRepository() {
        return personalLibraryRepository;
    }

    /** M5: learning & research surface (academy, quizzes, flashcards, research,
     *  scholars, fatwas — one API client, one repository). */
    public static AcademyRepository academyRepository() {
        return academyRepository;
    }

    public static ApiClient apiClient() {
        return apiClient;
    }

    public static SupabaseClient supabaseClient() {
        return supabaseClient;
    }

    public static AppExecutors executors() {
        return appExecutors;
    }

    public static SharedPreferences appPrefs() {
        return appPrefs;
    }

    @Nullable
    public static String backendBaseUrl() {
        return BuildConfig.BASE_URL;
    }

    /** Test hook — drops the whole graph so tests can install fakes. */
    public static synchronized void resetForTests() {
        database = null;
        tokenStore = null;
        sessionManager = null;
        networkMonitor = null;
        apiClient = null;
        supabaseClient = null;
        authRepository = null;
        subjectRepository = null;
        bookRepository = null;
        contentRepository = null;
        userContentRepository = null;
        personalLibraryRepository = null;
        academyRepository = null;
        appExecutors = null;
        appPrefs = null;
    }
}
