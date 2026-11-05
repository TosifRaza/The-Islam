package com.ikp.dev.app.ui.learning;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.NavOptions;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.common.StateView;
import com.ikp.dev.app.ui.common.TwoLineAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Flashcard decks (M5): published admin decks for everyone; the signed-in
 * member additionally sees and creates PRIVATE personal decks. Personal decks
 * are strictly separated server-side (owner-scoped reads).
 */
public final class FlashcardsFragment extends Fragment {

    private FlashcardsViewModel viewModel;
    private StateView stateView;
    private TwoLineAdapter adapter;
    private boolean myMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_flashcards, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(FlashcardsViewModel.class);
        stateView = view.findViewById(R.id.deck_state);
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        RecyclerView rv = view.findViewById(R.id.rv_decks);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TwoLineAdapter((item, pos) -> {
            AcademyDtos.DeckRowDto deck = currentDeck(pos);
            if (deck == null) return;
            Bundle args = new Bundle();
            args.putString("deckId", deck.deckId);
            args.putString("deckTitle", deck.title);
            NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
            NavHostFragment.findNavController(this).navigate(R.id.flashcardPracticeFragment, args, opts);
        });
        rv.setAdapter(adapter);

        ChipGroup chips = view.findViewById(R.id.chips_deck_mode);
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            myMode = ids.contains(R.id.chip_my_decks);
            renderList();
        });

        view.findViewById(R.id.btn_new_deck).setOnClickListener(v -> showNewDeckDialog());

        viewModel.adminDecks().observe(getViewLifecycleOwner(), s -> { if (!myMode) render(s); });
        viewModel.myDecks().observe(getViewLifecycleOwner(), s -> { if (myMode) render(s); });
        viewModel.reload();
    }

    private final List<AcademyDtos.DeckRowDto> shown = new ArrayList<>();

    private AcademyDtos.DeckRowDto currentDeck(int pos) {
        return pos >= 0 && pos < shown.size() ? shown.get(pos) : null;
    }

    private void renderList() {
        UiState<List<AcademyDtos.DeckRowDto>> s = myMode
                ? viewModel.myDecks().getValue()
                : viewModel.adminDecks().getValue();
        if (s != null) render(s);
    }

    private void render(UiState<List<AcademyDtos.DeckRowDto>> state) {
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
                for (AcademyDtos.DeckRowDto d : shown) {
                    rows.add(new TwoLineAdapter.ItemImpl(d.title,
                            d.cardCount + " cards", d.ownerType));
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

    private void showNewDeckDialog() {
        if (!ServiceLocator.sessionManager().isSignedIn()) {
            Toast.makeText(requireContext(), R.string.signin_required, Toast.LENGTH_SHORT).show();
            return;
        }
        View dialog = getLayoutInflater().inflate(R.layout.dialog_new_deck, null, false);
        TextInputEditText title = dialog.findViewById(R.id.et_deck_title);
        TextInputEditText cards = dialog.findViewById(R.id.et_deck_cards);
        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialog)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.deck_create, (d, w) -> {
                    String t = title.getText() == null ? "" : title.getText().toString().trim();
                    List<String> lines = new ArrayList<>();
                    if (cards.getText() != null) {
                        String[] parts = cards.getText().toString().split("\n");
                        for (String p : parts) lines.add(p.trim());
                    }
                    if (!t.isEmpty()) viewModel.createDeck(t, lines);
                })
                .show();
    }
}
