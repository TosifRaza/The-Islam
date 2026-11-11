package com.ikp.dev.app.ui.library;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.domain.model.Models;
import com.ikp.dev.app.ui.common.StateView;

import java.util.ArrayList;
import java.util.List;

/**
 * Islamic Book browse/search screen matching the master reference design.
 */
public final class BooksFragment extends Fragment {

    private BooksViewModel viewModel;
    private StateView stateView;
    private BookRowAdapter adapter;

    private final List<TextView> chipViews = new ArrayList<>();
    private final String[] chipCodes = {null, "ar", "en", "ur", "hi"};

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
        boolean focusSearch = args != null && args.getBoolean("focusSearch", false);
        viewModel.init(slug, initialQuery);
        String displayTitle = name == null ? getString(R.string.books_title_generic) : name.replace('\n', ' ').trim();
        title.setText(displayTitle);

        ImageButton back = view.findViewById(R.id.btn_back);
        back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        EditText etQuery = view.findViewById(R.id.et_query);
        etQuery.addTextChangedListener(viewModel.queryWatcher());
        etQuery.setOnEditorActionListener((textView, actionId, event) -> {
            boolean submit = actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN);
            if (!submit) {
                return false;
            }
            viewModel.searchNow();
            InputMethodManager keyboard = (InputMethodManager) requireContext()
                    .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(etQuery.getWindowToken(), 0);
            }
            etQuery.clearFocus();
            return true;
        });



        setupChips(view);

        RecyclerView rv = view.findViewById(R.id.rv_books);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BookRowAdapter(book -> {
            Bundle d = new Bundle();
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
        if (focusSearch) {
            etQuery.requestFocus();
            etQuery.post(() -> {
                if (!isAdded()) {
                    return;
                }
                InputMethodManager keyboard = (InputMethodManager) requireContext()
                        .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (keyboard != null) {
                    keyboard.showSoftInput(etQuery, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    private void setupChips(@NonNull View root) {
        chipViews.clear();
        chipViews.add(root.findViewById(R.id.chip_all));
        chipViews.add(root.findViewById(R.id.chip_ar));
        chipViews.add(root.findViewById(R.id.chip_en));
        chipViews.add(root.findViewById(R.id.chip_ur));
        chipViews.add(root.findViewById(R.id.chip_hi));

        for (int i = 0; i < chipViews.size(); i++) {
            final int index = i;
            TextView chip = chipViews.get(i);
            if (chip != null) {
                chip.setOnClickListener(v -> selectChip(index));
            }
        }
    }

    private void selectChip(int selectedIndex) {
        for (int i = 0; i < chipViews.size(); i++) {
            TextView chip = chipViews.get(i);
            if (chip == null) continue;
            if (i == selectedIndex) {
                chip.setBackgroundResource(R.drawable.bg_chip_book_selected);
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                chip.setTypeface(null, Typeface.BOLD);
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_book_unselected);
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.library_ink));
                chip.setTypeface(null, Typeface.NORMAL);
            }
        }
        viewModel.setLanguage(chipCodes[selectedIndex]);
    }
}
