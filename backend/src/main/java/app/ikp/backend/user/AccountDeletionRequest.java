package app.ikp.backend.user;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Admin-started account deletion workflow (approved spec §3.2.19). */
@Entity
@Table(name = "account_deletion_requests")
public class AccountDeletionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(nullable = false)
    private String state;

    @Column(length = 500)
    private String reason;

    @Column(name = "purge_at")
    private Instant purgeAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    void prePersist() {
        if (publicId == null) publicId = UUID.randomUUID();
        if (state == null) state = "pending_review";
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getPurgeAt() { return purgeAt; }
    public void setPurgeAt(Instant purgeAt) { this.purgeAt = purgeAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
