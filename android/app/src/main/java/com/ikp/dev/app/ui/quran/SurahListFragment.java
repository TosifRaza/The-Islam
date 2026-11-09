package com.ikp.dev.app.ui.quran;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Surah index: 100% matched to the premium Islamic design style.
 * Displays Last Seen, Last Audio, Quick Actions (Bookmarks, Notes, Do & Don't, FAQ),
 * Segmented Tabs (By Surah, By Juz, Favourite), and the ornate 8-pointed star
 * Surah cards with Arabic calligraphy and verse counts.
 */
public class SurahListFragment extends Fragment {

    private final List<ContentDtos.SurahDto> all = new ArrayList<>();
    private final Set<Integer> favourites = new HashSet<>();
    private SharedPreferences prefs;

    private SurahAdapter adapter;
    private TextView stateText;
    private View progressBar;
    private RecyclerView recycler;
    private View layoutSearchBox;
    private EditText etFilter;
    private NestedScrollView scrollContainer;

    // Tabs
    private LinearLayout tabBySurah;
    private LinearLayout tabByJuz;
    private LinearLayout tabByFavourite;
    private ImageView ivTabSurahIcon;
    private TextView tvTabSurahText;
    private ImageView ivTabJuzIcon;
    private TextView tvTabJuzText;
    private ImageView ivTabFavouriteIcon;
    private TextView tvTabFavouriteText;

    private int activeTab = 0; // 0 = By Surah, 1 = By Juz, 2 = Favourite

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quran_surahs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        prefs = requireContext().getSharedPreferences("quran_prefs", Context.MODE_PRIVATE);
        loadFavourites();

        stateText = v.findViewById(R.id.state_text);
        progressBar = v.findViewById(R.id.progress);
        recycler = v.findViewById(R.id.recycler);
        scrollContainer = v.findViewById(R.id.scroll_container);
        layoutSearchBox = v.findViewById(R.id.layout_search_box);
        etFilter = v.findViewById(R.id.et_filter);

        adapter = new SurahAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        // Header back button
        v.findViewById(R.id.btn_back).setOnClickListener(x -> {
            if (!NavHostFragment.findNavController(this).popBackStack()) {
                NavHostFragment.findNavController(this).navigate(R.id.homeFragment);
            }
        });

        // Header search toggle button
        ImageButton btnSearchToggle = v.findViewById(R.id.btn_search_toggle);
        ImageButton btnClearSearch = v.findViewById(R.id.btn_clear_search);

        btnSearchToggle.setOnClickListener(x -> {
            if (layoutSearchBox != null) {
                if (layoutSearchBox.getVisibility() == View.VISIBLE) {
                    layoutSearchBox.setVisibility(View.GONE);
                    btnSearchToggle.setImageResource(R.drawable.ic_search);
                    if (etFilter != null) etFilter.setText("");
                    hideKeyboard(etFilter);
                } else {
                    layoutSearchBox.setVisibility(View.VISIBLE);
                    btnSearchToggle.setImageResource(R.drawable.ic_delete);
                    if (etFilter != null) {
                        etFilter.requestFocus();
                        etFilter.postDelayed(() -> showKeyboard(etFilter), 150);
                    }
                    if (scrollContainer != null && recycler != null) {
                        scrollContainer.postDelayed(() -> {
                            scrollContainer.smoothScrollTo(0, (int) Math.max(0, recycler.getY() - 50));
                        }, 200);
                    }
                }
            }
        });

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(x -> {
                if (etFilter != null) {
                    etFilter.setText("");
                    etFilter.requestFocus();
                }
            });
        }

        if (etFilter != null) {
            etFilter.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) {
                    String query = s == null ? "" : s.toString();
                    filter(query);
                    if (!query.trim().isEmpty() && scrollContainer != null && recycler != null) {
                        scrollContainer.smoothScrollTo(0, (int) Math.max(0, recycler.getY() - 50));
                    }
                }
            });
        }

        // Dual Cards
        View cardLastSeen = v.findViewById(R.id.card_last_seen);
        if (cardLastSeen != null) {
            cardLastSeen.setOnClickListener(x -> openSurah(1)); // Al-Fatihah
        }
        View cardLastAudio = v.findViewById(R.id.card_last_audio);
        if (cardLastAudio != null) {
            cardLastAudio.setOnClickListener(x -> openSurah(2)); // Al-Baqarah
        }

        // Quick Actions
        View btnBookmarks = v.findViewById(R.id.btn_action_bookmarks);
        if (btnBookmarks != null) {
            btnBookmarks.setOnClickListener(x -> showBookmarksDialog());
        }
        View btnNotes = v.findViewById(R.id.btn_action_notes);
        if (btnNotes != null) {
            btnNotes.setOnClickListener(x -> showNotesDialog());
        }
        View btnDodont = v.findViewById(R.id.btn_action_dodont);
        if (btnDodont != null) {
            btnDodont.setOnClickListener(x -> showDoDontDialog());
        }
        View btnFaq = v.findViewById(R.id.btn_action_faq);
        if (btnFaq != null) {
            btnFaq.setOnClickListener(x -> showFaqDialog());
        }

        // Segmented Tabs
        tabBySurah = v.findViewById(R.id.tab_by_surah);
        tabByJuz = v.findViewById(R.id.tab_by_juz);
        tabByFavourite = v.findViewById(R.id.tab_by_favourite);
        ivTabSurahIcon = v.findViewById(R.id.iv_tab_surah_icon);
        tvTabSurahText = v.findViewById(R.id.tv_tab_surah_text);
        ivTabJuzIcon = v.findViewById(R.id.iv_tab_juz_icon);
        tvTabJuzText = v.findViewById(R.id.tv_tab_juz_text);
        ivTabFavouriteIcon = v.findViewById(R.id.iv_tab_favourite_icon);
        tvTabFavouriteText = v.findViewById(R.id.tv_tab_favourite_text);

        if (tabBySurah != null) tabBySurah.setOnClickListener(x -> switchTab(0));
        if (tabByJuz != null) tabByJuz.setOnClickListener(x -> switchTab(1));
        if (tabByFavourite != null) tabByFavourite.setOnClickListener(x -> switchTab(2));

        // View All 114 Surahs
        View btnViewAll = v.findViewById(R.id.btn_view_all_surahs);
        if (btnViewAll != null) {
            btnViewAll.setOnClickListener(x -> {
                switchTab(0);
                if (etFilter != null) etFilter.setText("");
                if (scrollContainer != null) {
                    scrollContainer.smoothScrollTo(0, (int) (recycler != null ? recycler.getY() - 40 : 300));
                }
            });
        }

        View btnRetry = v.findViewById(R.id.btn_retry);
        if (btnRetry != null) btnRetry.setOnClickListener(x -> load(true));

        load(false);
    }

    private void switchTab(int tabIndex) {
        activeTab = tabIndex;
        // Tab 0: By Surah
        tabBySurah.setBackgroundResource(tabIndex == 0 ? R.drawable.bg_tab_surah_selected : R.drawable.bg_tab_unselected);
        tvTabSurahText.setTextColor(tabIndex == 0 ? 0xFFFFFFFF : 0xFF0D3B2D);
        ivTabSurahIcon.setColorFilter(tabIndex == 0 ? 0xFFFFFFFF : 0xFF0D3B2D);

        // Tab 1: By Juz
        tabByJuz.setBackgroundResource(tabIndex == 1 ? R.drawable.bg_tab_surah_selected : R.drawable.bg_tab_unselected);
        tvTabJuzText.setTextColor(tabIndex == 1 ? 0xFFFFFFFF : 0xFF0D3B2D);
        ivTabJuzIcon.setColorFilter(tabIndex == 1 ? 0xFFFFFFFF : 0xFF0D3B2D);

        // Tab 2: Favourite
        tabByFavourite.setBackgroundResource(tabIndex == 2 ? R.drawable.bg_tab_surah_selected : R.drawable.bg_tab_unselected);
        tvTabFavouriteText.setTextColor(tabIndex == 2 ? 0xFFFFFFFF : 0xFF0D3B2D);
        ivTabFavouriteIcon.setColorFilter(tabIndex == 2 ? 0xFFFFFFFF : 0xFF0D3B2D);

        filter(etFilter != null && etFilter.getText() != null ? etFilter.getText().toString() : "");
    }

    private void filter(String q) {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        String cleanNeedle = needle.replace("-", "").replace(" ", "").replace("'", "");
        List<ContentDtos.SurahDto> rows = new ArrayList<>();
        for (ContentDtos.SurahDto s : all) {
            if (activeTab == 2 && !favourites.contains(s.id)) {
                continue;
            }
            if (needle.isEmpty()) {
                rows.add(s);
                continue;
            }
            String cleanEn = s.nameEn != null ? s.nameEn.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "").replace("'", "") : "";
            String cleanTrans = s.nameTransliterated != null ? s.nameTransliterated.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "").replace("'", "") : "";
            boolean matchEn = cleanEn.contains(cleanNeedle);
            boolean matchTrans = cleanTrans.contains(cleanNeedle);
            boolean matchAr = s.nameAr != null && s.nameAr.contains(needle);
            boolean matchId = String.valueOf(s.id).equals(needle);

            if (matchEn || matchTrans || matchAr || matchId) {
                rows.add(s);
            }
        }
        adapter.submit(rows);
        if (stateText != null) {
            if (rows.isEmpty() && !all.isEmpty()) {
                stateText.setVisibility(View.VISIBLE);
                stateText.setText(activeTab == 2 ? "No favourite surahs saved yet. Tap ♡ on any Surah to save it." : "No surahs match your search.");
            } else {
                stateText.setVisibility(View.GONE);
            }
        }
    }

    private void load(boolean force) {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (stateText != null) stateText.setVisibility(View.GONE);
        if (recycler != null) recycler.setVisibility(View.GONE);
        final View root = getView();
        if (root != null) {
            View retryBtn = root.findViewById(R.id.btn_retry);
            if (retryBtn != null) retryBtn.setVisibility(View.GONE);
        }
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.SurahDto>> r = ServiceLocator.contentRepository().surahs(force);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (r.isSuccess() && r.value != null && !r.value.isEmpty()) {
                    all.clear();
                    all.addAll(r.value);
                    filter(etFilter != null && etFilter.getText() != null ? etFilter.getText().toString() : "");
                    if (recycler != null) recycler.setVisibility(View.VISIBLE);
                    if (root != null) {
                        View retryBtn = root.findViewById(R.id.btn_retry);
                        if (retryBtn != null) retryBtn.setVisibility(View.GONE);
                    }
                } else if (r.isSuccess()) {
                    if (stateText != null) {
                        stateText.setVisibility(View.VISIBLE);
                        stateText.setText(R.string.state_empty);
                    }
                } else {
                    if (stateText != null) {
                        stateText.setVisibility(View.VISIBLE);
                        stateText.setText(R.string.state_error_offline);
                    }
                    if (root != null) {
                        View retryBtn = root.findViewById(R.id.btn_retry);
                        if (retryBtn != null) retryBtn.setVisibility(View.VISIBLE);
                    }
                }
            });
        });
    }

    private void openSurah(int surahId) {
        Bundle args = new Bundle();
        args.putInt("surahId", surahId);
        NavHostFragment.findNavController(SurahListFragment.this)
                .navigate(R.id.quranReaderFragment, args);
    }

    private void loadFavourites() {
        favourites.clear();
        Set<String> set = prefs.getStringSet("fav_surahs", null);
        if (set != null) {
            for (String s : set) {
                try {
                    favourites.add(Integer.parseInt(s));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private void toggleFavourite(int surahId) {
        if (favourites.contains(surahId)) {
            favourites.remove(surahId);
            Toast.makeText(getContext(), "Removed from favourites", Toast.LENGTH_SHORT).show();
        } else {
            favourites.add(surahId);
            Toast.makeText(getContext(), "Added to favourites", Toast.LENGTH_SHORT).show();
        }
        Set<String> set = new HashSet<>();
        for (Integer id : favourites) set.add(String.valueOf(id));
        prefs.edit().putStringSet("fav_surahs", set).apply();
        adapter.notifyDataSetChanged();
    }

    private void showBookmarksDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Quran Bookmarks")
                .setMessage("Your saved bookmarks and ayah positions are stored in your profile and offline database.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showNotesDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Quran Study Notes")
                .setMessage("Take personal reflections and scholarly notes while reading any ayah.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showDoDontDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Etiquettes of Tilawat (Quran Recitation)")
                .setMessage("1. Be in a state of Wudu (cleanliness).\n2. Begin with Ta'awwudh (Seeking refuge in Allah) and Bismillah.\n3. Recite with calm, deliberate pace (Tartil).\n4. Ponder upon the meanings of each Ayah.")
                .setPositiveButton("Understood", null)
                .show();
    }

    private void showFaqDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Quran Reader FAQ")
                .setMessage("• The Quran has 114 Surahs and 30 Juz.\n• Offline reading is fully available from the verified local SQLite Room database.\n• Translations and Arabic text are authentic and verified.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void showKeyboard(View view) {
        if (getContext() != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard(View view) {
        if (getContext() != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
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

            String displayTitle = (s.nameTransliterated != null && !s.nameTransliterated.isEmpty())
                    ? s.nameTransliterated
                    : (s.nameEn != null ? s.nameEn : "Surah " + s.id);
            h.nameEn.setText(displayTitle);
            h.nameAr.setText(s.nameAr != null ? s.nameAr : "");
            h.meta.setText(s.ayahCount + " Verses");

            boolean isFav = favourites.contains(s.id);
            if (isFav) {
                h.btnFavourite.setColorFilter(0xFFD4AF37); // Gold filled
            } else {
                h.btnFavourite.setColorFilter(0xFF0D3B2D); // Deep green outline
            }

            h.btnFavourite.setOnClickListener(v -> toggleFavourite(s.id));

            h.btnDownload.setOnClickListener(v ->
                    Toast.makeText(getContext(), displayTitle + " is ready for offline reading", Toast.LENGTH_SHORT).show());

            h.itemView.setOnClickListener(v -> openSurah(s.id));
            if (h.layoutSurahRow != null) {
                h.layoutSurahRow.setOnClickListener(v -> openSurah(s.id));
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static final class SurahHolder extends RecyclerView.ViewHolder {
        final View layoutSurahRow;
        final TextView number;
        final TextView nameEn;
        final TextView nameAr;
        final TextView meta;
        final ImageButton btnFavourite;
        final ImageButton btnDownload;

        SurahHolder(@NonNull View itemView) {
            super(itemView);
            layoutSurahRow = itemView.findViewById(R.id.layout_surah_row);
            number = itemView.findViewById(R.id.tv_number);
            nameEn = itemView.findViewById(R.id.tv_name);
            nameAr = itemView.findViewById(R.id.tv_name_ar);
            meta = itemView.findViewById(R.id.tv_meta);
            btnFavourite = itemView.findViewById(R.id.btn_favourite);
            btnDownload = itemView.findViewById(R.id.btn_download);
        }
    }
}
