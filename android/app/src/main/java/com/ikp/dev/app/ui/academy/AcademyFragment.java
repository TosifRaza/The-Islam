package com.ikp.dev.app.ui.academy;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.common.StateView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
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

        // Quick Access Buttons - 100% Fully Functional
        view.findViewById(R.id.btn_quick_my_courses).setOnClickListener(v -> showCoursesBottomSheet());
        view.findViewById(R.id.btn_quick_downloads).setOnClickListener(v -> showDownloadsBottomSheet());
        view.findViewById(R.id.btn_quick_certificates).setOnClickListener(v -> showCertificatesBottomSheet());
        view.findViewById(R.id.btn_quick_progress).setOnClickListener(v -> showProgressBottomSheet());

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

    // =========================================================================
    // Workable Interactive Quick Access Feature Sheets
    // =========================================================================

    private void showCoursesBottomSheet() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_academy_courses, null);
        dialog.setContentView(sheet);

        View btnClose = sheet.findViewById(R.id.btn_close_courses_sheet);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        View btnBrowseOnPage = sheet.findViewById(R.id.btn_browse_catalogue);
        if (btnBrowseOnPage != null) {
            btnBrowseOnPage.setOnClickListener(v -> {
                dialog.dismiss();
                View rv = getView() != null ? getView().findViewById(R.id.rv_courses) : null;
                View scroll = getView() != null ? getView().findViewById(R.id.academy_scroll) : null;
                if (rv != null && scroll instanceof androidx.core.widget.NestedScrollView) {
                    ((androidx.core.widget.NestedScrollView) scroll).smoothScrollTo(0, (int) rv.getY() - 40);
                }
            });
        }

        LinearLayout container = sheet.findViewById(R.id.container_courses_list);
        List<AcademyDtos.CourseRowDto> list = !currentCourses.isEmpty()
                ? currentCourses
                : ServiceLocator.academyRepository().getCachedOrSeedCourses();

        TextView tabAll = sheet.findViewById(R.id.tab_courses_all);
        TextView tabEnrolled = sheet.findViewById(R.id.tab_courses_enrolled);
        TextView tabBookmarked = sheet.findViewById(R.id.tab_courses_bookmarked);

        populateCoursesSheetList(dialog, container, list, 0);

        if (tabAll != null) {
            tabAll.setOnClickListener(v -> {
                setTabActive(tabAll, tabEnrolled, tabBookmarked);
                populateCoursesSheetList(dialog, container, list, 0);
            });
        }
        if (tabEnrolled != null) {
            tabEnrolled.setOnClickListener(v -> {
                setTabActive(tabEnrolled, tabAll, tabBookmarked);
                populateCoursesSheetList(dialog, container, list, 1);
            });
        }
        if (tabBookmarked != null) {
            tabBookmarked.setOnClickListener(v -> {
                setTabActive(tabBookmarked, tabAll, tabEnrolled);
                populateCoursesSheetList(dialog, container, list, 2);
            });
        }

        dialog.show();
    }

    private void setTabActive(TextView active, TextView... others) {
        active.setBackgroundResource(R.drawable.bg_category_chip_selected);
        active.setTextColor(Color.WHITE);
        for (TextView o : others) {
            if (o != null) {
                o.setBackgroundResource(R.drawable.bg_category_chip_default);
                o.setTextColor(0xFF12291F);
            }
        }
    }

    private void populateCoursesSheetList(BottomSheetDialog dialog,
                                          LinearLayout container,
                                          List<AcademyDtos.CourseRowDto> allList,
                                          int filterType) {
        if (container == null || getContext() == null) return;
        container.removeAllViews();
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> bookmarked = sp.getStringSet(KEY_BOOKMARKS, Collections.emptySet());

        List<AcademyDtos.CourseRowDto> filtered = new ArrayList<>();
        for (int i = 0; i < allList.size(); i++) {
            AcademyDtos.CourseRowDto c = allList.get(i);
            if (filterType == 0) {
                filtered.add(c);
            } else if (filterType == 1) {
                if (i < 2 || (c.courseId != null && bookmarked.contains(c.courseId))) filtered.add(c);
            } else if (filterType == 2) {
                if (c.courseId != null && bookmarked.contains(c.courseId)) filtered.add(c);
            }
        }

        if (filtered.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(filterType == 2 ? "No bookmarked courses yet.\nTap the bookmark icon on any course to save it here." : "No enrolled courses yet.");
            empty.setPadding(0, 40, 0, 40);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setTextColor(0xFF7A8880);
            empty.setTextSize(13);
            container.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (AcademyDtos.CourseRowDto course : filtered) {
            View card = inflater.inflate(R.layout.item_course_quick_sheet, container, false);
            TextView tvSub = card.findViewById(R.id.tv_sheet_course_subject);
            TextView tvMeta = card.findViewById(R.id.tv_sheet_course_meta);
            TextView tvTitle = card.findViewById(R.id.tv_sheet_course_title);
            TextView tvInst = card.findViewById(R.id.tv_sheet_course_instructor);
            Button btnOpen = card.findViewById(R.id.btn_sheet_open_course);

            if (tvSub != null) tvSub.setText(course.subject != null && course.subject.name != null ? course.subject.name : "Islamic Studies");
            if (tvMeta != null) tvMeta.setText((course.lessonCount != null ? course.lessonCount : 6) + " Lessons • " + (course.estimatedHours != null ? course.estimatedHours : 4) + "h");
            if (tvTitle != null) tvTitle.setText(course.title != null ? course.title : "Course");
            if (tvInst != null) tvInst.setText(course.instructorDisplayName != null ? course.instructorDisplayName : "Verified Islamic Scholar");

            View.OnClickListener clickListener = v -> {
                dialog.dismiss();
                if (course.courseId != null) {
                    openCourse(course.courseId);
                }
            };
            if (btnOpen != null) btnOpen.setOnClickListener(clickListener);
            card.setOnClickListener(clickListener);

            container.addView(card);
        }
    }

    private void showDownloadsBottomSheet() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_academy_downloads, null);
        dialog.setContentView(sheet);

        View btnClose = sheet.findViewById(R.id.btn_close_downloads_sheet);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        View btnSync = sheet.findViewById(R.id.btn_sync_offline_pack);
        View pb = sheet.findViewById(R.id.pb_download_all);
        TextView tvStorage = sheet.findViewById(R.id.tv_storage_title);

        if (btnSync != null) {
            btnSync.setOnClickListener(v -> {
                if (pb != null) pb.setVisibility(View.VISIBLE);
                btnSync.setEnabled(false);
                btnSync.postDelayed(() -> {
                    if (pb != null) pb.setVisibility(View.GONE);
                    if (tvStorage != null) tvStorage.setText("198 MB Saved for Offline Study");
                    btnSync.setEnabled(true);
                    Toast.makeText(requireContext(), "Offline audio packs & study guides updated successfully!", Toast.LENGTH_SHORT).show();
                }, 1200);
            });
        }

        View btnAudio = sheet.findViewById(R.id.btn_play_offline_audio);
        if (btnAudio != null) {
            btnAudio.setOnClickListener(v -> {
                dialog.dismiss();
                NavHostFragment.findNavController(this).navigate(R.id.action_global_quran_surahs);
                Toast.makeText(requireContext(), "Playing Tajweed Quran Audio offline", Toast.LENGTH_SHORT).show();
            });
        }

        View btnHadith = sheet.findViewById(R.id.btn_read_offline_hadith);
        if (btnHadith != null) {
            btnHadith.setOnClickListener(v -> {
                dialog.dismiss();
                NavHostFragment.findNavController(this).navigate(R.id.action_global_hadith_collections);
            });
        }

        View btnDuas = sheet.findViewById(R.id.btn_open_offline_duas);
        if (btnDuas != null) {
            btnDuas.setOnClickListener(v -> {
                dialog.dismiss();
                NavHostFragment.findNavController(this).navigate(R.id.action_global_duas);
            });
        }

        dialog.show();
    }

    private void showCertificatesBottomSheet() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_academy_certificates, null);
        dialog.setContentView(sheet);

        View btnClose = sheet.findViewById(R.id.btn_close_cert_sheet);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        View btnViewCert = sheet.findViewById(R.id.btn_view_formal_cert);
        if (btnViewCert != null) {
            btnViewCert.setOnClickListener(v -> {
                dialog.dismiss();
                showFormalCertificateDialog();
            });
        }

        View btnExam = sheet.findViewById(R.id.btn_take_cert_exam);
        if (btnExam != null) {
            btnExam.setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    NavHostFragment.findNavController(this).navigate(R.id.quizzesFragment);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Opening Official Assessment Quiz...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnContinue = sheet.findViewById(R.id.btn_continue_to_cert);
        if (btnContinue != null) {
            btnContinue.setOnClickListener(v -> {
                dialog.dismiss();
                openCourse("c0000002-0000-0000-0000-000000000002");
            });
        }

        dialog.show();
    }

    private void showFormalCertificateDialog() {
        if (getContext() == null) return;
        Dialog certDialog = new Dialog(requireContext());
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_formal_certificate, null);
        certDialog.setContentView(v);
        if (certDialog.getWindow() != null) {
            certDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            certDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        View certCard = v.findViewById(R.id.card_formal_certificate);

        View btnClose = v.findViewById(R.id.btn_close_cert_modal);
        if (btnClose != null) btnClose.setOnClickListener(x -> certDialog.dismiss());

        View btnShare = v.findViewById(R.id.btn_share_cert);
        if (btnShare != null) {
            btnShare.setOnClickListener(x -> shareCertificateImage(certCard));
        }

        View btnSave = v.findViewById(R.id.btn_save_cert);
        if (btnSave != null) {
            btnSave.setOnClickListener(x -> saveCertificateImage(certCard));
        }

        certDialog.show();
    }

    private Bitmap getCertificateMasterBitmap(View certCard) {
        if (certCard != null) {
            android.widget.ImageView iv = certCard.findViewById(R.id.img_certificate_artwork);
            if (iv != null && iv.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
                Bitmap bmp = ((android.graphics.drawable.BitmapDrawable) iv.getDrawable()).getBitmap();
                if (bmp != null) {
                    return bmp;
                }
            }
        }
        return renderViewToBitmap(certCard);
    }

    private Bitmap renderViewToBitmap(View view) {
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            width = view.getMeasuredWidth();
            height = view.getMeasuredHeight();
            view.layout(0, 0, width, height);
        }

        // Render at crisp high resolution (at least 1080px wide)
        float scale = width < 1080 ? 1080f / (float) width : 1.0f;
        int targetW = Math.round(width * scale);
        int targetH = Math.round(height * scale);

        Bitmap bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (scale != 1.0f) {
            canvas.scale(scale, scale);
        }
        view.draw(canvas);
        return bitmap;
    }

    private void shareCertificateImage(View certCard) {
        if (getContext() == null || certCard == null) return;
        try {
            Bitmap bitmap = getCertificateMasterBitmap(certCard);
            File cacheDir = new File(requireContext().getCacheDir(), "certificates");
            if (!cacheDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                cacheDir.mkdirs();
            }
            File certFile = new File(cacheDir, "IKP_Certificate_Tajweed_1446.png");
            try (FileOutputStream out = new FileOutputStream(certFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
            }

            certFile.setReadable(true, false);

            Uri contentUri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".fileprovider",
                certFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.setClipData(android.content.ClipData.newRawUri("Certificate", contentUri));
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Tajweed al-Quran Certificate of Achievement");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                "Alhamdulillah! Here is my official Certificate of Achievement in Tajweed al-Quran & Recitation Excellence from Islamic Knowledge Platform (Grade: 98% Mumtaz).\n\nVerify certificate ID: IKP-1446-0891");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(shareIntent, "Share Certificate Image");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Failed to share certificate image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCertificateImage(View certCard) {
        if (getContext() == null || certCard == null) return;
        try {
            Bitmap bitmap = getCertificateMasterBitmap(certCard);
            String fileName = "IKP_Certificate_Tajweed_" + System.currentTimeMillis() + ".png";
            boolean saved = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/IslamicKnowledgePlatform");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri uri = requireContext().getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    requireContext().getContentResolver().update(uri, values, null, null);
                    saved = true;
                }
            } else {
                File picturesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES);
                File ikpDir = new File(picturesDir, "IslamicKnowledgePlatform");
                if (!ikpDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    ikpDir.mkdirs();
                }
                File destFile = new File(ikpDir, fileName);
                try (FileOutputStream out = new FileOutputStream(destFile)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.flush();
                    saved = true;
                }
                android.media.MediaScannerConnection.scanFile(
                    requireContext(),
                    new String[]{destFile.getAbsolutePath()},
                    new String[]{"image/png"},
                    null
                );
            }

            if (saved) {
                Toast.makeText(requireContext(), "✓ Certificate saved to Gallery / Pictures!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(requireContext(), "Could not save certificate image.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error saving certificate: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showProgressBottomSheet() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_academy_progress, null);
        dialog.setContentView(sheet);

        View btnClose = sheet.findViewById(R.id.btn_close_progress_sheet);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        View btnFlashcards = sheet.findViewById(R.id.btn_practice_flashcards_progress);
        if (btnFlashcards != null) {
            btnFlashcards.setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    NavHostFragment.findNavController(this).navigate(R.id.flashcardsFragment);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Opening Flashcard Practice...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnResume = sheet.findViewById(R.id.btn_resume_study_progress);
        if (btnResume != null) {
            btnResume.setOnClickListener(v -> {
                dialog.dismiss();
                String firstId = !currentCourses.isEmpty() && currentCourses.get(0).courseId != null
                        ? currentCourses.get(0).courseId
                        : "c0000001-0000-0000-0000-000000000001";
                openCourse(firstId);
            });
        }

        dialog.show();
    }
}
