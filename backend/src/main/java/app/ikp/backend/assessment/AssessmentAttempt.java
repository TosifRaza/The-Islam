package app.ikp.backend.assessment;

import app.ikp.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A user's attempt at a quiz. The presented question set is frozen at start. */
@Entity
@Table(name = "assessment_attempts")
public class AssessmentAttempt {

    public enum Status { in_progress, submitted, expired }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private AssessmentQuiz quiz;

    /** JSON [{questionId, optionIds[]}] fixed at start; grading uses only this set. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String presented = "[]";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.in_progress;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "score_pct")
    private Integer scorePct;

    @Column
    private Boolean passed;

    @PrePersist
    void defaults() {
        if (publicId == null) publicId = UUID.randomUUID();
        if (startedAt == null) startedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public AssessmentQuiz getQuiz() { return quiz; }
    public void setQuiz(AssessmentQuiz quiz) { this.quiz = quiz; }
    public String getPresented() { return presented; }
    public void setPresented(String presented) { this.presented = presented; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Integer getScorePct() { return scorePct; }
    public void setScorePct(Integer scorePct) { this.scorePct = scorePct; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
}
