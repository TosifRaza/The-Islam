package app.ikp.backend.book;

import app.ikp.backend.subject.Subject;
import app.ikp.backend.storage.MediaAsset;
import app.ikp.backend.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Platform-managed library book (approved spec §3.5 / M2 Module D). Lifecycle
 * is server-enforced: draft → in_editorial_review → (in_scholarly_review) →
 * approved → published → archived. An uploaded book is never public merely
 * because it was uploaded — publication is a separate, authorised, audited
 * state transition. Private user uploads are a different domain and can never
 * appear here (personal-library separation).
 */
@Entity
@Table(name = "books")
public class Book {

    public enum Status { draft, in_editorial_review, in_scholarly_review, approved, published, archived }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 300)
    private String subtitle;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 8)
    private String language = "en";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(length = 200)
    private String publisher;

    @Column(length = 100)
    private String edition;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(length = 32)
    private String isbn;

    @Column(name = "copyright_info", length = 500)
    private String copyrightInfo;

    @Column(name = "licence_id")
    private Long licenceId;

    @Column(name = "download_allowed", nullable = false)
    private boolean downloadAllowed = false;

    @Column(name = "scholarly_review_required", nullable = false)
    private boolean scholarlyReviewRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Book.Status status = Book.Status.draft;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_media_id")
    private MediaAsset cover;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<BookAuthor> authors = new ArrayList<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookFile> files = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @PrePersist
    void prePersist() {
        if (publicId == null) publicId = UUID.randomUUID();
        if (status == null) status = Status.draft;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public void setPublicId(UUID publicId) { this.publicId = publicId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getEdition() { return edition; }
    public void setEdition(String edition) { this.edition = edition; }
    public Integer getPublicationYear() { return publicationYear; }
    public void setPublicationYear(Integer publicationYear) { this.publicationYear = publicationYear; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getCopyrightInfo() { return copyrightInfo; }
    public void setCopyrightInfo(String copyrightInfo) { this.copyrightInfo = copyrightInfo; }
    public Long getLicenceId() { return licenceId; }
    public void setLicenceId(Long licenceId) { this.licenceId = licenceId; }
    public boolean isDownloadAllowed() { return downloadAllowed; }
    public void setDownloadAllowed(boolean downloadAllowed) { this.downloadAllowed = downloadAllowed; }
    public boolean isScholarlyReviewRequired() { return scholarlyReviewRequired; }
    public void setScholarlyReviewRequired(boolean scholarlyReviewRequired) { this.scholarlyReviewRequired = scholarlyReviewRequired; }
    public Book.Status getStatus() { return status; }
    public void setStatus(Book.Status status) { this.status = status; }
    public MediaAsset getCover() { return cover; }
    public void setCover(MediaAsset cover) { this.cover = cover; }
    public List<BookAuthor> getAuthors() { return authors; }
    public List<BookFile> getFiles() { return files; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
