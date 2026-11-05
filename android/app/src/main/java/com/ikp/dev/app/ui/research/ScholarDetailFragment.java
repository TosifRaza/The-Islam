package com.ikp.dev.app.ui.research;

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

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.ui.common.StateView;

/**
 * Scholar profile (M5): verified directory entry. Only admin-recorded
 * credentials are shown; the verification status is always visible — the app
 * never presents an unverified profile as endorsed.
 */
public final class ScholarDetailFragment extends Fragment {

    private ResearchDetailViewModel viewModel;
    private StateView stateView;
    private View content;
    private String scholarId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scholar_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ResearchDetailViewModel.class);
        stateView = view.findViewById(R.id.scholar_state);
        content = view.findViewById(R.id.scholar_content);
        Bundle args = getArguments();
        scholarId = args == null ? null : args.getString("scholarId");
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        viewModel.scholar().observe(getViewLifecycleOwner(), this::render);
        viewModel.loadScholar(scholarId);
    }

    private void render(UiState<AcademyDtos.ScholarDto> state) {
        switch (state.kind) {
            case LOADING -> stateView.renderLoading();
            case SUCCESS -> {
                stateView.setVisibility(View.GONE);
                content.setVisibility(View.VISIBLE);
                AcademyDtos.ScholarDto s = state.data;
                ((TextView) requireView().findViewById(R.id.tv_scholar_name)).setText(
                        s.name == null ? "" : s.name
                                + (s.nameAr == null || s.nameAr.isEmpty() ? "" : " · " + s.nameAr));
                ((TextView) requireView().findViewById(R.id.tv_scholar_status)).setText(
                        s.verificationStatus == null ? "" : s.verificationStatus);
                ((TextView) requireView().findViewById(R.id.tv_scholar_qualifications)).setText(
                        s.qualifications == null ? "" : s.qualifications);
                ((TextView) requireView().findViewById(R.id.tv_scholar_expertise)).setText(
                        s.areasOfExpertise == null ? "" : s.areasOfExpertise);
                ((TextView) requireView().findViewById(R.id.tv_scholar_bio)).setText(
                        s.biography == null ? "" : s.biography);
            }
            case EMPTY -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderEmpty();
            }
            case ERROR -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderError(state.error);
                stateView.setOnRetry(() -> viewModel.loadScholar(scholarId));
            }
        }
    }
}
