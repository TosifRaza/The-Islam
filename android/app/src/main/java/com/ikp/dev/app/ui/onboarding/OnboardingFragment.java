package com.ikp.dev.app.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewpager2.widget.ViewPager2;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.security.SessionManager;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.splash.SplashViewModel;

/**
 * Onboarding introduction: 3 feature pages + a permissions-introduction page.
 * Guest browsing entry ("Explore as guest") and login/registration entries.
 */
public final class OnboardingFragment extends Fragment {

    private ViewPager2 pager;
    private Button next;
    private TextView skip;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pager = view.findViewById(R.id.onboarding_pager);
        next = view.findViewById(R.id.btn_onboarding_next);
        skip = view.findViewById(R.id.btn_onboarding_skip);
        LinearLayout dots = view.findViewById(R.id.dots);

        pager.setAdapter(new OnboardingAdapter());
        buildDots(dots);

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(dots, position);
                next.setText(position == 3
                        ? R.string.action_get_started
                        : R.string.onboarding_continue);
            }
        });

        next.setOnClickListener(v -> {
            if (pager.getCurrentItem() < 3) {
                pager.setCurrentItem(pager.getCurrentItem() + 1, true);
            } else {
                finishOnboarding(true);
            }
        });
        skip.setOnClickListener(v -> finishOnboarding(true));
    }

    private void finishOnboarding(boolean continueToApp) {
        SplashViewModel.markOnboardingSeen();
        if (!continueToApp) {
            return;
        }
        // Guest browsing entry: continue into the app unauthenticated.
        ServiceLocator.sessionManager().onGuest();
        NavHostFragment.findNavController(this).navigate(R.id.action_onboarding_to_home);
    }

    private void buildDots(LinearLayout dots) {
        for (int i = 0; i < 4; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (8 * getResources().getDisplayMetrics().density),
                    (int) (8 * getResources().getDisplayMetrics().density));
            lp.setMargins((int) (4 * getResources().getDisplayMetrics().density), 0, 0, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i == 0
                    ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            dots.addView(dot);
        }
    }

    private void updateDots(LinearLayout dots, int position) {
        for (int i = 0; i < dots.getChildCount(); i++) {
            dots.getChildAt(i).setBackgroundResource(i == position
                    ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
        }
    }
}
