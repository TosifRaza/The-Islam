package com.ikp.dev.app.core.common;

import android.content.Context;

import androidx.core.os.LocaleListCompat;

/**
 * Per-app language preference helper (AppCompat per-app locales).
 * OD-02 controls the FINAL language list; this only stores/apply the user's
 * in-app choice (English/Arabic proven at M3) and never declares a language
 * list. Arabic is applied through LocaleListCompat so RTL flips automatically
 * (manifest already sets supportsRtl).
 */
public final class LocalePrefs {

    public static final String PREF_KEY = "pref_language";

    private LocalePrefs() {
    }

    public static String currentLanguage() {
        LocaleListCompat locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) {
            String tag = locales.get(0).getLanguage();
            if ("ar".equals(tag)) {
                return "ar";
            }
        }
        return "en";
    }

    public static void setLanguage(Context context, String tag) {
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(tag));
    }
}
