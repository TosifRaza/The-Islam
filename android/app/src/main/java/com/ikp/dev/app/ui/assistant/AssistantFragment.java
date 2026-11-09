package com.ikp.dev.app.ui.assistant;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AI Research Assistant (M6 Research Subsystem).
 * Provides an authoritative, academic research companion grounded strictly in
 * IKP's peer-reviewed articles, verified fatwas, and authenticated classical compendia.
 */
public final class AssistantFragment extends Fragment {

    private AssistantViewModel viewModel;

    // Header Views
    private View btnBackHome;
    private TextView tvHeaderTitle;
    private TextView tvHeaderStatus;
    private ImageButton btnNewChat;
    private ImageButton btnHistory;
    private ImageButton btnChatOptions;

    // Home / Welcome Views
    private View scrollAssistantHome;
    private RecyclerView rvSuggestions;
    private RecyclerView rvRecentConversations;
    private TextView tvNoRecentConversations;
    private View btnViewAllHistory;

    // Active Chat Views
    private View llActiveChat;
    private RecyclerView rvChatMessages;

    // Composer Views
    private ImageButton btnVoiceInput;
    private EditText etAssistantInput;
    private ImageButton btnSendQuestion;
    private ProgressBar pbGenerating;

    // Adapters
    private SuggestedQuestionAdapter suggestionsAdapter;
    private AssistantConversationAdapter recentConversationsAdapter;
    private ChatMessageAdapter chatAdapter;

