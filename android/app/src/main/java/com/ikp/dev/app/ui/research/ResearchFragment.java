package com.ikp.dev.app.ui.research;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.NavOptions;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.ui.common.StateView;
import com.ikp.dev.app.ui.common.TwoLineAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Research tab (M5): three directories behind one screen — research articles
 * (clearly tiered primary/secondary/editorial), the verified-scholar directory
 * and the published-fatwa directory. Guests may browse; saving/noteing
 * requires sign-in (backend enforced, surfaced via stable error codes).
 * The AI research subsystem with citations is M6 — nothing generated here.
 */
public final class ResearchFragment extends Fragment {

    private ResearchViewModel viewModel;
    private StateView stateView;
    private TwoLineAdapter adapter;
    private TextInputLayout fatwaSearchBox;
    private final List<ResearchViewModel.TwoLineRow> shown = new ArrayList<>();

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

        RecyclerView rv = view.findViewById(R.id.rv_research);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TwoLineAdapter((item, pos) -> openDetail(pos));
        rv.setAdapter(adapter);

        fatwaSearchBox = view.findViewById(R.id.til_fatwa_search);
        TextInputEditText query = view.findViewById(R.id.et_fatwa_query);
        if (query != null) {
            query.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

                @Override public void afterTextChanged(android.text.Editable s) {
                    viewModel.setFatwaQuery(s == null ? null : s.toString());
                }
            });
            query.setOnEditorActionListener((v, actionId, event) -> {
                viewModel.reload();
                return false;
            });
        }

        ChipGroup chips = view.findViewById(R.id.chips_research_mode);
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.contains(R.id.chip_scholars)) {
                viewModel.setMode(ResearchViewModel.Mode.SCHOLARS);
            } else if (ids.contains(R.id.chip_fatwas)) {
                viewModel.setMode(ResearchViewModel.Mode.FATWAS);
            } else {
                viewModel.setMode(ResearchViewModel.Mode.ARTICLES);
            }
            renderList();
            viewModel.reload();
        });

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.reload();
    }

    private void openDetail(int pos) {
        if (pos < 0 || pos >= shown.size()) return;
        ResearchViewModel.TwoLineRow row = shown.get(pos);
        Bundle args = new Bundle();
        NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
        switch (viewModel.mode()) {
            case ARTICLES -> args.putString("articleId", row.id);
            case SCHOLARS -> args.putString("scholarId", row.id);
            case FATWAS -> args.putString("fatwaId", row.id);
        }
        int dest = viewModel.mode() == ResearchViewModel.Mode.ARTICLES ? R.id.researchArticleFragment
                : viewModel.mode() == ResearchViewModel.Mode.SCHOLARS ? R.id.scholarDetailFragment
                : R.id.fatwaDetailFragment;
        NavHostFragment.findNavController(this).navigate(dest, args, opts);
    }

    private void renderList() {
        fatwaSearchBox.setVisibility(
                viewModel.mode() == ResearchViewModel.Mode.FATWAS ? View.VISIBLE : View.GONE);
        UiState<List<ResearchViewModel.TwoLineRow>> s = viewModel.state().getValue();
        if (s != null) render(s);
    }

    private void render(UiState<List<ResearchViewModel.TwoLineRow>> state) {
        switch (state.kind) {
            case LOADING -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderLoading();
            }
            case SUCCESS -> {
                stateView.setVisibility(View.GONE);
                shown.clear();
                if (state.data != null) shown.addAll(state.data);
                List<TwoLineAdapter.Item> rows = new ArrayList<>();
                for (ResearchViewModel.TwoLineRow r : shown) {
                    rows.add(new TwoLineAdapter.ItemImpl(r.title, r.subtitle, r.badge));
                }
                adapter.submit(rows);
            }
            case EMPTY -> {
                shown.clear();
                adapter.submit(new ArrayList<>());
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
