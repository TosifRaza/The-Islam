package com.ikp.dev.app.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.domain.model.Models;
import com.ikp.dev.app.ui.common.StateView;

/**
 * Book browse/search screen: free-text search (debounced), subject filter,
 * language chips, cursor-based load-more, real backend paging.
 */
public final class BooksFragment extends Fragment {

    private BooksViewModel viewModel;
    private StateView stateView;
    private BookRowAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_books, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BooksViewModel.class);
        stateView = view.findViewById(R.id.books_state);

        TextView title = view.findViewById(R.id.tv_title);
        Bundle args = getArguments();
        String slug = args == null ? null : args.getString("subjectSlug");
        String name = args == null ? null : args.getString("subjectName");
        String initialQuery = args == null ? null : args.getString("query");
        viewModel.init(slug, initialQuery);
        title.setText(name == null ? getString(R.string.books_title_generic) : name);

        ImageButton back = view.findViewById(R.id.btn_back);
        back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        TextInputLayout tilQuery = view.findViewById(R.id.til_query);
        TextInputEditText etQuery = view.findViewById(R.id.et_query);
        etQuery.addTextChangedListener(viewModel.queryWatcher());

        ChipGroup chips = view.findViewById(R.id.chips_language);
        buildLanguageChips(chips);

        RecyclerView rv = view.findViewById(R.id.rv_books);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BookRowAdapter(book -> {
            android.os.Bundle d = new android.os.Bundle();
            d.putString("bookId", book.bookId);
            NavHostFragment.findNavController(this).navigate(R.id.action_books_to_detail, d);
        });
        adapter.setLoadMore(viewModel::loadMore);
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
                        adapter.submit(state.data.items);
                    }
                    break;
                case EMPTY:
                    stateView.renderEmpty();
                    break;
                case ERROR:
                    stateView.renderError(state.error);
                    stateView.setOnRetry(viewModel::reload);
                    break;
            }
        });

        if (initialQuery != null && !initialQuery.isEmpty()) {
            etQuery.setText(initialQuery);
        }
        viewModel.reload();
    }

    private void buildLanguageChips(ChipGroup group) {
        String[] codes = {null, "ar", "en", "ur", "hi"};
        int[] labels = {R.string.library_all_books, R.string.language_ar,
                R.string.language_en, R.string.language_ur, R.string.language_hi};
        for (int i = 0; i < codes.length; i++) {
            Chip chip = new Chip(requireContext());
            chip.setText(labels[i]);
            chip.setCheckable(true);
            final String code = codes[i];
            chip.setOnClickListener(v -> viewModel.setLanguage(code));
            group.addView(chip);
            if (i == 0) {
                chip.setChecked(true);
            }
        }
    }
}
