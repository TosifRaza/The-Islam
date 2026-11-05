package app.ikp.backend.research;

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

/**
 * Research article (M5 spec Module 11). sourceTier is the mandatory three-way
 * classification: primary sources / secondary research / editorial commentary.
 * Primary and secondary articles require scholarly review before publication.
 */
@Entity
@Table(name = "research_articles")
public class ResearchArticle {

    public enum Status { draft, in_editorial_review, in_scholarly_review, approved, published, archived }
    public enum SourceTier { primary, secondary, editorial }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private ResearchTopic topic;

    @Column(nullable = false, length = 400)
    private String title;

    @Column(name = "title_ar", length = 400)
    private String titleAr;

    @Column(length = 1000)
    private String summary;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false, length = 8)
    private String language = "en";

    @Column(name = "author_display_name", length = 200)
    private String authorDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_tier", nullable = false, length = 20)
    private SourceTier sourceTier = SourceTier.secondary;

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
    public ResearchTopic getTopic() { return topic; }
    public void setTopic(ResearchTopic topic) { this.topic = topic; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTitleAr() { return titleAr; }
    public void setTitleAr(String titleAr) { this.titleAr = titleAr; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getAuthorDisplayName() { return authorDisplayName; }
    public void setAuthorDisplayName(String authorDisplayName) { this.authorDisplayName = authorDisplayName; }
    public SourceTier getSourceTier() { return sourceTier; }
    public void setSourceTier(SourceTier sourceTier) { this.sourceTier = sourceTier; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
