package com.ikp.dev.app.ui.academy;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.Locale;

public final class LessonViewerFragment extends Fragment {

    private String lessonId;
    private String courseId;
    private String lessonTitle;
    private String lessonBody;
    private String courseTitle;

    private boolean isPlayingAudio = false;
    private int audioProgress = 0;
    private final Handler audioHandler = new Handler(Looper.getMainLooper());
    private Runnable audioRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lesson_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            lessonId = args.getString("lessonId");
            courseId = args.getString("courseId");
            lessonTitle = args.getString("lessonTitle");
            lessonBody = args.getString("lessonBody");
            courseTitle = args.getString("courseTitle");
        }

        ImageButton backBtn = view.findViewById(R.id.btn_lesson_back);
        backBtn.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        TextView courseTitleView = view.findViewById(R.id.tv_lesson_course_title);
        courseTitleView.setText(courseTitle != null ? courseTitle : "Islamic Learning");

        TextView lessonTitleView = view.findViewById(R.id.tv_lesson_view_title);
        lessonTitleView.setText(lessonTitle != null ? lessonTitle : "Lesson");

        TextView bodyView = view.findViewById(R.id.tv_lesson_body);
        bodyView.setText(lessonBody != null ? lessonBody : "No content available for this lesson.");

        // Audio player simulation
        ImageButton playBtn = view.findViewById(R.id.btn_play_audio);
        SeekBar seekBar = view.findViewById(R.id.sb_audio_progress);
        TextView timerView = view.findViewById(R.id.tv_audio_timer);

        audioRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlayingAudio && audioProgress < 100) {
                    audioProgress += 2;
                    seekBar.setProgress(audioProgress);
                    int seconds = (int) (audioProgress * 1.8);
                    timerView.setText(String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60));
                    audioHandler.postDelayed(this, 1000);
                } else if (audioProgress >= 100) {
                    isPlayingAudio = false;
                    playBtn.setImageResource(R.drawable.ic_play_triangle_white);
                }
            }
        };

        playBtn.setOnClickListener(v -> {
            isPlayingAudio = !isPlayingAudio;
            if (isPlayingAudio) {
                playBtn.setImageResource(R.drawable.ic_verified);
                audioHandler.post(audioRunnable);
            } else {
                playBtn.setImageResource(R.drawable.ic_play_triangle_white);
                audioHandler.removeCallbacks(audioRunnable);
            }
        });

        // Mark as Complete button
        Button markCompleteBtn = view.findViewById(R.id.btn_lesson_mark_complete);
        markCompleteBtn.setOnClickListener(v -> {
            boolean signedIn = ServiceLocator.sessionManager().isSignedIn();
            if (!signedIn) {
                Toast.makeText(requireContext(), R.string.academy_signin_hint, Toast.LENGTH_SHORT).show();
                return;
            }

            if (lessonId != null) {
                markCompleteBtn.setEnabled(false);
                ServiceLocator.executors().io.execute(() -> {
                    Outcome<AcademyDtos.ProgressDto> outcome =
                            ServiceLocator.academyRepository().completeLesson(lessonId);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            if (outcome.isSuccess()) {
                                markCompleteBtn.setText("Completed ✓");
                                markCompleteBtn.setBackgroundColor(0xFF2E7D32);
                                Toast.makeText(requireContext(), "Lesson marked as complete! +50 Points", Toast.LENGTH_SHORT).show();
                            } else {
                                markCompleteBtn.setEnabled(true);
                                Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            } else {
                markCompleteBtn.setText("Completed ✓");
                Toast.makeText(requireContext(), "Lesson completed!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        audioHandler.removeCallbacks(audioRunnable);
    }
}
