package app.ikp.backend.fatwa;

import app.ikp.backend.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Fatwa record — dedicated strict workflow (approved spec §3.17, normative
 * state machine): draft → in_review → approved → published → archived;
 * reject → draft; edit after approval resets to draft. Publish is STATE-GATED
 * server-side: requires approved state + a recorded Scholar Reviewer approval
 * + publisher ≠ approving reviewer. Violations → WORKFLOW-001, nothing is
 * published, the attempt is audited. No actual Fatwa content is created by
 * M2 — records hold synthetic development fixtures only.
 */
@Entity
@Table(name = "fatwas")
public class Fatwa {

    public enum State { draft, in_review, approved, published, archived }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "answer_draft", nullable = false, columnDefinition = "text")
    private String answerDraft;

    @Column(columnDefinition = "text")
    private String answer;

    @Column(length = 60)
    private String category;

    @Column(nullable = false, length = 8)
    private String language = "ar";

    // jsonb column — the JDBC type code makes Hibernate bind the String as a
    // jsonb parameter (a plain varchar bind is rejected by PostgreSQL).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_refs", nullable = false, columnDefinition = "jsonb")
    private String sourceRefs = "[]";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Fatwa.State state = Fatwa.State.draft;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "assigned_reviewer_id")
    private Long assignedReviewerId;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_notes", columnDefinition = "text")
    private String decisionNotes;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int version = 1;

    @CreationTimestamp
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (publicId == null) publicId = UUID.randomUUID();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswerDraft() { return answerDraft; }
    public void setAnswerDraft(String answerDraft) { this.answerDraft = answerDraft; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getSourceRefs() { return sourceRefs; }
    public void setSourceRefs(String sourceRefs) { this.sourceRefs = sourceRefs; }
    public Fatwa.State getState() { return state; }
    public void setState(Fatwa.State state) { this.state = state; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Long getAssignedReviewerId() { return assignedReviewerId; }
    public void setAssignedReviewerId(Long assignedReviewerId) { this.assignedReviewerId = assignedReviewerId; }
    public Long getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionNotes() { return decisionNotes; }
    public void setDecisionNotes(String decisionNotes) { this.decisionNotes = decisionNotes; }
    public Long getPublishedBy() { return publishedBy; }
    public void setPublishedBy(Long publishedBy) { this.publishedBy = publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
