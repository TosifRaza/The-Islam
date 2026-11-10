package com.ikp.dev.app.ui.learning;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.UiState;
import com.ikp.dev.app.data.remote.dto.AcademyDtos;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.ui.common.StateView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quiz attempt (M5): one page of questions with radio options, submit for
 * server-side grading, then the result with per-answer explanations. The
 * pre-submission payload never contains correctness data.
 */
public final class QuizAttemptFragment extends Fragment {

    private QuizAttemptViewModel viewModel;
    private StateView stateView;
    private View attemptContent;
    private View resultContent;
    private AcademyDtos.AttemptStartedDto attempt;
    private final Map<String, String> selected = new LinkedHashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quiz_attempt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(QuizAttemptViewModel.class);
        stateView = view.findViewById(R.id.attempt_state);
        attemptContent = view.findViewById(R.id.attempt_content);
        resultContent = view.findViewById(R.id.result_content);
        Bundle args = getArguments();
        viewModel.init(args == null ? null : args.getString("quizId"));
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btn_submit).setOnClickListener(v -> {
            if (attempt != null) viewModel.submit(attempt, selected);
        });
        viewModel.start().observe(getViewLifecycleOwner(), this::renderStart);
        viewModel.result().observe(getViewLifecycleOwner(), this::renderResult);

        if (!ServiceLocator.tokenStore().hasSession()) {
            stateView.setVisibility(View.VISIBLE);
            stateView.renderAuthRequired(() -> {
                try {
                    NavHostFragment.findNavController(this).navigate(R.id.action_global_session_expired);
                } catch (Exception ignored) {
                }
            });
            return;
        }

        viewModel.begin();
    }

    private void renderStart(UiState<AcademyDtos.AttemptStartedDto> state) {
        switch (state.kind) {
            case LOADING -> stateView.renderLoading();
            case SUCCESS -> {
                stateView.setVisibility(View.GONE);
                attemptContent.setVisibility(View.VISIBLE);
                resultContent.setVisibility(View.GONE);
                attempt = state.data;
                selected.clear();
                LinearLayout box = requireView().findViewById(R.id.questions_container);
                box.removeAllViews();
                if (attempt.timeLimitSeconds != null) {
                    TextView lim = requireView().findViewById(R.id.tv_time_limit);
                    lim.setVisibility(View.VISIBLE);
                    lim.setText(getString(R.string.quiz_time_limit_fmt, attempt.timeLimitSeconds));
                }
                int n = 1;
                for (AcademyDtos.QuestionPublicDto q : attempt.questions) {
                    box.addView(questionView(n++, q));
                }
            }
            case EMPTY -> stateView.renderEmpty();
            case ERROR -> {
                if (state.error != null && ("AUTH-002".equals(state.error.code) || "AUTH-005".equals(state.error.code))) {
                    stateView.setVisibility(View.VISIBLE);
                    stateView.renderAuthRequired(() -> {
                        try {
                            NavHostFragment.findNavController(this).navigate(R.id.action_global_session_expired);
                        } catch (Exception ignored) {
                        }
                    });
                } else {
                    stateView.setVisibility(View.VISIBLE);
                    stateView.renderError(state.error);
                    stateView.setOnRetry(viewModel::begin);
                }
            }
        }
    }

    private View questionView(int number, AcademyDtos.QuestionPublicDto q) {
        View v = getLayoutInflater().inflate(R.layout.item_quiz_question,
                requireView().findViewById(R.id.questions_container), false);
        ((TextView) v.findViewById(R.id.tv_question_prompt)).setText(number + ". " + q.prompt);
        RadioGroup rg = v.findViewById(R.id.rg_options);
        if (q.options != null) {
            for (AcademyDtos.OptionPublicDto o : q.options) {
                RadioButton rb = new RadioButton(requireContext());
                rb.setText(o.optionText);
                rb.setOnCheckedChangeListener((b, checked) -> {
                    if (checked) selected.put(q.questionId, o.optionId);
                });
                rg.addView(rb);
            }
        }
        return v;
    }

    private void renderResult(UiState<AcademyDtos.AttemptResultDto> state) {
        switch (state.kind) {
            case LOADING -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderLoading();
            }
            case SUCCESS -> {
                stateView.setVisibility(View.GONE);
                attemptContent.setVisibility(View.GONE);
                resultContent.setVisibility(View.VISIBLE);
                AcademyDtos.AttemptResultDto r = state.data;
                ((TextView) requireView().findViewById(R.id.tv_score)).setText(
                        getString(R.string.quiz_score_fmt, r.scorePct == null ? 0 : r.scorePct));
                ((TextView) requireView().findViewById(R.id.tv_pass_state)).setText(
                        getString(Boolean.TRUE.equals(r.passed)
                                ? R.string.quiz_passed_fmt : R.string.quiz_failed_fmt,
                                r.passScorePct == null ? 0 : r.passScorePct));
                LinearLayout box = requireView().findViewById(R.id.answers_container);
                box.removeAllViews();
                if (r.answers != null) {
                    for (AcademyDtos.AnswerResultDto a : r.answers) {
                        View row = getLayoutInflater().inflate(R.layout.item_answer_result,
                                requireView().findViewById(R.id.answers_container), false);
                        ((TextView) row.findViewById(R.id.tv_result_verdict)).setText(
                                Boolean.TRUE.equals(a.correct) ? R.string.quiz_correct : R.string.quiz_wrong);
                        ((TextView) row.findViewById(R.id.tv_result_explanation)).setText(
                                a.explanation == null ? "" : a.explanation);
                        box.addView(row);
                    }
                }
            }
            case EMPTY -> { /* not reached for results */ }
            case ERROR -> {
                stateView.setVisibility(View.VISIBLE);
                stateView.renderError(state.error);
                Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
