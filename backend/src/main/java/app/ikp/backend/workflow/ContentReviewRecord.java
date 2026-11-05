package app.ikp.backend.workflow;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Review decision row (M1 V2 spine) — reviewer identity + decision + notes. */
@Entity
@Table(name = "content_review_records")
public class ContentReviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Stage stage;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(name = "decided_at", updatable = false)
    private Instant decidedAt;

    public enum Stage { editorial_review, scholarly_review }

    public enum Decision { approved, rejected, changes_requested }

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public Stage getStage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Decision getDecision() { return decision; }
    public void setDecision(Decision decision) { this.decision = decision; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getDecidedAt() { return decidedAt; }
}
