package com.ikp.dev.app.ui.research;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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

        View shareBtn = view.findViewById(R.id.btn_share_fatwa);
        if (shareBtn != null) {
            shareBtn.setOnClickListener(v -> {
                UiState<AcademyDtos.FatwaDetailDto> s = viewModel.fatwa().getValue();
                if (s != null && s.data != null) {
                    ClipboardManager cb = (ClipboardManager)
                            requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null) {
                        String clip = "Question:\n" + (s.data.question != null ? s.data.question : "")
                                + "\n\nRuling:\n" + (s.data.answer != null ? s.data.answer : "");
                        cb.setPrimaryClip(ClipData.newPlainText("Fatwa", clip));
                        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

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

                TextView topicBadge = requireView().findViewById(R.id.tv_fatwa_topic_badge);
                if (topicBadge != null) {
                    if (f.category != null && !f.category.isEmpty()) {
                        topicBadge.setText(f.category.toUpperCase());
                    } else {
                        topicBadge.setText("FATWA & RULING");
                    }
                }

                ((TextView) requireView().findViewById(R.id.tv_fatwa_question)).setText(
                        f.question == null ? "" : f.question);

                String meta = (f.category == null ? "" : f.category + " · ")
                        + (f.language == null ? "" : f.language.toUpperCase() + " · ")
                        + (f.publicationDate == null ? "" : f.publicationDate);
                ((TextView) requireView().findViewById(R.id.tv_fatwa_meta)).setText(meta);

                ((TextView) requireView().findViewById(R.id.tv_fatwa_answer)).setText(
                        f.answer == null ? "" : f.answer);

                TextView review = requireView().findViewById(R.id.tv_fatwa_review);
                View reviewerBox = requireView().findViewById(R.id.box_fatwa_reviewer);

                if (f.review != null && f.review.scholar != null) {
                    StringBuilder sb = new StringBuilder();
                    if (f.review.scholar.name != null) {
                        sb.append("Reviewed by ").append(f.review.scholar.name);
                    }
                    if (f.review.scholar.qualifications != null && !f.review.scholar.qualifications.isEmpty()) {
                        sb.append(" (").append(f.review.scholar.qualifications).append(")");
                    }
                    if (f.review.reviewDate != null) {
                        sb.append("\nDate: ").append(f.review.reviewDate);
                    }
                    if (f.review.reviewNotes != null && !f.review.reviewNotes.isEmpty()) {
                        sb.append("\nAttestation: ").append(f.review.reviewNotes);
                    }
                    if (reviewerBox != null) reviewerBox.setVisibility(View.VISIBLE);
                    review.setVisibility(View.VISIBLE);
                    review.setText(sb.toString());
                } else if (f.review != null && f.review.reviewNotes != null && !f.review.reviewNotes.isEmpty()) {
                    if (reviewerBox != null) reviewerBox.setVisibility(View.VISIBLE);
                    review.setVisibility(View.VISIBLE);
                    review.setText("Attestation: " + f.review.reviewNotes);
                } else {
                    if (reviewerBox != null) reviewerBox.setVisibility(View.GONE);
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
