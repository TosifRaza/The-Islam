package com.ikp.dev.app.ui.learning;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.NavOptions;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.ui.common.StateView;
import com.ikp.dev.app.ui.common.TwoLineAdapter;
import com.ikp.dev.app.util.QuizRows;

import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

/** Published quiz catalogue (M5); starting an attempt requires sign-in. */
public final class QuizzesFragment extends Fragment {

    private QuizAttemptViewModel unused; // reserved: VM split lands with timed-attempt UI
    private StateView stateView;
    private TwoLineAdapter adapter;
    private final List<AcademyDtos.QuizRowDto> shown = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quizzes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        stateView = view.findViewById(R.id.quiz_state);
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        RecyclerView rv = view.findViewById(R.id.rv_quizzes);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TwoLineAdapter((item, pos) -> {
            if (pos < 0 || pos >= shown.size()) return;
            if (!com.ikp.dev.app.di.ServiceLocator.tokenStore().hasSession()) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Sign In Required to Start Quiz")
                        .setMessage("Taking official quizzes and examinations requires an account to record your answers, track your score, and award certificates.\n\nWould you like to sign in or create an account now?")
                        .setPositiveButton("Sign In / Register", (d, which) -> {
                            try {
                                NavHostFragment.findNavController(this).navigate(R.id.action_global_session_expired);
                            } catch (Exception ignored) {
                            }
                        })
                        .setNegativeButton("Continue Browsing", null)
                        .show();
                return;
            }
            Bundle args = new Bundle();
            args.putString("quizId", shown.get(pos).quizId);
            NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
            NavHostFragment.findNavController(this).navigate(R.id.quizAttemptFragment, args, opts);
        });
        rv.setAdapter(adapter);
        load();
    }

    private void load() {
        stateView.setVisibility(View.VISIBLE);
        stateView.renderLoading();
        com.ikp.dev.app.di.ServiceLocator.executors().io.execute(() -> {
            com.ikp.dev.app.core.common.Outcome<List<AcademyDtos.QuizRowDto>> r =
                    com.ikp.dev.app.di.ServiceLocator.academyRepository().quizzes(1, 50);
            List<TwoLineAdapter.Item> rows = QuizRows.catalogue(r.isSuccess() && r.value != null ? r.value : null);
            boolean error = !r.isSuccess();
            boolean empty = r.isSuccess() && rows.isEmpty();
            com.ikp.dev.app.core.error.ApiError err = r.error;
            requireActivity().runOnUiThread(() -> {
                shown.clear();
                if (!error && r.value != null) shown.addAll(r.value);
                if (error) {
                    stateView.setVisibility(View.VISIBLE);
                    stateView.renderError(err);
                    stateView.setOnRetry(this::load);
                } else if (empty) {
                    stateView.setVisibility(View.VISIBLE);
                    stateView.renderEmpty();
                } else {
                    stateView.setVisibility(View.GONE);
                    adapter.submit(rows);
                }
            });
        });
    }
}
