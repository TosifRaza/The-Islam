package com.ikp.dev.app.ui.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.repository.AssistantRepository;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.List;

public final class AssistantViewModel extends ViewModel {

    public static final class ViewState {
        public final boolean isChatActive;
        @Nullable public final AssistantModels.Conversation activeConversation;
        public final List<AssistantModels.ChatMessage> messages;
        public final List<AssistantModels.SuggestedQuestion> suggestions;
        public final List<AssistantModels.Conversation> recentConversations;
        public final boolean isGenerating;

        public ViewState(boolean isChatActive,
                         @Nullable AssistantModels.Conversation activeConversation,
                         List<AssistantModels.ChatMessage> messages,
                         List<AssistantModels.SuggestedQuestion> suggestions,
                         List<AssistantModels.Conversation> recentConversations,
                         boolean isGenerating) {
            this.isChatActive = isChatActive;
            this.activeConversation = activeConversation;
            this.messages = messages != null ? messages : new ArrayList<>();
            this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
            this.recentConversations = recentConversations != null ? recentConversations : new ArrayList<>();
            this.isGenerating = isGenerating;
        }
    }

    private final MutableLiveData<UiState<ViewState>> state = new MutableLiveData<>();
    private final AssistantRepository repository;

    private boolean isChatActive = false;
    private AssistantModels.Conversation activeConversation = null;
    private final List<AssistantModels.ChatMessage> currentMessages = new ArrayList<>();
    private final List<AssistantModels.SuggestedQuestion> suggestions = new ArrayList<>();
    private final List<AssistantModels.Conversation> recentConversations = new ArrayList<>();
    private boolean isGenerating = false;

    public AssistantViewModel() {
        this(ServiceLocator.assistantRepository());
    }

    public AssistantViewModel(AssistantRepository repository) {
        this.repository = repository;
        this.suggestions.addAll(repository.defaultSuggestions());
        postSuccessState();
    }

    public LiveData<UiState<ViewState>> state() {
        return state;
    }

    public void initOrRefresh() {
        ServiceLocator.executors().io.execute(() -> {
            loadConversationsInternal();
            if (suggestions.isEmpty()) {
                suggestions.addAll(repository.defaultSuggestions());
            }

            if (isChatActive && activeConversation != null) {
                currentMessages.clear();
                currentMessages.addAll(repository.getMessages(activeConversation.id));
            }

            postSuccessState();

            // Background refresh of suggestions
            loadSuggestionsInternal();
            postSuccessState();
        });
    }

    public void openConversation(@NonNull AssistantModels.Conversation conv) {
        ServiceLocator.executors().io.execute(() -> {
            isChatActive = true;
            activeConversation = conv;
            currentMessages.clear();
            currentMessages.addAll(repository.getMessages(conv.id));
            postSuccessState();
        });
    }

    public void startNewConversation() {
        ServiceLocator.executors().io.execute(() -> {
            AssistantModels.Conversation newConv = repository.createConversation("New Research Inquiry");
            isChatActive = true;
            activeConversation = newConv;
            currentMessages.clear();
            loadConversationsInternal();
            postSuccessState();
        });
    }

    public void backToHome() {
        ServiceLocator.executors().io.execute(() -> {
            isChatActive = false;
            activeConversation = null;
            currentMessages.clear();
            loadConversationsInternal();
            postSuccessState();
        });
    }

    public void askQuestion(@NonNull String text) {
        if (text.trim().isEmpty() || isGenerating) return;

        ServiceLocator.executors().io.execute(() -> {
            if (!isChatActive || activeConversation == null) {
                activeConversation = repository.createConversation(null);
                isChatActive = true;
                currentMessages.clear();
            }

            final String conversationId = activeConversation != null ? activeConversation.id : "c_" + System.currentTimeMillis();

            // Instantly append temporary user message for immediate UI responsiveness
            AssistantModels.ChatMessage pendingUser = AssistantModels.ChatMessage.user(conversationId, text.trim());
            currentMessages.add(pendingUser);
            isGenerating = true;
            postSuccessState();

            Outcome<AssistantModels.ChatMessage> res = repository.query(conversationId, text.trim(), "en");
            isGenerating = false;

            if (res.isSuccess() && res.value != null) {
                // Refresh messages from repository store if still in this conversation
                if (activeConversation != null && conversationId.equals(activeConversation.id)) {
                    currentMessages.clear();
                    currentMessages.addAll(repository.getMessages(conversationId));
                }
            }

            loadConversationsInternal();
            postSuccessState();
        });
    }

    public void renameConversation(@NonNull String id, @NonNull String newTitle) {
        ServiceLocator.executors().io.execute(() -> {
            repository.renameConversation(id, newTitle);
            if (activeConversation != null && activeConversation.id.equals(id)) {
                activeConversation.title = newTitle;
            }
            loadConversationsInternal();
            postSuccessState();
        });
    }

    public void deleteConversation(@NonNull String id) {
        ServiceLocator.executors().io.execute(() -> {
            repository.deleteConversation(id);
            if (activeConversation != null && activeConversation.id.equals(id)) {
                isChatActive = false;
                activeConversation = null;
                currentMessages.clear();
            }
            loadConversationsInternal();
            postSuccessState();
        });
    }

    public void toggleBookmark(@NonNull AssistantModels.ChatMessage msg) {
        ServiceLocator.executors().io.execute(() -> {
            if (activeConversation != null) {
                repository.toggleBookmark(activeConversation.id, msg.id);
                msg.isBookmarked = !msg.isBookmarked;
            }
        });
    }

    public void submitFeedback(@NonNull AssistantModels.ChatMessage msg, @NonNull String rating) {
        ServiceLocator.executors().io.execute(() -> {
            if (activeConversation != null) {
                repository.submitFeedback(activeConversation.id, msg.id, msg.content, rating, "");
                msg.feedback = rating;
            }
        });
    }

    private void loadSuggestionsInternal() {
        Outcome<List<AssistantModels.SuggestedQuestion>> suggRes = repository.getSuggestions();
        suggestions.clear();
        if (suggRes.isSuccess() && suggRes.value != null) {
            suggestions.addAll(suggRes.value);
        }
    }

    private void loadConversationsInternal() {
        recentConversations.clear();
        recentConversations.addAll(repository.getConversations());
    }

    private void postSuccessState() {
        ViewState vs = new ViewState(
                isChatActive,
                activeConversation,
                new ArrayList<>(currentMessages),
                new ArrayList<>(suggestions),
                new ArrayList<>(recentConversations),
                isGenerating
        );
        state.postValue(UiState.success(vs));
    }
}
