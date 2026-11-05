package app.ikp.backend.assessment;

import app.ikp.backend.user.User;
import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Append-only version history for question edits (never updated or deleted). */
@Entity
@Table(name = "assessment_question_versions")
public class AssessmentQuestionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private AssessmentQuestion question;

    @Column(nullable = false)
    private Integer version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "change_note", length = 500)
    private String changeNote;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "edited_by")
    private User editedBy;

    @CreationTimestamp
    @Column(name = "edited_at", insertable = false, updatable = false)
    private Instant editedAt;

    public Long getId() { return id; }
    public AssessmentQuestion getQuestion() { return question; }
    public void setQuestion(AssessmentQuestion question) { this.question = question; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
    public User getEditedBy() { return editedBy; }
    public void setEditedBy(User editedBy) { this.editedBy = editedBy; }
    public Instant getEditedAt() { return editedAt; }
}
