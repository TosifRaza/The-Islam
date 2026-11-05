package app.ikp.backend.storage.dto;

import java.time.Instant;
import java.util.UUID;

/** MediaRef-ish admin payload (approved spec §1 MediaRef / §3.19.3). */
public record MediaView(UUID mediaId, String purpose, String originalFilename, String mime,
                        Long sizeBytes, String scanStatus, String scanner, Instant createdAt) {
}
