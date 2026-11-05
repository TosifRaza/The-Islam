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
 * Flashcard deck. Two strictly separated owners share one schema:
 * owner_type=admin (platform decks, publish-gated, managed with
 * assessment.manage) and owner_type=user (personal decks, private by
 * construction — every read path filters owner_user_id server-side).
 */
@Entity
@Table(name = "flashcard_decks")
public class FlashcardDeck {

    public enum OwnerType { admin, user }
    public enum PublishState { draft, approved, published, disabled }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 10)
    private OwnerType ownerType = OwnerType.user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_user_id")
    private User ownerUser;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id")
    private app.ikp.backend.subject.Subject subject;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 8)
    private String language = "en";

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_state", nullable = false, length = 20)
    private PublishState publishState = PublishState.draft;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "deck", fetch = FetchType.EAGER, cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @jakarta.persistence.OrderBy("sort_order ASC")
    private List<Flashcard> cards = new ArrayList<>();

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
    public OwnerType getOwnerType() { return ownerType; }
    public void setOwnerType(OwnerType ownerType) { this.ownerType = ownerType; }
    public User getOwnerUser() { return ownerUser; }
    public void setOwnerUser(User ownerUser) { this.ownerUser = ownerUser; }
    public app.ikp.backend.subject.Subject getSubject() { return subject; }
    public void setSubject(app.ikp.backend.subject.Subject subject) { this.subject = subject; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public PublishState getPublishState() { return publishState; }
    public void setPublishState(PublishState publishState) { this.publishState = publishState; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public List<Flashcard> getCards() { return cards; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
