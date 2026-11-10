package com.ikp.dev.app.ui.academy;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.NavOptions;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.common.StateView;

/**
 * Course detail (M5): meta + enrol + chapters/lessons. Text lessons open the
 * body; quiz lessons start an attempt; flashcard lessons open practice; PDF
 * material opens via the signed media URL; video is an external reference.
 */
public final class CourseDetailFragment extends Fragment {

    private CourseDetailViewModel viewModel;
    private StateView stateView;
    private View content;
    private String currentCourseId;
    private String currentCourseTitle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_course_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CourseDetailViewModel.class);
        stateView = view.findViewById(R.id.course_state);
        content = view.findViewById(R.id.course_content);
        Bundle args = getArguments();
        viewModel.init(args == null ? null : args.getString("courseId"));
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btn_enrol).setOnClickListener(v -> {
            if (!ServiceLocator.sessionManager().isSignedIn()) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Sign In Required to Enrol")
                        .setMessage("Enrolling in courses and saving your progress requires an account.\n\nWould you like to sign in or register now?")
                        .setPositiveButton("Sign In / Register", (d, which) -> {
                            try {
                                NavHostFragment.findNavController(this).navigate(R.id.action_global_session_expired);
                            } catch (Exception ignored) {
                            }
                        })
                        .setNegativeButton("Continue Browsing", null)
                        .show();
                return;
            }
            viewModel.enrol();
        });
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.progress().observe(getViewLifecycleOwner(), p -> {
            if (p == null) return;
            TextView tv = view.findViewById(R.id.tv_progress);
            tv.setVisibility(View.VISIBLE);
            String status = p.status == null ? "" : p.status;
            tv.setText(getString(R.string.academy_progress_fmt,
                    p.completedLessons == null ? 0 : p.completedLessons,
                    p.totalLessons == null ? 0 : p.totalLessons,
                    p.percent == null ? 0 : p.percent, status));
        });
        viewModel.enrolDone().observe(getViewLifecycleOwner(), ok -> {
            Toast.makeText(requireContext(), ok ? R.string.academy_enrolled : R.string.error_generic,
                    Toast.LENGTH_SHORT).show();
        });
        viewModel.reload();
    }

    private void render(UiState<AcademyDtos.CourseDetailDto> state) {
        switch (state.kind) {
            case LOADING -> stateView.renderLoading();
            case SUCCESS -> {
                stateView.setVisibility(View.GONE);
                content.setVisibility(View.VISIBLE);
                AcademyDtos.CourseDetailDto c = state.data;
                currentCourseId = c.courseId;
                currentCourseTitle = c.title;
                TextView title = requireView().findViewById(R.id.tv_course_title);
                title.setText(c.title);
                String meta = (c.subject == null ? "" : c.subject.name + " · ")
                        + (c.level == null ? "" : c.level + " · ")
                        + (c.estimatedHours == null ? "" : "~" + c.estimatedHours + "h");
                ((TextView) requireView().findViewById(R.id.tv_course_meta)).setText(meta);
                TextView creds = requireView().findViewById(R.id.tv_credentials);
                if (c.instructorCredentialsNote != null && !c.instructorCredentialsNote.isEmpty()) {
                    creds.setVisibility(View.VISIBLE);
                    creds.setText(getString(R.string.academy_credentials_fmt, c.instructorCredentialsNote));
                }
                ((TextView) requireView().findViewById(R.id.tv_description)).setText(
                        c.description == null ? "" : c.description);
                LinearLayout chapters = requireView().findViewById(R.id.chapters_container);
                chapters.removeAllViews();
                if (c.chapters != null) {
                    for (AcademyDtos.ChapterPublicDto ch : c.chapters) {
                        chapters.addView(chapterHeader(ch));
                        if (ch.lessons != null) {
                            for (AcademyDtos.LessonPublicDto l : ch.lessons) {
                                chapters.addView(lessonRow(l));
                            }
                        }
                    }
                }
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

    private View chapterHeader(AcademyDtos.ChapterPublicDto ch) {
        TextView tv = new TextView(requireContext());
        tv.setTextAppearance(R.style.TextAppearance_IKP_SectionHeader);
        tv.setPadding(0, dp(12), 0, dp(4));
        tv.setText(ch.title == null ? "" : ch.title);
        return tv;
    }

    private View lessonRow(AcademyDtos.LessonPublicDto l) {
        View row = getLayoutInflater().inflate(R.layout.item_lesson_row,
                requireView().findViewById(R.id.chapters_container), false);
        ((TextView) row.findViewById(R.id.tv_lesson_title)).setText(l.title);
        ((TextView) row.findViewById(R.id.tv_lesson_type)).setText(l.lessonType);
        Button complete = row.findViewById(R.id.btn_complete);
        boolean signedIn = ServiceLocator.sessionManager().isSignedIn();
        complete.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        complete.setOnClickListener(v -> viewModel.completeLesson(l.lessonId));
        row.setOnClickListener(v -> openLesson(l));
        return row;
    }

    private void openLesson(AcademyDtos.LessonPublicDto l) {
        String type = l.lessonType == null ? "text" : l.lessonType;
        switch (type) {
            case "text" -> {
                Bundle args = new Bundle();
                args.putString("lessonId", l.lessonId);
                args.putString("courseId", currentCourseId);
                args.putString("lessonTitle", l.title);
                args.putString("lessonBody", l.bodyText);
                args.putString("courseTitle", currentCourseTitle);
                NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
                NavHostFragment.findNavController(this).navigate(R.id.lessonViewerFragment, args, opts);
            }
            case "quiz" -> {
                if (l.quizId != null) {
                    if (!ServiceLocator.sessionManager().isSignedIn()) {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Sign In Required for Quiz")
                                .setMessage("Taking lesson quizzes requires an account to record your score.\n\nWould you like to sign in or register now?")
                                .setPositiveButton("Sign In / Register", (d, which) -> {
                                    try {
                                        NavHostFragment.findNavController(this).navigate(R.id.action_global_session_expired);
                                    } catch (Exception ignored) {
                                    }
                                })
                                .setNegativeButton("Continue Browsing", null)
                                .show();
                        return;
                    }
                    Bundle args = new Bundle();
                    args.putString("quizId", l.quizId);
                    NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
                    NavHostFragment.findNavController(this).navigate(R.id.quizAttemptFragment, args, opts);
                }
            }
            case "flashcards" -> {
                if (l.flashcardDeckId != null) {
                    Bundle args = new Bundle();
                    args.putString("deckId", l.flashcardDeckId);
                    args.putString("deckTitle", l.title);
                    NavOptions opts = new NavOptions.Builder().setLaunchSingleTop(true).build();
                    NavHostFragment.findNavController(this).navigate(R.id.flashcardPracticeFragment, args, opts);
                }
            }
            case "pdf" -> {
                if (l.mediaUrl != null) {
                    openExternal(l.mediaUrl);
                }
            }
            case "video" -> {
                if (l.videoUrl != null) {
                    openExternal(l.videoUrl);
                }
            }
            default -> { /* unknown type: no-op */ }
        }
    }

    private void openExternal(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
