package app.ikp.backend.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** List row per approved spec §3.2.14. */
public record UserSummary(UUID userId, String email, String displayName, String status,
                          List<String> roles, Instant registeredAt, Instant lastSeenAt,
                          String deletionState) {
}
