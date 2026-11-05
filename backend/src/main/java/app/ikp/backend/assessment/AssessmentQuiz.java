package app.ikp.backend.assessment;

import app.ikp.backend.academy.AcademyCourse;
import app.ikp.backend.subject.Subject;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Quiz / assessment (M5 spec Module 13). Publication is gated on ALL its questions being approved. */
@Entity
@Table(name = "assessment_quizzes")
public class AssessmentQuiz {

    public enum Status { draft, in_editorial_review, approved, published, archived }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id")
    private AcademyCourse course;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 8)
    private String language = "en";

    @Column(name = "time_limit_seconds")
    private Integer timeLimitSeconds;

    @Column(name = "pass_score_pct", nullable = false)
    private Integer passScorePct = 60;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.draft;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

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
    public AcademyCourse getCourse() { return course; }
    public void setCourse(AcademyCourse course) { this.course = course; }
    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Integer getTimeLimitSeconds() { return timeLimitSeconds; }
    public void setTimeLimitSeconds(Integer v) { this.timeLimitSeconds = v; }
    public Integer getPassScorePct() { return passScorePct; }
    public void setPassScorePct(Integer v) { this.passScorePct = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
