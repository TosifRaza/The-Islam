package app.ikp.backend.book;

import app.ikp.backend.storage.MediaAsset;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Platform book file (PDF/EPUB) — must reference a scan-clean media asset. */
@Entity
@Table(name = "book_files")
public class BookFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", unique = true)
    private MediaAsset media;

    @Column(nullable = false, length = 10)
    private String format;

    @Column(name = "download_allowed", nullable = false)
    private boolean downloadAllowed = false;

    @Column(name = "ocr_available", nullable = false)
    private boolean ocrAvailable = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public MediaAsset getMedia() { return media; }
    public void setMedia(MediaAsset media) { this.media = media; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public boolean isDownloadAllowed() { return downloadAllowed; }
    public void setDownloadAllowed(boolean downloadAllowed) { this.downloadAllowed = downloadAllowed; }
    public boolean isOcrAvailable() { return ocrAvailable; }
    public void setOcrAvailable(boolean ocrAvailable) { this.ocrAvailable = ocrAvailable; }
    public Instant getCreatedAt() { return createdAt; }
}
