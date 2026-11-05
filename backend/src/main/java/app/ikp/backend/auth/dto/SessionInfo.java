package app.ikp.backend.auth.dto;

import java.time.Instant;

/** Session registry entry per approved spec §3.1.9. */
public record SessionInfo(Long sessionId, String deviceName, Instant createdAt, Instant expiresAt,
                          boolean current) {
}
