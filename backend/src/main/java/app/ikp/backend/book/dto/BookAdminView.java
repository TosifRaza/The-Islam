package app.ikp.backend.book.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin book view — complete metadata + file info (M2 Module D). */
public record BookAdminView(UUID bookId, String title, String subtitle, String description,
                            String language, SubjectRef subject, List<AuthorRef> authors,
                            String publisher, String edition, Integer publicationYear,
                            Integer pageCount, String isbn, String copyrightInfo,
                            LicenceRef licence, boolean downloadAllowed,
                            boolean scholarlyReviewRequired, String status,
                            FileRef cover, List<FileRef> files,
                            Instant createdAt, Instant updatedAt, Instant publishedAt) {

    public record SubjectRef(String slug, String name) {
    }

    public record AuthorRef(UUID authorId, String name) {
    }

    public record LicenceRef(Long licenceId, String code, String title, boolean downloadAllowedByLicence) {
    }

    public record FileRef(UUID fileId, String format, String mime, Long sizeBytes,
                          boolean downloadAllowed, String scanStatus) {
    }
}
