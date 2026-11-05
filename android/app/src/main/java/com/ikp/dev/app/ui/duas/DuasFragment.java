package com.ikp.dev.app.ui.duas;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Duas & Adhkar (M4 §14): categories + duas. Category rows carry the honest
 * fixture note when rights are unresolved (SR-16); audio is shown as
 * unavailable unless an audioMediaId exists.
 */
public class DuasFragment extends Fragment {

    private final Map<String, String> categories = new LinkedHashMap<>();
    private final List<ContentDtos.DuaDto> rows = new ArrayList<>();
    private DuaAdapter adapter;
    private TextView stateText;
    private ChipGroup chips;
    private String selectedCategory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_duas, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        stateText = v.findViewById(R.id.state_text);
        chips = v.findViewById(R.id.chips_categories);
        RecyclerView recycler = v.findViewById(R.id.recycler);
        adapter = new DuaAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        v.findViewById(R.id.btn_retry).setOnClickListener(x -> {
            loadCategories();
            loadDuas();
        });
        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                View chipView = group.findViewById(checkedIds.get(0));
                if (chipView != null) {
                    Object tag = chipView.getTag();
                    selectedCategory = tag == null ? null : String.valueOf(tag);
                    loadDuas();
                }
            }
        });
        loadCategories();
        loadDuas();
    }

    private void loadCategories() {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.DuaCategoryDto>> r =
                    ServiceLocator.contentRepository().duaCategories();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (r.isSuccess() && r.value != null) {
                    categories.clear();
                    categories.put("", getString(R.string.duas_all));
                    for (ContentDtos.DuaCategoryDto c : r.value) categories.put(c.code, c.nameEn);
                    buildChips();
                }
            });
        });
    }

    private void buildChips() {
        chips.removeAllViews();
        int id = 1000;
        for (Map.Entry<String, String> e : categories.entrySet()) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
            chip.setId(++id);
            chip.setText(e.getValue());
            chip.setTag(e.getKey());
            chip.setCheckable(true);
            chips.addView(chip);
        }
    }

    private void loadDuas() {
        stateText.setText(R.string.state_loading);
        stateText.setVisibility(View.VISIBLE);
        ServiceLocator.executors().io.execute(() -> {
            String cat = selectedCategory == null || selectedCategory.isEmpty() ? null : selectedCategory;
            Outcome<List<ContentDtos.DuaDto>> r =
                    ServiceLocator.contentRepository().duas(cat, 1, 50);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (r.isSuccess() && r.value != null) {
                    rows.clear();
                    rows.addAll(r.value);
                    adapter.notifyDataSetChanged();
                    stateText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                    stateText.setText(rows.isEmpty() ? R.string.state_empty : R.string.state_empty);
                } else {
                    stateText.setText(R.string.state_error_offline);
                }
            });
        });
    }

    private final class DuaAdapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dua_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ContentDtos.DuaDto d = rows.get(position);
            h.title.setText(d.title == null ? "" : d.title);
            h.arabic.setText(d.textAr);
            StringBuilder body = new StringBuilder();
            if (d.transliteration != null) body.append(d.transliteration).append("\n");
            if (d.meaningEn != null) body.append(d.meaningEn);
            h.meaning.setText(body.toString());
            h.fixture.setVisibility(Boolean.TRUE.equals(d.isFixture) ? View.VISIBLE : View.GONE);
            h.audio.setVisibility(d.audioMediaId == null ? View.VISIBLE : View.GONE);
            h.audio.setText(R.string.dua_audio_unavailable);
            h.itemView.setOnClickListener(v -> ServiceLocator.executors().io.execute(() ->
                    ServiceLocator.userContentRepository().putFavourite("dua:" + d.id)));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView arabic;
        final TextView meaning;
        final TextView fixture;
        final TextView audio;

        Holder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tv_title);
            arabic = v.findViewById(R.id.tv_arabic);
            meaning = v.findViewById(R.id.tv_meaning);
            fixture = v.findViewById(R.id.tv_fixture);
            audio = v.findViewById(R.id.tv_audio);
        }
    }
}
