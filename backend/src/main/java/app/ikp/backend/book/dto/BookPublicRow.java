package app.ikp.backend.book.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public book row per approved spec §3.5.1 (published only; signed cover).
 *  M4: files[] added (additive) so the reader can request a download token
 *  for a specific format — the M3-documented contract gap. */
public record BookPublicRow(UUID bookId, String title, String subtitle, String language,
                            SubjectRef subject, List<AuthorRef> authors, List<String> formats,
                            List<FileRef> files, boolean downloadAllowed, Instant publishedAt) {

    public record SubjectRef(String slug, String name) {
    }

    public record AuthorRef(UUID authorId, String name) {
    }

    public record FileRef(UUID fileId, String format, boolean downloadAllowed, boolean ocrAvailable) {
    }
}
