package com.ikp.dev.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.core.security.SessionManager;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;
import com.ikp.dev.app.ui.common.StateView;

/**
 * Devices & sessions: list + revoke (real backend endpoints §3.1.9/3.1.10).
 * The current session cannot be revoked from here — sign out instead.
 */
public final class SessionsFragment extends Fragment {

    private SessionsViewModel viewModel;
    private StateView stateView;
    private SessionAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sessions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SessionsViewModel.class);
        stateView = view.findViewById(R.id.sessions_state);

        ImageButton back = view.findViewById(R.id.btn_back);
        back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        RecyclerView rv = view.findViewById(R.id.rv_sessions);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SessionAdapter(this::confirmRevoke);
        rv.setAdapter(adapter);

        viewModel.state().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            switch (state.kind) {
                case LOADING:
                    stateView.renderLoading();
                    break;
                case SUCCESS:
                    stateView.setVisibility(View.GONE);
                    if (state.data != null) {
                        adapter.submit(state.data);
                    }
                    break;
                case EMPTY:
                    stateView.renderEmpty();
                    break;
                case ERROR:
                    stateView.renderError(state.error);
                    stateView.setOnRetry(viewModel::load);
                    break;
            }
        });

        viewModel.load();
    }

    private void confirmRevoke(Models.SessionInfo session) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sessions_revoke_confirm_title)
                .setMessage(R.string.sessions_revoke_confirm_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.sessions_revoke, (d, w) -> {
                    SessionManager.CurrentUser current =
                            ServiceLocator.sessionManager().currentUserOrNull();
                    long currentId = -1;
                    // currentSessionId is not part of the public /me payload;
                    // the backend rejects cross-user revocation server-side
                    // (AUTHZ-002) and 'current' rows are hidden from the UI.
                    viewModel.revoke(session.sessionId, currentId);
                })
                .show();
    }
}
