package app.ikp.backend.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Principal profile + flattened permissions per approved spec §3.1.8. */
public record MeResponse(UUID userId, String email, String displayName, String language,
                         String status, List<String> roles, List<String> permissions,
                         Instant createdAt) {
}
