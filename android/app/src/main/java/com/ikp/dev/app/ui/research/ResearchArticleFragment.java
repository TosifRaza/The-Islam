package com.ikp.dev.app.ui.research;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
 * Research article detail (M5): full body with the three-tier source
 * classification shown explicitly, plus the citation list. Save/note require
 * sign-in (backend enforced). Citations are only rendered from stored records
 * — nothing is generated client-side.
 */
public final class ResearchArticleFragment extends Fragment {

    private ResearchDetailViewModel viewModel;
    private StateView stateView;
    private View content;
    private String articleId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_research_article, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ResearchDetailViewModel.class);
        stateView = view.findViewById(R.id.article_state);
        content = view.findViewById(R.id.article_content);
        Bundle args = getArguments();
        articleId = args == null ? null : args.getString("articleId");
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        View shareBtn = view.findViewById(R.id.btn_share_article);
        if (shareBtn != null) {
            shareBtn.setOnClickListener(v -> {
                UiState<AcademyDtos.ArticleDetailDto> s = viewModel.article().getValue();
                if (s != null && s.data != null) {
                    ClipboardManager cb = (ClipboardManager)
                            requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null) {
                        String clip = s.data.title + "\n\n"
                                + (s.data.summary != null ? s.data.summary + "\n\n" : "")
                                + (s.data.body != null ? s.data.body : "");
                        cb.setPrimaryClip(ClipData.newPlainText("Article", clip));
                        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        view.findViewById(R.id.btn_save_article).setOnClickListener(v -> {
            if (articleId == null) return;
            if (!ServiceLocator.sessionManager().isSignedIn()) {
                Toast.makeText(requireContext(), R.string.signin_required, Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.saveArticle(articleId);
        });
        view.findViewById(R.id.btn_note_article).setOnClickListener(v -> {
            if (articleId == null) return;
            if (!ServiceLocator.sessionManager().isSignedIn()) {
                Toast.makeText(requireContext(), R.string.signin_required, Toast.LENGTH_SHORT).show();
                return;
            }
            promptForNote();
        });
        viewModel.article().observe(getViewLifecycleOwner(), this::render);
        viewModel.saved().observe(getViewLifecycleOwner(), ok -> Toast.makeText(requireContext(),
                ok != null && ok ? R.string.research_save : R.string.error_generic,
                Toast.LENGTH_SHORT).show());
        viewModel.loadArticle(articleId);
    }

    private void promptForNote() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setMinLines(2);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.research_note)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.research_note, (d, w) -> {
                    String text = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!text.isEmpty()) viewModel.noteArticle(articleId, text);
                })
                .show();
    }

    private void render(UiState<AcademyDtos.ArticleDetailDto> state) {
        switch (state.kind) {
            case LOADING -> stateView.renderLoading();
            case SUCCESS -> {
                stateView.setVisibility(View.GONE);
                content.setVisibility(View.VISIBLE);
                AcademyDtos.ArticleDetailDto a = state.data;
                ((TextView) requireView().findViewById(R.id.tv_article_title)).setText(a.title != null ? a.title : "");

                TextView headline = requireView().findViewById(R.id.tv_article_headline);
                if (headline != null) {
                    headline.setText(a.title != null ? a.title : "");
                }

                TextView readTime = requireView().findViewById(R.id.tv_article_read_time);
                if (readTime != null) {
                    if (a.readingMinutes != null && a.readingMinutes > 0) {
                        readTime.setVisibility(View.VISIBLE);
                        readTime.setText(a.readingMinutes + " min read");
                    } else {
                        readTime.setVisibility(View.GONE);
                    }
                }

                String meta = (a.topic == null ? "" : a.topic.name + " · ")
                        + (a.language == null ? "" : a.language + " · ")
                        + (a.authorDisplayName == null ? "" : a.authorDisplayName);
                ((TextView) requireView().findViewById(R.id.tv_article_meta)).setText(meta);
                ((TextView) requireView().findViewById(R.id.tv_article_tier)).setText(
                        a.sourceTier != null ? a.sourceTier.toUpperCase() : "RESEARCH");
                ((TextView) requireView().findViewById(R.id.tv_article_summary)).setText(
                        a.summary == null ? "" : a.summary);
                ((TextView) requireView().findViewById(R.id.tv_article_body)).setText(
                        a.body == null ? "" : a.body);

                LinearLayout box = requireView().findViewById(R.id.citations_container);
                box.removeAllViews();
                if (a.citations != null && !a.citations.isEmpty()) {
                    for (AcademyDtos.CitationDto c : a.citations) {
                        TextView row = new TextView(requireContext());
                        row.setTextAppearance(R.style.TextAppearance_IKP_BodySecondary);
                        row.setText(citationText(c));
                        row.setPadding(dp(12), dp(8), dp(12), dp(8));
                        row.setBackgroundResource(R.drawable.bg_citation_card);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.setMargins(0, 0, 0, dp(8));
                        row.setLayoutParams(lp);
                        box.addView(row);
                    }
                }
            }
            case EMPTY -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderEmpty();
            }
            case ERROR -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderError(state.error);
                stateView.setOnRetry(() -> viewModel.loadArticle(articleId));
            }
        }
    }

    /** Citation text only from stored fields — never fabricated. */
    private String citationText(AcademyDtos.CitationDto c) {
        StringBuilder sb = new StringBuilder();
        if (c.citationKind != null) sb.append("[").append(c.citationKind.toUpperCase()).append("] ");
        if (c.surahId != null && c.ayahNumber != null) {
            sb.append("Surah ").append(c.surahId).append(":").append(c.ayahNumber).append(" ");
        }
        if (c.hadithId != null) sb.append("Hadith #").append(c.hadithId).append(" ");
        if (c.externalRef != null && !c.externalRef.isEmpty()) sb.append(c.externalRef).append(" ");
        if (c.quote != null && !c.quote.isEmpty()) sb.append("\n“").append(c.quote).append("”");
        if (c.note != null && !c.note.isEmpty()) sb.append("\nNote: ").append(c.note);
        return sb.toString().trim();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
