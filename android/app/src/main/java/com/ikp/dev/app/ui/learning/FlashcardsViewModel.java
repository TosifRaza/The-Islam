package com.ikp.dev.app.ui.learning;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.data.repository.AcademyRepository;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.List;

/** Flashcard deck browser (M5): published admin decks and private user decks. */
public final class FlashcardsViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<AcademyDtos.DeckRowDto>>> adminDecks =
            new MutableLiveData<>();
    private final MutableLiveData<UiState<List<AcademyDtos.DeckRowDto>>> myDecks =
            new MutableLiveData<>();
    private final AcademyRepository repository;

    public FlashcardsViewModel() {
        this(ServiceLocator.academyRepository());
    }

    public FlashcardsViewModel(AcademyRepository repository) {
        this.repository = repository;
    }

    public LiveData<UiState<List<AcademyDtos.DeckRowDto>>> adminDecks() { return adminDecks; }

    public LiveData<UiState<List<AcademyDtos.DeckRowDto>>> myDecks() { return myDecks; }

    public void reload() {
        adminDecks.postValue(UiState.loading());
        myDecks.postValue(UiState.loading());
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<AcademyDtos.DeckRowDto>> a = repository.publicDecks(1, 50);
            adminDecks.postValue(a.isSuccess() && !a.value.isEmpty()
                    ? UiState.success(a.value) : (a.isSuccess() ? UiState.empty() : UiState.error(a.error)));
            if (ServiceLocator.sessionManager().isSignedIn()) {
                Outcome<List<AcademyDtos.DeckRowDto>> m = repository.myDecks();
                myDecks.postValue(m.isSuccess() && !m.value.isEmpty()
                        ? UiState.success(m.value) : (m.isSuccess() ? UiState.empty() : UiState.error(m.error)));
            } else {
                myDecks.postValue(UiState.empty());
            }
        });
    }

    public void createDeck(String title, List<String> lines) {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<AcademyDtos.DeckDetailDto> r = repository.createMyDeck(title, lines);
            reload();
        });
    }
}
