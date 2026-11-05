package com.ikp.dev.app;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.Configuration;

import com.ikp.dev.app.di.ServiceLocator;

/**
 * Application entry point:
 * - initialises the dependency graph once;
 * - applies the persisted appearance (light/dark/system) before any Activity;
 * - provides a WorkManager configuration (default worker factory — the
 *   PendingSyncWorker pulls its dependencies from the locator).
 */
public final class IkpApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ServiceLocator.init(this);
        applyPersistedTheme();
    }

    private void applyPersistedTheme() {
        String theme = ServiceLocator.appPrefs() != null
                ? ServiceLocator.appPrefs().getString("pref_theme", "system") : "system";
        if ("light".equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if ("dark".equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(base);
    }
}
