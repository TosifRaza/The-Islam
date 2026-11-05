package com.ikp.dev.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * App launch + navigation (instrumentation, prompt §21).
 * Runs on a device/emulator (CI provides one; this sandbox has no KVM —
 * recorded honestly in the M3 test evidence).
 *
 * Expected flows without a saved session: splash → login (deterministic,
 * no network needed because restore() short-circuits when no refresh token
 * exists). With a saved session the flow validates against the configured
 * backend.
 */
@RunWith(AndroidJUnit4.class)
public class AppLaunchTest {

    @Test
    public void launchesAndReachesSignInWhenNoSession() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        // Sign-in screen must eventually appear (no stored session).
        onView(withId(R.id.et_email)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_login)).check(matches(isDisplayed()));
        scenario.close();
    }

    @Test
    public void signInShowsValidationErrorsForEmptyInput() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        onView(withId(R.id.btn_login)).perform(androidx.test.espresso.action.ViewActions.click());
        // Email field must show the inline validation error
        onView(withText(com.ikp.dev.app.R.string.auth_invalid_email))
                .check(matches(isDisplayed()));
        scenario.close();
    }

    @Test
    public void bottomBarHiddenOnAuthScreens() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        onView(withId(R.id.bottom_bar)).check(matches(withEffectiveVisibilityBottomGone()));
        scenario.close();
    }

    private static org.hamcrest.Matcher<android.view.View> withEffectiveVisibilityBottomGone() {
        return androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility(
                androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE);
    }
}
