package app.ikp.backend.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** Graded answer of one question within one attempt (server-side grading only). */
@Entity
@Table(name = "assessment_attempt_answers")
@IdClass(AssessmentAttemptAnswer.PK.class)
public class AssessmentAttemptAnswer {

    public static final class PK implements Serializable {
        public Long attemptId;
        public Long questionId;

        public PK() { }
        public PK(Long attemptId, Long questionId) { this.attemptId = attemptId; this.questionId = questionId; }

        @Override public boolean equals(Object o) {
            return o instanceof PK other && Objects.equals(attemptId, other.attemptId)
                    && Objects.equals(questionId, other.questionId);
        }
        @Override public int hashCode() { return Objects.hash(attemptId, questionId); }
    }

    @Id
    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Id
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "attempt_id", insertable = false, updatable = false)
    private AssessmentAttempt attempt;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private AssessmentQuestion question;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "selected_option_id")
    private AssessmentQuestionOption selectedOption;

    @Column(nullable = false)
    private boolean correct = false;

    public AssessmentAttemptAnswer() { }

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public AssessmentAttempt getAttempt() { return attempt; }
    public AssessmentQuestion getQuestion() { return question; }
    public void setAttempt(AssessmentAttempt attempt) { this.attempt = attempt; }
    public void setQuestion(AssessmentQuestion question) { this.question = question; }
    public AssessmentQuestionOption getSelectedOption() { return selectedOption; }
    public void setSelectedOption(AssessmentQuestionOption selectedOption) { this.selectedOption = selectedOption; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
}
