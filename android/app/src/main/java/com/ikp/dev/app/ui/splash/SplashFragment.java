package com.ikp.dev.app.ui.splash;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.ikp.dev.app.R;

/**
 * Splash decision screen. The system splash (SplashScreen API) hands off to
 * this fragment, which runs the startup session validation and routes to the
 * correct destination. A failing network never stalls the splash: offline
 * with a saved session lands in guest-browse home with the offline banner.
 */
public final class SplashFragment extends Fragment {

    private SplashViewModel viewModel;

    /** fix6: consume-once latch — a sticky/stale navigation LiveData event must
     * never trigger a second navigate() after the splash already routed. */
    private boolean navigationHandled;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_splash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SplashViewModel.class);
        viewModel.navigation().observe(getViewLifecycleOwner(), next -> {
            if (next == null || navigationHandled) {
                return;
            }
            // fix6: only navigate while we are still the current destination.
            // A delayed event dispatched after the router already moved on
            // (e.g. splash -> login done) used to crash with
            // IllegalArgumentException: action not found from loginFragment.
            NavController nav;
            try {
                nav = NavHostFragment.findNavController(this);
            } catch (IllegalStateException detached) {
                return; // view no longer attached to a NavHost — stale event
            }
            if (nav.getCurrentDestination() == null
                    || nav.getCurrentDestination().getId() != R.id.splashFragment) {
                return;
            }
            navigationHandled = true;
            switch (next) {
                case ONBOARDING:
                    nav.navigate(R.id.action_splash_to_onboarding);
                    break;
                case LOGIN:
                    nav.navigate(R.id.action_splash_to_login);
                    break;
                case HOME_OFFLINE:
                case HOME:
                default:
                    nav.navigate(R.id.action_splash_to_home);
                    break;
            }
        });
        viewModel.start();
    }
}
