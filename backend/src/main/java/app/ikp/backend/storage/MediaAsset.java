package app.ikp.backend.storage;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Media asset row (M1 V2 spine + M2 V4 pipeline columns). */
@Entity
@Table(name = "media_assets")
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(nullable = false, length = 100)
    private String mime;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "sha256", columnDefinition = "char(64)", unique = true)
    private String sha256;

    @Column(name = "scan_status", nullable = false, length = 20)
    private String scanStatus = "pending";

    @Column(name = "licence_id")
    private Long licenceId;

    @Column(length = 40)
    private String purpose;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @Column(length = 60)
    private String scanner;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (publicId == null) publicId = UUID.randomUUID();
        if (scanStatus == null) scanStatus = "pending";
    }

    public boolean isClean() {
        return "clean".equals(scanStatus);
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public void setPublicId(UUID publicId) { this.publicId = publicId; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getScanStatus() { return scanStatus; }
    public void setScanStatus(String scanStatus) { this.scanStatus = scanStatus; }
    public Long getLicenceId() { return licenceId; }
    public void setLicenceId(Long licenceId) { this.licenceId = licenceId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getScannedAt() { return scannedAt; }
    public void setScannedAt(Instant scannedAt) { this.scannedAt = scannedAt; }
    public String getScanner() { return scanner; }
    public void setScanner(String scanner) { this.scanner = scanner; }
    public Instant getCreatedAt() { return createdAt; }
}
