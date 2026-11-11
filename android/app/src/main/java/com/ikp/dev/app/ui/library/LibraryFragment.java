package com.ikp.dev.app.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;
import com.ikp.dev.app.ui.common.StateView;
import com.ikp.dev.app.ui.home.BookCardAdapter;
import com.ikp.dev.app.ui.home.SubjectAdapter;

/**
 * Library main screen: the 14 public categories (backend enforces position 06
 * hidden), search entry, recently-viewed rail (local history), browse-all.
 */
public final class LibraryFragment extends Fragment {

    private LibraryViewModel viewModel;
    private StateView stateView;
    private View sections;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        stateView = view.findViewById(R.id.library_state);
        sections = view.findViewById(R.id.library_sections);

        RecyclerView subjects = view.findViewById(R.id.rv_subjects);
        subjects.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        SubjectAdapter subjectAdapter = new SubjectAdapter(this::openSubject);
        subjects.setAdapter(subjectAdapter);

        RecyclerView recent = view.findViewById(R.id.rv_recent);
        recent.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        BookCardAdapter recentAdapter = new BookCardAdapter(b -> {
            android.os.Bundle args = new android.os.Bundle();
            args.putString("bookId", b.bookId);
            NavHostFragment.findNavController(this).navigate(R.id.action_global_book_detail, args);
        });
        recent.setAdapter(recentAdapter);
        recent.setVisibility(View.GONE); // populated by local history (below)

        bindFeatured(view, R.id.featured_essential, R.string.library_featured_essential,
                R.drawable.ic_shortcut_books, "coll_essential", "Essential Books");
        bindFeatured(view, R.id.featured_scholarly, R.string.library_featured_scholarly,
                R.drawable.ic_chip_layers, "coll_scholarly", "Scholarly Series");
        bindFeatured(view, R.id.featured_beginner, R.string.library_featured_beginner,
                R.drawable.ic_chip_ribbon, "coll_beginner", "Beginner Guides");

        view.findViewById(R.id.library_search_entry).setOnClickListener(v -> {
            Bundle searchArgs = new Bundle();
            searchArgs.putBoolean("focusSearch", true);
            NavHostFragment.findNavController(this).navigate(
                    R.id.action_library_to_books, searchArgs);
        });
        view.findViewById(R.id.btn_browse_all).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_library_to_books));

        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe =
                view.findViewById(R.id.swipe_refresh);
        swipe.setOnRefreshListener(() -> {
            viewModel.load(true);
            swipe.setRefreshing(false);
        });

        viewModel.state().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            switch (state.kind) {
                case LOADING:
                    stateView.renderLoading();
                    sections.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    stateView.setVisibility(View.GONE);
                    sections.setVisibility(View.VISIBLE);
                    subjectAdapter.submit(state.data);
                    break;
                case EMPTY:
                    stateView.renderEmpty();
                    sections.setVisibility(View.GONE);
                    break;
                case ERROR:
                    stateView.renderError(state.error);
                    stateView.setOnRetry(() -> viewModel.load(false));
                    sections.setVisibility(View.GONE);
                    break;
            }
        });

        viewModel.load(false);
    }

    private void bindFeatured(@NonNull View root, int containerId, int labelId, int iconId,
                              @NonNull String slug, @NonNull String title) {
        View card = root.findViewById(containerId);
        ((android.widget.TextView) card.findViewById(R.id.featured_label)).setText(labelId);
        ((android.widget.ImageView) card.findViewById(R.id.featured_icon)).setImageResource(iconId);
        card.setOnClickListener(v -> {
            android.os.Bundle args = new android.os.Bundle();
            args.putString("subjectSlug", slug);
            args.putString("subjectName", title);
            NavHostFragment.findNavController(this).navigate(R.id.action_library_to_books, args);
        });
    }

    private void openSubject(Models.Subject subject) {
        android.os.Bundle args = new android.os.Bundle();
        args.putString("subjectSlug", subject.slug);
        args.putString("subjectName", subject.name);
        NavHostFragment.findNavController(this).navigate(R.id.action_library_to_books, args);
    }
}
