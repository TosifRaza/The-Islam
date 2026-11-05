package com.ikp.dev.app.ui.search;

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
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global grouped search (M4 §6 / SRCH-01). Groups with a note and no results
 * (research/courses/lessons/scholars/fatwas) declare their later-milestone
 * availability honestly. Personal uploads can never appear (different table).
 */
public class GlobalSearchFragment extends Fragment {

    private final List<Object> display = new ArrayList<>();
    private ResultAdapter adapter;
    private TextView stateText;
    private EditText query;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_global_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        stateText = v.findViewById(R.id.state_text);
        RecyclerView recycler = v.findViewById(R.id.recycler);
        adapter = new ResultAdapter();
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
        Bundle args = getArguments();
        if (args != null && args.getString("query") != null) {
            query.setText(args.getString("query"));
            search(args.getString("query"));
        }
    }

    private void search(String q) {
        if (q == null || q.trim().isEmpty()) return;
        stateText.setText(R.string.state_loading);
        stateText.setVisibility(View.VISIBLE);
        display.clear();
        adapter.notifyDataSetChanged();
        ServiceLocator.executors().io.execute(() -> {
            Outcome<Map<String, ContentDtos.SearchGroupDto>> r =
                    ServiceLocator.contentRepository().globalSearch(q.trim());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!r.isSuccess() || r.value == null) {
                    stateText.setText(R.string.state_error_offline);
                    return;
                }
                stateText.setVisibility(View.GONE);
                display.clear();
                for (Map.Entry<String, ContentDtos.SearchGroupDto> e : r.value.entrySet()) {
                    ContentDtos.SearchGroupDto g = e.getValue();
                    display.add(new GroupRow(groupTitle(e.getKey())));
                    if (g.items != null) display.addAll(g.items);
                    if ((g.items == null || g.items.isEmpty()) && g.note != null) {
                        display.add(new NoteRow(g.note));
                    }
                    if (g.items != null && g.items.isEmpty() && g.note == null) {
                        display.add(new NoteRow(getString(R.string.search_no_results_group)));
                    }
                }
                adapter.notifyDataSetChanged();
                if (display.isEmpty()) {
                    stateText.setText(R.string.state_empty);
                    stateText.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private static final class GroupRow {
        final String title;

        GroupRow(String title) {
            this.title = title;
        }
    }

    private static final class NoteRow {
        final String text;

        NoteRow(String text) {
            this.text = text;
        }
    }

    private String groupTitle(String key) {
        switch (key) {
            case "quran": return getString(R.string.search_group_quran);
            case "hadith": return getString(R.string.search_group_hadith);
            case "books": return getString(R.string.search_group_books);
            case "authors": return getString(R.string.search_group_authors);
            case "subjects": return getString(R.string.search_group_subjects);
            case "duas": return getString(R.string.search_group_duas);
            case "dictionary": return getString(R.string.search_group_dictionary);
            case "research": return getString(R.string.search_group_research);
            case "courses": return getString(R.string.search_group_courses);
            case "lessons": return getString(R.string.search_group_lessons);
            case "scholars": return getString(R.string.search_group_scholars);
            case "fatwas": return getString(R.string.search_group_fatwas);
            default: return key;
        }
    }

    private final class ResultAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            Object o = display.get(position);
            if (o instanceof GroupRow) return 0;
            if (o instanceof NoteRow) return 1;
            return 2;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == 0) {
                return new GroupHolder(inf.inflate(R.layout.item_search_group_header, parent, false));
            }
            if (viewType == 1) {
                return new NoteHolder(inf.inflate(R.layout.item_search_note_row, parent, false));
            }
            return new ItemHolder(inf.inflate(R.layout.item_search_result_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object o = display.get(position);
            if (holder instanceof GroupHolder && o instanceof GroupRow) {
                ((GroupHolder) holder).title.setText(((GroupRow) o).title);
            } else if (holder instanceof NoteHolder && o instanceof NoteRow) {
                ((NoteHolder) holder).note.setText(((NoteRow) o).text);
            } else if (holder instanceof ItemHolder && o instanceof ContentDtos.SearchItemDto) {
                ContentDtos.SearchItemDto item = (ContentDtos.SearchItemDto) o;
                ((ItemHolder) holder).title.setText(item.title);
                ((ItemHolder) holder).subtitle.setText(item.subtitle);
            }
        }

        @Override
        public int getItemCount() {
            return display.size();
        }
    }

    private static final class GroupHolder extends RecyclerView.ViewHolder {
        final TextView title;

        GroupHolder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tv_group_title);
        }
    }

    private static final class NoteHolder extends RecyclerView.ViewHolder {
        final TextView note;

        NoteHolder(@NonNull View v) {
            super(v);
            note = v.findViewById(R.id.tv_note);
        }
    }

    private static final class ItemHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        ItemHolder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tv_title);
            subtitle = v.findViewById(R.id.tv_subtitle);
        }
    }
}
