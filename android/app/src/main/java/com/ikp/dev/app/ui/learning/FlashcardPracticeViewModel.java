package com.ikp.dev.app.ui.learning;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.data.repository.AcademyRepository;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.List;

/** Flashcard practice state (M5): deck cards, current index, reveal flag. */
public final class FlashcardPracticeViewModel extends ViewModel {

    public static final class PracticeState {
        public final List<AcademyDtos.CardDto> cards;
        public final int index;
        public final boolean revealed;

        PracticeState(List<AcademyDtos.CardDto> cards, int index, boolean revealed) {
            this.cards = cards;
            this.index = index;
            this.revealed = revealed;
        }
    }

    private final MutableLiveData<UiState<PracticeState>> state = new MutableLiveData<>();
    private final AcademyRepository repository;
    private List<AcademyDtos.CardDto> cards;
    private int index;
    private boolean revealed;

    public FlashcardPracticeViewModel() {
        this(ServiceLocator.academyRepository());
    }

    public FlashcardPracticeViewModel(AcademyRepository repository) {
        this.repository = repository;
    }

    public LiveData<UiState<PracticeState>> state() { return state; }

    public void load(String deckId) {
        state.postValue(UiState.loading());
        ServiceLocator.executors().io.execute(() -> {
            Outcome<AcademyDtos.DeckDetailDto> r = repository.deck(deckId);
            if (!r.isSuccess() || r.value == null || r.value.cards == null || r.value.cards.isEmpty()) {
                state.postValue(!r.isSuccess() ? UiState.error(r.error) : UiState.empty());
                return;
            }
            cards = r.value.cards;
            index = 0;
            revealed = false;
            state.postValue(UiState.success(new PracticeState(cards, index, revealed)));
        });
    }

    public void reveal() {
        revealed = true;
        publish();
    }

    public void next() {
        if (cards == null || cards.isEmpty()) return;
        index = (index + 1) % cards.size();
        revealed = false;
        publish();
    }

    public void previous() {
        if (cards == null || cards.isEmpty()) return;
        index = (index - 1 + cards.size()) % cards.size();
        revealed = false;
        publish();
    }

    private void publish() {
        state.postValue(UiState.success(new PracticeState(cards, index, revealed)));
    }
}
