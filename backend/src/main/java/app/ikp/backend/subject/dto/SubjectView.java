package app.ikp.backend.subject.dto;

import java.time.Instant;

/**
 * Subject shape per approved spec §3.4.1/§3.4.9. The stable public identifier
 * is the CAT-* slug (subjects carry no UUID column — the slug IS the stable
 * category ID); `position` is display order only and never a join key.
 */
public record SubjectView(String subjectId, String slug, String name, String nameAr,
                          String description, String imageUrl, int position,
                          String status, Instant updatedAt) {
}
