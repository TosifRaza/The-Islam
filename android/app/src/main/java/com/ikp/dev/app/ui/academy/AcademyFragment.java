package com.ikp.dev.app.ui.academy;

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
import java.util.List;

/**
 * Academy tab (Phases 1-3):
 * Full Islamic learning platform — course catalogue with category chips,
 * debounced search, continue-learning rail, flashcard decks, and daily lesson.
 */
public final class AcademyFragment extends Fragment {

    private AcademyViewModel viewModel;
    private StateView stateView;

    private CategoryChipAdapter categoryAdapter;
    private CourseCardAdapter courseAdapter;
    private FlashcardDeckAdapter flashcardAdapter;
    private ContinueLearningAdapter continueAdapter;

    private EditText searchEditText;
    private ImageButton clearSearchBtn;
    private LinearLayout continueContainer;
    private TextView activeFilterHint;
    private TextView noCoursesHint;
    private TextView signinHint;

    private View dailyLessonCard;
    private TextView dailyTitle;
    private TextView dailyPreview;
    private TextView dailyDuration;
    private Button dailyStartBtn;

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
        continueContainer = view.findViewById(R.id.container_continue);
        activeFilterHint = view.findViewById(R.id.tv_active_filter_hint);
        noCoursesHint = view.findViewById(R.id.tv_no_courses);
        signinHint = view.findViewById(R.id.tv_signin_hint);

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
            viewModel.filterByCategory(slug);
            if (slug == null || slug.isEmpty()) {
                activeFilterHint.setText("");
            } else {
                activeFilterHint.setText("Filtered: " + name);
            }
        });
        categoriesRv.setAdapter(categoryAdapter);

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
                Toast.makeText(requireContext(), "Saved " + course.title + " to bookmarks", Toast.LENGTH_SHORT).show();
            }
        });
        coursesRv.setAdapter(courseAdapter);

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

        // Filter button dialog
        view.findViewById(R.id.btn_filter_dialog).setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Select categories from the category bar above", Toast.LENGTH_SHORT).show();
        });

        // Quick Access Buttons
        view.findViewById(R.id.btn_quick_my_courses).setOnClickListener(v -> {
            boolean signedIn = ServiceLocator.sessionManager().isSignedIn();
            if (signedIn) {
                if (continueContainer.getVisibility() == View.VISIBLE) {
                    continueContainer.getParent().requestChildFocus(continueContainer, continueContainer);
                } else {
                    Toast.makeText(requireContext(), "You have not enrolled in any courses yet.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireContext(), R.string.academy_signin_hint, Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_quick_downloads).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.coming_soon_feature, Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.btn_quick_certificates).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Complete all lessons and quizzes to earn verified certificates.", Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.btn_quick_progress).setOnClickListener(v -> {
            boolean signedIn = ServiceLocator.sessionManager().isSignedIn();
            if (signedIn) {
                Toast.makeText(requireContext(), "Your learning progress is synchronized with your profile.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), R.string.academy_signin_hint, Toast.LENGTH_SHORT).show();
            }
        });

        // Flashcards "View All"
        view.findViewById(R.id.btn_view_all_flashcards).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.flashcardsFragment));

        // Header bookmark button
        view.findViewById(R.id.btn_header_bookmark).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Bookmarked courses can be accessed here.", Toast.LENGTH_SHORT).show());

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
                stateView.setVisibility(View.VISIBLE);
                stateView.renderLoading();
            }
            case SUCCESS -> {
                stateView.setVisibility(View.GONE);
                AcademyViewModel.AcademyData data = state.data;

                // Categories
                if (data.categories != null && !data.categories.isEmpty()) {
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
                if (data.courses != null && !data.courses.isEmpty()) {
                    noCoursesHint.setVisibility(View.GONE);
                    courseAdapter.submitList(data.courses);
                } else {
                    noCoursesHint.setVisibility(View.VISIBLE);
                    courseAdapter.submitList(new ArrayList<>());
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
}
