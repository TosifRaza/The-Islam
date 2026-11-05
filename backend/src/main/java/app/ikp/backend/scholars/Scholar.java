package app.ikp.backend.scholars;

import app.ikp.backend.storage.MediaAsset;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Scholar profile (M5 spec Module 14). Qualifications are admin-recorded facts
 * — the platform never generates them. verification_status transitions
 * (unverified → verified → revoked) are audited and restricted to
 * scholars.manage holders.
 */
@Entity
@Table(name = "scholars")
public class Scholar {

    public enum VerificationStatus { unverified, verified, revoked }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "name_ar", length = 200)
    private String nameAr;

    @Column(columnDefinition = "text")
    private String biography;

    @Column(length = 1000)
    private String qualifications;

    @Column(name = "areas_of_expertise", length = 500)
    private String areasOfExpertise;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "photo_media_id")
    private MediaAsset photo;

    /** Optional platform account link — enables review-history resolution. */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", unique = true)
    private User linkedUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.unverified;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private User createdBy;

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameAr() { return nameAr; }
    public void setNameAr(String nameAr) { this.nameAr = nameAr; }
    public String getBiography() { return biography; }
    public void setBiography(String biography) { this.biography = biography; }
    public String getQualifications() { return qualifications; }
    public void setQualifications(String qualifications) { this.qualifications = qualifications; }
    public String getAreasOfExpertise() { return areasOfExpertise; }
    public void setAreasOfExpertise(String areasOfExpertise) { this.areasOfExpertise = areasOfExpertise; }
    public MediaAsset getPhoto() { return photo; }
    public void setPhoto(MediaAsset photo) { this.photo = photo; }
    public User getLinkedUser() { return linkedUser; }
    public void setLinkedUser(User linkedUser) { this.linkedUser = linkedUser; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }
    public User getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(User verifiedBy) { this.verifiedBy = verifiedBy; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
