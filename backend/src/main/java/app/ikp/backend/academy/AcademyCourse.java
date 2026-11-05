package app.ikp.backend.academy;

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

/** Academy course (M5 spec Module 10). State machine mirrors books §3.19.5. */
@Entity
@Table(name = "academy_courses")
public class AcademyCourse {

    public enum Status { draft, in_editorial_review, approved, published, archived }
    public enum Level { beginner, intermediate, advanced }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 8)
    private String language = "en";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Level level = Level.beginner;

    /** Display only — admins record verified text; the platform never generates credentials. */
    @Column(name = "instructor_display_name", length = 200)
    private String instructorDisplayName;

    @Column(name = "instructor_credentials_note", length = 500)
    private String instructorCredentialsNote;

    @Column(name = "estimated_hours")
    private Integer estimatedHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.draft;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

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
    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Level getLevel() { return level; }
    public void setLevel(Level level) { this.level = level; }
    public String getInstructorDisplayName() { return instructorDisplayName; }
    public void setInstructorDisplayName(String v) { this.instructorDisplayName = v; }
    public String getInstructorCredentialsNote() { return instructorCredentialsNote; }
    public void setInstructorCredentialsNote(String v) { this.instructorCredentialsNote = v; }
    public Integer getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Integer v) { this.estimatedHours = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
