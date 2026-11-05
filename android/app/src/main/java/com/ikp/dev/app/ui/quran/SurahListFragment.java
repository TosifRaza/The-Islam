package com.ikp.dev.app.ui.quran;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;
import java.util.Locale;

/**
 * Surah index (M4 §7): all 114 surahs from the backend (Room-cached for
 * offline start), local filter, navigation into the reader. Loading/error/
 * empty states are always rendered — the screen never goes permanently blank.
 */
public class SurahListFragment extends Fragment {

    private final List<ContentDtos.SurahDto> all = new ArrayList<>();
    private SurahAdapter adapter;
    private TextView stateText;
    private View progressBar;
    private RecyclerView recycler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quran_surahs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        stateText = v.findViewById(R.id.state_text);
        progressBar = v.findViewById(R.id.progress);
        recycler = v.findViewById(R.id.recycler);
        adapter = new SurahAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        ((EditText) v.findViewById(R.id.et_filter)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                filter(s == null ? "" : s.toString());
            }
        });
        v.findViewById(R.id.btn_retry).setOnClickListener(x -> load(true));
        load(false);
    }

    private void filter(String q) {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        List<ContentDtos.SurahDto> rows = new ArrayList<>();
        for (ContentDtos.SurahDto s : all) {
            if (needle.isEmpty()
                    || (s.nameEn != null && s.nameEn.toLowerCase(Locale.ROOT).contains(needle))
                    || (s.nameTransliterated != null
                        && s.nameTransliterated.toLowerCase(Locale.ROOT).contains(needle))
                    || String.valueOf(s.id).equals(needle)) {
                rows.add(s);
            }
        }
        adapter.submit(rows);
        stateText.setVisibility(rows.isEmpty() && !all.isEmpty() ? View.VISIBLE : View.GONE);
        stateText.setText(R.string.state_empty);
    }

    private void load(boolean force) {
        progressBar.setVisibility(View.VISIBLE);
        stateText.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        final View root = getView();
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.SurahDto>> r = ServiceLocator.contentRepository().surahs(force);
            requireActivity().runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (r.isSuccess() && r.value != null && !r.value.isEmpty()) {
                    all.clear();
                    all.addAll(r.value);
                    filter("");
                    recycler.setVisibility(View.VISIBLE);
                } else if (r.isSuccess()) {
                    stateText.setVisibility(View.VISIBLE);
                    stateText.setText(R.string.state_empty);
                } else {
                    stateText.setVisibility(View.VISIBLE);
                    stateText.setText(R.string.state_error_offline);
                    if (root != null) root.findViewById(R.id.btn_retry).setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private final class SurahAdapter extends RecyclerView.Adapter<SurahHolder> {
        private final List<ContentDtos.SurahDto> rows = new ArrayList<>();

        void submit(List<ContentDtos.SurahDto> rows) {
            this.rows.clear();
            this.rows.addAll(rows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SurahHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SurahHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_surah_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SurahHolder h, int position) {
            ContentDtos.SurahDto s = rows.get(position);
            h.number.setText(String.valueOf(s.id));
            h.nameEn.setText(getString(R.string.quran_surah_title,
                    s.nameTransliterated == null ? "" : s.nameTransliterated,
                    s.nameEn == null ? "" : s.nameEn));
            h.nameAr.setText(s.nameAr);
            h.meta.setText(getString(R.string.quran_surah_meta,
                    getString("madani".equals(s.revelationPlace)
                            ? R.string.revelation_madani : R.string.revelation_makki),
                    s.ayahCount));
            h.itemView.setOnClickListener(v -> {
                android.os.Bundle args = new android.os.Bundle();
                args.putInt("surahId", s.id);
                NavHostFragment.findNavController(SurahListFragment.this)
                        .navigate(R.id.quranReaderFragment, args);
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static final class SurahHolder extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView nameEn;
        final TextView nameAr;
        final TextView meta;

        SurahHolder(@NonNull View itemView) {
            super(itemView);
            number = itemView.findViewById(R.id.tv_number);
            nameEn = itemView.findViewById(R.id.tv_name);
            nameAr = itemView.findViewById(R.id.tv_name_ar);
            meta = itemView.findViewById(R.id.tv_meta);
        }
    }
}
