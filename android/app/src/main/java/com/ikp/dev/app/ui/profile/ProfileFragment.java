package com.ikp.dev.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.ikp.dev.app.MainActivity;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.core.security.SessionManager;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;
import com.ikp.dev.app.ui.common.StateView;

/**
 * Profile section: real identity from /auth/me when signed in; guest prompt
 * otherwise; devices & sessions; logout (with confirmation + real revocation).
 */
public final class ProfileFragment extends Fragment {

    private ProfileViewModel viewModel;
    private StateView stateView;
    private View identity;
    private TextView prompt;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        stateView = view.findViewById(R.id.profile_state);
        identity = view.findViewById(R.id.profile_identity);
        prompt = view.findViewById(R.id.tv_profile_prompt);

        view.findViewById(R.id.btn_settings).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_settings));
        view.findViewById(R.id.row_sessions).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_sessions));
        view.findViewById(R.id.row_sign_in).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_login));
        view.findViewById(R.id.row_logout).setOnClickListener(v ->
                MainActivity.confirmLogout(requireContext(), viewModel::logout));

        viewModel.logoutDone().observe(getViewLifecycleOwner(), done -> {
            if (Boolean.TRUE.equals(done)) {
                renderGuest();
                viewModel.loadProfile();
            }
        });

        viewModel.profileState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            switch (state.kind) {
                case LOADING:
                    stateView.setVisibility(View.VISIBLE);
                    stateView.renderLoading();
                    identity.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    stateView.setVisibility(View.GONE);
                    if (state.data != null) {
                        identity.setVisibility(View.VISIBLE);
                        render(state.data);
                    } else {
                        identity.setVisibility(View.GONE);
                        renderGuest();
                    }
                    break;
                case EMPTY:
                    stateView.setVisibility(View.GONE);
                    identity.setVisibility(View.GONE);
                    renderGuest();
                    break;
                case ERROR:
                    stateView.renderError(state.error);
                    stateView.setOnRetry(viewModel::loadProfile);
                    identity.setVisibility(View.GONE);
                    break;
            }
        });

        ServiceLocator.sessionManager().state().observe(getViewLifecycleOwner(), e -> {
            if (e != null) {
                viewModel.loadProfile();
            }
        });
        viewModel.loadProfile();
    }

    private void renderGuest() {
        TextView name = requireView().findViewById(R.id.tv_profile_name);
        name.setText(R.string.profile_title);
        prompt.setText(R.string.profile_sign_in_prompt);
        requireView().findViewById(R.id.tv_guest_badge).setVisibility(View.VISIBLE);
        requireView().findViewById(R.id.row_sign_in).setVisibility(View.VISIBLE);
        requireView().findViewById(R.id.row_logout).setVisibility(View.GONE);
        requireView().findViewById(R.id.row_sessions).setVisibility(View.GONE);
    }

    private void render(Models.User user) {
        View view = requireView();
        TextView name = view.findViewById(R.id.tv_profile_name);
        name.setText(user.displayName == null || user.displayName.isEmpty()
                ? getString(R.string.profile_title) : user.displayName);
        prompt.setText(R.string.action_ok);
        prompt.setVisibility(user.email == null || user.email.isEmpty() ? View.GONE : View.VISIBLE);
        prompt.setText(user.email == null ? "" : user.email);
        view.findViewById(R.id.tv_guest_badge).setVisibility(View.GONE);
        view.findViewById(R.id.row_sign_in).setVisibility(View.GONE);
        view.findViewById(R.id.row_logout).setVisibility(View.VISIBLE);
        view.findViewById(R.id.row_sessions).setVisibility(View.VISIBLE);

        ((TextView) view.findViewById(R.id.tv_profile_email)).setText(
                user.email == null || user.email.isEmpty() ? "—" : user.email);
        ((TextView) view.findViewById(R.id.tv_profile_status)).setText(
                user.status == null || user.status.isEmpty() ? "—" : user.status);
        if (user.createdAt != null && !user.createdAt.isEmpty()) {
            try {
                java.time.Instant instant = java.time.Instant.parse(user.createdAt);
                java.time.LocalDate d = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                ((TextView) view.findViewById(R.id.tv_member_since)).setText(
                        String.format(java.util.Locale.getDefault(), "%d-%02d-%02d",
                                d.getYear(), d.getMonthValue(), d.getDayOfMonth()));
            } catch (Exception ignored) {
                ((TextView) view.findViewById(R.id.tv_member_since)).setText("—");
            }
        }
    }
}
