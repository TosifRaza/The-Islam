package com.ikp.dev.app.ui.academy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.common.StateView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Academy tab (Phases 1-3):
 * Full Islamic learning platform — course catalogue with category chips,
 * debounced search, continue-learning rail, flashcard decks, and daily lesson.
 */
public final class AcademyFragment extends Fragment {

    private static final String PREFS_NAME = "academy_prefs";
    private static final String KEY_BOOKMARKS = "bookmarked_courses";

    private AcademyViewModel viewModel;
    private StateView stateView;

    private CategoryChipAdapter categoryAdapter;
    private CourseCardAdapter courseAdapter;
    private FlashcardDeckAdapter flashcardAdapter;
    private ContinueLearningAdapter continueAdapter;

    private EditText searchEditText;
    private ImageButton clearSearchBtn;
    private ImageButton btnHeaderBookmark;
    private LinearLayout continueContainer;
    private TextView activeFilterHint;
    private TextView noCoursesHint;
    private TextView signinHint;

    private View dailyLessonCard;
    private TextView dailyTitle;
    private TextView dailyPreview;
    private TextView dailyDuration;
    private Button dailyStartBtn;

    private boolean isBookmarkFilterActive = false;
    private final List<AcademyDtos.CourseRowDto> currentCourses = new ArrayList<>();
    private final List<CategoryChipAdapter.ChipItem> currentCategories = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_academy, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AcademyViewModel.class);

        stateView = view.findViewById(R.id.academy_state);
        searchEditText = view.findViewById(R.id.et_academy_search);
        clearSearchBtn = view.findViewById(R.id.btn_clear_search);
        btnHeaderBookmark = view.findViewById(R.id.btn_header_bookmark);
        continueContainer = view.findViewById(R.id.container_continue);
        activeFilterHint = view.findViewById(R.id.tv_active_filter_hint);
        noCoursesHint = view.findViewById(R.id.tv_no_courses);
        signinHint = view.findViewById(R.id.tv_signin_hint);
        if (signinHint != null) {
            signinHint.setOnClickListener(v -> {
                try {
                    NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                } catch (Exception ignored) {}
            });
        }

        // Header Back Navigation Button
        View btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (!NavHostFragment.findNavController(this).popBackStack()) {
                    NavHostFragment.findNavController(this).navigate(R.id.homeFragment);
                }
            });
        }

        // Header Bookmark Filter Button
        if (btnHeaderBookmark != null) {
            btnHeaderBookmark.setOnClickListener(v -> {
                isBookmarkFilterActive = !isBookmarkFilterActive;
                updateBookmarkFilterUI();
            });
        }

        // Daily lesson card views
        dailyLessonCard = view.findViewById(R.id.card_daily_lesson);
        dailyTitle = view.findViewById(R.id.tv_daily_title);
        dailyPreview = view.findViewById(R.id.tv_daily_preview);
        dailyDuration = view.findViewById(R.id.tv_daily_duration);
        dailyStartBtn = view.findViewById(R.id.btn_start_daily_lesson);

        // 1. Categories Horizontal RecyclerView
        RecyclerView categoriesRv = view.findViewById(R.id.rv_categories);
        categoriesRv.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryChipAdapter((slug, name) -> {
            if (isBookmarkFilterActive) {
                isBookmarkFilterActive = false;
                if (btnHeaderBookmark != null) {
                    btnHeaderBookmark.setImageResource(R.drawable.ic_bookmark_outline);
                }
            }
            viewModel.filterByCategory(slug);
            if (slug == null || slug.isEmpty()) {
                activeFilterHint.setText("");
            } else {
                activeFilterHint.setText("Filtered: " + name);
            }
        });
        categoriesRv.setAdapter(categoryAdapter);
        List<CategoryChipAdapter.ChipItem> defaultChips = new ArrayList<>();
        defaultChips.add(new CategoryChipAdapter.ChipItem(null, "All"));
        defaultChips.add(new CategoryChipAdapter.ChipItem("cat_quran_tafsir", "Al-Quran & Tafsir"));
        defaultChips.add(new CategoryChipAdapter.ChipItem("cat_hadith", "Hadith Library"));
        categoryAdapter.submitList(defaultChips);

        View btnCategoryNext = view.findViewById(R.id.btn_category_next);
        if (btnCategoryNext != null) {
            btnCategoryNext.setOnClickListener(v -> categoriesRv.smoothScrollBy(300, 0));
        }

        // 2. Continue Learning RecyclerView
        RecyclerView continueRv = view.findViewById(R.id.rv_continue);
        continueRv.setLayoutManager(new LinearLayoutManager(requireContext()));
        continueAdapter = new ContinueLearningAdapter(item -> {
            if (item.courseId != null) {
                openCourse(item.courseId);
            }
        });
        continueRv.setAdapter(continueAdapter);

        // 3. Flashcards Horizontal RecyclerView
        RecyclerView flashcardsRv = view.findViewById(R.id.rv_flashcards);
        flashcardsRv.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        flashcardAdapter = new FlashcardDeckAdapter(deck -> {
            Bundle args = new Bundle();
            args.putString("deckId", deck.deckId);
            args.putString("deckTitle", deck.title);
            NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
            NavHostFragment.findNavController(this).navigate(R.id.flashcardPracticeFragment, args, opts);
        });
        flashcardsRv.setAdapter(flashcardAdapter);

        // 4. Featured Courses RecyclerView
        RecyclerView coursesRv = view.findViewById(R.id.rv_courses);
        coursesRv.setLayoutManager(new LinearLayoutManager(requireContext()));
        courseAdapter = new CourseCardAdapter(new CourseCardAdapter.OnCourseClickListener() {
            @Override
            public void onCourseClick(@NonNull AcademyDtos.CourseRowDto course) {
                if (course.courseId != null) {
                    openCourse(course.courseId);
                }
            }

            @Override
            public void onBookmarkClick(@NonNull AcademyDtos.CourseRowDto course) {
                if (course.courseId == null) return;
                SharedPreferences sp = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                Set<String> updated = new HashSet<>(sp.getStringSet(KEY_BOOKMARKS, Collections.emptySet()));
                if (updated.contains(course.courseId)) {
                    updated.remove(course.courseId);
                    Toast.makeText(requireContext(), "Removed " + (course.title != null ? course.title : "course") + " from bookmarks", Toast.LENGTH_SHORT).show();
                } else {
                    updated.add(course.courseId);
                    Toast.makeText(requireContext(), "Saved " + (course.title != null ? course.title : "course") + " to bookmarks", Toast.LENGTH_SHORT).show();
                }
                sp.edit().putStringSet(KEY_BOOKMARKS, updated).apply();
                courseAdapter.setBookmarkedIds(updated);
                if (isBookmarkFilterActive) {
                    updateBookmarkFilterUI();
                }
            }
        });
        coursesRv.setAdapter(courseAdapter);

        // Initial load of bookmarked course IDs
        SharedPreferences initPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        courseAdapter.setBookmarkedIds(initPrefs.getStringSet(KEY_BOOKMARKS, Collections.emptySet()));

        // Search Input & Clear
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString() : "";
                clearSearchBtn.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                viewModel.searchCourses(q);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        clearSearchBtn.setOnClickListener(v -> searchEditText.setText(""));

        // Filter button in search bar
        View btnFilter = view.findViewById(R.id.btn_filter_dialog);
        if (btnFilter != null) {
            btnFilter.setOnClickListener(this::showCategoryFilterMenu);
        }

        // Quick Access Buttons
        view.findViewById(R.id.btn_quick_my_courses).setOnClickListener(v -> {
            boolean signedIn = ServiceLocator.sessionManager().isSignedIn();
            if (signedIn && continueContainer.getVisibility() == View.VISIBLE) {
                continueContainer.getParent().requestChildFocus(continueContainer, continueContainer);
            } else {
                SharedPreferences sp = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                Set<String> bookmarked = sp.getStringSet(KEY_BOOKMARKS, Collections.emptySet());
                if (!bookmarked.isEmpty()) {
                    isBookmarkFilterActive = true;
                    updateBookmarkFilterUI();
                } else {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("My Courses")
                            .setMessage("You have not enrolled in or bookmarked any courses yet.\n\nExplore courses below and tap the bookmark icon to save them to your list.")
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            }
        });

        view.findViewById(R.id.btn_quick_downloads).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Offline Downloads")
                        .setMessage("Download audio lectures, summaries, and PDF study guides directly within each lesson for offline study.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show());

        view.findViewById(R.id.btn_quick_certificates).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Verified Certificates")
                        .setMessage("Complete 100% of course lessons and score 80% or higher on chapter assessments to earn verified Islamic Knowledge Platform certificates.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show());

        view.findViewById(R.id.btn_quick_progress).setOnClickListener(v -> {
            boolean signedIn = ServiceLocator.sessionManager().isSignedIn();
            String msg = signedIn
                    ? "Your lesson completion streak and quiz milestones are synchronized with your profile."
                    : "Sign in with an IKP account to synchronize course progress, track quiz scores, and save your learning journey.";
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("My Progress")
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });

        // Flashcards "View All"
        view.findViewById(R.id.btn_view_all_flashcards).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.flashcardsFragment));

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.reload();
    }

    private void openCourse(@NonNull String courseId) {
        Bundle args = new Bundle();
        args.putString("courseId", courseId);
        NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
        NavHostFragment.findNavController(this).navigate(R.id.courseDetailFragment, args, opts);
    }

    private void render(UiState<AcademyViewModel.AcademyData> state) {
        switch (state.kind) {
            case LOADING -> {
                if (currentCourses.isEmpty()) {
                    stateView.setVisibility(View.VISIBLE);
                    stateView.renderLoading();
                } else {
                    stateView.setVisibility(View.GONE);
                }
            }
            case SUCCESS -> {
                stateView.setVisibility(View.GONE);
                AcademyViewModel.AcademyData data = state.data;

                // Categories
                currentCategories.clear();
                if (data.categories != null && !data.categories.isEmpty()) {
                    currentCategories.addAll(data.categories);
                    categoryAdapter.submitList(data.categories);
                }

                // Continue learning rail
                if (data.continueLearning != null && !data.continueLearning.isEmpty()) {
                    continueContainer.setVisibility(View.VISIBLE);
                    continueAdapter.submitList(data.continueLearning);
                } else {
                    continueContainer.setVisibility(View.GONE);
                }

                // Flashcard decks
                if (data.flashcards != null && !data.flashcards.isEmpty()) {
                    flashcardAdapter.submitList(data.flashcards);
                }

                // Daily lesson
                if (data.dailyLesson != null) {
                    dailyTitle.setText(data.dailyLesson.title);
                    dailyPreview.setText(data.dailyLesson.preview);
                    dailyDuration.setText(data.dailyLesson.duration);
                    dailyStartBtn.setOnClickListener(v -> {
                        if (data.dailyLesson.courseId != null) {
                            openCourse(data.dailyLesson.courseId);
                        } else {
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(data.dailyLesson.title)
                                    .setMessage(data.dailyLesson.preview)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                    });
                }

                // Featured Courses
                currentCourses.clear();
                if (data.courses != null) {
                    currentCourses.addAll(data.courses);
                }

                if (isBookmarkFilterActive) {
                    updateBookmarkFilterUI();
                } else {
                    if (data.courses != null && !data.courses.isEmpty()) {
                        noCoursesHint.setVisibility(View.GONE);
                        courseAdapter.submitList(data.courses);
                    } else {
                        noCoursesHint.setVisibility(View.VISIBLE);
                        noCoursesHint.setText(R.string.academy_no_courses_found);
                        courseAdapter.submitList(new ArrayList<>());
                    }
                }

                // Sign-in hint for guest users
                signinHint.setVisibility(data.memberSections ? View.GONE : View.VISIBLE);
            }
            case EMPTY -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderEmpty();
            }
            case ERROR -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderError(state.error);
                stateView.setOnRetry(viewModel::reload);
            }
        }
    }

    private void updateBookmarkFilterUI() {
        if (btnHeaderBookmark == null || !isAdded()) return;
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> bookmarked = sp.getStringSet(KEY_BOOKMARKS, Collections.emptySet());

        if (isBookmarkFilterActive) {
            btnHeaderBookmark.setImageResource(R.drawable.ic_bookmark_filled);
            androidx.core.widget.ImageViewCompat.setImageTintList(btnHeaderBookmark,
                    android.content.res.ColorStateList.valueOf(0xFFF3DE97));

            List<AcademyDtos.CourseRowDto> bookmarkedList = new ArrayList<>();
            for (AcademyDtos.CourseRowDto c : currentCourses) {
                if (c.courseId != null && bookmarked.contains(c.courseId)) {
                    bookmarkedList.add(c);
                }
            }

            courseAdapter.submitList(bookmarkedList);
            if (bookmarkedList.isEmpty()) {
                noCoursesHint.setVisibility(View.VISIBLE);
                noCoursesHint.setText("No bookmarked courses yet.\nTap the bookmark icon on any course card to save it.");
            } else {
                noCoursesHint.setVisibility(View.GONE);
            }
            activeFilterHint.setText("Bookmarks (" + bookmarkedList.size() + ")");
            Toast.makeText(requireContext(), "Showing bookmarked courses (" + bookmarkedList.size() + ")", Toast.LENGTH_SHORT).show();
        } else {
            btnHeaderBookmark.setImageResource(R.drawable.ic_bookmark_outline);
            androidx.core.widget.ImageViewCompat.setImageTintList(btnHeaderBookmark,
                    android.content.res.ColorStateList.valueOf(0xFFF3DE97));
            btnHeaderBookmark.setColorFilter(0xFFF3DE97);
            activeFilterHint.setText("");
            noCoursesHint.setText(R.string.academy_no_courses_found);

            if (currentCourses.isEmpty()) {
                noCoursesHint.setVisibility(View.VISIBLE);
            } else {
                noCoursesHint.setVisibility(View.GONE);
            }
            courseAdapter.submitList(currentCourses);
            Toast.makeText(requireContext(), "Showing all courses", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCategoryFilterMenu(@NonNull View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 0, 0, "All Categories");

        int index = 1;
        for (CategoryChipAdapter.ChipItem item : currentCategories) {
            if (item.slug != null && !item.slug.isEmpty()) {
                popup.getMenu().add(0, index++, 0, item.name);
            }
        }

        popup.setOnMenuItemClickListener(menuItem -> {
            int order = menuItem.getOrder();
            if (order == 0) {
                if (isBookmarkFilterActive) {
                    isBookmarkFilterActive = false;
                    if (btnHeaderBookmark != null) {
                        btnHeaderBookmark.setImageResource(R.drawable.ic_bookmark_outline);
                    }
                }
                viewModel.filterByCategory(null);
                activeFilterHint.setText("");
                Toast.makeText(requireContext(), "All Categories", Toast.LENGTH_SHORT).show();
            } else {
                if (isBookmarkFilterActive) {
                    isBookmarkFilterActive = false;
                    if (btnHeaderBookmark != null) {
                        btnHeaderBookmark.setImageResource(R.drawable.ic_bookmark_outline);
                    }
                }
                String selectedName = menuItem.getTitle().toString();
                String selectedSlug = null;
                for (CategoryChipAdapter.ChipItem item : currentCategories) {
                    if (item.name.equals(selectedName)) {
                        selectedSlug = item.slug;
                        break;
                    }
                }
                viewModel.filterByCategory(selectedSlug);
                activeFilterHint.setText("Filtered: " + selectedName);
                Toast.makeText(requireContext(), "Filtered: " + selectedName, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
    }
}
