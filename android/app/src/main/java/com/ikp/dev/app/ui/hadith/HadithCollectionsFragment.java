package com.ikp.dev.app.ui.hadith;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Authentic Hadith Library screen presenting the Major Hadith Collections
 * with rich Islamic aesthetics, search, chips filtering, sorting, bookmarking,
 * and journey tracking.
 */
public class HadithCollectionsFragment extends Fragment {

    private static final String PREFS_NAME = "hadith_prefs";
    private static final String KEY_BOOKMARKS = "bookmarked_codes";
    private static final String KEY_EXPLORED = "explored_codes";

    private final List<ContentDtos.HadithCollectionDto> allCollections = new ArrayList<>();
    private final List<ContentDtos.HadithCollectionDto> displayList = new ArrayList<>();

    private CollectionAdapter adapter;
    private TextView stateText;
    private View btnRetry;
    private RecyclerView recycler;
    private TextView tvEmptyState;

    private EditText etSearch;
    private ImageButton btnClearSearch;
    private ImageButton btnFilter;
    private ImageButton btnHeaderBookmark;
    private TextView tvSortLabel;

    // Filter Chips
    private LinearLayout chipAll;
    private LinearLayout chipPrimary;
    private LinearLayout chipSecondary;
    private LinearLayout chipTopical;
    private LinearLayout chipChapters;
    private TextView tvChipAll;
    private TextView tvChipPrimary;
    private TextView tvChipSecondary;
    private TextView tvChipTopical;
    private TextView tvChipChapters;
    private int currentChipId = R.id.chip_all;

    // Sort options
    private static final int SORT_RECOMMENDED = 0;
    private static final int SORT_HADITH_COUNT = 1;
    private static final int SORT_BOOK_COUNT = 2;
    private static final int SORT_ALPHABETICAL = 3;
    private static final int SORT_VERIFIED_FIRST = 4;
    private int currentSort = SORT_RECOMMENDED;

    private boolean isBookmarkFilterActive = false;

    // Journey
    private ProgressBar pbJourney;
    private TextView tvJourneyProgress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hadith_collections, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        initViews(v);
        setupSearch();
        setupChips();
        setupSort();
        setupJourney(v);
        setupNavigation(v);

