package com.ikp.dev.app.ui.dictionary;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.local.entity.ContentCacheEntities.DictionaryHistoryEntity;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.List;

/**
 * Arabic dictionary (M4 §11). Search history is stored locally (Room);
 * entries whose dataset rights are unresolved are labelled fixtures.
 */
public class DictionaryFragment extends Fragment {

    private final List<ContentDtos.DictionaryEntryDto> rows = new ArrayList<>();
    private EntryAdapter adapter;
    private TextView stateText;
    private TextView history;
    private EditText query;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dictionary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        stateText = v.findViewById(R.id.state_text);
        history = v.findViewById(R.id.tv_history);
        RecyclerView recycler = v.findViewById(R.id.recycler);
        adapter = new EntryAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        query = v.findViewById(R.id.et_query);
        query.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(query.getText().toString());
                return true;
            }
            return false;
        });
        showHistory();
    }

    private void showHistory() {
        ServiceLocator.executors().io.execute(() -> {
            List<String> terms = ServiceLocator.database().contentDao().recentDictionaryTerms(8);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (terms.isEmpty()) {
                    history.setVisibility(View.GONE);
                } else {
                    history.setText(getString(R.string.dictionary_recent,
                            android.text.TextUtils.join(" · ", terms)));
                    history.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void search(String q) {
        if (q == null || q.trim().isEmpty()) return;
        stateText.setText(R.string.state_loading);
        stateText.setVisibility(View.VISIBLE);
        rows.clear();
        adapter.notifyDataSetChanged();
        ServiceLocator.executors().io.execute(() -> {
            DictionaryHistoryEntity e = new DictionaryHistoryEntity();
            e.term = q.trim();
            e.searchedAtEpochMs = System.currentTimeMillis();
            ServiceLocator.database().contentDao().addDictionaryHistory(e);
            Outcome<List<ContentDtos.DictionaryEntryDto>> r =
                    ServiceLocator.contentRepository().dictionarySearch(q.trim());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (r.isSuccess() && r.value != null) {
                    rows.addAll(r.value);
                    adapter.notifyDataSetChanged();
                    stateText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                    stateText.setText(rows.isEmpty() ? R.string.state_empty : R.string.state_empty);
                    showHistory();
                } else {
                    stateText.setText(R.string.state_error_offline);
                }
            });
        });
    }

    private final class EntryAdapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dictionary_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ContentDtos.DictionaryEntryDto e = rows.get(position);
            h.word.setText(e.headwordAr);
            h.meaning.setText(e.meaningEn == null ? "" : e.meaningEn);
            h.fixture.setVisibility(Boolean.TRUE.equals(e.isFixture) ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView word;
        final TextView meaning;
        final TextView fixture;

        Holder(@NonNull View v) {
            super(v);
            word = v.findViewById(R.id.tv_word);
            meaning = v.findViewById(R.id.tv_meaning);
            fixture = v.findViewById(R.id.tv_fixture);
        }
    }
}
