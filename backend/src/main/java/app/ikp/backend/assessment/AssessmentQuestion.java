package app.ikp.backend.assessment;

import app.ikp.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Versioned MCQ question. Every edit bumps {@code version} and appends an
 * immutable snapshot row; publishing a quiz requires review_state=approved on
 * every question (review recorded in content_review_records, reviewer ≠ author).
 */
@Entity
@Table(name = "assessment_questions")
public class AssessmentQuestion {

    public enum ReviewState { draft, pending_review, approved }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private AssessmentQuiz quiz;

    @Column(name = "question_type", nullable = false, length = 20)
    private String questionType = "mcq";

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(name = "prompt_ar", columnDefinition = "text")
    private String promptAr;

    /** Served only inside submitted attempt results — never pre-submission. */
    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    @Column(nullable = false)
    private Integer points = 1;

    @Column(nullable = false)
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_state", nullable = false, length = 20)
    private ReviewState reviewState = ReviewState.draft;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "question", fetch = FetchType.EAGER, cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @jakarta.persistence.OrderBy("sort_order ASC")
    private List<AssessmentQuestionOption> options = new ArrayList<>();

    @CreationTimestamp
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    @PrePersist
    void defaults() {
        if (publicId == null) publicId = UUID.randomUUID();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public AssessmentQuiz getQuiz() { return quiz; }
    public void setQuiz(AssessmentQuiz quiz) { this.quiz = quiz; }
    public String getQuestionType() { return questionType; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getPromptAr() { return promptAr; }
    public void setPromptAr(String promptAr) { this.promptAr = promptAr; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public ReviewState getReviewState() { return reviewState; }
    public void setReviewState(ReviewState reviewState) { this.reviewState = reviewState; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public List<AssessmentQuestionOption> getOptions() { return options; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