        load();
    }

    private void initViews(View v) {
        stateText = v.findViewById(R.id.state_text);
        btnRetry = v.findViewById(R.id.btn_retry);
        recycler = v.findViewById(R.id.recycler);
        tvEmptyState = v.findViewById(R.id.tv_empty_state);

        etSearch = v.findViewById(R.id.et_search_hadith);
        btnClearSearch = v.findViewById(R.id.btn_clear_search);
        btnFilter = v.findViewById(R.id.btn_filter);
        btnHeaderBookmark = v.findViewById(R.id.btn_header_bookmark);
        tvSortLabel = v.findViewById(R.id.tv_sort_label);

        chipAll = v.findViewById(R.id.chip_all);
        chipPrimary = v.findViewById(R.id.chip_primary);
        chipSecondary = v.findViewById(R.id.chip_secondary);
        chipTopical = v.findViewById(R.id.chip_topical);
        chipChapters = v.findViewById(R.id.chip_chapters);

        tvChipAll = v.findViewById(R.id.tv_chip_all);
        tvChipPrimary = v.findViewById(R.id.tv_chip_primary);
        tvChipSecondary = v.findViewById(R.id.tv_chip_secondary);
        tvChipTopical = v.findViewById(R.id.tv_chip_topical);
        tvChipChapters = v.findViewById(R.id.tv_chip_chapters);

        pbJourney = v.findViewById(R.id.pb_journey);
        tvJourneyProgress = v.findViewById(R.id.tv_journey_progress);

        adapter = new CollectionAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        if (btnRetry != null) {
            btnRetry.setOnClickListener(x -> load());
        }
    }

    private void setupSearch() {
        if (etSearch == null) return;

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (btnClearSearch != null) {
                    btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                }
                applyFilterAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(x -> {
                etSearch.setText("");
                etSearch.clearFocus();
            });
        }
    }

    private void setupChips() {
        View.OnClickListener chipListener = v -> {
            currentChipId = v.getId();
            updateChipSelectionUI();
            applyFilterAndSort();
        };

        if (chipAll != null) chipAll.setOnClickListener(chipListener);
        if (chipPrimary != null) chipPrimary.setOnClickListener(chipListener);
        if (chipSecondary != null) chipSecondary.setOnClickListener(chipListener);
        if (chipTopical != null) chipTopical.setOnClickListener(chipListener);
        if (chipChapters != null) chipChapters.setOnClickListener(chipListener);
    }

    private void updateChipSelectionUI() {
        resetChip(chipAll, tvChipAll);
        resetChip(chipPrimary, tvChipPrimary);
        resetChip(chipSecondary, tvChipSecondary);
        resetChip(chipTopical, tvChipTopical);
        resetChip(chipChapters, tvChipChapters);

        if (currentChipId == R.id.chip_all) {
            setSelectedChip(chipAll, tvChipAll);
        } else if (currentChipId == R.id.chip_primary) {
            setSelectedChip(chipPrimary, tvChipPrimary);
        } else if (currentChipId == R.id.chip_secondary) {
            setSelectedChip(chipSecondary, tvChipSecondary);
        } else if (currentChipId == R.id.chip_topical) {
            setSelectedChip(chipTopical, tvChipTopical);
        } else if (currentChipId == R.id.chip_chapters) {
            setSelectedChip(chipChapters, tvChipChapters);
        }
    }

    private void setSelectedChip(LinearLayout chip, TextView tv) {
        if (chip != null) chip.setBackgroundResource(R.drawable.bg_category_chip_selected);
        if (tv != null) tv.setTextColor(0xFFFFFFFF);
    }

    private void resetChip(LinearLayout chip, TextView tv) {
        if (chip != null) chip.setBackgroundResource(R.drawable.bg_category_chip_default);
        if (tv != null) tv.setTextColor(0xFF0D3B2E);
    }

    private void setupSort() {
        View.OnClickListener sortClickListener = v -> showSortMenu(v);

        View btnSort = getView() != null ? getView().findViewById(R.id.btn_sort) : null;
        if (btnSort != null) btnSort.setOnClickListener(sortClickListener);
        if (btnFilter != null) btnFilter.setOnClickListener(sortClickListener);
    }

    private void showSortMenu(View anchor) {
        if (getContext() == null) return;
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, SORT_RECOMMENDED, 0, "Recommended");
        popup.getMenu().add(0, SORT_HADITH_COUNT, 1, "Most Ahadith");
        popup.getMenu().add(0, SORT_BOOK_COUNT, 2, "Most Books");
        popup.getMenu().add(0, SORT_ALPHABETICAL, 3, "Alphabetical (A - Z)");
        popup.getMenu().add(0, SORT_VERIFIED_FIRST, 4, "Verified First");

        popup.setOnMenuItemClickListener(item -> {
            currentSort = item.getItemId();
            if (tvSortLabel != null) {
                tvSortLabel.setText("Sort: " + item.getTitle());
            }
            applyFilterAndSort();
            return true;
        });
        popup.show();
    }

    private void setupJourney(View root) {
        updateJourneyUI();

        View journeyCard = root.findViewById(R.id.card_journey);
        View btnNext = root.findViewById(R.id.btn_journey_next);

        View.OnClickListener journeyClick = v -> {
            ContentDtos.HadithCollectionDto nextCollection = getNextUnexploredCollection();
            if (nextCollection != null) {
                openCollection(nextCollection);
            } else if (!allCollections.isEmpty()) {
                openCollection(allCollections.get(0));
            }
        };

        if (journeyCard != null) journeyCard.setOnClickListener(journeyClick);
        if (btnNext != null) btnNext.setOnClickListener(journeyClick);
    }

    private void updateJourneyUI() {
        if (getContext() == null || pbJourney == null || tvJourneyProgress == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> explored = prefs.getStringSet(KEY_EXPLORED, null);
        if (explored == null) {
            // Seed with default initial progress (Bukhari & Muslim explored)
            explored = new HashSet<>();
            explored.add("bukhari");
            explored.add("muslim");
            prefs.edit().putStringSet(KEY_EXPLORED, explored).apply();
        }

        int count = Math.min(explored.size(), 6);
        pbJourney.setMax(6);
        pbJourney.setProgress(count);
        tvJourneyProgress.setText(count + " of 6 collections explored");
    }

    private ContentDtos.HadithCollectionDto getNextUnexploredCollection() {
        if (getContext() == null) return null;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> explored = prefs.getStringSet(KEY_EXPLORED, Collections.emptySet());

        for (ContentDtos.HadithCollectionDto c : allCollections) {
            if (!explored.contains(c.code)) {
                return c;
            }
        }
        return null;
    }

    private void markCollectionExplored(String code) {
        if (getContext() == null || code == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> current = new HashSet<>(prefs.getStringSet(KEY_EXPLORED, Collections.emptySet()));
        current.add(code);
        prefs.edit().putStringSet(KEY_EXPLORED, current).apply();
        updateJourneyUI();
    }

    private void setupNavigation(View root) {
        View btnBack = root.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(x -> NavHostFragment.findNavController(this).popBackStack());
        }

        if (btnHeaderBookmark != null) {
            btnHeaderBookmark.setOnClickListener(v -> {
                isBookmarkFilterActive = !isBookmarkFilterActive;
                btnHeaderBookmark.setImageResource(isBookmarkFilterActive
                        ? R.drawable.ic_bookmark_filled
                        : R.drawable.ic_bookmark_outline);
                Toast.makeText(requireContext(),
                        isBookmarkFilterActive ? "Showing Saved Collections" : "Showing All Collections",
                        Toast.LENGTH_SHORT).show();
                applyFilterAndSort();
            });
        }
    }

    private void load() {
        if (btnRetry != null) btnRetry.setVisibility(View.GONE);
        if (stateText != null) {
            stateText.setText(R.string.state_loading);
            stateText.setVisibility(View.VISIBLE);
        }

        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.HadithCollectionDto>> r =
                    ServiceLocator.contentRepository().hadithCollections();
            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (r.isSuccess() && r.value != null && !r.value.isEmpty()) {
                    allCollections.clear();
                    allCollections.addAll(r.value);
                    applyFilterAndSort();
                    if (stateText != null) stateText.setVisibility(View.GONE);
                    if (recycler != null) recycler.setVisibility(View.VISIBLE);
                    if (btnRetry != null) btnRetry.setVisibility(View.GONE);
                } else {
                    if (stateText != null) stateText.setText(R.string.state_error_offline);
                    if (btnRetry != null) btnRetry.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void applyFilterAndSort() {
        displayList.clear();

        String query = etSearch != null ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> bookmarked = prefs.getStringSet(KEY_BOOKMARKS, Collections.emptySet());

        for (ContentDtos.HadithCollectionDto c : allCollections) {
            // Bookmark header filter
            if (isBookmarkFilterActive && !bookmarked.contains(c.code)) {
                continue;
            }

            // Chips Filter
            if (currentChipId == R.id.chip_secondary) {
                // Secondary hadith collections (if none in primary 6, show empty state or filter)
                if (!"secondary".equalsIgnoreCase(c.editionNote)) {
                    continue;
                }
            }

            // Text Search filter
            if (!query.isEmpty()) {
                boolean matchEn = c.nameEn != null && c.nameEn.toLowerCase(Locale.ROOT).contains(query);
                boolean matchAr = c.nameAr != null && c.nameAr.contains(query);
                boolean matchCode = c.code != null && c.code.toLowerCase(Locale.ROOT).contains(query);
                String subtitle = getSubtitle(c.code).toLowerCase(Locale.ROOT);
                boolean matchSub = subtitle.contains(query);

                if (!matchEn && !matchAr && !matchCode && !matchSub) {
                    continue;
                }
            }

            displayList.add(c);
        }

        // Apply Sorting
        switch (currentSort) {
            case SORT_HADITH_COUNT:
                Collections.sort(displayList, (a, b) -> Integer.compare(b.hadithCount, a.hadithCount));
                break;
            case SORT_BOOK_COUNT:
                Collections.sort(displayList, (a, b) -> Integer.compare(b.bookCount, a.bookCount));
                break;
            case SORT_ALPHABETICAL:
                Collections.sort(displayList, (a, b) -> a.nameEn.compareToIgnoreCase(b.nameEn));
                break;
            case SORT_VERIFIED_FIRST:
                Collections.sort(displayList, (a, b) -> {
                    boolean aVer = isCollectionVerified(a);
                    boolean bVer = isCollectionVerified(b);
                    return Boolean.compare(bVer, aVer);
                });
                break;
            case SORT_RECOMMENDED:
            default:
                // Recommended canonical six books order
                Collections.sort(displayList, (a, b) -> Integer.compare(getCanonicalRank(a.code), getCanonicalRank(b.code)));
                break;
        }

        adapter.notifyDataSetChanged();

        if (tvEmptyState != null) {
            if (displayList.isEmpty()) {
                tvEmptyState.setVisibility(View.VISIBLE);
                if (currentChipId == R.id.chip_secondary) {
                    tvEmptyState.setText("Secondary Hadith collections (Muwatta, Musnad Ahmad) are scheduled for subsequent library releases.");
                } else if (isBookmarkFilterActive) {
                    tvEmptyState.setText("No saved collections yet. Tap the bookmark icon on any collection to save it.");
                } else {
                    tvEmptyState.setText("No collections found matching \"" + query + "\".");
                }
            } else {
                tvEmptyState.setVisibility(View.GONE);
            }
        }
    }

    private static int getCanonicalRank(String code) {
        if (code == null) return 99;
        switch (code.toLowerCase(Locale.ROOT)) {
            case "bukhari": return 1;
            case "muslim": return 2;
            case "abudawud": return 3;
            case "tirmidhi": return 4;
            case "nasai": return 5;
            case "ibnmajah": return 6;
            default: return 10;
        }
    }

    private static String getSubtitle(String code) {
        if (code == null) return "Collection of Prophetic Traditions";
        switch (code.toLowerCase(Locale.ROOT)) {
            case "bukhari": return "The Most Authentic Collection";
            case "muslim": return "An Authentic Collection of Hadith";
            case "abudawud": return "A Key Collection of Hadith";
            case "tirmidhi": return "A Renowned Collection of Hadith";
            case "nasai": return "A Comprehensive Hadith Collection";
            case "ibnmajah": return "A Valuable Collection of Hadith";
            default: return "Collection of Prophetic Traditions";
        }
    }

    private static int getBookCoverDrawable(String code) {
        if (code == null) return R.drawable.ic_hadith_book_bukhari;
        switch (code.toLowerCase(Locale.ROOT)) {
            case "bukhari": return R.drawable.ic_hadith_book_bukhari;
            case "muslim": return R.drawable.ic_hadith_book_muslim;
            case "abudawud": return R.drawable.ic_hadith_book_abudawud;
            case "tirmidhi": return R.drawable.ic_hadith_book_tirmidhi;
            case "nasai": return R.drawable.ic_hadith_book_nasai;
            case "ibnmajah": return R.drawable.ic_hadith_book_ibnmajah;
            default: return R.drawable.ic_hadith_book_bukhari;
        }
    }

    private static boolean isCollectionVerified(ContentDtos.HadithCollectionDto c) {
        if (c == null) return false;
        if ("passed".equalsIgnoreCase(c.scholarlyQaStatus)) return true;
        String code = c.code == null ? "" : c.code.toLowerCase(Locale.ROOT);
        return "bukhari".equals(code) || "muslim".equals(code);
    }

    private void openCollection(ContentDtos.HadithCollectionDto c) {
        markCollectionExplored(c.code);
        Bundle args = new Bundle();
        args.putString("collectionCode", c.code);
        args.putString("collectionName", c.nameEn);
        NavHostFragment.findNavController(HadithCollectionsFragment.this)
                .navigate(R.id.hadithBooksFragment, args);
    }

    private final class CollectionAdapter extends RecyclerView.Adapter<Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hadith_collection_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ContentDtos.HadithCollectionDto c = displayList.get(position);

            h.nameEn.setText(c.nameEn);
            h.nameAr.setText(c.nameAr);
            h.subtitle.setText(getSubtitle(c.code));

            NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
            h.hadithCount.setText(nf.format(c.hadithCount) + " Ahadith");
            h.bookCount.setText(nf.format(c.bookCount) + " Books");

            // 3D Book Graphic
            h.bookCover.setImageResource(getBookCoverDrawable(c.code));

            // Status Badge: Verified vs Review Pending
            boolean verified = isCollectionVerified(c);
            h.badgeVerified.setVisibility(verified ? View.VISIBLE : View.GONE);
            h.badgePending.setVisibility(verified ? View.GONE : View.VISIBLE);

            // Bookmark State
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> bookmarked = prefs.getStringSet(KEY_BOOKMARKS, Collections.emptySet());
            boolean isBookmarked = bookmarked.contains(c.code);
            h.btnBookmark.setImageResource(isBookmarked
                    ? R.drawable.ic_bookmark_filled
                    : R.drawable.ic_bookmark_outline);

            h.btnBookmark.setOnClickListener(v -> {
                Set<String> updated = new HashSet<>(prefs.getStringSet(KEY_BOOKMARKS, Collections.emptySet()));
                if (updated.contains(c.code)) {
                    updated.remove(c.code);
                    Toast.makeText(requireContext(), "Removed " + c.nameEn + " from bookmarks", Toast.LENGTH_SHORT).show();
                } else {
                    updated.add(c.code);
                    Toast.makeText(requireContext(), "Saved " + c.nameEn + " to bookmarks", Toast.LENGTH_SHORT).show();
                }
                prefs.edit().putStringSet(KEY_BOOKMARKS, updated).apply();
                notifyItemChanged(h.getAdapterPosition());
            });

            // Card Navigation
            h.itemView.setOnClickListener(v -> openCollection(c));
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView bookCover;
        final TextView nameEn;
        final TextView subtitle;
        final TextView hadithCount;
        final TextView bookCount;
        final TextView nameAr;
        final View badgeVerified;
        final View badgePending;
        final ImageButton btnBookmark;

        Holder(@NonNull View itemView) {
            super(itemView);
            bookCover = itemView.findViewById(R.id.iv_book_cover);
            nameEn = itemView.findViewById(R.id.tv_name_en);
            subtitle = itemView.findViewById(R.id.tv_subtitle);
            hadithCount = itemView.findViewById(R.id.tv_hadith_count);
            bookCount = itemView.findViewById(R.id.tv_book_count);
            nameAr = itemView.findViewById(R.id.tv_name_ar);
            badgeVerified = itemView.findViewById(R.id.badge_verified);
            badgePending = itemView.findViewById(R.id.badge_pending);
            btnBookmark = itemView.findViewById(R.id.btn_bookmark);
        }
    }
}
