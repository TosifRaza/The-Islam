package com.ikp.dev.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.ikp.dev.app.R;
import com.ikp.dev.app.di.ServiceLocator;

import androidx.core.os.LocaleListCompat;

/**
 * Settings: appearance (WORKING light/dark/system via AppCompatDelegate),
 * language preference (working in-app switch EN/AR; final list gated by
 * OD-02), and documented-unavailable sections (personal-info editing,
 * reader prefs, notifications, data export, account deletion) that state
 * plainly why they cannot act yet.
 */
public final class SettingsFragment extends Fragment {

    private static final String PREF_THEME = "pref_theme";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        RadioGroup rgTheme = view.findViewById(R.id.rg_theme);
        String theme = currentTheme();
        if ("light".equals(theme)) {
            rgTheme.check(R.id.rb_theme_light);
        } else if ("dark".equals(theme)) {
            rgTheme.check(R.id.rb_theme_dark);
        } else {
            rgTheme.check(R.id.rb_theme_system);
        }
        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = checkedId == R.id.rb_theme_light ? "light"
                    : checkedId == R.id.rb_theme_dark ? "dark" : "system";
            persistTheme(mode);
            applyTheme(mode);
        });

        MaterialButtonToggleGroup langGroup = view.findViewById(R.id.tg_language);
        String locale = com.ikp.dev.app.core.common.LocalePrefs.currentLanguage();
        langGroup.check("ar".equals(locale) ? R.id.lang_ar : R.id.lang_en);
        langGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            String tag = checkedId == R.id.lang_ar ? "ar" : "en";
            com.ikp.dev.app.core.common.LocalePrefs.setLanguage(requireContext(), tag);
        });

        view.findViewById(R.id.row_data_export).setOnClickListener(v ->
                Snackbar.make(view, R.string.settings_data_export_note, Snackbar.LENGTH_LONG).show());
        view.findViewById(R.id.row_delete_account).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.settings_delete_account)
                        .setMessage(R.string.settings_delete_account_note)
                        .setPositiveButton(R.string.action_ok, null)
                        .show());
    }

    private String currentTheme() {
        android.content.SharedPreferences p = ServiceLocator.appPrefs();
        return p == null ? "system" : p.getString(PREF_THEME, "system");
    }

    private void persistTheme(String mode) {
        android.content.SharedPreferences p = ServiceLocator.appPrefs();
        if (p != null) {
            p.edit().putString(PREF_THEME, mode).apply();
        }
    }

    private void applyTheme(String mode) {
        if ("light".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if ("dark".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
