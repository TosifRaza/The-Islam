package app.ikp.backend.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M5 Assessment contracts (spec Module 13). Public payloads NEVER carry is_correct or explanations. */
public final class AssessmentDtos {

    private AssessmentDtos() { }

    public record QuizRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 5000) String description,
            @Pattern(regexp = "ar|en|ur|hi") String language,
            UUID courseId,
            String subjectSlug,
            @Min(10) @Max(7200) Integer timeLimitSeconds,
            @Min(0) @Max(100) Integer passScorePct) { }

    public record QuestionRequest(
            @NotBlank String prompt,
            String promptAr,
            @NotBlank String explanation,
            @Min(1) @Max(100) Integer points,
            @Min(0) @Max(10000) Integer sortOrder,
            String changeNote,
            @NotNull @Valid List<OptionRequest> options) { }

    public record OptionRequest(@NotBlank String optionText, boolean correct) { }

    public record OptionAdminView(UUID optionId, String optionText, boolean correct, int sortOrder) { }

    public record QuestionAdminView(UUID questionId, String prompt, String promptAr, String explanation,
                                    int points, int version, String reviewState, int sortOrder,
                                    List<OptionAdminView> options) { }

    public record QuestionVersionView(int version, Map<String, Object> snapshot, String changeNote,
                                      String editedBy, String editedAt) { }

    public record QuizAdminRow(UUID quizId, String title, String language, String status,
                               String subjectSlug, UUID courseId, Integer timeLimitSeconds,
                               int passScorePct, int questionCount, int approvedQuestionCount,
                               String updatedAt) { }

    public record QuizAdminDetail(UUID quizId, String title, String description, String language,
                                  String status, String subjectSlug, UUID courseId,
                                  Integer timeLimitSeconds, int passScorePct,
                                  List<QuestionAdminView> questions) { }

    public record TransitionView(UUID quizId, String before, String after, String changedAt) { }

    // ----- public / member surface -----

    /** Public quiz descriptor: no options, no answers, no explanations. */
    public record QuizPublicRow(UUID quizId, String title, String description, String language,
                                String subjectSlug, UUID courseId, Integer timeLimitSeconds,
                                int passScorePct, int questionCount) { }

    /**
     * One answerable option in the pre-submission payload — deliberately
     * carries ONLY the id and text: no correct flag, no explanation (the
     * grade-facing fields exist solely in AnswerResult after server-side
     * grading).
     */
    public record PublicOption(UUID optionId, String optionText) { }

    /** Pre-submission question: answerable, but with no correctness data. */
    public record PublicQuestion(UUID questionId, String prompt, String promptAr, int points, int sortOrder,
                                 List<PublicOption> options) { }

    public record QuizPublicDetail(UUID quizId, String title, String description, String language,
                                   Integer timeLimitSeconds, int passScorePct, List<PublicQuestion> questions) { }

    public record AttemptStarted(UUID attemptId, UUID quizId, String startedAt,
                                 Integer timeLimitSeconds, List<PublicQuestion> questions) { }

    public record SubmitAnswer(@NotNull UUID questionId, UUID optionId) { }

    public record SubmitRequest(@NotNull @Valid List<SubmitAnswer> answers) { }

    public record AnswerResult(UUID questionId, UUID selectedOptionId, UUID correctOptionId,
                               boolean correct, String explanation) { }

    public record AttemptResult(UUID attemptId, UUID quizId, String status, int scorePct, boolean passed,
                                int passScorePct, List<AnswerResult> answers, String submittedAt) { }

    public record AttemptRow(UUID attemptId, UUID quizId, String quizTitle, String status,
                             Integer scorePct, Boolean passed, String startedAt, String submittedAt) { }

    public record WeakTopic(String subjectSlug, String topicLabel, int wrong, int total, int wrongPct) { }

    public record DeckRequest(@NotBlank @Size(max = 300) String title,
                              @Size(max = 5000) String description,
                              String language, String subjectSlug,
                              @Valid List<CardRequest> cards) { }

    public record CardRequest(@NotBlank String frontText, @NotBlank String backText,
                              @Size(max = 500) String hint, @Min(0) @Max(10000) Integer sortOrder) { }

    public record CardView(UUID cardId, String frontText, String backText, String hint, int sortOrder) { }

    public record DeckRow(UUID deckId, String title, String description, String language,
                          String subjectSlug, int cardCount, String ownerType,
                          String publishState, String updatedAt) { }

    public record DeckDetail(UUID deckId, String title, String description, String language,
                             String subjectSlug, String ownerType, String publishState,
                             List<CardView> cards) { }

    public record DeckPatch(String title, String description, String language,
                            String publishState) { }
}
