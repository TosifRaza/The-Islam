package com.ikp.dev.app.ui.research;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.common.StateView;

import java.util.List;

/**
 * Research tab (M5): academic research platform with peer-reviewed articles,
 * verified scholar directory, and authenticated fatwas.
 */
public final class ResearchFragment extends Fragment {

    private ResearchViewModel viewModel;
    private StateView stateView;

    private RecyclerView rvTopics;
    private RecyclerView rvResearch;
    private View cardFeatured;
    private View clearSearchBtn;
    private TextInputEditText etSearch;

    private TextView tvSectionTitle;
    private TextView tvSectionCount;
    private TextView tvFeaturedTitle;
    private TextView tvFeaturedSummary;
    private TextView tvFeaturedTime;
    private TextView tvFeaturedAuthor;
    private View btnFeaturedRead;

    private ResearchTopicAdapter topicAdapter;
    private ResearchArticleAdapter articleAdapter;
    private ScholarAdapter scholarAdapter;
    private FatwaAdapter fatwaAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_research, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ResearchViewModel.class);

        stateView = view.findViewById(R.id.research_state);
        rvTopics = view.findViewById(R.id.rv_topics);
        rvResearch = view.findViewById(R.id.rv_research);
        cardFeatured = view.findViewById(R.id.card_featured);
        clearSearchBtn = view.findViewById(R.id.btn_clear_search);
        etSearch = view.findViewById(R.id.et_fatwa_query);

        tvSectionTitle = view.findViewById(R.id.tv_section_title);
        tvSectionCount = view.findViewById(R.id.tv_section_count);
        tvFeaturedTitle = view.findViewById(R.id.tv_featured_title);
        tvFeaturedSummary = view.findViewById(R.id.tv_featured_summary);
        tvFeaturedTime = view.findViewById(R.id.tv_featured_time);
        tvFeaturedAuthor = view.findViewById(R.id.tv_featured_author);
        btnFeaturedRead = view.findViewById(R.id.btn_featured_read);

        setupAdapters();
        setupSearch();
        setupModeChips(view);
        setupBookmarkButton(view);
        observeViewModel();

        viewModel.reload();
    }

    private void setupAdapters() {
        // Topics Adapter
        topicAdapter = new ResearchTopicAdapter(topic -> {
            String slug = topic != null ? topic.slug : null;
            viewModel.setTopic(slug);
            viewModel.reload();
        });
        rvTopics.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvTopics.setAdapter(topicAdapter);

        // Research List Adapters
        articleAdapter = new ResearchArticleAdapter(
                this::openArticleDetail,
                this::saveArticleBookmark
        );

        scholarAdapter = new ScholarAdapter(this::openScholarDetail);

        fatwaAdapter = new FatwaAdapter(this::openFatwaDetail);

        rvResearch.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvResearch.setAdapter(articleAdapter);
    }

    private void setupSearch() {
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    String q = s != null ? s.toString().trim() : "";
                    if (clearSearchBtn != null) {
                        clearSearchBtn.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    viewModel.setFatwaQuery(q);
                }
            });

            etSearch.setOnEditorActionListener((v, actionId, event) -> {
                viewModel.reload();
                return false;
            });
        }

        if (clearSearchBtn != null) {
            clearSearchBtn.setOnClickListener(v -> {
                if (etSearch != null) etSearch.setText("");
                viewModel.setFatwaQuery(null);
                viewModel.reload();
            });
        }
    }

    private void setupModeChips(View root) {
        ChipGroup chips = root.findViewById(R.id.chips_research_mode);
        if (chips == null) return;

        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip_scholars)) {
                viewModel.setMode(ResearchViewModel.Mode.SCHOLARS);
                rvResearch.setAdapter(scholarAdapter);
                rvTopics.setVisibility(View.GONE);
                cardFeatured.setVisibility(View.GONE);
                tvSectionTitle.setText("Verified Scholars");
                if (etSearch != null) etSearch.setHint("Search scholars by name or specialization...");
            } else if (checkedIds.contains(R.id.chip_fatwas)) {
                viewModel.setMode(ResearchViewModel.Mode.FATWAS);
                rvResearch.setAdapter(fatwaAdapter);
                rvTopics.setVisibility(View.VISIBLE);
                cardFeatured.setVisibility(View.GONE);
                tvSectionTitle.setText("Authenticated Fatwas & Rulings");
                if (etSearch != null) etSearch.setHint("Search fatwas by topic or question...");
            } else {
                viewModel.setMode(ResearchViewModel.Mode.ARTICLES);
                rvResearch.setAdapter(articleAdapter);
                rvTopics.setVisibility(View.VISIBLE);
                tvSectionTitle.setText("Published Research Articles");
                if (etSearch != null) etSearch.setHint("Search articles, scholars, or fatwas...");
            }
            if (topicAdapter != null) topicAdapter.setSelectedSlug(null);
            viewModel.reload();
        });
    }

    private void setupBookmarkButton(View root) {
        View bookmarkBtn = root.findViewById(R.id.btn_research_bookmarks);
        if (bookmarkBtn != null) {
            bookmarkBtn.setOnClickListener(v -> {
                if (!ServiceLocator.sessionManager().isSignedIn()) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.signin_required)
                            .setMessage(R.string.profile_guest_prompt)
                            .setPositiveButton(R.string.personal_sign_in, (dialog, which) ->
                                    NavHostFragment.findNavController(this).navigate(R.id.loginFragment))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    return;
                }
                Bundle args = new Bundle();
                args.putString("section", com.ikp.dev.app.ui.profile.SavedContentFragment.SECTION_BOOKMARKS);
                NavHostFragment.findNavController(this).navigate(R.id.savedContentFragment, args);
            });
        }
    }

    private void observeViewModel() {
        viewModel.topics().observe(getViewLifecycleOwner(), topics -> {
            if (topics != null && !topics.isEmpty()) {
                topicAdapter.submitList(topics, viewModel.selectedTopic().getValue());
            }
        });

        viewModel.featuredArticle().observe(getViewLifecycleOwner(), article -> {
            if (viewModel.mode() == ResearchViewModel.Mode.ARTICLES && article != null) {
                cardFeatured.setVisibility(View.VISIBLE);
                tvFeaturedTitle.setText(article.title != null ? article.title : "");
                tvFeaturedSummary.setText(article.summary != null ? article.summary : "");
                tvFeaturedTime.setText((article.readingMinutes != null && article.readingMinutes > 0)
                        ? article.readingMinutes + " min read" : "Academic Paper");
                tvFeaturedAuthor.setText(article.authorDisplayName != null
                        ? "By " + article.authorDisplayName : "Academic Research");

                View.OnClickListener openFeatured = v -> openArticleDetail(article);
                cardFeatured.setOnClickListener(openFeatured);
                if (btnFeaturedRead != null) btnFeaturedRead.setOnClickListener(openFeatured);
            } else {
                cardFeatured.setVisibility(View.GONE);
            }
        });

        viewModel.articlesState().observe(getViewLifecycleOwner(), this::renderArticles);
        viewModel.scholarsState().observe(getViewLifecycleOwner(), this::renderScholars);
        viewModel.fatwasState().observe(getViewLifecycleOwner(), this::renderFatwas);

        viewModel.bookmarkSaved().observe(getViewLifecycleOwner(), ok -> {
            if (ok != null && ok) {
                Toast.makeText(requireContext(), "Saved to Bookmarks", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderArticles(UiState<List<AcademyDtos.ArticleRowDto>> state) {
        if (viewModel.mode() != ResearchViewModel.Mode.ARTICLES) return;
        handleCommonState(state, state.data != null ? state.data.size() : 0, "articles");
        if (state.kind == UiState.Kind.SUCCESS && state.data != null) {
            articleAdapter.submitList(state.data);
        } else {
            articleAdapter.submitList(new java.util.ArrayList<>());
        }
    }

    private void renderScholars(UiState<List<AcademyDtos.ScholarDto>> state) {
        if (viewModel.mode() != ResearchViewModel.Mode.SCHOLARS) return;
        handleCommonState(state, state.data != null ? state.data.size() : 0, "scholars");
        if (state.kind == UiState.Kind.SUCCESS && state.data != null) {
            scholarAdapter.submitList(state.data);
        } else {
            scholarAdapter.submitList(new java.util.ArrayList<>());
        }
    }

    private void renderFatwas(UiState<List<AcademyDtos.FatwaRowDto>> state) {
        if (viewModel.mode() != ResearchViewModel.Mode.FATWAS) return;
        handleCommonState(state, state.data != null ? state.data.size() : 0, "fatwas");
        if (state.kind == UiState.Kind.SUCCESS && state.data != null) {
            fatwaAdapter.submitList(state.data);
        } else {
            fatwaAdapter.submitList(new java.util.ArrayList<>());
        }
    }

    private <T> void handleCommonState(UiState<T> state, int count, String entityName) {
        View scroll = getView() != null ? getView().findViewById(R.id.research_scroll) : null;
        switch (state.kind) {
            case LOADING -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderLoading();
                if (scroll != null) scroll.setVisibility(View.GONE);
                tvSectionCount.setText("");
            }
            case SUCCESS -> {
                stateView.setVisibility(View.GONE);
                if (scroll != null) scroll.setVisibility(View.VISIBLE);
                tvSectionCount.setText("(" + count + " " + entityName + ")");
            }
            case EMPTY -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderEmpty();
                if (scroll != null) scroll.setVisibility(View.GONE);
                tvSectionCount.setText("(0 " + entityName + ")");
            }
            case ERROR -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderError(state.error);
                stateView.setOnRetry(viewModel::reload);
                if (scroll != null) scroll.setVisibility(View.GONE);
                tvSectionCount.setText("");
            }
        }
    }

    private void openArticleDetail(AcademyDtos.ArticleRowDto article) {
        if (article == null || article.articleId == null) return;
        Bundle args = new Bundle();
        args.putString("articleId", article.articleId);
        NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
        NavHostFragment.findNavController(this).navigate(R.id.researchArticleFragment, args, opts);
    }

    private void openScholarDetail(AcademyDtos.ScholarDto scholar) {
        if (scholar == null || scholar.scholarId == null) return;
        Bundle args = new Bundle();
        args.putString("scholarId", scholar.scholarId);
        NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
        NavHostFragment.findNavController(this).navigate(R.id.scholarDetailFragment, args, opts);
    }

    private void openFatwaDetail(AcademyDtos.FatwaRowDto fatwa) {
        if (fatwa == null || fatwa.fatwaId == null) return;
        Bundle args = new Bundle();
        args.putString("fatwaId", fatwa.fatwaId);
        NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
        NavHostFragment.findNavController(this).navigate(R.id.fatwaDetailFragment, args, opts);
    }

    private void saveArticleBookmark(AcademyDtos.ArticleRowDto article) {
        if (article == null || article.articleId == null) return;
        if (!ServiceLocator.sessionManager().isSignedIn()) {
            Toast.makeText(requireContext(), R.string.signin_required, Toast.LENGTH_SHORT).show();
            return;
        }
        viewModel.saveArticleBookmark(article.articleId);
    }
}
