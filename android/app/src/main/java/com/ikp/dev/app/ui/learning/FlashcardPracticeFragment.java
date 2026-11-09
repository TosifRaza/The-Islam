package com.ikp.dev.app.ui.learning;

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
import com.ikp.dev.app.ui.learning.FlashcardPracticeViewModel.PracticeState;

/** Card-by-card flashcard practice (M5); works fully online, offline sync in M7. */
public final class FlashcardPracticeFragment extends Fragment {

    private FlashcardPracticeViewModel viewModel;
    private StateView stateView;
    private View content;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_flashcard_practice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(FlashcardPracticeViewModel.class);
        stateView = view.findViewById(R.id.practice_state);
        content = view.findViewById(R.id.practice_content);
        Bundle args = getArguments();
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        if (args != null && args.getString("deckTitle") != null) {
            ((TextView) view.findViewById(R.id.tv_deck_title)).setText(args.getString("deckTitle"));
        }
        view.findViewById(R.id.btn_reveal).setOnClickListener(v -> viewModel.reveal());
        view.findViewById(R.id.btn_next).setOnClickListener(v -> viewModel.next());
        view.findViewById(R.id.btn_prev).setOnClickListener(v -> viewModel.previous());
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        if (args != null) viewModel.load(args.getString("deckId"));
    }

    private void render(UiState<PracticeState> state) {
        switch (state.kind) {
            case LOADING -> stateView.renderLoading();
            case SUCCESS -> {
                PracticeState s = state.data;
                if (s == null || s.cards == null || s.cards.isEmpty() || s.index < 0 || s.index >= s.cards.size()) {
                    stateView.setVisibility(View.VISIBLE);
                    stateView.renderEmpty();
                    content.setVisibility(View.GONE);
                    return;
                }
                stateView.setVisibility(View.GONE);
                content.setVisibility(View.VISIBLE);
                AcademyDtos.CardDto card = s.cards.get(s.index);
                ((TextView) requireView().findViewById(R.id.tv_card_progress)).setText(
                        getString(R.string.deck_progress_fmt, s.index + 1, s.cards.size()));
                ((TextView) requireView().findViewById(R.id.tv_card_front)).setText(card.frontText);
                TextView hint = requireView().findViewById(R.id.tv_card_hint);
                if (card.hint != null && !card.hint.isEmpty()) {
                    hint.setVisibility(View.VISIBLE);
                    hint.setText(card.hint);
                } else {
                    hint.setVisibility(View.GONE);
                }
                TextView back = requireView().findViewById(R.id.tv_card_back);
                back.setVisibility(s.revealed ? View.VISIBLE : View.GONE);
                back.setText(card.backText);
            }
            case EMPTY -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderEmpty();
            }
            case ERROR -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderError(state.error);
                Bundle args = getArguments();
                stateView.setOnRetry(() -> viewModel.load(args == null ? null : args.getString("deckId")));
            }
        }
    }
}
