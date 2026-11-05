package com.ikp.dev.app.ui.academy;

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
import androidx.navigation.NavOptions;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.ui.common.StateView;
import com.ikp.dev.app.ui.common.TwoLineAdapter;
import com.ikp.dev.app.util.CourseRows;

import java.util.ArrayList;
import java.util.List;

/**
 * Academy tab (M5): continue-learning rail, quick tools (quizzes,
 * flashcards) and the published course catalogue. Guests see the catalogue
 * plus an honest sign-in hint — member rows are hidden, never faked.
 */
public final class AcademyFragment extends Fragment {

    private AcademyViewModel viewModel;
    private StateView stateView;
    private TwoLineAdapter courseAdapter;
    private TwoLineAdapter continueAdapter;
    private final List<AcademyDtos.CourseRowDto> shownCourses = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_academy, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AcademyViewModel.class);
        stateView = view.findViewById(R.id.academy_state);

        RecyclerView continueRv = view.findViewById(R.id.rv_continue);
        continueRv.setLayoutManager(new LinearLayoutManager(requireContext()));
        continueAdapter = new TwoLineAdapter(null);
        continueRv.setAdapter(continueAdapter);

        RecyclerView coursesRv = view.findViewById(R.id.rv_courses);
        coursesRv.setLayoutManager(new LinearLayoutManager(requireContext()));
        courseAdapter = new TwoLineAdapter((item, pos) -> {
            if (pos < 0 || pos >= shownCourses.size()) return;
            Bundle args = new Bundle();
            args.putString("courseId", shownCourses.get(pos).courseId);
            NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
            NavHostFragment.findNavController(this).navigate(R.id.courseDetailFragment, args, opts);
        });
        coursesRv.setAdapter(courseAdapter);

        view.findViewById(R.id.btn_quizzes).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.quizzesFragment));
        view.findViewById(R.id.btn_flashcards).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.flashcardsFragment));

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.reload();
    }

    private void render(UiState<AcademyViewModel.AcademyData> state) {
        switch (state.kind) {
            case LOADING -> stateView.renderLoading();
            case SUCCESS -> {
                stateView.setVisibility(View.GONE);
                AcademyViewModel.AcademyData data = state.data;
                shownCourses.clear();
                if (data.courses != null) shownCourses.addAll(data.courses);
                courseAdapter.submit(CourseRows.catalogue(shownCourses));
                List<TwoLineAdapter.Item> cont = new ArrayList<>();
                if (data.continueLearning != null) {
                    for (AcademyDtos.ContinueDto c : data.continueLearning) {
                        TwoLineAdapter.Item row = CourseRows.continueRow(c);
                        if (row != null) cont.add(row);
                    }
                }
                continueAdapter.submit(cont);
                View contList = requireView().findViewById(R.id.rv_continue);
                View contHeader = requireView().findViewById(R.id.label_continue);
                int vis = cont.isEmpty() ? View.GONE : View.VISIBLE;
                contHeader.setVisibility(vis);
                contList.setVisibility(vis);
                TextView hint = requireView().findViewById(R.id.tv_signin_hint);
                hint.setVisibility(data.memberSections ? View.GONE : View.VISIBLE);
                if (!data.memberSections) hint.setText(R.string.academy_signin_hint);
            }
            case EMPTY -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderEmpty();
            }
            case ERROR -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderError(state.error);
                stateView.setOnRetry(viewModel::reload);
            }
        }
    }
}
