package com.ikp.dev.app.ui.research;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.common.StateView;

/**
 * Published fatwa detail (M5). Visible only through the public directory —
 * which itself serves solely scholar-approved records (OD-08 separation of
 * duties). The disclaimer is always shown; the review block names the
 * reviewing scholar and review date when recorded.
 */
public final class FatwaDetailFragment extends Fragment {

    private ResearchDetailViewModel viewModel;
    private StateView stateView;
    private View content;
    private String fatwaId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fatwa_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ResearchDetailViewModel.class);
        stateView = view.findViewById(R.id.fatwa_state);
        content = view.findViewById(R.id.fatwa_content);
        Bundle args = getArguments();
        fatwaId = args == null ? null : args.getString("fatwaId");
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btn_save_fatwa).setOnClickListener(v -> {
            if (fatwaId == null) return;
            if (!ServiceLocator.sessionManager().isSignedIn()) {
                Toast.makeText(requireContext(), R.string.signin_required, Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.saveFatwa(fatwaId);
        });
        viewModel.fatwa().observe(getViewLifecycleOwner(), this::render);
        viewModel.saved().observe(getViewLifecycleOwner(), ok -> Toast.makeText(requireContext(),
                ok != null && ok ? R.string.research_save : R.string.error_generic,
                Toast.LENGTH_SHORT).show());
        viewModel.loadFatwa(fatwaId);
    }

    private void render(UiState<AcademyDtos.FatwaDetailDto> state) {
        switch (state.kind) {
            case LOADING -> stateView.renderLoading();
            case SUCCESS -> {
                stateView.setVisibility(View.GONE);
                content.setVisibility(View.VISIBLE);
                AcademyDtos.FatwaDetailDto f = state.data;
                ((TextView) requireView().findViewById(R.id.tv_fatwa_question)).setText(
                        f.question == null ? "" : f.question);
                String meta = (f.category == null ? "" : f.category + " · ")
                        + (f.language == null ? "" : f.language + " · ")
                        + (f.publicationDate == null ? "" : f.publicationDate);
                ((TextView) requireView().findViewById(R.id.tv_fatwa_meta)).setText(meta);
                ((TextView) requireView().findViewById(R.id.tv_fatwa_answer)).setText(
                        f.answer == null ? "" : f.answer);
                TextView review = requireView().findViewById(R.id.tv_fatwa_review);
                if (f.review != null && f.review.scholar != null) {
                    StringBuilder sb = new StringBuilder();
                    if (f.review.scholar.name != null) sb.append(f.review.scholar.name);
                    if (f.review.reviewDate != null) {
                        sb.append(sb.length() == 0 ? "" : " · ").append(f.review.reviewDate);
                    }
                    if (f.review.reviewNotes != null && !f.review.reviewNotes.isEmpty()) {
                        sb.append("\n").append(f.review.reviewNotes);
                    }
                    review.setVisibility(View.VISIBLE);
                    review.setText(sb.toString());
                } else {
                    review.setVisibility(View.GONE);
                }
            }
            case EMPTY -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderEmpty();
            }
            case ERROR -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderError(state.error);
                stateView.setOnRetry(() -> viewModel.loadFatwa(fatwaId));
            }
        }
    }
}
