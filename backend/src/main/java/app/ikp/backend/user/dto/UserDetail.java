package app.ikp.backend.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** User detail per approved spec §3.2.15 (+ deletion workflow rows). */
public record UserDetail(UUID userId, String email, String displayName, String status,
                         String deletionState, List<String> roles,
                         Instant registeredAt, Instant lastSeenAt,
                         List<DeletionRequestView> deletionRequests) {

    public record DeletionRequestView(UUID requestId, String state, String reason,
                                      Instant requestedAt, Instant purgeAt) {
    }
}
