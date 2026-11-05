package app.ikp.backend.user;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Maps the M1 V1 identity schema (Flyway owns the DDL; Hibernate never alters it). */
@Entity
@Table(name = "users")
public class User {

    public enum Status { active, suspended }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String status;

    @Column(name = "deletion_state", nullable = false)
    private String deletionState = "none";

    @Column(name = "language_pref", nullable = false)
    private String languagePref = "en";

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (publicId == null) publicId = UUID.randomUUID();
        if (status == null) status = Status.active.name();
    }

    public boolean isActive() {
        return Status.active.name().equals(status);
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public void setPublicId(UUID publicId) { this.publicId = publicId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDeletionState() { return deletionState; }
    public void setDeletionState(String deletionState) { this.deletionState = deletionState; }
    public String getLanguagePref() { return languagePref; }
    public void setLanguagePref(String languagePref) { this.languagePref = languagePref; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
