package com.ikp.dev.app;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.core.splashscreen.SplashScreen;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.ikp.dev.app.core.common.NetworkMonitor;
import com.ikp.dev.app.core.security.SessionManager;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.auth.AuthViewModel;
import com.ikp.dev.app.ui.splash.SplashViewModel;

import java.util.HashMap;
import java.util.Map;

/**
 * Single-activity host (Navigation Component, prompt §5/§6):
 * - SplashScreen API gate; destination decided by SplashViewModel;
 * - custom six-section bottom bar (BottomNavigationView caps at five items);
 * - tab switches preserve per-section state (saveState/restoreState);
 * - offline banner driven by NetworkMonitor;
 * - protected destinations/actions react to session expiry globally.
 */
public final class MainActivity extends AppCompatActivity {

    private NavController navController;
    private android.widget.LinearLayout bottomBar;
    private final Map<Integer, android.view.View> tabByDestination = new HashMap<>();
    private final Map<android.view.View, Integer> destinationByTab = new HashMap<>();
    private SessionManager sessionManager;
    private NetworkMonitor networkMonitor;
    private androidx.lifecycle.Observer<Boolean> offlineObserver;
    private androidx.lifecycle.Observer<SessionManager.SessionEvent> sessionObserver;
    private androidx.appcompat.app.AlertDialog sessionExpiredDialog;

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            NestedScrollView quranScroll = findViewById(R.id.quran_scroll);
            if (quranScroll != null && quranScroll.getVisibility() == View.VISIBLE) {
                float vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (vertical == 0f) vertical = event.getAxisValue(MotionEvent.AXIS_SCROLL);
                if (vertical != 0f) {
                    quranScroll.scrollBy(0, Math.round(-vertical * 96f));
                    return true;
                }
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = ServiceLocator.sessionManager();
        networkMonitor = ServiceLocator.networkMonitor();

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        if (host == null) {
            finish();
            return;
        }
        navController = host.getNavController();
        splash.setKeepOnScreenCondition(() -> false); // SplashFragment owns flow

        bottomBar = findViewById(R.id.bottom_bar);
        bindTabs();
        observeDestinations();
        observeOffline();
        observeSession();

        // Method 1: Check for in-app updates in background on startup
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                com.ikp.dev.app.core.update.AppUpdateManager.checkForUpdate(this, false);
            }
        }, 2000);
    }

    private void bindTabs() {
        tabByDestination.put(R.id.homeFragment, findViewById(R.id.btn_home));
        tabByDestination.put(R.id.libraryFragment, findViewById(R.id.btn_library));
        tabByDestination.put(R.id.academyFragment, findViewById(R.id.btn_academy));
        tabByDestination.put(R.id.researchFragment, findViewById(R.id.btn_research));
        tabByDestination.put(R.id.assistantFragment, findViewById(R.id.btn_assistant));
        tabByDestination.put(R.id.profileFragment, findViewById(R.id.btn_profile));

        for (Map.Entry<Integer, android.view.View> e : tabByDestination.entrySet()) {
            android.view.View tab = e.getValue();
            destinationByTab.put(tab, e.getKey());
            tab.setOnClickListener(v -> navigateToTab(e.getKey()));
        }
    }

    private void navigateToTab(int destinationId) {
        Integer current = null;
        for (Map.Entry<Integer, android.view.View> e : tabByDestination.entrySet()) {
            if (e.getValue().isSelected()) {
                current = e.getKey();
                break;
            }
        }
        if (current != null && current == destinationId) {
            return; // reselect: no-op (state stays)
        }
        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(R.id.homeFragment, true, true) // saveState of popped tabs
                .build();
        navController.navigate(destinationId, null, options);
    }

    private void observeDestinations() {
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            int activeTabId = (id == R.id.booksFragment) ? R.id.libraryFragment : id;
            boolean showBottomBar = tabByDestination.containsKey(activeTabId);
            bottomBar.setVisibility(showBottomBar ? android.view.View.VISIBLE : android.view.View.GONE);
            for (Map.Entry<Integer, android.view.View> e : tabByDestination.entrySet()) {
                boolean selected = e.getKey() == activeTabId;
                android.view.View tab = e.getValue();
                tab.setSelected(selected);
                tab.setActivated(selected);
                if (tab instanceof android.view.ViewGroup) {
                    android.view.ViewGroup vg = (android.view.ViewGroup) tab;
                    for (int i = 0; i < vg.getChildCount(); i++) {
                        android.view.View child = vg.getChildAt(i);
                        child.setSelected(selected);
                        child.setActivated(selected);
                    }
                }
            }
        });
    }

    private void observeOffline() {
        offlineObserver = online -> {
            android.view.View banner = findViewById(R.id.offline_banner);
            if (banner != null) {
                banner.setVisibility(Boolean.TRUE.equals(online)
                        ? android.view.View.GONE : android.view.View.VISIBLE);
            }
        };
        networkMonitor.observe(this, offlineObserver);
    }

    private void observeSession() {
        sessionObserver = event -> {
            if (event != null && event.state == SessionManager.AuthState.EXPIRED) {
                // Immediately transition state to signed-out to prevent sticky LiveData re-triggering loop
                sessionManager.onSignedOut();

                int currentDestId = navController.getCurrentDestination() != null
                        ? navController.getCurrentDestination().getId()
                        : -1;

                // Never display the session-expired popup on splash/startup or when already on login/onboarding
                if (currentDestId != R.id.loginFragment
                        && currentDestId != R.id.splashFragment
                        && currentDestId != R.id.onboardingFragment
                        && currentDestId != -1) {
                    showSessionExpiredDialog();
                    try {
                        navController.navigate(R.id.action_global_session_expired);
                    } catch (IllegalArgumentException ignored) {
                        // already navigating
                    }
                }
            }
        };
        sessionManager.state().observe(this, sessionObserver);
    }

    private void showSessionExpiredDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (sessionExpiredDialog != null && sessionExpiredDialog.isShowing()) {
            return;
        }
        sessionExpiredDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auth_session_expired_title)
                .setMessage(R.string.auth_session_expired_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionExpiredDialog != null && sessionExpiredDialog.isShowing()) {
            sessionExpiredDialog.dismiss();
            sessionExpiredDialog = null;
        }
    }

    /** Toast-free logout confirmation shared by Profile screen (reusable). */
    public static void confirmLogout(android.content.Context context, Runnable onConfirm) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.logout_confirm_action, (d, w) -> onConfirm.run())
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
