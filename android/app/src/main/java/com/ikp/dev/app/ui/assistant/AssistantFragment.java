package com.ikp.dev.app.ui.assistant;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ikp.dev.app.R;

/**
 * AI Assistant foundation (M3). The approved RAG subsystem lands in M6 —
 * until then: composer disabled with an explicit banner, empty conversation
 * state, and NO connection to any AI provider (unapproved providers are
 * forbidden by the project restrictions).
 */
public final class AssistantFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_assistant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.assistant_content).setVisibility(View.VISIBLE);
        view.findViewById(R.id.assistant_state).setVisibility(View.GONE);
        // Composer stays disabled (enabled=false in layout) — honest status.
    }
}