    // Speech-to-Text Launcher
    private ActivityResultLauncher<Intent> speechLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_assistant, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        ArrayList<String> spoken = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (spoken != null && !spoken.isEmpty() && etAssistantInput != null) {
                            etAssistantInput.setText(spoken.get(0));
                            etAssistantInput.setSelection(spoken.get(0).length());
                        }
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AssistantViewModel.class);

        // Find Header Views
        btnBackHome = view.findViewById(R.id.btn_back_home);
        tvHeaderTitle = view.findViewById(R.id.tv_header_title);
        tvHeaderStatus = view.findViewById(R.id.tv_header_status);
        btnNewChat = view.findViewById(R.id.btn_new_chat);
        btnHistory = view.findViewById(R.id.btn_history);
        btnChatOptions = view.findViewById(R.id.btn_chat_options);

        // Find Home Views
        scrollAssistantHome = view.findViewById(R.id.scroll_assistant_home);
        rvSuggestions = view.findViewById(R.id.rv_suggestions);
        rvRecentConversations = view.findViewById(R.id.rv_recent_conversations);
        tvNoRecentConversations = view.findViewById(R.id.tv_no_recent_conversations);
        btnViewAllHistory = view.findViewById(R.id.btn_view_all_history);

        // Find Chat Views
        llActiveChat = view.findViewById(R.id.ll_active_chat);
        rvChatMessages = view.findViewById(R.id.rv_chat_messages);

        // Find Composer Views
        btnVoiceInput = view.findViewById(R.id.btn_voice_input);
        etAssistantInput = view.findViewById(R.id.et_assistant_input);
        btnSendQuestion = view.findViewById(R.id.btn_send_question);
        pbGenerating = view.findViewById(R.id.pb_generating);

        setupRecyclerViews();
        setupListeners();

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.initOrRefresh();
    }

    private void setupRecyclerViews() {
        // Suggestions RecyclerView
        rvSuggestions.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        suggestionsAdapter = new SuggestedQuestionAdapter(question -> {
            etAssistantInput.setText(question.question);
            viewModel.askQuestion(question.question);
            etAssistantInput.setText("");
        });
        rvSuggestions.setAdapter(suggestionsAdapter);

        // Recent Conversations RecyclerView
        rvRecentConversations.setLayoutManager(new LinearLayoutManager(requireContext()));
        recentConversationsAdapter = new AssistantConversationAdapter(new AssistantConversationAdapter.OnConversationActionListener() {
            @Override
            public void onOpenConversation(@NonNull AssistantModels.Conversation conversation) {
                viewModel.openConversation(conversation);
            }

            @Override
            public void onRenameConversation(@NonNull AssistantModels.Conversation conversation) {
                showRenameDialog(conversation);
            }

            @Override
            public void onDeleteConversation(@NonNull AssistantModels.Conversation conversation) {
                showDeleteDialog(conversation);
            }
        });
        rvRecentConversations.setAdapter(recentConversationsAdapter);

        // Active Chat Messages RecyclerView
        LinearLayoutManager chatLm = new LinearLayoutManager(requireContext());
        chatLm.setStackFromEnd(true);
        rvChatMessages.setLayoutManager(chatLm);
        chatAdapter = new ChatMessageAdapter(new ChatMessageAdapter.OnChatInteractionListener() {
            @Override
            public void onCitationClick(@NonNull AssistantModels.SourceCitation citation) {
                openCitationTarget(citation);
            }

            @Override
            public void onFollowUpClick(@NonNull String followUpQuestion) {
                etAssistantInput.setText("");
                viewModel.askQuestion(followUpQuestion);
            }

            @Override
            public void onBookmarkToggle(@NonNull AssistantModels.ChatMessage message) {
                viewModel.toggleBookmark(message);
                Toast.makeText(requireContext(), message.isBookmarked ? "Answer saved" : "Answer removed from bookmarks", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFeedbackSubmit(@NonNull AssistantModels.ChatMessage message, @NonNull String rating) {
                viewModel.submitFeedback(message, rating);
            }
        });
        rvChatMessages.setAdapter(chatAdapter);
    }

    private void setupListeners() {
        btnBackHome.setOnClickListener(v -> viewModel.backToHome());
        btnNewChat.setOnClickListener(v -> viewModel.startNewConversation());
        btnHistory.setOnClickListener(v -> showHistoryDialog());
        btnViewAllHistory.setOnClickListener(v -> showHistoryDialog());

        btnChatOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            popup.getMenu().add(0, 1, 0, "Rename Conversation");
            popup.getMenu().add(0, 2, 1, "Delete Conversation");
            popup.getMenu().add(0, 3, 2, "New Conversation");
            popup.setOnMenuItemClickListener(item -> {
                AssistantModels.Conversation current = viewModel.state().getValue() != null
                        ? viewModel.state().getValue().data.activeConversation : null;
                if (current == null) return false;

                if (item.getItemId() == 1) {
                    showRenameDialog(current);
                    return true;
                } else if (item.getItemId() == 2) {
                    showDeleteDialog(current);
                    return true;
                } else if (item.getItemId() == 3) {
                    viewModel.startNewConversation();
                    return true;
                }
                return false;
            });
            popup.show();
        });

        btnSendQuestion.setOnClickListener(v -> {
            String text = etAssistantInput.getText().toString();
            if (!text.trim().isEmpty()) {
                viewModel.askQuestion(text.trim());
                etAssistantInput.setText("");
            }
        });

        btnVoiceInput.setOnClickListener(v -> launchVoiceRecognition());
    }

    private void launchVoiceRecognition() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask an Islamic research question…");
            speechLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Voice input not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCitationTarget(@NonNull AssistantModels.SourceCitation citation) {
        String type = citation.type != null ? citation.type.toLowerCase(Locale.ROOT) : "";
        NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();

        try {
            if ("article".equals(type)) {
                Bundle args = new Bundle();
                args.putString("articleId", citation.id);
                NavHostFragment.findNavController(this).navigate(R.id.researchArticleFragment, args, opts);
            } else if ("scholar".equals(type)) {
                Bundle args = new Bundle();
                args.putString("scholarId", citation.id);
                NavHostFragment.findNavController(this).navigate(R.id.scholarDetailFragment, args, opts);
            } else if ("fatwa".equals(type)) {
                Bundle args = new Bundle();
                args.putString("fatwaId", citation.id);
                NavHostFragment.findNavController(this).navigate(R.id.fatwaDetailFragment, args, opts);
            } else if ("quran".equals(type)) {
                int surah = 1;
                if (citation.referenceCode != null && citation.referenceCode.toLowerCase().contains("surah")) {
                    try {
                        String numStr = citation.referenceCode.replaceAll("[^0-9:]", "").split(":")[0];
                        surah = Integer.parseInt(numStr);
                    } catch (Exception ignored) {}
                }
                Bundle args = new Bundle();
                args.putInt("surahId", surah);
                NavHostFragment.findNavController(this).navigate(R.id.quranReaderFragment, args, opts);
            } else if ("hadith".equals(type)) {
                NavHostFragment.findNavController(this).navigate(R.id.hadithCollectionsFragment, null, opts);
            } else {
                Toast.makeText(requireContext(), "Opening " + citation.title, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Navigation: " + citation.title, Toast.LENGTH_SHORT).show();
        }
    }

    private void showRenameDialog(@NonNull AssistantModels.Conversation conversation) {
        EditText input = new EditText(requireContext());
        input.setText(conversation.title);
        input.setSelection(conversation.title != null ? conversation.title.length() : 0);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rename Conversation")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (!newTitle.isEmpty()) {
                        viewModel.renameConversation(conversation.id, newTitle);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteDialog(@NonNull AssistantModels.Conversation conversation) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Conversation")
                .setMessage("Are you sure you want to delete '" + conversation.title + "' and its research history?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteConversation(conversation.id);
                    Toast.makeText(requireContext(), "Conversation deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHistoryDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_assistant_history, null, false);
        RecyclerView rvHistory = dialogView.findViewById(R.id.rv_history_list);
        EditText etSearch = dialogView.findViewById(R.id.et_history_search);
        View tvEmpty = dialogView.findViewById(R.id.tv_history_empty);

        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        List<AssistantModels.Conversation> allConvs = viewModel.state().getValue() != null
                ? viewModel.state().getValue().data.recentConversations : new ArrayList<>();

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .create();

        AssistantConversationAdapter historyAdapter = new AssistantConversationAdapter(new AssistantConversationAdapter.OnConversationActionListener() {
            @Override
            public void onOpenConversation(@NonNull AssistantModels.Conversation conversation) {
                dialog.dismiss();
                viewModel.openConversation(conversation);
            }

            @Override
            public void onRenameConversation(@NonNull AssistantModels.Conversation conversation) {
                dialog.dismiss();
                showRenameDialog(conversation);
            }

            @Override
            public void onDeleteConversation(@NonNull AssistantModels.Conversation conversation) {
                dialog.dismiss();
                showDeleteDialog(conversation);
            }
        });

        rvHistory.setAdapter(historyAdapter);
        historyAdapter.submitList(allConvs);
        tvEmpty.setVisibility(allConvs.isEmpty() ? View.VISIBLE : View.GONE);

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString().toLowerCase().trim() : "";
                List<AssistantModels.Conversation> filtered = new ArrayList<>();
                for (AssistantModels.Conversation c : allConvs) {
                    if (c.title != null && c.title.toLowerCase().contains(q)) {
                        filtered.add(c);
                    } else if (c.lastPreview != null && c.lastPreview.toLowerCase().contains(q)) {
                        filtered.add(c);
                    }
                }
                historyAdapter.submitList(filtered);
                tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        dialog.show();
    }

    private void render(UiState<AssistantViewModel.ViewState> state) {
        if (state.kind != UiState.Kind.SUCCESS || state.data == null) return;
        AssistantViewModel.ViewState data = state.data;

        // View Mode Switch: Home vs Active Chat
        if (data.isChatActive && data.activeConversation != null) {
            scrollAssistantHome.setVisibility(View.GONE);
            llActiveChat.setVisibility(View.VISIBLE);
            btnBackHome.setVisibility(View.VISIBLE);
            btnChatOptions.setVisibility(View.VISIBLE);
            tvHeaderTitle.setText(data.activeConversation.title);
            tvHeaderStatus.setText(data.messages.size() + " messages");

            chatAdapter.submitList(data.messages);
            if (!data.messages.isEmpty()) {
                rvChatMessages.post(() -> rvChatMessages.smoothScrollToPosition(data.messages.size() - 1));
            }
        } else {
            scrollAssistantHome.setVisibility(View.VISIBLE);
            llActiveChat.setVisibility(View.GONE);
            btnBackHome.setVisibility(View.GONE);
            btnChatOptions.setVisibility(View.GONE);
            tvHeaderTitle.setText("AI Research Assistant");
            tvHeaderStatus.setText("Approved Library Grounded");

            suggestionsAdapter.submitList(data.suggestions);

            if (data.recentConversations.isEmpty()) {
                rvRecentConversations.setVisibility(View.GONE);
                tvNoRecentConversations.setVisibility(View.VISIBLE);
                btnViewAllHistory.setVisibility(View.GONE);
            } else {
                rvRecentConversations.setVisibility(View.VISIBLE);
                tvNoRecentConversations.setVisibility(View.GONE);
                btnViewAllHistory.setVisibility(View.VISIBLE);
                recentConversationsAdapter.submitList(data.recentConversations);
            }
        }

        // Generating / Progress Indicator
        if (data.isGenerating) {
            pbGenerating.setVisibility(View.VISIBLE);
            btnSendQuestion.setVisibility(View.INVISIBLE);
            etAssistantInput.setEnabled(false);
            btnVoiceInput.setEnabled(false);
        } else {
            pbGenerating.setVisibility(View.GONE);
            btnSendQuestion.setVisibility(View.VISIBLE);
            etAssistantInput.setEnabled(true);
            btnVoiceInput.setEnabled(true);
        }
    }
}
